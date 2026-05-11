package com.example.application.model

import kotlinx.serialization.Serializable

@Serializable
data class UserProfileResponse(
    val username: String,
    val bio: String?,
    val avatarUrl: String?,
    val createdCount: Int,
    val likedCount: Int,
    val totalLikesReceived: Int,
    val followerCount: Int, // 👈 Remplace totalHours
    val isFollowing: Boolean, // 👈 Pour savoir si on est déjà abonné
    val preferredCategories: String? // 👈 Ex: "CULTURE,LOISIRS"
)