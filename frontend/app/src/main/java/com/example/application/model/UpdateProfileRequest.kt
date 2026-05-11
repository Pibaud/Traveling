package com.example.application.model

import kotlinx.serialization.Serializable

@Serializable
data class UpdateProfileRequest(
    val bio: String? = null,
    val avatarUrl: String? = null,
    val preferences: String? = null // 👈 Nouvel ajout
)