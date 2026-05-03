package com.example.application.routes

import com.example.application.models.GeneratePathRequest
import com.example.application.models.ItineraryResponse
import com.example.application.models.SavePathRequest
import com.example.application.services.PathService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.pathRoutes() {
    route("/path") {

        // Endpoint pour l'export PDF
        get("/export/{id}") {
            val id = call.parameters["id"]
            call.respondText("Export PDF pour le trajet $id")
        }

        get("/list") {
            try {
                val userId = call.request.queryParameters["userId"] ?: ""
                val category = call.request.queryParameters["category"] ?: "MES_PARCOURS"

                val results = PathService.getItinerariesByCategory(userId, category)
                call.respond(HttpStatusCode.OK, results)

            } catch (e: Exception) {
                application.log.error("Erreur lors de la récupération de la liste", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Erreur inconnue")))
            }
        }

        post("/generate") {
            try {
                val request = call.receive<GeneratePathRequest>()
                val results = PathService.generatePath(request)

                if (results.isEmpty() || results.all { it.steps.isEmpty() }) {
                    call.respond(HttpStatusCode.OK, emptyList<ItineraryResponse>())
                } else {
                    call.respond(HttpStatusCode.OK, results)
                }

            } catch (e: Exception) {
                application.log.error("Erreur lors de la génération", e)
                call.respond(
                    status = HttpStatusCode.BadRequest,
                    message = "Erreur de format ou d'algorithme : ${e.localizedMessage}"
                )
            }
        }

        post("/save") {
            try {
                val request = call.receive<SavePathRequest>()
                PathService.savePath(request)
                call.respond(HttpStatusCode.OK, mapOf("status" to "success"))

            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Erreur inconnue")))
            }
        }

        // 👇 NOUVELLE ROUTE POUR LE LIKE/DISLIKE 👇
        post("/like") {
            try {
                val userId = call.request.queryParameters["userId"] ?: throw Exception("userId manquant")
                val itineraryIdStr = call.request.queryParameters["itineraryId"] ?: throw Exception("itineraryId manquant")
                val itineraryId = itineraryIdStr.toInt()

                // On appelle le service qu'on a créé précédemment
                val isNowLiked = PathService.toggleLike(userId, itineraryId)

                call.respond(HttpStatusCode.OK, mapOf("liked" to isNowLiked))
            } catch (e: Exception) {
                application.log.error("Erreur lors du toggleLike", e)
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Erreur inconnue")))
            }
        }
    }
}