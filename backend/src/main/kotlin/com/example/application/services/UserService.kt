package com.example.application.services

import com.example.application.DatabaseFactory.dbQuery
import com.example.application.Users
import com.example.application.models.Itineraries
import com.example.application.ItineraryLikes
import com.example.application.models.UserProfileResponse
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update

object UserService {
    suspend fun syncUser(uid: String, email: String) = dbQuery {
        try {
            Users.insertIgnore {
                it[Users.firebaseId] = uid
                it[Users.email] = email
                it[Users.username] = email.substringBefore("@")
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun getUserProfile(uid: String): UserProfileResponse? = dbQuery {
        // 1. Récupération des infos basiques de l'utilisateur
        val userRow = Users.select { Users.firebaseId eq uid }.singleOrNull() ?: return@dbQuery null

        val username = userRow[Users.username].toString()
        // Si tu n'as pas encore ajouté bio et avatarUrl dans ton objet Users,
        // n'oublie pas de le faire : val bio = text("bio").nullable()
        val bio = userRow.getOrNull(Users.bio)
        val avatarUrl = userRow.getOrNull(Users.avatarUrl)

        // 2. Calcul des statistiques
        val createdCount = Itineraries.select { Itineraries.authorId eq uid }.count().toInt()
        val likedCount = ItineraryLikes.select { ItineraryLikes.userId eq uid }.count().toInt()

        // Total des likes reçus sur SES itinéraires
        val totalLikesReceived = Itineraries.innerJoin(ItineraryLikes)
            .select { Itineraries.authorId eq uid }
            .count().toInt()

        // Cumul des heures de tous ses itinéraires créés
        var totalHours = 0
        Itineraries.select { Itineraries.authorId eq uid }.forEach {
            totalHours += it[Itineraries.totalDuration] ?: 0
        }

        UserProfileResponse(
            username = username,
            bio = bio,
            avatarUrl = avatarUrl,
            createdCount = createdCount,
            likedCount = likedCount,
            totalLikesReceived = totalLikesReceived,
            totalHours = totalHours
        )
    }

    suspend fun updateProfile(uid: String, bio: String?, avatarUrl: String?): Boolean = dbQuery {
        try {
            val updatedRows = Users.update({ Users.firebaseId eq uid }) {
                // On met à jour seulement si la valeur n'est pas nulle
                if (bio != null) {
                    it[Users.bio] = bio
                }
                if (avatarUrl != null) {
                    it[Users.avatarUrl] = avatarUrl
                }
            }
            updatedRows > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}