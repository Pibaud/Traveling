package com.example.application.model

import kotlinx.serialization.Serializable

@Serializable
data class Post(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorAvatarUrl: String = "",
    val description: String = "",
    val imageUrls: List<String> = emptyList(), // Les photos du carrousel horizontal
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val tags: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),

    val place: Place = Place()
)