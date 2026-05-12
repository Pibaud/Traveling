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
import kotlin.math.roundToInt
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.count
import com.example.application.Itineraries
import com.example.application.Steps

object PathService {
    suspend fun generatePath(req: GeneratePathRequest): List<ItineraryResponse> = dbQuery {

        // 1. Récupération de TOUS les lieux avec du SQL Brut
        val sql = """
            SELECT id, name, category, ST_Y(location::geometry) as lat, ST_X(location::geometry) as lng, price, duration, effort, opening_hours::text as opening_hours
            FROM places
        """.trimIndent()

        val allPlaces = mutableListOf<Place>()

        TransactionManager.current().exec(sql) { rs ->
            while (rs.next()) {
                allPlaces.add(Place(
                    id = rs.getString("id"),
                    name = rs.getString("name"),
                    latitude = rs.getDouble("lat"),
                    longitude = rs.getDouble("lng"),
                    category = try { PlaceCategory.valueOf(rs.getString("category").uppercase()) } catch (e: Exception) { PlaceCategory.CULTURE },
                    price = rs.getInt("price"),
                    duration = rs.getInt("duration"),
                    effort = rs.getInt("effort"),
                    openingHours = rs.getString("opening_hours")
                ))
            }
        }

        // 2. On isole le(s) lieu(x) obligatoire(s)
        val mandatoryPlaces = allPlaces.filter { it.id in req.selectedPlaceIds }

        // 👇 LA CORRECTION EST ICI 👇

        // Si l'utilisateur n'a rien coché, on cherche dans les 3 catégories de base au lieu de tout bloquer
        val baseCategories = if (req.categories.isEmpty()) {
            listOf("CULTURE", "DECOUVERTE", "LOISIRS")
        } else {
            req.categories.map { it.uppercase() }
        }

        // On ajoute intelligemment la restauration si la case était cochée !
        val requestedCategories = if (req.mealIncluded) {
            baseCategories + "RESTAURATION"
        } else {
            baseCategories
        }

        // 3. On filtre les candidats (ceux qu'on a le droit de rajouter)
        val candidatePlaces = allPlaces.filter {
            it.category.name in requestedCategories &&
                    it.effort <= req.effortLevel &&
                    it.id !in req.selectedPlaceIds // On ne remet pas le lieu imposé !
        }

        val mandatoryCost = mandatoryPlaces.sumOf { it.price }
        val mandatoryDuration = mandatoryPlaces.sumOf { it.duration }

        if (mandatoryCost > req.budgetMax) {
            return@dbQuery listOf(ItineraryResponse(
                name = "Erreur", hexColor = "#FF0000", totalPrice = 0, totalDuration = 0,
                avgEffort = 0, mealIncluded = false,
                errorMessage = "Le budget est trop bas pour inclure vos lieux favoris ($mandatoryCost€ requis).",
                startTimeMinutes = req.startTimeMinutes
            ))
        }

        // ... (La suite de ton code avec la fonction `buildVariant` reste identique) ...

        fun buildVariant(name: String, color: String, sortedCandidates: List<Place>): ItineraryResponse {
            val finalSteps = mutableListOf<Place>()
            var currentCost = 0
            var currentDuration = 0
            var currentTimeMinutes = req.startTimeMinutes

            for (place in mandatoryPlaces) {
                finalSteps.add(place.copy(arrivalTime = formatTime(currentTimeMinutes)))
                currentCost += place.price
                currentDuration += place.duration
                currentTimeMinutes += (place.duration * 60) + 30
            }

            for (place in sortedCandidates) {
                if (currentCost + place.price <= req.budgetMax && currentDuration + place.duration <= req.durationHours) {

                    val placeDurationMin = place.duration * 60
                    val estimatedArrival = currentTimeMinutes
                    val estimatedDeparture = currentTimeMinutes + placeDurationMin

                    if (isPlaceOpen(place.openingHours, estimatedArrival, estimatedDeparture)) {
                        finalSteps.add(place.copy(arrivalTime = formatTime(estimatedArrival)))
                        currentCost += place.price
                        currentDuration += place.duration
                        currentTimeMinutes += placeDurationMin + 30
                    }
                }
            }

            val coverImages = getTopImagesForPlaces(finalSteps)
            val placesWithSchedules = recalculateSchedules(finalSteps, req.startTimeMinutes)

            return ItineraryResponse(
                name = name,
                hexColor = color,
                totalPrice = currentCost,
                totalDuration = currentDuration,
                avgEffort = if(finalSteps.isEmpty()) 0 else finalSteps.sumOf { it.effort } / finalSteps.size,
                mealIncluded = finalSteps.any { it.category.name == "RESTAURATION" },
                steps = placesWithSchedules,
                coverImages = coverImages,
                likeCount = 0,
                startTimeMinutes = req.startTimeMinutes, // 👈 Ajout ici
                authorName = "IA Traveling"
            )
        }

        val result = listOf(
            buildVariant("Éco", "#2D5A27", candidatePlaces.sortedBy { it.price }),
            buildVariant("Équilibré", "#E59866", candidatePlaces.shuffled()),
            buildVariant("Confort", "#884154", candidatePlaces.sortedByDescending { it.price })
        )

        return@dbQuery result
    }

