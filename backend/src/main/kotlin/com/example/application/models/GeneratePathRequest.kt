package com.example.application.models

import kotlinx.serialization.Serializable

@Serializable
data class GeneratePathRequest(
    val activities: List<String>,
    val budgetMax: Int,
    val durationDays: Int,
    val effortLevel: Int, // 1 à 5
    val weatherTolerance: Int // Index du curseur
)