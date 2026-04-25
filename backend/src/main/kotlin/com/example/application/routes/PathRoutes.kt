package com.example.application.routes

import com.example.application.models.GeneratePathRequest
import com.example.application.models.ItineraryResponse
import com.example.application.services.PathService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun Route.pathRoutes() {
    route("/path") {

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
            try {
                // On récupère le texte brut pour le log en cas d'erreur
                val bodyText = call.receiveText()

                // On convertit manuellement pour attraper l'erreur précise
                val request = Json.decodeFromString<GeneratePathRequest>(bodyText)

                // Simulation pour l'instant
                val simulation = listOf(
                    ItineraryResponse(1, "Eco", "#2D5A27", 8, 5, true, 3.0),
                    ItineraryResponse(2, "Équilibré", "#E59866", 40, 24, true, 3.0),
                    ItineraryResponse(3, "Confort", "#884154", 300, 48, true, 3.0)
                )
                call.respond(simulation)
            } catch (e: Exception) {
                println("ERREUR DETAIL : ${e.localizedMessage}")
                call.respond(HttpStatusCode.BadRequest, "Erreur de format : ${e.localizedMessage}")
            }
        }
    }
}