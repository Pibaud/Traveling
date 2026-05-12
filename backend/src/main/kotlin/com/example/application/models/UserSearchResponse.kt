package com.example.application.models

import kotlinx.serialization.Serializable

@Serializable
data class UserSearchResponse(
    val uid: String,
    val username: String,
    val avatarUrl: String?
)