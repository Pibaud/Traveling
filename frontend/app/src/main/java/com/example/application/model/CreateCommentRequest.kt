package com.example.application.model

import kotlinx.serialization.Serializable

@Serializable
data class CreateCommentRequest(
    val postId: String,
    val userId: String,
    val content: String
)