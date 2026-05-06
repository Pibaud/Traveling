package com.example.application.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_itineraries")
data class CachedItinerary(
    @PrimaryKey val id: Int,
    val category: String, // "LIKED", "MINE", "SUGGESTIONS"
    val jsonPayload: String, // L'objet ItineraryResponse complet transformé en texte !
    val timestamp: Long = System.currentTimeMillis()
)