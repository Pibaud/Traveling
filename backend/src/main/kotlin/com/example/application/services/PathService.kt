package com.example.application.services

import com.example.application.DatabaseFactory.dbQuery
import com.example.application.ItineraryLikes
import com.example.application.UserFollows
import com.example.application.models.GeneratePathRequest
import com.example.application.models.ItineraryResponse
import com.example.application.models.Place
import com.example.application.models.PlaceCategory
import com.example.application.models.SavePathRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import kotlin.math.*
import kotlin.math.roundToInt
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.count
import com.example.application.Itineraries
import com.example.application.Steps

object PathService {

    // ---------------------------------------------------------------------------
    // GÉNÉRATION D'ITINÉRAIRES
    // ---------------------------------------------------------------------------

    suspend fun generatePath(req: GeneratePathRequest): List<ItineraryResponse> = dbQuery {

        // ── 1. Chargement de tous les lieux ────────────────────────────────────
        val sql = """
            SELECT id, name, category,
                   ST_Y(location::geometry) AS lat,
                   ST_X(location::geometry) AS lng,
                   price, duration, effort, meteo,
                   opening_hours::text AS opening_hours
            FROM places
        """.trimIndent()

        val allPlaces = mutableListOf<Place>()
        TransactionManager.current().exec(sql) { rs ->
            while (rs.next()) {
                allPlaces.add(
                    Place(
                        id           = rs.getString("id"),
                        name         = rs.getString("name"),
                        latitude     = rs.getDouble("lat"),
                        longitude    = rs.getDouble("lng"),
                        category     = try {
                            PlaceCategory.valueOf(rs.getString("category").uppercase())
                        } catch (e: Exception) { PlaceCategory.CULTURE },
                        price        = rs.getInt("price"),
                        duration     = rs.getInt("duration"),   // en MINUTES
                        effort       = rs.getInt("effort"),
                        meteo        = rs.getInt("meteo"),       // 0 = intérieur, 1 = mixte, 2 = extérieur
                        openingHours = rs.getString("opening_hours")
                    )
                )
            }
        }

        // ── 2. Lieux obligatoires (sélectionnés par l'utilisateur) ─────────────
        val mandatoryPlaces = allPlaces.filter { it.id in req.selectedPlaceIds }

        // ── 3. Vérification budget minimal ────────────────────────────────────
        val mandatoryCost = mandatoryPlaces.sumOf { it.price }
        if (mandatoryCost > req.budgetMax) {
            return@dbQuery listOf(
                ItineraryResponse(
                    name          = "Erreur",
                    hexColor      = "#FF0000",
                    totalPrice    = 0,
                    totalDuration = 0,
                    avgEffort     = 0,
                    mealIncluded  = false,
                    errorMessage  = "Le budget est trop bas pour inclure vos lieux favoris ($mandatoryCost€ requis).",
                    startTimeMinutes = req.startTimeMinutes
                )
            )
        }

        // ── 4. Catégories demandées ────────────────────────────────────────────
        val baseCategories = if (req.categories.isEmpty()) {
            listOf("CULTURE", "DECOUVERTE", "LOISIRS")
        } else {
            req.categories.map { it.uppercase() }
        }
        val requestedCategories = if (req.mealIncluded) baseCategories + "RESTAURATION" else baseCategories

        // ── 5. Budget max et durée max en MINUTES ─────────────────────────────
        //   req.durationHours est en heures → on convertit une fois pour toutes
        val budgetMax       = req.budgetMax
        val durationMaxMin  = req.durationHours * 60   // ← CORRECTION BUG (heures → minutes)

        // ── 6. Pool de candidats ───────────────────────────────────────────────
        //   - bonne catégorie
        //   - effort acceptable
        //   - météo compatible : meteo du lieu <= tolérance météo de l'utilisateur
        //     (0 = intérieur → toujours ok ; 2 = plein air → ok seulement si tolérance >= 2)
        //   - pas déjà dans les obligatoires
        val candidatePool = allPlaces.filter { place ->
            place.category.name in requestedCategories &&
                    place.effort       <= req.effortLevel       &&
                    place.meteo        <= req.weatherTolerance  &&
                    place.id           !in req.selectedPlaceIds
        }

        // ── 7. Point de départ géographique ───────────────────────────────────
        //   Si l'utilisateur a des lieux obligatoires, on part du premier.
        //   Sinon on utilise le centroïde du pool pour initialiser le nearest neighbor.
        val startPoint: Pair<Double, Double> = if (mandatoryPlaces.isNotEmpty()) {
            mandatoryPlaces.first().latitude to mandatoryPlaces.first().longitude
        } else if (candidatePool.isNotEmpty()) {
            val avgLat = candidatePool.sumOf { it.latitude }  / candidatePool.size
            val avgLng = candidatePool.sumOf { it.longitude } / candidatePool.size
            avgLat to avgLng
        } else {
            0.0 to 0.0
        }

        // ── 8. Fonction nearest-neighbor ──────────────────────────────────────
        //
        //   Principe :
        //   - On part d'un point de départ (dernier lieu ajouté ou startPoint).
        //   - À chaque itération on choisit dans les candidats restants le lieu :
        //       * dont le coût cumulé reste dans le budget
        //       * dont la durée cumulée reste dans le temps max
        //       * qui sera ouvert à l'heure estimée d'arrivée
        //       * qui minimise la "distance pondérée" = distance géo + pénalité selon la variante
        //   - On continue jusqu'à ce qu'aucun candidat ne soit ajoutable.
        //
        //   Le paramètre `priorityWeight` est une lambda qui retourne un score de
        //   préférence (plus bas = plus prioritaire). Il est combiné à la distance
        //   géographique pour nuancer le choix selon la variante.
        //
        //   score = distanceKm + priorityWeight(place) * priorityFactor
        //   priorityFactor détermine l'importance du critère métier vs distance.

        fun buildVariantNearestNeighbor(
            name           : String,
            color          : String,
            candidates     : List<Place>,
            priorityFactor : Double,
            priorityWeight : (Place) -> Double,
            budgetCap      : Int = budgetMax    // plafond propre à chaque variante
        ): ItineraryResponse {

            val visited      = mutableListOf<Place>()
            var currentCost  = 0
            var currentDurMin = 0
            var currentTimeMin = req.startTimeMinutes

            // Ajouter les lieux obligatoires en premier (dans l'ordre de sélection)
            mandatoryPlaces.forEachIndexed { index, place ->
                val durationMin = place.duration * 60   // BDD stocke en heures → minutes
                // Transit depuis le lieu précédent (0 pour le tout premier)
                if (index > 0) {
                    val prev = mandatoryPlaces[index - 1]
                    currentTimeMin += transitMin(prev.latitude, prev.longitude, place.latitude, place.longitude)
                }
                visited.add(place.copy(arrivalTime = formatTime(currentTimeMin)))
                currentCost    += place.price
                currentDurMin  += durationMin
                currentTimeMin += durationMin
            }

            // Point courant = dernier lieu visité, ou startPoint si aucun obligatoire
            var currentLat = if (visited.isNotEmpty()) visited.last().latitude  else startPoint.first
            var currentLng = if (visited.isNotEmpty()) visited.last().longitude else startPoint.second

            // Si on a des lieux obligatoires, on ajoute le transit vers le premier candidat
            // (on ne connaît pas encore la destination, mais currentLat/Lng est prêt pour
            //  que le filtre calcule transitMin correctement dès le premier tour)

            val remaining = candidates.toMutableList()

            // Nearest-neighbor : on ajoute un lieu à la fois.
            // À chaque tour : on calcule le transit depuis la position courante,
            // on arrive au lieu suivant, on y passe sa durée, puis on repart.
            while (remaining.isNotEmpty()) {
                val next = remaining
                    .filter { place ->
                        val transit     = transitMin(currentLat, currentLng, place.latitude, place.longitude)
                        val arrivalTime = currentTimeMin + transit
                        val durationMin = place.duration * 60   // heures → minutes
                        // Contrainte budget (plafond de la variante)
                        currentCost + place.price <= budgetCap &&
                                // Contrainte durée — LIMITE STRICTE (transit + visite doivent tenir)
                                currentDurMin + transit + durationMin <= durationMaxMin &&
                                // Contrainte horaires : le lieu doit être ouvert à l'heure d'arrivée réelle
                                isPlaceOpen(place.openingHours, arrivalTime, arrivalTime + durationMin)
                    }
                    .minByOrNull { place ->
                        val dist = haversineKm(currentLat, currentLng, place.latitude, place.longitude)
                        dist + priorityWeight(place) * priorityFactor
                    }

                if (next == null) break

                val transit        = transitMin(currentLat, currentLng, next.latitude, next.longitude)
                val nextDurationMin = next.duration * 60    // heures → minutes
                currentTimeMin += transit                    // on voyage
                visited.add(next.copy(arrivalTime = formatTime(currentTimeMin)))
                currentTimeMin += nextDurationMin            // on visite
                currentCost    += next.price
                currentDurMin  += transit + nextDurationMin
                currentLat      = next.latitude
                currentLng      = next.longitude
                remaining.remove(next)
            }

            val coverImages        = getTopImagesForPlaces(visited)
            val placesWithSchedule = recalculateSchedules(visited, req.startTimeMinutes)

            return ItineraryResponse(
                name          = name,
                hexColor      = color,
                totalPrice    = currentCost,
                totalDuration = currentDurMin / 60,   // minutes → heures pour le front
                avgEffort     = if (visited.isEmpty()) 0 else visited.sumOf { it.effort } / visited.size,
                mealIncluded  = visited.any { it.category.name == "RESTAURATION" },
                steps         = placesWithSchedule,
                coverImages   = coverImages,
                likeCount     = 0,
                startTimeMinutes = req.startTimeMinutes,
                authorName    = "IA Traveling"
            )
        }

        // ── 9. Construction des 3 variantes ───────────────────────────────────
        //
        //   Chaque variante reçoit le même pool de candidats mais un critère de
        //   préférence différent. Le nearest-neighbor s'en sert pour départager
        //   deux lieux à distance équivalente.
        //
        //   • ÉCO      → favorise les lieux peu chers.
        //                priorityWeight = prix normalisé [0-1] × budget
        //                (un lieu à 0€ aura score 0, à budgetMax€ aura score 1 × factor)
        //
        //   • ÉQUILIBRÉ → aucune pondération métier : choix purement géographique.
        //                C'est la variante "distance minimale" pure.
        //
        //   • CONFORT  → favorise les lieux peu physiques (effort faible).
        //                priorityWeight = effort normalisé [0-1]

        val maxPrice  = candidatePool.maxOfOrNull { it.price }?.toDouble()  ?: 1.0
        val maxEffort = candidatePool.maxOfOrNull { it.effort }?.toDouble() ?: 1.0

        // facteur de pondération = 5 km équivalents au max du critère métier.
        // Autrement dit, préférer un lieu de prix/effort minimal vaut ~5 km de détour.
        val PRIORITY_FACTOR = 5.0

        // Plafonds budgétaires par variante (par rapport au budgetMax demandé) :
        //   Éco       → 60 % du budget max  (sélection frugale, vraiment moins cher)
        //   Équilibré → 85 % du budget max  (compromis raisonnable)
        //   Confort   → 100 % du budget max (on se fait plaisir)
        // On s'assure que le coût obligatoire est toujours couvert (max avec mandatoryCost).
        val budgetEco      = maxOf(mandatoryCost, (budgetMax * 0.60).toInt())
        val budgetEquilibre = maxOf(mandatoryCost, (budgetMax * 0.85).toInt())
        val budgetConfort  = budgetMax

        val result = listOf(
            // Éco : dépenser le moins possible, dans un périmètre géo cohérent
            buildVariantNearestNeighbor(
                name           = "Éco",
                color          = "#2D5A27",
                candidates     = candidatePool,
                priorityFactor = PRIORITY_FACTOR,
                priorityWeight = { place -> place.price / maxPrice },
                budgetCap      = budgetEco
            ),
            // Équilibré : itinéraire géographiquement optimal, budget modéré
            buildVariantNearestNeighbor(
                name           = "Équilibré",
                color          = "#E59866",
                candidates     = candidatePool,
                priorityFactor = 0.0,
                priorityWeight = { 0.0 },
                budgetCap      = budgetEquilibre
            ),
            // Confort : on évite les lieux physiquement exigeants, budget plein
            buildVariantNearestNeighbor(
                name           = "Confort",
                color          = "#884154",
                candidates     = candidatePool,
                priorityFactor = PRIORITY_FACTOR,
                priorityWeight = { place -> place.effort / maxEffort },
                budgetCap      = budgetConfort
            )
        )

        return@dbQuery result
    }

