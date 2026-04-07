package com.example.application.routes

import com.example.application.models.PathRequest
import com.example.application.models.PathResponse
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.pathRoutes() {
    route("/api/path") {
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
    }
}