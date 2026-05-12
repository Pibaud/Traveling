package com.example.application.models

import kotlinx.serialization.Serializable

@Serializable
data class ShareItineraryRequest(
    val itineraryId: Int,
    val groupId: String
)