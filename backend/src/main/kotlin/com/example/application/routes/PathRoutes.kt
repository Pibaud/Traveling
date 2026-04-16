package com.example.application.routes

import com.example.application.models.GeneratePathRequest
import com.example.application.models.ItineraryResponse
import com.example.application.models.PathRequest
import com.example.application.models.PathResponse
import com.example.application.services.PathService
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.pathRoutes() {
    route("/path") {
        // Endpoint pour générer les 2-3 options de parcours (Eco, Confort...)
        post("/generate") {
            val request = call.receive<PathRequest>()
            // Ici l'appel à votre algorithme complexe
            val result = listOf<PathResponse>() // Simulation de réponse
            call.respond(result)
        }

        // Endpoint pour l'export PDF mentionné dans le sujet
        get("/export/{id}") {
            val id = call.parameters["id"]
            call.respondText("Export PDF pour le trajet $id")
        }

        get("/list") {
            val userId = call.request.queryParameters["userId"] ?: ""
            val category = call.request.queryParameters["category"] ?: "SUGGESTIONS"
            call.respond(PathService.getItinerariesByCategory(userId, category))
        }

        // Dans PathRoutes.kt
        post("/generate") {
            val request = call.receive<GeneratePathRequest>()

            // Simulation des 3 parcours types de la maquette
            val simulation = listOf(
                ItineraryResponse(1, "Eco", "#2D5A27", 8, 5, true),       // 8€, 5h, repas compris [cite: 234, 246]
                ItineraryResponse(2, "Équilibré", "#E59866", 40, 24, true), // 40€, 1 journée [cite: 242, 248]
                ItineraryResponse(3, "Confort", "#884154", 300, 48, true)  // 300€, 2 jours [cite: 245, 252]
            )

            call.respond(simulation)
        }
    }
}