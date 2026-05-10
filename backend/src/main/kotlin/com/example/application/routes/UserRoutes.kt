package com.example.application.routes

import com.example.application.models.UpdateProfileRequest
import com.example.application.models.UserSyncRequest
import com.example.application.services.UserService
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.server.request.receive

fun Route.userRoutes() {
    post("/users/sync") {
        val request = call.receive<UserSyncRequest>()
        val success = UserService.syncUser(request.uid, request.email)
        if (success) {
            call.respond(HttpStatusCode.OK)
        } else {
            call.respond(HttpStatusCode.InternalServerError)
        }
    }

    // 👇 NOUVELLE ROUTE POUR LE PROFIL 👇
    get("/users/{uid}/profile") {
        val uid = call.parameters["uid"] ?: return@get call.respond(HttpStatusCode.BadRequest, "UID manquant")

        val profile = UserService.getUserProfile(uid)
        if (profile != null) {
            call.respond(HttpStatusCode.OK, profile)
        } else {
            call.respond(HttpStatusCode.NotFound, "Utilisateur introuvable")
        }
    }

    put("/users/{uid}/profile") {
        val uid = call.parameters["uid"] ?: return@put call.respond(HttpStatusCode.BadRequest, "UID manquant")

        try {
            val request = call.receive<UpdateProfileRequest>()
            val success = UserService.updateProfile(uid, request.bio, request.avatarUrl)

            if (success) {
                call.respond(HttpStatusCode.OK, mapOf("status" to "success"))
            } else {
                call.respond(HttpStatusCode.InternalServerError, "Erreur lors de la mise à jour")
            }
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, "Format de requête invalide")
        }
    }
}