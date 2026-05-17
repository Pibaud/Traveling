package com.example.application.services

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NotificationService {

    suspend fun notifyFollowersNewItinerary(authorName: String, itineraryName: String, tokens: List<String>) {
        if (tokens.isEmpty()) return

        // On bascule sur un thread IO pour ne pas bloquer le serveur Ktor
        withContext(Dispatchers.IO) {

            // Création de la notification visuelle
            val notification = Notification.builder()
                .setTitle("Nouvel itinéraire de @$authorName ! 🗺️")
                .setBody("Découvrez son nouveau parcours : $itineraryName")
                .build()

            var successCount = 0
            var failureCount = 0

            // 👇 LA PARADE : On boucle sur les tokens et on envoie individuellement 👇
            for (token in tokens) {
                try {
                    val message = Message.builder()
                        .setNotification(notification)
                        .putData("type", "NEW_ITINERARY")
                        .setToken(token) // On cible un seul token à la fois
                        .build()

                    // Envoi via la route classique (qui ne plante jamais en 404)
                    FirebaseMessaging.getInstance().send(message)
                    successCount++
                } catch (e: Exception) {
                    println("FCM Erreur pour le token $token : ${e.message}")
                    failureCount++
                }
            }

            println("FCM: Notifications terminées. Succès: $successCount, Échecs: $failureCount")
        }
    }

    suspend fun notifyNewPostPublished(
        authorName: String,
        placeName: String,
        tokens: List<String>
    ) {
        if (tokens.isEmpty()) return

        withContext(Dispatchers.IO) {
            val notification = Notification.builder()
                .setTitle("Nouveau post de @$authorName ! 📸")
                .setBody("Découvrez sa nouvelle photo prise à $placeName.")
                .build()

            var successCount = 0
            var failureCount = 0

            for (token in tokens) {
                try {
                    val message = Message.builder()
                        .setNotification(notification)
                        .putData("type", "NEW_POST")
                        .setToken(token)
                        .build()

                    FirebaseMessaging.getInstance().send(message)
                    successCount++
                } catch (e: Exception) {
                    println("FCM Erreur pour le token $token : ${e.message}")
                    failureCount++
                }
            }
            println("FCM Posts: Terminé. Succès: $successCount, Échecs: $failureCount")
        }
    }
}