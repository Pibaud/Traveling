package com.example.application.model

import kotlinx.serialization.Serializable

@Serializable
data class Comment(
    val id: String,
    val postId: String,
    val authorId: String,
    val authorName: String,
    val authorAvatarUrl: String? = null,
    val content: String,
    val timestamp: Long
)