package com.example.application.model

import kotlinx.serialization.Serializable

@Serializable
data class CreateGroupRequest(
    val name: String,
    val description: String,
    val isPublic: Boolean,
    val tags: List<String>,
    val photoUrl: String,
    val authorId: String
)