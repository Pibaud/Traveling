package com.example.application.routes

import com.example.application.models.CreateGroupRequest
import com.example.application.models.CreatePostRequest
import com.example.application.models.LikeRequest
import com.example.application.models.JoinGroupRequest
import com.example.application.models.NotificationToggleRequest
import com.example.application.services.GroupService
import com.example.application.services.PlaceService
import com.example.application.services.PostService
import com.example.application.services.TagService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
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
                val success = PostService.createNewPost(
                    description = request.description,
                    placeId = request.placeId,
                    isPublic = request.isPublic,
                    tags = request.tags,
                    imageUrls = request.imageUrls,
                    authorId = request.authorId
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
            try {
                // On récupère l'ID de l'utilisateur passé en paramètre par Android
                val currentUserId = call.request.queryParameters["userId"]

                val feed = PostService.getFeed(currentUserId)
                call.respond(HttpStatusCode.OK, feed)
            } catch (e: Exception) {
                application.log.error("Erreur feed", e)
                call.respond(HttpStatusCode.InternalServerError, "Erreur")
            }
        }

        post("/like") {
            try {
                // On reçoit le couple (postId, userId)
                val request = call.receive<LikeRequest>()

                // On appelle le service magique
                val isLiked = PostService.toggleLike(request.postId, request.userId)

                // On répond avec un petit JSON indiquant le nouvel état du bouton
                call.respond(HttpStatusCode.OK, mapOf("liked" to isLiked))

            } catch (e: Exception) {
                application.log.error("Erreur lors du like : ${e.message}")
                call.respond(HttpStatusCode.BadRequest, "Impossible de traiter le like")
            }
        }

        post("/groups/create") {
            try {
                val request = call.receive<CreateGroupRequest>()
                val success = GroupService.createNewGroup(request.name, request.description, request.isPublic, request.tags, request.photoUrl, request.authorId)
                if (success) call.respond(HttpStatusCode.Created) else call.respond(HttpStatusCode.InternalServerError, "Erreur création groupe")
            } catch (e: Exception) { call.respond(HttpStatusCode.BadRequest, "Requête invalide") }
        }

        get("/groups/popular") {
            val currentUserId = call.request.queryParameters["userId"]
            val groups = GroupService.getPopularGroups(currentUserId)
            call.respond(HttpStatusCode.OK, groups)
        }

        get("/groups/my") {
            val userId = call.request.queryParameters["userId"]
            if (userId.isNullOrBlank()) { call.respond(HttpStatusCode.BadRequest, "userId manquant"); return@get }
            val groups = GroupService.getMyGroups(userId)
            call.respond(HttpStatusCode.OK, groups)
        }

        post("/groups/notifications") {
            val request = call.receive<NotificationToggleRequest>()
            val success = GroupService.toggleNotification(request.groupId, request.userId, request.shouldNotify)
            if (success) call.respond(HttpStatusCode.OK) else call.respond(HttpStatusCode.NotFound)
        }

        post("/groups/join") {
            val request = call.receive<JoinGroupRequest>()
            val resultStatus = GroupService.joinGroup(request.groupId, request.userId)

            if (resultStatus == "NOT_FOUND") {
                call.respond(HttpStatusCode.NotFound, "Groupe introuvable")
            } else {
                // On renvoie le statut pour que le téléphone sache quoi afficher
                call.respond(HttpStatusCode.OK, mapOf("status" to resultStatus))
            }
        }
    }
}