package com.example.application.model

import kotlinx.serialization.Serializable

@Serializable
data class ReportRequest(
    val description: String,
    val postId: String? = null
)