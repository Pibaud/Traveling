package com.example.application.models // Vérifie ton package

import kotlinx.serialization.Serializable

@Serializable
data class LikeRequest(
    val postId: String,
    val userId: String // L'ID Firebase de l'utilisateur qui clique sur le cœur
)