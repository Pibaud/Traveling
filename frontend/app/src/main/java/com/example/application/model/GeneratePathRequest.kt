package com.example.application.model

import kotlinx.serialization.Serializable

@Serializable
data class GeneratePathRequest(
    val categories: List<String>,
    val selectedPlaceIds: List<String> = emptyList(),
    val budgetMax: Int,
    val durationHours: Int,
    val effortLevel: Int,
    val weatherTolerance: Int
)