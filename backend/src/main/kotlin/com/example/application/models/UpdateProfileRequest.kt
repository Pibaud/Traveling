package com.example.application.models // Vérifie que c'est le bon package

import kotlinx.serialization.Serializable

@Serializable
data class UpdateProfileRequest(
    val bio: String? = null,
    val avatarUrl: String? = null,
    val preferences: String? = null
)