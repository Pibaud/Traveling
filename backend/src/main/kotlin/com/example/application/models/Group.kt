package com.example.application.models

import kotlinx.serialization.Serializable

@Serializable
data class Group(
    val id: String,
    val name: String,
    val description: String,
    val photoUrl: String,
    val nbMembers: Int,
    val nbPosts: Int,
    val isPublic: Boolean,
    val tags: List<String>,
    val isMember: Boolean = false,
    var isNotificationEnabled: Boolean = false
)