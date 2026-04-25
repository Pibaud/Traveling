package com.example.application.models

import kotlinx.serialization.Serializable

@Serializable
data class ItineraryResponse(
    val id: Int = 0,
    val name: String,
    val hexColor: String,
    val totalPrice: Int,
    val totalDuration: Int,
    val avgEffort: Int,
    val mealIncluded: Boolean,
    val steps: List<Place> = emptyList(), // La liste ordonnée des lieux pour la carte
    val errorMessage: String? = null      // Pour prévenir l'utilisateur si budget trop bas
)