    // ---------------------------------------------------------------------------
    // SAUVEGARDE
    // ---------------------------------------------------------------------------

    suspend fun savePath(request: SavePathRequest) = dbQuery {
        val newItineraryId = Itineraries.insert {
            it[name]             = request.name
            it[hexColor]         = request.hexColor
            it[totalPrice]       = request.totalPrice
            it[totalDuration]    = request.totalDuration
            it[avgEffort]        = request.avgEffort.toDouble()
            it[mealIncluded]     = request.mealIncluded
            it[authorId]         = request.userId
            it[startTimeMinutes] = request.startTimeMinutes
        } get Itineraries.id

        val tokens       = UserService.getFollowerTokens(request.userId)
        val authorProfile = UserService.getUserProfile(request.userId, null)
        val authorName   = authorProfile?.username ?: "Un voyageur"

        NotificationService.notifyFollowersNewItinerary(
            authorName    = authorName,
            itineraryName = request.name,
            tokens        = tokens
        )

        request.placeIds.forEachIndexed { index, placeId ->
            Steps.insert {
                it[itineraryId] = newItineraryId
                it[this.placeId] = placeId
                it[stepOrder]   = index + 1
            }
        }
    }

    // ---------------------------------------------------------------------------
    // LIKE / UNLIKE
    // ---------------------------------------------------------------------------

