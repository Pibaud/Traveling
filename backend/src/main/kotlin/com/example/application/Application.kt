package com.example.application

import io.ktor.server.application.*
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import java.io.FileInputStream

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    DatabaseFactory.init()

    // 👇 ON INITIALISE FIREBASE JUSTE ICI 👇
    configureFirebase()

    configureHTTP()
    configureMonitoring()
    configureFrameworks()
    configureSerialization()
    configureSecurity()
    configureRouting()
}

// Fonction dédiée à l'initialisation de Firebase
fun Application.configureFirebase() {
    try {
        // Le nom exact de ton fichier JSON tel qu'il est à la racine de ton projet
        val serviceAccount = FileInputStream("traveling-abb04-firebase-adminsdk-fbsvc-8a2a158bea.json")

        val options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.fromStream(serviceAccount))
            .build()

        // Sécurité : on vérifie que Firebase n'est pas déjà initialisé (utile si Ktor redémarre à chaud)
        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options)
            println("✅ Firebase Admin SDK initialisé avec succès ! 🚀")
        }
    } catch (e: Exception) {
        println("❌ Erreur lors de l'initialisation de Firebase: ${e.message}")
    }
}