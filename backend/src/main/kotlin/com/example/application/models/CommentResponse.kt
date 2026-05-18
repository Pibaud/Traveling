package com.example.application.models

import kotlinx.serialization.Serializable

@Serializable
data class CommentResponse(
    val id: String,
    val postId: String,
    val authorId: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val content: String,
    val timestamp: Long
)