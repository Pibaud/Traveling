package com.example.application.services

import com.example.application.DatabaseFactory.dbQuery
import com.example.application.*
import com.example.application.models.ItineraryResponse
import org.jetbrains.exposed.sql.*

object PathService {
    suspend fun getItinerariesByCategory(userId: String, category: String): List<ItineraryResponse> = dbQuery {
        val query = when (category) {
            "MINE" -> Itineraries.select { Itineraries.authorId eq userId }
            "SAVED" -> (Itineraries innerJoin ItineraryLikes).select { ItineraryLikes.userId eq userId }
            else -> Itineraries.selectAll()
        }

        query.map { row ->
            ItineraryResponse(
                id = row[Itineraries.id],
                name = row[Itineraries.name],
                hexColor = row[Itineraries.hexColor],
                totalPrice = row[Itineraries.totalPrice],
                totalDuration = row[Itineraries.totalDuration],
                mealIncluded = row[Itineraries.mealIncluded]
            )
        }
    }
}