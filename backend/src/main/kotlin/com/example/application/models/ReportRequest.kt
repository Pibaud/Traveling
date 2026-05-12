package com.example.application.models

import kotlinx.serialization.Serializable

@Serializable
data class ReportRequest(
    val description: String,
    val postId: String? = null
)