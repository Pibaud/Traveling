package com.example.application.model

import kotlinx.serialization.Serializable

@Serializable
data class GeneratePathRequest(
    val categories: List<String>,
    val selectedPlaceIds: List<String>,
    val budgetMax: Int,
    val durationHours: Int,
    val effortLevel: Int,
    val weatherTolerance: Int,
    val mealIncluded: Boolean,
    val startTimeMinutes: Int // 👈 NOUVEAU CHAMP (ex: 9h30 = 9 * 60 + 30 = 570)
)