    suspend fun savePath(request: SavePathRequest) = dbQuery {
        val newItineraryId = Itineraries.insert {
            it[name] = request.name
            it[hexColor] = request.hexColor
            it[totalPrice] = request.totalPrice
            it[totalDuration] = request.totalDuration
            it[avgEffort] = request.avgEffort.toDouble()
            it[mealIncluded] = request.mealIncluded
            it[authorId] = request.userId
            it[startTimeMinutes] = request.startTimeMinutes // 👈 On la sauvegarde en base !
        } get Itineraries.id

        val tokens = UserService.getFollowerTokens(request.userId)

        val authorProfile = UserService.getUserProfile(request.userId, null)
        val authorName = authorProfile?.username ?: "Un voyageur"

        NotificationService.notifyFollowersNewItinerary(
            authorName = authorName,
            itineraryName = request.name,
            tokens = tokens
        )

        request.placeIds.forEachIndexed { index, placeId ->
            Steps.insert {
                it[itineraryId] = newItineraryId
                it[this.placeId] = placeId
                it[stepOrder] = index + 1
            }
        }
    }

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
                it[this.userId] = userId
                it[this.itineraryId] = itineraryId
            }
            true
        }
    }

    suspend fun getItinerariesByCategory(userId: String, category: String): List<ItineraryResponse> = dbQuery {

        var sharedTimestamps = mapOf<Int, Long>()

        val likedItineraryIds = ItineraryLikes
            .select { ItineraryLikes.userId eq userId }
            .map { it[ItineraryLikes.itineraryId] }
            .toSet()

        val query = when {
            category == "SUGGESTIONS" -> Itineraries.selectAll().limit(10)

            category == "LIKED" -> Itineraries.innerJoin(ItineraryLikes).select { ItineraryLikes.userId eq userId }

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
                val followedIds = UserFollows.select { UserFollows.followerId eq userId }.map { it[UserFollows.followedId] }
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
                val authorId = category.removePrefix("AUTHOR_")
                val likeCount = ItineraryLikes.userId.count()
                Itineraries.leftJoin(ItineraryLikes)
                    .slice(Itineraries.columns + likeCount)
                    .select { Itineraries.authorId eq authorId }
                    .groupBy(Itineraries.id)
                    .orderBy(likeCount to SortOrder.DESC)
            }

            category.startsWith("GROUP_") -> {
                val groupUuid = java.util.UUID.fromString(category.removePrefix("GROUP_"))

                // 👇 NOUVEAU : On récupère l'ID ET le timestamp
                val sharedData = com.example.application.GroupItineraries
                    .select { com.example.application.GroupItineraries.groupId eq groupUuid }
                    .associate {
                        it[com.example.application.GroupItineraries.itineraryId] to it[com.example.application.GroupItineraries.sharedAt]
                    }

                sharedTimestamps = sharedData // On sauvegarde pour plus tard
                val sharedItineraryIds = sharedData.keys.toList()

                if (sharedItineraryIds.isEmpty()) return@dbQuery emptyList()

                val likeCount = ItineraryLikes.userId.count()
                Itineraries.leftJoin(ItineraryLikes)
                    .slice(Itineraries.columns + likeCount)
                    .select { Itineraries.id inList sharedItineraryIds }
                    .groupBy(Itineraries.id)
                    .orderBy(Itineraries.id to SortOrder.DESC)
            }

            else -> Itineraries.select { Itineraries.authorId eq userId } // "MINE"
        }

        val rows = query.toList()
        if (rows.isEmpty()) return@dbQuery emptyList()

        val itineraryIds = rows.map { it[Itineraries.id] }
        val authorIds = rows.map { it[Itineraries.authorId] }.distinct()

        val allLikes = ItineraryLikes
            .select { ItineraryLikes.itineraryId inList itineraryIds }
            .toList()

        val authorNamesMap = mutableMapOf<String, String>()
        if (authorIds.isNotEmpty()) {
            val idsFormatted = authorIds.joinToString("','", "'", "'")
            val sqlUsers = "SELECT firebase_id, username FROM users WHERE firebase_id IN ($idsFormatted)"

            TransactionManager.current().exec(sqlUsers) { rs ->
                while (rs.next()) {
                    val fId = rs.getString("firebase_id")
                    val uName = rs.getString("username")
                    if (fId != null && uName != null) {
                        authorNamesMap[fId] = uName
                    }
                }
            }
        }

        rows.map { row ->
            val itineraryId = row[Itineraries.id]
            val authorId = row[Itineraries.authorId]
            val currentAuthorName = authorNamesMap[authorId] ?: "Utilisateur"

            val sql = """
            SELECT p.id, p.name, p.category, ST_Y(p.location::geometry) as lat, ST_X(p.location::geometry) as lng, p.price, p.duration, p.effort, p.opening_hours::text as opening_hours
            FROM step s
            JOIN places p ON s.place_id = p.id
            WHERE s.itinerary_id = $itineraryId
            ORDER BY s.step_order ASC
        """.trimIndent()

            val places = mutableListOf<Place>()

            TransactionManager.current().exec(sql) { rs ->
                while (rs.next()) {
                    places.add(Place(
                        id = rs.getString("id"),
                        name = rs.getString("name"),
                        latitude = rs.getDouble("lat"),
                        longitude = rs.getDouble("lng"),
                        category = try { PlaceCategory.valueOf(rs.getString("category").uppercase()) } catch (e: Exception) { PlaceCategory.CULTURE },
                        price = rs.getInt("price"),
                        duration = rs.getInt("duration"),
                        effort = rs.getInt("effort"),
                        openingHours = rs.getString("opening_hours")
                    ))
                }
            }

            val coverImages = getTopImagesForPlaces(places)

            // 👇 On récupère la vraie heure et on l'injecte 👇
            val dbStartTime = row[Itineraries.startTimeMinutes]
            val placesWithSchedules = recalculateSchedules(places, dbStartTime)

            val totalLikesForThisItinerary = allLikes.count { it[ItineraryLikes.itineraryId] == itineraryId }

            ItineraryResponse(
                id = itineraryId,
                name = row[Itineraries.name],
                hexColor = row[Itineraries.hexColor],
                totalPrice = row[Itineraries.totalPrice]?: 0,
                totalDuration = row[Itineraries.totalDuration]?: 0,
                avgEffort = (row[Itineraries.avgEffort]?: 0.0).roundToInt(),
                mealIncluded = row[Itineraries.mealIncluded]?: false,
                steps = placesWithSchedules,
                coverImages = coverImages,
                isLiked = likedItineraryIds.contains(itineraryId),
                likeCount = totalLikesForThisItinerary,
                startTimeMinutes = dbStartTime, // 👈 Ajout ici
                userId = authorId,
                authorName = currentAuthorName,
                sharedAt = sharedTimestamps[itineraryId] ?: 0L
            )
        }
    }

    suspend fun getItineraryById(itineraryId: Int): ItineraryResponse? = dbQuery {
        val row = Itineraries.select { Itineraries.id eq itineraryId }.singleOrNull()
            ?: return@dbQuery null

        val authorId = row[Itineraries.authorId]
        var currentAuthorName = "Utilisateur"

        TransactionManager.current().exec("SELECT username FROM users WHERE firebase_id = '$authorId'") { rs ->
            if (rs.next()) {
                currentAuthorName = rs.getString("username") ?: "Utilisateur"
            }
        }

        val sql = """
            SELECT p.id, p.name, p.category, ST_Y(p.location::geometry) as lat, ST_X(p.location::geometry) as lng, p.price, p.duration, p.effort, p.opening_hours::text as opening_hours
            FROM step s
            JOIN places p ON s.place_id = p.id
            WHERE s.itinerary_id = $itineraryId
            ORDER BY s.step_order ASC
        """.trimIndent()

        val places = mutableListOf<Place>()

        TransactionManager.current().exec(sql) { rs ->
            while (rs.next()) {
                places.add(Place(
                    id = rs.getString("id"),
                    name = rs.getString("name"),
                    latitude = rs.getDouble("lat"),
                    longitude = rs.getDouble("lng"),
                    category = try { PlaceCategory.valueOf(rs.getString("category").uppercase()) } catch (e: Exception) { PlaceCategory.CULTURE },
                    price = rs.getInt("price"),
                    duration = rs.getInt("duration"),
                    effort = rs.getInt("effort"),
                    openingHours = rs.getString("opening_hours")
                ))
            }
        }

        // 👇 On récupère la vraie heure et on l'injecte 👇
        val dbStartTime = row[Itineraries.startTimeMinutes]
        val placesWithSchedules = recalculateSchedules(places, dbStartTime)

        val totalLikes = ItineraryLikes.select { ItineraryLikes.itineraryId eq itineraryId }.count()

        ItineraryResponse(
            id = itineraryId,
            name = row[Itineraries.name],
            hexColor = row[Itineraries.hexColor],
            totalPrice = row[Itineraries.totalPrice] ?: 0,
            totalDuration = row[Itineraries.totalDuration] ?: 0,
            avgEffort = (row[Itineraries.avgEffort] ?: 0.0).roundToInt(),
            mealIncluded = row[Itineraries.mealIncluded] ?: false,
            steps = placesWithSchedules,
            likeCount = totalLikes.toInt(),
            startTimeMinutes = dbStartTime, // 👈 Ajout ici
            userId = authorId,
            authorName = currentAuthorName
        )
    }

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

    private fun isPlaceOpen(jsonStr: String?, arrivalMin: Int, departureMin: Int): Boolean {
        if (jsonStr.isNullOrEmpty() || jsonStr == "null") return true
        try {
            val jsonArray = Json.parseToJsonElement(jsonStr).jsonArray
            val arrivalNormalized = arrivalMin % (24 * 60)
            var departureNormalized = departureMin % (24 * 60)
            if (departureNormalized < arrivalNormalized) departureNormalized += 24 * 60

            for (element in jsonArray) {
                val obj = element.jsonObject
                val openStr = obj["open"]?.jsonPrimitive?.content ?: continue
                val closeStr = obj["close"]?.jsonPrimitive?.content ?: continue

                val openParts = openStr.split(":")
                val closeParts = closeStr.split(":")
                val openMin = openParts[0].toInt() * 60 + openParts[1].toInt()
                var closeMin = closeParts[0].toInt() * 60 + closeParts[1].toInt()

                if (closeMin < openMin) closeMin += 24 * 60

                if (arrivalNormalized >= openMin && departureNormalized <= closeMin) {
                    return true
                }
            }
            return false
        } catch (e: Exception) {
            println("Erreur de parsing des horaires : ${e.localizedMessage}")
            return true
        }
    }

    private fun formatTime(minutes: Int): String {
        val h = (minutes / 60) % 24
        val m = minutes % 60
        return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
    }

    private fun getTopImagesForPlaces(places: List<Place>): List<String> {
        if (places.isEmpty()) return emptyList()

        val placeIds = places.map { it.id }.joinToString("','", "'", "'")
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
                    val urls = urlsString.split(",").map { it.trim() }
                    imageUrls.addAll(urls)
                }
            }
        }
        return imageUrls.distinct().take(4)
    }

    private fun recalculateSchedules(places: List<Place>, startTimeMinutes: Int = 570): List<Place> {
        var currentTimeMinutes = startTimeMinutes
        return places.map { place ->
            val arrival = formatTime(currentTimeMinutes)
            currentTimeMinutes += (place.duration * 60) + 30
            place.copy(arrivalTime = arrival)
        }
    }

    suspend fun shareItineraryToGroup(itineraryId: Int, groupIdStr: String): Boolean = dbQuery {
        try {
            val groupUuid = java.util.UUID.fromString(groupIdStr)

            // 1. On vérifie si l'itinéraire n'est pas DÉJÀ partagé dans ce groupe pour éviter les doublons
            val exists = com.example.application.GroupItineraries.select {
                (com.example.application.GroupItineraries.groupId eq groupUuid) and
                        (com.example.application.GroupItineraries.itineraryId eq itineraryId)
            }.count() > 0

            if (!exists) {
                // 2. On insère le lien
                com.example.application.GroupItineraries.insert {
                    it[groupId] = groupUuid
                    it[this.itineraryId] = itineraryId
                    it[sharedAt] = System.currentTimeMillis()
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}