package com.example.application.model

import kotlinx.serialization.Serializable

@Serializable
data class NotificationToggleRequest(
    val groupId: String,
    val userId: String,
    val shouldNotify: Boolean
)