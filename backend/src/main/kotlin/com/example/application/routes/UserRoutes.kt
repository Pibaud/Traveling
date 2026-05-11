package com.example.application.routes

import com.example.application.DatabaseFactory
import com.example.application.Users
import com.example.application.models.UpdateProfileRequest
import com.example.application.models.UserSyncRequest
import com.example.application.services.UserService
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.server.request.receive
import org.jetbrains.exposed.sql.update

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
        val targetUid = call.parameters["uid"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val currentUid = call.request.queryParameters["currentUserId"] // Optionnel
        val profile = UserService.getUserProfile(targetUid, currentUid)
        if (profile != null) call.respond(HttpStatusCode.OK, profile) else call.respond(HttpStatusCode.NotFound)
    }

    put("/users/{uid}/profile") {
        val uid = call.parameters["uid"] ?: return@put call.respond(HttpStatusCode.BadRequest, "UID manquant")

        try {
            val request = call.receive<UpdateProfileRequest>()
            val success = UserService.updateProfile(uid, request.bio, request.avatarUrl, request.preferences)

            if (success) {
                call.respond(HttpStatusCode.OK, mapOf("status" to "success"))
            } else {
                call.respond(HttpStatusCode.InternalServerError, "Erreur lors de la mise à jour")
            }
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, "Format de requête invalide")
        }
    }

    post("/users/{uid}/follow/{targetUid}") {
        val uid = call.parameters["uid"] ?: return@post call.respond(HttpStatusCode.BadRequest)
        val targetUid = call.parameters["targetUid"] ?: return@post call.respond(HttpStatusCode.BadRequest)
        val isNowFollowing = UserService.toggleFollow(uid, targetUid)
        call.respond(HttpStatusCode.OK, mapOf("isFollowing" to isNowFollowing))
    }

    post("/users/{uid}/fcm-token") {
        val uid = call.parameters["uid"] ?: return@post call.respond(HttpStatusCode.BadRequest)
        val request = call.receive<Map<String, String>>()
        val token = request["fcmToken"]

        val updated = DatabaseFactory.dbQuery {
            Users.update({ Users.firebaseId eq uid }) {
                it[Users.fcmToken] = token
            } > 0
        }

        if (updated) call.respond(HttpStatusCode.OK)
        else call.respond(HttpStatusCode.InternalServerError)
    }
}