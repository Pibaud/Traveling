package com.example.application.services

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NotificationService {

    suspend fun notifyFollowersNewItinerary(authorName: String, itineraryName: String, tokens: List<String>) {
        if (tokens.isEmpty()) return

        // On bascule sur un thread IO pour ne pas bloquer le serveur Ktor pendant l'envoi
        withContext(Dispatchers.IO) {
            try {
                // Création de la notification visuelle
                val notification = Notification.builder()
                    .setTitle("Nouvel itinéraire de @$authorName ! 🗺️")
                    .setBody("Découvrez son nouveau parcours : $itineraryName")
                    .build()

                // MulticastMessage permet d'envoyer le MÊME message à une LISTE de tokens
                val message = MulticastMessage.builder()
                    .setNotification(notification)
                    .putData("type", "NEW_ITINERARY") // Donnée invisible pour l'app (utile plus tard pour la navigation)
                    .addAllTokens(tokens)
                    .build()

                // Envoi via les serveurs de Google
                val response = FirebaseMessaging.getInstance().sendMulticast(message)
                println("FCM: Notifications envoyées. Succès: ${response.successCount}, Échecs: ${response.failureCount}")

            } catch (e: Exception) {
                println("FCM Erreur: ${e.message}")
            }
        }
    }
}