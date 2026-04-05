package com.example.application.models

import kotlinx.serialization.Serializable

@Serializable
data class PathRequest(
    val selectedActivities: List<PlaceCategory>, // Restauration, Culture, etc. [cite: 51, 297]
    val budgetMax: Double,                       // Le budget saisi [cite: 52, 309]
    val durationHours: Int,                      // Durée totale du séjour [cite: 52, 310]
    val effortLevel: Int,                        // Niveau d'effort (1 à 5) [cite: 52, 319]
    val weatherTolerance: String,                // Sensibilité météo [cite: 52, 319]
    val mandatoryPlacesIds: List<String> = emptyList() // Lieux obligatoires [cite: 58, 304]
)
