package com.example.application.services

import com.example.application.DatabaseFactory.dbQuery
import com.example.application.models.GeneratePathRequest
import com.example.application.models.ItineraryResponse
import com.example.application.model.Place
import com.example.application.model.PlaceCategory
import com.example.application.models.Itineraries
import com.example.application.models.SavePathRequest
import com.example.application.models.Steps
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import kotlin.math.roundToInt

object PathService {
    suspend fun generatePath(req: GeneratePathRequest): List<ItineraryResponse> = dbQuery {

        // 1. Récupération de TOUS les lieux avec du SQL Brut (comme ton ami)
        val sql = """
            SELECT id, name, category, ST_Y(location::geometry) as lat, ST_X(location::geometry) as lng, price, duration, effort
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
                    effort = rs.getInt("effort")
                ))
            }
        }

        // 2. Séparation entre lieux Obligatoires et Candidats (Filtrage en Kotlin)
        val mandatoryPlaces = allPlaces.filter { it.id in req.selectedPlaceIds }

        val requestedCategories = req.categories.map {
            when (it) {
                "Restaurant" -> "RESTAURATION" // Traduction spécifique
                else -> it.uppercase()         // "Culture" -> "CULTURE", "Sport" -> "SPORT"
            }
        }

        // On filtre les candidats avec notre liste traduite
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

        // 3. Fonction de remplissage intelligente
        fun buildVariant(name: String, color: String, sortedCandidates: List<Place>): ItineraryResponse {
            val finalSteps = mandatoryPlaces.toMutableList()
            var currentCost = mandatoryCost
            var currentDuration = mandatoryDuration

            for (place in sortedCandidates) {
                if (currentCost + place.price <= req.budgetMax && currentDuration + place.duration <= req.durationHours) {
                    finalSteps.add(place)
                    currentCost += place.price
                    currentDuration += place.duration
                }
            }

            return ItineraryResponse(
                name = name,
                hexColor = color,
                totalPrice = currentCost,
                totalDuration = currentDuration,
                avgEffort = if(finalSteps.isEmpty()) 0 else finalSteps.sumOf { it.effort } / finalSteps.size,
                mealIncluded = finalSteps.any { it.category.name == "RESTAURATION" }, // Vérifie bien le nom de ton enum !
                steps = finalSteps // LA LISTE AVEC LAT/LNG POUR LA CARTE !
            )
        }

        // 4. Génération des 3 variantes
        listOf(
            buildVariant("Éco", "#2D5A27", candidatePlaces.sortedBy { it.price }),
            buildVariant("Équilibré", "#E59866", candidatePlaces.shuffled()),
            buildVariant("Confort", "#884154", candidatePlaces.sortedByDescending { it.price })
        )
    }

    suspend fun savePath(request: SavePathRequest) = dbQuery {

        // 1. Sauvegarde de l'itinéraire (PostgreSQL génère l'ID)
        val newItineraryId = Itineraries.insert {
            it[name] = request.name
            it[hexColor] = request.hexColor
            it[totalPrice] = request.totalPrice
            it[totalDuration] = request.totalDuration
            it[avgEffort] = request.avgEffort
            it[mealIncluded] = request.mealIncluded
            it[authorId] = request.userId
        } get Itineraries.id // On récupère l'ID auto-incrémenté !

        // 2. Sauvegarde des étapes du parcours (table 'step')
        request.placeIds.forEachIndexed { index, placeId ->
            Steps.insert {
                it[itineraryId] = newItineraryId
                it[this.placeId] = placeId
                it[stepOrder] = index + 1 // Ordre de passage : 1, 2, 3...
            }
        }
    }

    suspend fun getItinerariesByCategory(userId: String, category: String): List<ItineraryResponse> = dbQuery {

        // 1. On filtre la table itinerary (soit ceux du user, soit tous pour des suggestions)
        val query = if (category == "SUGGESTIONS") {
            Itineraries.selectAll().limit(10) // Prendre 10 suggestions au hasard
        } else {
            // "MES_PARCOURS" par défaut : on filtre avec l'authorId
            Itineraries.select { Itineraries.authorId eq userId }
        }

        // 2. On transforme chaque ligne trouvée en ItineraryResponse
        query.map { row ->
            val itineraryId = row[Itineraries.id]

            // 3. On récupère les étapes associées avec leurs coordonnées GPS
            val sql = """
            SELECT p.id, p.name, p.category, ST_Y(p.location::geometry) as lat, ST_X(p.location::geometry) as lng, p.price, p.duration, p.effort
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
                        effort = rs.getInt("effort")
                    ))
                }
            }

            // 4. On retourne l'objet complet
            ItineraryResponse(
                id = itineraryId,
                name = row[Itineraries.name],
                hexColor = row[Itineraries.hexColor],
                totalPrice = row[Itineraries.totalPrice]?: 0,
                totalDuration = row[Itineraries.totalDuration]?: 0,
                avgEffort = (row[Itineraries.avgEffort]?: 0.0).roundToInt(),
                mealIncluded = row[Itineraries.mealIncluded]?: false,
                steps = places // On attache la liste ordonnée des lieux qu'on vient de récupérer
            )
        }
    }
}