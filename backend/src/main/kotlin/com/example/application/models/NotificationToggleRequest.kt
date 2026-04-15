package com.example.application.models

import kotlinx.serialization.Serializable

@Serializable
data class NotificationToggleRequest(
    val groupId: String,
    val userId: String,
    val shouldNotify: Boolean
)