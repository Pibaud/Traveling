package com.example.application

import com.example.application.routes.pathRoutes
import com.example.application.routes.shareRoutes
import com.example.application.routes.userRoutes
import io.ktor.server.application.*
import io.ktor.server.response.respondText
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        // On appelle simplement nos fichiers séparés
        shareRoutes()
        pathRoutes()
        userRoutes()

        // Optionnel : une route de test à la racine
        get("/") {
            call.respondText("Backend Traveling opérationnel !")
        }
    }
}