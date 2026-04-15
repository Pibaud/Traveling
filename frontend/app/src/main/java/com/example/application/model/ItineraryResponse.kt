package com.example.application.models

import kotlinx.serialization.Serializable

@Serializable
data class ItineraryResponse(
    val id: Int,
    val name: String,
    val hexColor: String,
    val totalPrice: Int,
    val totalDuration: Int,
    val mealIncluded: Boolean,
    val imageUrls: List<String> = emptyList()
)