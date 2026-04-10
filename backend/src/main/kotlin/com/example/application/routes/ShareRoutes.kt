package com.example.application.routes

import com.example.application.models.CreatePostRequest
import com.example.application.models.Post
import com.example.application.services.PlaceService
import com.example.application.services.PostService
import com.example.application.services.TagService
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.request.receive

fun Route.shareRoutes() {
    route("/share") {
        // Flux aléatoire de découverte
        get ("/places/searchbbox"){
            val minLat = call.parameters["minLat"]?.toDoubleOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
            val minLng = call.parameters["minLng"]?.toDoubleOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
            val maxLat = call.parameters["maxLat"]?.toDoubleOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)
            val maxLng = call.parameters["maxLng"]?.toDoubleOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)

            val response = PlaceService.searchByBoundingBox(minLat, minLng, maxLat, maxLng)

            call.respond(response)
        }

        // Ajoute ceci dans ton route("/share") { ... }
        get("/places/search") {
            val query = call.request.queryParameters["q"]

            if (query.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "La requête 'q' est obligatoire")
                return@get
            }

            // Appelle la base de données
            val places = PlaceService.searchByName(query)
            call.respond(places)
        }

        get("/tags/suggest") {
            val q = call.request.queryParameters["q"] ?: ""
            if (q.length < 2) return@get call.respond(emptyList<String>())
            call.respond(TagService.searchTags(q))
        }

        post("/publish") {
            try {
                // Ktor transforme automatiquement le JSON reçu en CreatePostRequest !
                val request = call.receive<CreatePostRequest>()

                // Tu passes toutes les infos à ton service de base de données
                // (Assure-toi de mettre à jour PostService pour qu'il accepte la liste d'URLs)
                val success = PostService.createNewPost(
                    description = request.description,
                    placeId = request.placeId,
                    isPublic = request.isPublic,
                    tags = request.tags,
                    imageUrls = request.imageUrls // Sauvegarde ces URLs dans la table posts
                )

                if (success) {
                    call.respond(HttpStatusCode.Created)
                } else {
                    call.respond(HttpStatusCode.InternalServerError, "Erreur d'insertion DB")
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Format de requête invalide : ${e.message}")
            }
        }

        get("/feed") {
            call.respond(listOf<Post>())
        }
    }
}