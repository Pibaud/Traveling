package com.example.application.model

import kotlinx.serialization.Serializable

@Serializable
data class LikeResponse(
    val liked: Boolean
)