    suspend fun toggleLike(userId: String, itineraryId: Int): Boolean = dbQuery {
        val existingLike = ItineraryLikes.select {
            (ItineraryLikes.userId eq userId) and (ItineraryLikes.itineraryId eq itineraryId)
        }.singleOrNull()

        if (existingLike != null) {
            ItineraryLikes.deleteWhere {
                (ItineraryLikes.userId eq userId) and (ItineraryLikes.itineraryId eq itineraryId)
            }
            false
        } else {
            ItineraryLikes.insert {
                it[this.userId]      = userId
                it[this.itineraryId] = itineraryId
            }
            true
        }
    }

    // ---------------------------------------------------------------------------
    // LISTE PAR CATÉGORIE
    // ---------------------------------------------------------------------------

    suspend fun getItinerariesByCategory(userId: String, category: String): List<ItineraryResponse> = dbQuery {

        var sharedTimestamps = mapOf<Int, Long>()

        val likedItineraryIds = ItineraryLikes
            .select { ItineraryLikes.userId eq userId }
            .map { it[ItineraryLikes.itineraryId] }
            .toSet()

        val query = when {
            category == "SUGGESTIONS" -> Itineraries.selectAll().limit(10)

            category == "LIKED" ->
                Itineraries.innerJoin(ItineraryLikes)
                    .select { ItineraryLikes.userId eq userId }

            category == "POPULAR" -> {
                val likeCount = ItineraryLikes.userId.count()
                Itineraries.innerJoin(ItineraryLikes)
                    .slice(Itineraries.columns + likeCount)
                    .selectAll()
                    .groupBy(Itineraries.id)
                    .orderBy(likeCount to SortOrder.DESC)
                    .limit(30)
            }

            category == "FOLLOWING" -> {
                val followedIds = UserFollows
                    .select { UserFollows.followerId eq userId }
                    .map { it[UserFollows.followedId] }
                if (followedIds.isEmpty()) return@dbQuery emptyList()

                val likeCount = ItineraryLikes.userId.count()
                Itineraries.leftJoin(ItineraryLikes)
                    .slice(Itineraries.columns + likeCount)
                    .select { Itineraries.authorId inList followedIds }
                    .groupBy(Itineraries.id)
                    .orderBy(likeCount to SortOrder.DESC)
                    .limit(20)
            }

            category.startsWith("AUTHOR_") -> {
                val authorId  = category.removePrefix("AUTHOR_")
                val likeCount = ItineraryLikes.userId.count()
                Itineraries.leftJoin(ItineraryLikes)
                    .slice(Itineraries.columns + likeCount)
                    .select { Itineraries.authorId eq authorId }
                    .groupBy(Itineraries.id)
                    .orderBy(likeCount to SortOrder.DESC)
            }

            category.startsWith("GROUP_") -> {
                val groupUuid   = java.util.UUID.fromString(category.removePrefix("GROUP_"))
                val sharedData  = com.example.application.GroupItineraries
                    .select { com.example.application.GroupItineraries.groupId eq groupUuid }
                    .associate {
                        it[com.example.application.GroupItineraries.itineraryId] to
                                it[com.example.application.GroupItineraries.sharedAt]
                    }

                sharedTimestamps = sharedData
                val sharedItineraryIds = sharedData.keys.toList()
                if (sharedItineraryIds.isEmpty()) return@dbQuery emptyList()

                val likeCount = ItineraryLikes.userId.count()
                Itineraries.leftJoin(ItineraryLikes)
                    .slice(Itineraries.columns + likeCount)
                    .select { Itineraries.id inList sharedItineraryIds }
                    .groupBy(Itineraries.id)
                    .orderBy(Itineraries.id to SortOrder.DESC)
            }

            else -> Itineraries.select { Itineraries.authorId eq userId } // "MES_PARCOURS"
        }

        val rows = query.toList()
        if (rows.isEmpty()) return@dbQuery emptyList()

        val itineraryIds = rows.map { it[Itineraries.id] }
        val authorIds    = rows.map { it[Itineraries.authorId] }.distinct()

        val allLikes = ItineraryLikes
            .select { ItineraryLikes.itineraryId inList itineraryIds }
            .toList()

        val authorNamesMap = mutableMapOf<String, String>()
        if (authorIds.isNotEmpty()) {
            val idsFormatted = authorIds.joinToString("','", "'", "'")
            TransactionManager.current().exec(
                "SELECT firebase_id, username FROM users WHERE firebase_id IN ($idsFormatted)"
            ) { rs ->
                while (rs.next()) {
                    val fId   = rs.getString("firebase_id")
                    val uName = rs.getString("username")
                    if (fId != null && uName != null) authorNamesMap[fId] = uName
                }
            }
        }

        rows.map { row ->
            val itineraryId      = row[Itineraries.id]
            val authorId         = row[Itineraries.authorId]
            val currentAuthorName = authorNamesMap[authorId] ?: "Utilisateur"

            val sqlSteps = """
                SELECT p.id, p.name, p.category,
                       ST_Y(p.location::geometry) AS lat,
                       ST_X(p.location::geometry) AS lng,
                       p.price, p.duration, p.effort, p.meteo,
                       p.opening_hours::text AS opening_hours
                FROM step s
                JOIN places p ON s.place_id = p.id
                WHERE s.itinerary_id = $itineraryId
                ORDER BY s.step_order ASC
            """.trimIndent()

            val places = mutableListOf<Place>()
            TransactionManager.current().exec(sqlSteps) { rs ->
                while (rs.next()) {
                    places.add(
                        Place(
                            id           = rs.getString("id"),
                            name         = rs.getString("name"),
                            latitude     = rs.getDouble("lat"),
                            longitude    = rs.getDouble("lng"),
                            category     = try {
                                PlaceCategory.valueOf(rs.getString("category").uppercase())
                            } catch (e: Exception) { PlaceCategory.CULTURE },
                            price        = rs.getInt("price"),
                            duration     = rs.getInt("duration"),
                            effort       = rs.getInt("effort"),
                            meteo        = rs.getInt("meteo"),
                            openingHours = rs.getString("opening_hours")
                        )
                    )
                }
            }

            val coverImages           = getTopImagesForPlaces(places)
            val dbStartTime           = row[Itineraries.startTimeMinutes]
            val placesWithSchedules   = recalculateSchedules(places, dbStartTime)
            val totalLikesForThisItinerary = allLikes.count { it[ItineraryLikes.itineraryId] == itineraryId }

            ItineraryResponse(
                id               = itineraryId,
                name             = row[Itineraries.name],
                hexColor         = row[Itineraries.hexColor],
                totalPrice       = row[Itineraries.totalPrice]    ?: 0,
                totalDuration    = row[Itineraries.totalDuration] ?: 0,
                avgEffort        = (row[Itineraries.avgEffort]    ?: 0.0).roundToInt(),
                mealIncluded     = row[Itineraries.mealIncluded]  ?: false,
                steps            = placesWithSchedules,
                coverImages      = coverImages,
                isLiked          = likedItineraryIds.contains(itineraryId),
                likeCount        = totalLikesForThisItinerary,
                startTimeMinutes = dbStartTime,
                userId           = authorId,
                authorName       = currentAuthorName,
                sharedAt         = sharedTimestamps[itineraryId] ?: 0L
            )
        }
    }

