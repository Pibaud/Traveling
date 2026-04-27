package com.example.application.model

import kotlinx.serialization.Serializable

@Serializable
data class Place(
    val id: String = "",
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val category: PlaceCategory = PlaceCategory.CULTURE,
    val price: Int = 0,         // Prix en euros
    val duration: Int = 1,      // Durée en heures
    val effort: Int = 1         // Effort de 1 à 3 (ou 5)
)

@Serializable
enum class PlaceCategory {
    CULTURE, RESTAURATION, LOISIRS, DECOUVERTE, SPORT, NATURE
}