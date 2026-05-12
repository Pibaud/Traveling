package com.example.application.model

import kotlinx.serialization.Serializable

@Serializable
data class AnalysisResult(
    val tags: List<String>,
    val embedding: List<Float>
)