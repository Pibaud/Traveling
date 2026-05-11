package com.example.application.utils // Ou ton package

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.application.R
import com.example.application.model.RetrofitInstance
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TravelingFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val currentUser = Firebase.auth.currentUser
        if (currentUser != null) {
            sendTokenToServer(currentUser.uid, token)
        }
    }

    // 👇 LA NOUVELLE FONCTION EST ICI 👇
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        // On récupère le titre et le texte envoyés par le backend Ktor
        val title = message.notification?.title ?: "Nouvelle notification"
        val body = message.notification?.body ?: ""

        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        val channelId = "traveling_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Android 8+ (Oreo) exige la création d'un "Channel" (Canal)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Notifications Traveling",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Création du design de la pop-up de notification
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // 👈 Met une icône de ton app ici !
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true) // Ferme la notif quand on clique dessus

        // On affiche la notification avec un ID unique (ici l'heure actuelle)
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun sendTokenToServer(uid: String, token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                RetrofitInstance.api.updateFcmToken(uid, mapOf("fcmToken" to token))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}