    // ---------------------------------------------------------------------------
    // DÉTAIL D'UN ITINÉRAIRE
    // ---------------------------------------------------------------------------

    suspend fun getItineraryById(itineraryId: Int): ItineraryResponse? = dbQuery {
        val row = Itineraries.select { Itineraries.id eq itineraryId }.singleOrNull()
            ?: return@dbQuery null

        val authorId         = row[Itineraries.authorId]
        var currentAuthorName = "Utilisateur"

        TransactionManager.current().exec(
            "SELECT username FROM users WHERE firebase_id = '$authorId'"
        ) { rs ->
            if (rs.next()) currentAuthorName = rs.getString("username") ?: "Utilisateur"
        }

        val sqlSteps = """
            SELECT p.id, p.name, p.category,
                   ST_Y(p.location::geometry) AS lat,
                   ST_X(p.location::geometry) AS lng,
                   p.price, p.duration, p.effort, p.meteo,
                   p.opening_hours::text AS opening_hours
            FROM step s
            JOIN places p ON s.place_id = p.id
            WHERE s.itinerary_id = $itineraryId
            ORDER BY s.step_order ASC
        """.trimIndent()

        val places = mutableListOf<Place>()
        TransactionManager.current().exec(sqlSteps) { rs ->
            while (rs.next()) {
                places.add(
                    Place(
                        id           = rs.getString("id"),
                        name         = rs.getString("name"),
                        latitude     = rs.getDouble("lat"),
                        longitude    = rs.getDouble("lng"),
                        category     = try {
                            PlaceCategory.valueOf(rs.getString("category").uppercase())
                        } catch (e: Exception) { PlaceCategory.CULTURE },
                        price        = rs.getInt("price"),
                        duration     = rs.getInt("duration"),
                        effort       = rs.getInt("effort"),
                        meteo        = rs.getInt("meteo"),
                        openingHours = rs.getString("opening_hours")
                    )
                )
            }
        }

        val dbStartTime        = row[Itineraries.startTimeMinutes]
        val placesWithSchedule = recalculateSchedules(places, dbStartTime)
        val totalLikes         = ItineraryLikes.select { ItineraryLikes.itineraryId eq itineraryId }.count()

        ItineraryResponse(
            id               = itineraryId,
            name             = row[Itineraries.name],
            hexColor         = row[Itineraries.hexColor],
            totalPrice       = row[Itineraries.totalPrice]    ?: 0,
            totalDuration    = row[Itineraries.totalDuration] ?: 0,
            avgEffort        = (row[Itineraries.avgEffort]    ?: 0.0).roundToInt(),
            mealIncluded     = row[Itineraries.mealIncluded]  ?: false,
            steps            = placesWithSchedule,
            likeCount        = totalLikes.toInt(),
            startTimeMinutes = dbStartTime,
            userId           = authorId,
            authorName       = currentAuthorName
        )
    }

