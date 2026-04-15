package com.example.application.model

import kotlinx.serialization.Serializable

@Serializable
data class UserSyncRequest(
    val uid: String,
    val email: String
)