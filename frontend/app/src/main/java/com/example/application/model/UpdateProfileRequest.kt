package com.example.application.model

import kotlinx.serialization.Serializable

@Serializable
data class UpdateProfileRequest(
    val bio: String?,
    val avatarUrl: String?
)