package com.example.application.models

import kotlinx.serialization.Serializable

@Serializable
data class UpdateProfileRequest(
    val bio: String? = null,
    val avatarUrl: String? = null,
    val preferences: String? = null // 👈 Nouvel ajout
)