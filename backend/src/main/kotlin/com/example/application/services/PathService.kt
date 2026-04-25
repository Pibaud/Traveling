package com.example.application.services

import com.example.application.DatabaseFactory.dbQuery
import com.example.application.Places
import com.example.application.models.GeneratePathRequest
import com.example.application.models.ItineraryResponse
import org.jetbrains.exposed.sql.select

object PathService {
    suspend fun generatePath(req: GeneratePathRequest): List<ItineraryResponse> = dbQuery {
        // 1. Récupérer les lieux obligatoires (sélectionnés précisément)
        val mandatoryPlaces = Places.select { Places.id inList req.selectedPlaceIds }.map { toPlace(it) }

        // VÉRIFICATION IMMÉDIATE
        val mandatoryCost = mandatoryPlaces.sumOf { it.price }
        val mandatoryDuration = mandatoryPlaces.sumOf { it.duration }

        if (mandatoryCost > req.budgetMax) {
            return@dbQuery listOf(ItineraryResponse(
                name = "Erreur", hexColor = "#FF0000", totalPrice = 0, totalDuration = 0,
                avgEffort = 0, mealIncluded = false,
                errorMessage = "Le budget est trop bas pour inclure vos lieux favoris ($mandatoryCost€ requis)."
            ))
        }

        // 2. Récupérer les lieux possibles par catégorie pour compléter
        val candidatePlaces = Places.select {
            (Places.category inList req.categories) and (Places.id notInList req.selectedPlaceIds)
        }.map { toPlace(it) }

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
                mealIncluded = finalSteps.any { it.category == "Restaurant" },
                steps = finalSteps // ENVOI DES ÉTAPES POUR LA CARTE
            )
        }

        // Génération des 3 variantes (Eco, Équilibré, Confort)
        listOf(
            buildVariant("Éco", "#2D5A27", candidatePlaces.sortedBy { it.price }),
            buildVariant("Équilibré", "#E59866", candidatePlaces.shuffled()),
            buildVariant("Confort", "#884154", candidatePlaces.sortedByDescending { it.price })
        )
    }
}