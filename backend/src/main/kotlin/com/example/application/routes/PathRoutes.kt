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
import kotlinx.serialization.json.Json

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
                // Si l'app Android n'envoie pas de catégorie, on cherche les parcours de l'utilisateur par défaut
                val category = call.request.queryParameters["category"] ?: "MES_PARCOURS"

                val results = PathService.getItinerariesByCategory(userId, category)
                call.respond(HttpStatusCode.OK, results)

            } catch (e: Exception) {
                application.log.error("Erreur lors de la récupération de la liste", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Erreur inconnue")))
            }
        }

        // LA VRAIE ROUTE DE GÉNÉRATION
        post("/generate") {
            try {
                // 1. On reçoit et décode la requête d'Android
                val request = call.receive<GeneratePathRequest>()

                // 2. ON APPELLE LE VRAI ALGORITHME (Plus de simulation !)
                val results = PathService.generatePath(request)

                // 3. On renvoie les 3 choix au téléphone
                call.respond(results)

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

                // 2. APPEL À TA BASE DE DONNÉES (VIA LE SERVICE)
                PathService.savePath(request)

                // 3. On répond que tout est OK
                call.respond(HttpStatusCode.OK, mapOf("status" to "success"))

            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Erreur inconnue")))
            }
        }
    }
}