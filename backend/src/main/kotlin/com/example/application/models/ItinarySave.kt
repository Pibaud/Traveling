package com.example.application.models

import org.jetbrains.exposed.sql.Table

// Table Itinerary
object Itineraries : Table("itinerary") {
    val id = integer("id").autoIncrement() // Géré par PostgreSQL
    val name = varchar("name", 255)
    val description = text("description").nullable()
    val hexColor = varchar("hex_color", 50)
    val totalPrice = integer("total_price").nullable()
    val totalDuration = integer("total_duration").nullable()
    val avgEffort = double("avg_effort").nullable()
    val mealIncluded = bool("meal_included").nullable()
    val authorId = varchar("author_id", 255)

    override val primaryKey = PrimaryKey(id)
}

// Table Step (pour relier l'itinéraire à ses lieux)
object Steps : Table("step") {
    val itineraryId = integer("itinerary_id").references(Itineraries.id)
    val placeId = varchar("place_id", 255) // Mets integer() si tes places ont des IDs en int
    val stepOrder = integer("step_order")  // Pour garder l'ordre 1, 2, 3...
}