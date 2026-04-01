package com.example.application.model

import kotlinx.serialization.Serializable

@Serializable
data class Place(
    val id: String = "",
    val name: String = "",
    val amenity: String = "",

    // Coordonnées pour TravelPath (OSMDroid)
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,

    // Médias pour TravelShare
    val imageUrls: List<String> = emptyList(),
    val authorId: String = "",
    val timestamp: Long = System.currentTimeMillis(),

    // Métadonnées pour l'algorithme de recommandation
    val category: PlaceCategory = PlaceCategory.CULTURE,
    val averagePrice: Double = 0.0,
    val rating: Float = 0f,
    val tags: List<String> = emptyList()
)

@Serializable
enum class PlaceCategory {
    CULTURE,        // Musées, monuments
    RESTAURATION,   // Restos, bars
    LOISIRS,        // Parcs, activités sportives
    DECOUVERTE      // Points de vue, balades
}