package com.example.application.routes

import com.example.application.model.UserSyncRequest
import com.example.application.services.UserService
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.server.request.receive

fun Route.userRoutes() {
    post("/users/sync") {
        val request = call.receive<UserSyncRequest>() // Ktor désérialise le JSON automatiquement

        val success = UserService.syncUser(request.uid, request.email)

        if (success) {
            call.respond(HttpStatusCode.OK)
        } else {
            call.respond(HttpStatusCode.InternalServerError)
        }
    }
}