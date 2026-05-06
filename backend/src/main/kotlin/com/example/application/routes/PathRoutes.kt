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
        post("/export/{id}") {
            try {
                val idStr = call.parameters["id"]
                if (idStr == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "ID manquant"))
                    return@post
                }
                val itineraryId = idStr.toInt()

                // On récupère l'image Base64 depuis le Body (elle peut être vide/nulle)
                // NOUVEAU CODE
                val base64MapImage = try { call.receiveText() } catch (e: Exception) { null }
                val cleanBase64 = if (base64MapImage.isNullOrBlank() || base64MapImage == "null") {
                    null
                } else {
                    base64MapImage.trim('"') // 👈 LA MAGIE EST ICI : On retire les guillemets !
                }

                val itinerary = PathService.getItineraryById(itineraryId)
                if (itinerary == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Itinéraire introuvable"))
                    return@post
                }

                // On donne l'image décodée au générateur PDF
                val pdfBytes = com.example.application.services.PdfGenerator.generateItineraryPdf(itinerary, cleanBase64)

                call.respondBytes(
                    bytes = pdfBytes,
                    contentType = io.ktor.http.ContentType.Application.Pdf,
                    status = HttpStatusCode.OK
                )

            } catch (e: Exception) {
                application.log.error("Erreur lors de la génération du PDF", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Impossible de générer le PDF"))
            }
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

        delete("/{id}") {
            try {
                // 1. On récupère l'ID de l'itinéraire depuis l'URL (ex: DELETE /path/42)
                val idParam = call.parameters["id"]
                if (idParam == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "ID manquant"))
                    return@delete
                }
                val itineraryId = idParam.toInt()

                // 2. On récupère l'ID de l'utilisateur (Firebase) pour sécuriser la suppression
                val userId = call.request.queryParameters["userId"]
                if (userId.isNullOrEmpty()) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Utilisateur non connecté"))
                    return@delete
                }

                // 3. On demande au service de supprimer
                val isDeleted = PathService.deletePath(userId, itineraryId)

                // 4. On répond selon le résultat
                if (isDeleted) {
                    call.respond(HttpStatusCode.OK, mapOf("status" to "success"))
                } else {
                    // Si false, c'est soit que l'ID n'existe pas, soit que le userId ne correspond pas à l'auteur
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Impossible de supprimer cet itinéraire"))
                }

            } catch (e: NumberFormatException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Format d'ID invalide"))
            } catch (e: Exception) {
                application.log.error("Erreur lors de la suppression de l'itinéraire", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Erreur inconnue")))
            }
        }
    }
}