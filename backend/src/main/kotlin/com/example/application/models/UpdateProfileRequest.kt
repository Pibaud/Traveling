package com.example.application.models

import kotlinx.serialization.Serializable

@Serializable
data class UpdateProfileRequest(
    val bio: String?,
    val avatarUrl: String?
)