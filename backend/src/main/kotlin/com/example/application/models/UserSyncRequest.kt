package com.example.application.models

import kotlinx.serialization.Serializable

@Serializable
data class UserSyncRequest(
    val uid: String,
    val email: String
)