    // ---------------------------------------------------------------------------
    // SUPPRESSION
    // ---------------------------------------------------------------------------

    suspend fun deletePath(userId: String, itineraryId: Int): Boolean = dbQuery {
        val itinerary = Itineraries.select { Itineraries.id eq itineraryId }.singleOrNull()

        if (itinerary == null || itinerary[Itineraries.authorId] != userId) {
            return@dbQuery false
        }

        Steps.deleteWhere { Steps.itineraryId eq itineraryId }
        ItineraryLikes.deleteWhere { ItineraryLikes.itineraryId eq itineraryId }
        val deletedCount = Itineraries.deleteWhere { Itineraries.id eq itineraryId }

        return@dbQuery deletedCount > 0
    }

    // ---------------------------------------------------------------------------
    // PARTAGE DANS UN GROUPE
    // ---------------------------------------------------------------------------

    suspend fun shareItineraryToGroup(itineraryId: Int, groupIdStr: String): Boolean = dbQuery {
        try {
            val groupUuid = java.util.UUID.fromString(groupIdStr)

            val exists = com.example.application.GroupItineraries.select {
                (com.example.application.GroupItineraries.groupId eq groupUuid) and
                        (com.example.application.GroupItineraries.itineraryId eq itineraryId)
            }.count() > 0

            if (!exists) {
                com.example.application.GroupItineraries.insert {
                    it[groupId]         = groupUuid
                    it[this.itineraryId] = itineraryId
                    it[sharedAt]        = System.currentTimeMillis()
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ---------------------------------------------------------------------------
    // HELPERS PRIVÉS
    // ---------------------------------------------------------------------------

    /**
     * Vérifie qu'un lieu est ouvert entre [arrivalMin] et [departureMin] (en minutes depuis minuit).
     * Retourne true si pas d'horaires renseignés (bénéfice du doute).
     */
    private fun isPlaceOpen(jsonStr: String?, arrivalMin: Int, departureMin: Int): Boolean {
        if (jsonStr.isNullOrEmpty() || jsonStr == "null") return true
        return try {
            val jsonArray           = Json.parseToJsonElement(jsonStr).jsonArray
            val arrivalNorm         = arrivalMin   % (24 * 60)
            var departureNorm       = departureMin % (24 * 60)
            if (departureNorm < arrivalNorm) departureNorm += 24 * 60

            for (element in jsonArray) {
                val obj       = element.jsonObject
                val openStr   = obj["open"]?.jsonPrimitive?.content  ?: continue
                val closeStr  = obj["close"]?.jsonPrimitive?.content ?: continue

                val openParts  = openStr.split(":")
                val closeParts = closeStr.split(":")
                val openMin    = openParts[0].toInt() * 60  + openParts[1].toInt()
                var closeMin   = closeParts[0].toInt() * 60 + closeParts[1].toInt()
                if (closeMin < openMin) closeMin += 24 * 60

                if (arrivalNorm >= openMin && departureNorm <= closeMin) return true
            }
            false
        } catch (e: Exception) {
            println("Erreur parsing horaires : ${e.localizedMessage}")
            true
        }
    }

    /**
     * Distance en kilomètres entre deux coordonnées GPS (formule de Haversine).
     */
    private fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val R      = 6371.0
        val dLat   = Math.toRadians(lat2 - lat1)
        val dLng   = Math.toRadians(lng2 - lng1)
        val a      = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return R * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /**
     * Temps de transit estimé en minutes entre deux points GPS.
     *
     * Logique mixte :
     *   < 1 km  → à pied  (5 km/h)  + 2 min de marge (traversées, feux)
     *   ≥ 1 km  → voiture (40 km/h) + 5 min de marge (parking, entrée)
     *   Minimum absolu : 5 min (même porte à porte)
     */
    private fun transitMin(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Int {
        val distKm = haversineKm(lat1, lng1, lat2, lng2)
        val rawMin = if (distKm < 1.0) {
            (distKm / 5.0  * 60).toInt() + 2   // à pied
        } else {
            (distKm / 40.0 * 60).toInt() + 5   // voiture
        }
        return maxOf(rawMin, 5)
    }

    private fun formatTime(minutes: Int): String {
        val h = (minutes / 60) % 24
        val m = minutes % 60
        return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
    }

    private fun getTopImagesForPlaces(places: List<Place>): List<String> {
        if (places.isEmpty()) return emptyList()
        val placeIds  = places.map { it.id }.joinToString("','", "'", "'")
        val imageUrls = mutableListOf<String>()

        val sql = """
            SELECT p.image_urls
            FROM posts p
            LEFT JOIN post_likes pl ON p.id = pl.post_id
            WHERE p.place_id IN ($placeIds) AND p.is_public = true
            GROUP BY p.id, p.image_urls
            ORDER BY COUNT(pl.user_id) DESC
        """.trimIndent()

        TransactionManager.current().exec(sql) { rs ->
            while (rs.next()) {
                val urlsString = rs.getString("image_urls")
                if (!urlsString.isNullOrBlank()) {
                    imageUrls.addAll(urlsString.split(",").map { it.trim() })
                }
            }
        }
        return imageUrls.distinct().take(4)
    }

    private fun recalculateSchedules(places: List<Place>, startTimeMinutes: Int = 570): List<Place> {
        var currentTimeMinutes = startTimeMinutes
        return places.mapIndexed { index, place ->
            // Transit depuis le lieu précédent (ou 0 pour le premier)
            val transit = if (index == 0) 0 else {
                val prev = places[index - 1]
                transitMin(prev.latitude, prev.longitude, place.latitude, place.longitude)
            }
            currentTimeMinutes += transit
            val arrival = formatTime(currentTimeMinutes)
            currentTimeMinutes += place.duration * 60   // BDD en heures → minutes
            place.copy(arrivalTime = arrival)
        }
    }


}