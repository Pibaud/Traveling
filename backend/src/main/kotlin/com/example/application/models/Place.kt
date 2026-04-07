package com.example.application.models

import kotlinx.serialization.Serializable

@Serializable
data class Place(
    val id: String = "",
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val category: PlaceCategory = PlaceCategory.CULTURE
)

@Serializable
enum class PlaceCategory {
    CULTURE, RESTAURATION, LOISIRS, DECOUVERTE
}