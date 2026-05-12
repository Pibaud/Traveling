package com.example.application.models // (ou .models côté back)

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
    val steps: List<Place> = emptyList(),
    val errorMessage: String? = null,
    val coverImages: List<String> = emptyList(),
    var isLiked: Boolean = false,
    var likeCount: Int = 0,
    val userId: String = "",
    val startTimeMinutes: Int,
    val authorName: String = "Utilisateur",
    val sharedAt: Long = 0L
)