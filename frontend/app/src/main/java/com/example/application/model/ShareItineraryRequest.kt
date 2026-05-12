package com.example.application.model

import kotlinx.serialization.Serializable

@Serializable
data class ShareItineraryRequest(
    val itineraryId: Int,
    val groupId: String
)