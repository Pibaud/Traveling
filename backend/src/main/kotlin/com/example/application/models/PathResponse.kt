package com.example.application.models

import com.example.application.model.Place
import kotlinx.serialization.Serializable

@Serializable
data class PathResponse(
    val type: String,               // "Eco", "Equilibré" ou "Confort" [cite: 59, 303]
    val steps: List<Place>,         // La liste ordonnée des lieux à visiter [cite: 60, 333]
    val totalCost: Double,          // Coût total estimé [cite: 61, 315]
    val totalDurationMinutes: Int,  // Temps total (visites + trajets) [cite: 61, 330]
    val totalDistanceKm: Double,    // Distance à parcourir [cite: 61, 330]
    val effortMetric: Int,          // Score d'effort calculé [cite: 61, 332]
    val mapStaticImageUrl: String? = null // URL pour l'image de la carte [cite: 61, 328]
)
