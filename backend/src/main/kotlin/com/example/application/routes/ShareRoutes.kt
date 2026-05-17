package com.example.application.routes

import com.example.application.models.CreateGroupRequest
import com.example.application.models.CreatePostRequest
import com.example.application.models.LikeRequest
import com.example.application.models.JoinGroupRequest
import com.example.application.models.NotificationToggleRequest
import com.example.application.services.AIService
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

        get("/places/{id}/posts") {
            val placeId = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, "ID du lieu manquant")
            val currentUserId = call.request.queryParameters["userId"]

            try {
                val posts = PostService.getPostsForPlace(placeId, currentUserId)
                call.respond(HttpStatusCode.OK, posts)
            } catch (e: Exception) {
                application.log.error("Erreur récupération des posts du lieu", e)
                call.respond(HttpStatusCode.InternalServerError)
            }
        }

        get("/places/category/{category}") {
            val category = call.parameters["category"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

            application.log.info("📥 [PAGINATION] category=$category | limit=$limit | offset=$offset")

            val places = PlaceService.getPlacesByCategory(category, limit, offset)

            application.log.info("📤 [PAGINATION] Renvoi de ${places.size} lieux | IDs: ${places.map { it.id }}")

            call.respond(HttpStatusCode.OK, places)
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
                    authorId = request.authorId,
                    groupIds = request.groupIds,
                    embedding = request.embedding
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
                val currentUserId = call.request.queryParameters["userId"]
                // On récupère le paramètre "tab" (par défaut "public")
                val tab = call.request.queryParameters["tab"] ?: "public"

                val focusPostId = call.request.queryParameters["focusPostId"]

                // Si tab vaut "groups", on active le filtre
                val isGroupsOnly = (tab == "groups")

                val feed = PostService.getFeed(currentUserId, isGroupsOnly, focusPostId)
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

        get("/groups/{groupId}/posts") {
            val groupId = call.parameters["groupId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val userId = call.request.queryParameters["userId"] // Optionnel pour savoir s'il a liké

            val posts = PostService.getPostsForGroup(groupId, userId)
            call.respond(HttpStatusCode.OK, posts)
        }

        get("/groups/{groupId}/members") {
            val groupId = call.parameters["groupId"] ?: return@get call.respond(HttpStatusCode.BadRequest)

            val members = GroupService.getGroupMembers(groupId)
            call.respond(HttpStatusCode.OK, members)
        }

        // 👉 1. Route pour partager un itinéraire dans un groupe
        post("/groups/share-itinerary") {
            try {
                val request = call.receive<com.example.application.models.ShareItineraryRequest>()
                val success = com.example.application.services.PathService.shareItineraryToGroup(request.itineraryId, request.groupId)

                if (success) {
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respond(HttpStatusCode.InternalServerError, "Erreur lors du partage")
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Requête invalide")
            }
        }

        // 👉 2. Route pour récupérer les itinéraires d'un groupe
        get("/groups/{groupId}/itineraries") {
            val groupId = call.parameters["groupId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val userId = call.request.queryParameters["userId"] ?: ""

            // On utilise notre astuce du préfixe GROUP_ pour tout faire d'un coup !
            val itineraries = com.example.application.services.PathService.getItinerariesByCategory(userId, "GROUP_$groupId")
            call.respond(HttpStatusCode.OK, itineraries)
        }

        get("/posts/author/{uid}") {
            val uid = call.parameters["uid"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
            val posts = PostService.getPostsByAuthor(uid, limit, offset)
            call.respond(HttpStatusCode.OK,posts)
        }

        // 1. Liker / Disliker un lieu
        post("/places/like") {
            try {
                // On réutilise la structure de requête qu'on avait pour les posts
                val request = call.receive<LikeRequest>()
                val isLiked = PlaceService.toggleLike(request.postId, request.userId) // On utilise postId comme placeId pour aller plus vite
                call.respond(HttpStatusCode.OK, mapOf("liked" to isLiked))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Erreur de like")
            }
        }

        // 2. Vérifier si on a liké un lieu
        get("/places/{id}/like-status") {
            val placeId = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val userId = call.request.queryParameters["userId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val isLiked = PlaceService.isPlaceLiked(placeId, userId)
            call.respond(HttpStatusCode.OK, mapOf("liked" to isLiked))
        }

        // 3. Récupérer les lieux favoris d'un utilisateur
        get("/places/liked") {
            val userId = call.request.queryParameters["userId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val places = PlaceService.getLikedPlaces(userId)
            call.respond(HttpStatusCode.OK, places)
        }

        post("/analyze-image") {
            try {
                // On attend un simple JSON : { "imageUrl": "https://firebase..." }
                val request = call.receive<Map<String, String>>()
                val imageUrl = request["imageUrl"] ?: return@post call.respond(HttpStatusCode.BadRequest)

                val aiResult = AIService.analyzeAndEmbedImage(imageUrl)

                if (aiResult != null) {
                    call.respond(HttpStatusCode.OK, aiResult)
                } else {
                    call.respond(HttpStatusCode.InternalServerError, "Erreur lors de l'analyse IA")
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Format invalide")
            }
        }

        get("/posts/{id}/similar") {
            try {
                val postId = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, "ID manquant")
                val userId = call.request.queryParameters["userId"]

                val similarPosts = PostService.getSimilarPosts(postId, userId)
                call.respond(HttpStatusCode.OK, similarPosts)
            } catch (e: Exception) {
                application.log.error("Erreur lors de la recherche par similarité", e)
                call.respond(HttpStatusCode.InternalServerError, "Erreur serveur")
            }
        }

        get("/posts/date-range") {
            val startMillis = call.request.queryParameters["start"]?.toLongOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest, "Start date manquante")
            val endMillis = call.request.queryParameters["end"]?.toLongOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest, "End date manquante")
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
            val currentUserId = call.request.queryParameters["userId"]

            try {
                val posts = PostService.getPostsByDateRange(startMillis, endMillis, limit, offset, currentUserId)
                call.respond(HttpStatusCode.OK, posts)
            } catch (e: Exception) {
                application.log.error("Erreur filtre date", e)
                call.respond(HttpStatusCode.InternalServerError)
            }
        }

        get("/posts/nearby") {
            val lat = call.request.queryParameters["lat"]?.toDoubleOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest, "Latitude manquante")
            val lng = call.request.queryParameters["lng"]?.toDoubleOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest, "Longitude manquante")
            val radius = call.request.queryParameters["radius"]?.toDoubleOrNull() ?: 2.5
            val currentUserId = call.request.queryParameters["userId"]
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

            try {
                val posts = PostService.getPostsNearby(lat, lng, radius, limit, offset, currentUserId)
                call.respond(HttpStatusCode.OK, posts)
            } catch (e: Exception) {
                application.log.error("Erreur récupération posts à proximité", e)
                call.respond(HttpStatusCode.InternalServerError)
            }
        }
    }
}