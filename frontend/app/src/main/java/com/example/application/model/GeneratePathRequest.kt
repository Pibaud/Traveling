package com.example.application.model

import kotlinx.serialization.Serializable

@Serializable
data class GeneratePathRequest(
    val categories: List<String>,      // ex: ["Sport", "Nature"]
    val selectedPlaceIds: List<Int>,   // ex: [12, 45] (les lieux likés forcés)
    val budgetMax: Int,
    val durationHours: Int,
    val effortLevel: Int,
    val weatherTolerance: Int
)