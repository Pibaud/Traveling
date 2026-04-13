package com.example.application.models // ou le package que tu utilises

import kotlinx.serialization.Serializable

@Serializable // OBLIGATOIRE pour l'envoi en JSON
data class CreatePostRequest(
    val description: String,
    val placeId: String,
    val tags: List<String>,
    val isPublic: Boolean,
    val groupIds: List<String> = emptyList(),
    val imageUrls: List<String>,
    val authorId: String
)