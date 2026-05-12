package com.example.application.model

import kotlinx.serialization.Serializable

@Serializable
data class UserSearchResponse(
    val uid: String,
    val username: String,
    val avatarUrl: String?
)