package com.example.application.routes

import com.example.application.models.Post
import com.example.application.services.PlaceService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

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

        get("/feed") {
            call.respond(listOf<Post>())
        }

        // Publication d'une photo avec tags IA
        post("/publish") {
            val post = call.receive<Post>()
            call.respondText("Photo publiée avec succès")
        }
    }
}