package com.example.application.model

import kotlinx.serialization.Serializable

@Serializable
data class SavePathRequest(
    val userId: String,
    val name: String,
    val hexColor: String,
    val totalPrice: Int,
    val totalDuration: Int,
    val avgEffort: Int,
    val mealIncluded: Boolean,
    val placeIds: List<String> // La liste des lieux qui composent ce parcours
)