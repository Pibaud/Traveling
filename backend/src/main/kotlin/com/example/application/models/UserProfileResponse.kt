package com.example.application.models

import kotlinx.serialization.Serializable

@Serializable
data class UserProfileResponse(
    val username: String,
    val bio: String?,
    val avatarUrl: String?,
    val createdCount: Int,
    val likedCount: Int,
    val totalLikesReceived: Int,
    val totalHours: Int // La fameuse 4ème case !
)