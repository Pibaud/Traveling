package com.example.application.services

import com.example.application.DatabaseFactory.dbQuery
import com.example.application.models.GeneratePathRequest
import com.example.application.models.ItineraryResponse
import com.example.application.models.Place
import com.example.application.models.PlaceCategory
import com.example.application.models.Itineraries
import com.example.application.models.SavePathRequest
import com.example.application.models.Steps
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import kotlin.math.roundToInt

object PathService {
    suspend fun generatePath(req: GeneratePathRequest): List<ItineraryResponse> = dbQuery {

        // 1. Récupération de TOUS les lieux avec du SQL Brut, en incluant opening_hours
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
                    openingHours = rs.getString("opening_hours") // NOUVEAU
                ))
            }
        }

        // 2. Séparation entre lieux Obligatoires et Candidats
        val mandatoryPlaces = allPlaces.filter { it.id in req.selectedPlaceIds }

        val requestedCategories = req.categories.map {
            when (it) {
                "Restaurant" -> "RESTAURATION"
                else -> it.uppercase()
            }
        }

        val candidatePlaces = allPlaces.filter {
            it.category.name in requestedCategories &&
                    it.effort <= req.effortLevel &&
                    it.id !in req.selectedPlaceIds
        }

        println("Lieux totaux dans la BDD : ${allPlaces.size}")
        println("Catégories demandées : $requestedCategories")
        println("Lieux candidats trouvés : ${candidatePlaces.size}")

        // VÉRIFICATION IMMÉDIATE DU BUDGET/TEMPS DE BASE
        val mandatoryCost = mandatoryPlaces.sumOf { it.price }
        val mandatoryDuration = mandatoryPlaces.sumOf { it.duration }

        if (mandatoryCost > req.budgetMax) {
            return@dbQuery listOf(ItineraryResponse(
                name = "Erreur", hexColor = "#FF0000", totalPrice = 0, totalDuration = 0,
                avgEffort = 0, mealIncluded = false,
                errorMessage = "Le budget est trop bas pour inclure vos lieux favoris ($mandatoryCost€ requis)."
            ))
        }

        // 3. Fonction de remplissage intelligente AVEC GESTION DU TEMPS
        fun buildVariant(name: String, color: String, sortedCandidates: List<Place>): ItineraryResponse {
            val finalSteps = mutableListOf<Place>()
            var currentCost = 0
            var currentDuration = 0
            var currentTimeMinutes = req.startTimeMinutes // On démarre l'horloge à l'heure choisie !

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
                        // 👈 On utilise .copy() pour injecter l'heure d'arrivée
                        finalSteps.add(place.copy(arrivalTime = formatTime(estimatedArrival)))
                        currentCost += place.price
                        currentDuration += place.duration
                        currentTimeMinutes += placeDurationMin + 30
                    }
                }
            }

            val coverImages = getTopImagesForPlaces(finalSteps)

            return ItineraryResponse(
                name = name,
                hexColor = color,
                totalPrice = currentCost,
                totalDuration = currentDuration,
                avgEffort = if(finalSteps.isEmpty()) 0 else finalSteps.sumOf { it.effort } / finalSteps.size,
                mealIncluded = finalSteps.any { it.category.name == "RESTAURATION" },
                steps = finalSteps,
                coverImages = coverImages
            )
        }

        // 4. Génération des 3 variantes
        val result = listOf(
            buildVariant("Éco", "#2D5A27", candidatePlaces.sortedBy { it.price }),
            buildVariant("Équilibré", "#E59866", candidatePlaces.shuffled()),
            buildVariant("Confort", "#884154", candidatePlaces.sortedByDescending { it.price })
        )

        // On retourne la liste
        result
    }

    suspend fun savePath(request: SavePathRequest) = dbQuery {
        val newItineraryId = Itineraries.insert {
            it[name] = request.name
            it[hexColor] = request.hexColor
            it[totalPrice] = request.totalPrice
            it[totalDuration] = request.totalDuration
            it[avgEffort] = request.avgEffort
            it[mealIncluded] = request.mealIncluded
            it[authorId] = request.userId
        } get Itineraries.id

        request.placeIds.forEachIndexed { index, placeId ->
            Steps.insert {
                it[itineraryId] = newItineraryId
                it[this.placeId] = placeId
                it[stepOrder] = index + 1
            }
        }
    }

    suspend fun getItinerariesByCategory(userId: String, category: String): List<ItineraryResponse> = dbQuery {
        val query = if (category == "SUGGESTIONS") {
            Itineraries.selectAll().limit(10)
        } else {
            Itineraries.select { Itineraries.authorId eq userId }
        }

        query.map { row ->
            val itineraryId = row[Itineraries.id]

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

            ItineraryResponse(
                id = itineraryId,
                name = row[Itineraries.name],
                hexColor = row[Itineraries.hexColor],
                totalPrice = row[Itineraries.totalPrice]?: 0,
                totalDuration = row[Itineraries.totalDuration]?: 0,
                avgEffort = (row[Itineraries.avgEffort]?: 0.0).roundToInt(),
                mealIncluded = row[Itineraries.mealIncluded]?: false,
                steps = places,
                coverImages = coverImages
            )
        }
    }

    // --- FONCTION UTILITAIRE : VÉRIFICATION DU TEMPS ---
    private fun isPlaceOpen(jsonStr: String?, arrivalMin: Int, departureMin: Int): Boolean {
        // Si le lieu n'a pas d'horaires dans la base, on le considère toujours ouvert
        if (jsonStr.isNullOrEmpty() || jsonStr == "null") return true

        try {
            val jsonArray = Json.parseToJsonElement(jsonStr).jsonArray

            // On normalise l'arrivée/départ sur 24h au cas où l'itinéraire dépasse minuit
            val arrivalNormalized = arrivalMin % (24 * 60)
            var departureNormalized = departureMin % (24 * 60)

            // Si la visite chevauche minuit (ex: 23h30 à 01h00), on ajuste pour le calcul
            if (departureNormalized < arrivalNormalized) departureNormalized += 24 * 60

            for (element in jsonArray) {
                val obj = element.jsonObject
                val openStr = obj["open"]?.jsonPrimitive?.content ?: continue
                val closeStr = obj["close"]?.jsonPrimitive?.content ?: continue

                // Convertir "10:30" en minutes : (10 * 60) + 30
                val openParts = openStr.split(":")
                val closeParts = closeStr.split(":")
                val openMin = openParts[0].toInt() * 60 + openParts[1].toInt()
                var closeMin = closeParts[0].toInt() * 60 + closeParts[1].toInt()

                // Si l'horaire de fermeture est après minuit (ex: 02:00)
                if (closeMin < openMin) closeMin += 24 * 60

                // Le lieu est valide si on arrive APRES l'ouverture et qu'on repart AVANT la fermeture
                if (arrivalNormalized >= openMin && departureNormalized <= closeMin) {
                    return true
                }
            }
            return false // Aucun créneau ne permettait la visite complète
        } catch (e: Exception) {
            println("Erreur de parsing des horaires : ${e.localizedMessage}")
            return true // En cas d'erreur de format dans la BDD, on ne bloque pas la génération
        }
    }

    private fun formatTime(minutes: Int): String {
        val h = (minutes / 60) % 24
        val m = minutes % 60
        return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
    }

    // Fonction pour récupérer jusqu'à 4 images pour un itinéraire
    private fun getTopImagesForPlaces(places: List<Place>): List<String> {
        if (places.isEmpty()) return emptyList()

        val placeIds = places.map { it.id }.joinToString("','", "'", "'")
        val imageUrls = mutableListOf<String>()

        // On cherche les posts liés à ces lieux, triés par le nombre de likes
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
                    // Les urls sont séparées par des virgules, on les coupe
                    val urls = urlsString.split(",").map { it.trim() }
                    imageUrls.addAll(urls)
                }
            }
        }

        // On retourne uniquement les 4 premières images distinctes (ou moins s'il n'y en a pas assez)
        return imageUrls.distinct().take(4)
    }
}