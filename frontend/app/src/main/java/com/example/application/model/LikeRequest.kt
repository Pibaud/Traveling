package com.example.application.model

import kotlinx.serialization.Serializable

@Serializable
data class LikeRequest(
    val postId: String,
    val userId: String
)