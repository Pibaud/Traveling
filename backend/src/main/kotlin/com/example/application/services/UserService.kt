package com.example.application.services

import com.example.application.DatabaseFactory.dbQuery
import com.example.application.Users
import com.example.application.models.Itineraries
import com.example.application.ItineraryLikes
import com.example.application.UserFollows
import com.example.application.models.UserProfileResponse
import com.example.application.models.UserSearchResponse
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.lowerCase
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

    suspend fun searchUsers(query: String, limit: Int = 5): List<UserSearchResponse> = dbQuery {
        Users.select { Users.username.lowerCase() like "%${query.lowercase()}%" }
            .limit(limit)
            .map { row ->
                UserSearchResponse(
                    uid = row[Users.firebaseId],
                    username = row[Users.username] ?: "Inconnu",
                    avatarUrl = row.getOrNull(Users.avatarUrl)
                )
            }
    }

    suspend fun getUserProfile(targetUid: String, currentUid: String?): UserProfileResponse? = dbQuery {
        val userRow = Users.select { Users.firebaseId eq targetUid }.singleOrNull() ?: return@dbQuery null

        val username = userRow[Users.username]
        val bio = userRow.getOrNull(Users.bio)
        val avatarUrl = userRow.getOrNull(Users.avatarUrl)
        val preferences = userRow.getOrNull(Users.preferences)

        val createdCount = Itineraries.select { Itineraries.authorId eq targetUid }.count().toInt()
        val likedCount = ItineraryLikes.select { ItineraryLikes.userId eq targetUid }.count().toInt()

        val totalLikesReceived = Itineraries.innerJoin(ItineraryLikes)
            .select { Itineraries.authorId eq targetUid }
            .count().toInt()

        // NOUVEAU : Calcul des followers
        val followerCount = UserFollows.select { UserFollows.followedId eq targetUid }.count().toInt()

        // NOUVEAU : Savoir si l'utilisateur actuel est abonné
        val isFollowing = if (currentUid != null) {
            UserFollows.select { (UserFollows.followerId eq currentUid) and (UserFollows.followedId eq targetUid) }.count() > 0
        } else false

        UserProfileResponse(
            username = username.toString(), bio = bio, avatarUrl = avatarUrl,
            createdCount = createdCount, likedCount = likedCount,
            totalLikesReceived = totalLikesReceived,
            followerCount = followerCount,
            isFollowing = isFollowing,
            preferredCategories = preferences
        )
    }

    suspend fun updateProfile(uid: String, bio: String?, avatarUrl: String?, preferences: String?): Boolean = dbQuery {
        try {
            val updatedRows = Users.update({ Users.firebaseId eq uid }) {
                if (bio != null) it[Users.bio] = bio
                if (avatarUrl != null) it[Users.avatarUrl] = avatarUrl
                if (preferences != null) it[Users.preferences] = preferences
            }
            updatedRows > 0
        } catch (e: Exception) { false }
    }

    suspend fun toggleFollow(followerId: String, followedId: String): Boolean = dbQuery {
        if (followerId == followedId) return@dbQuery false
        val exists = UserFollows.select { (UserFollows.followerId eq followerId) and (UserFollows.followedId eq followedId) }.singleOrNull() != null
        if (exists) {
            UserFollows.deleteWhere { (UserFollows.followerId eq followerId) and (UserFollows.followedId eq followedId) }
            false // Désabonné
        } else {
            UserFollows.insert {
                it[UserFollows.followerId] = followerId
                it[UserFollows.followedId] = followedId
            }
            true // Abonné
        }
    }

    suspend fun getFollowerTokens(authorId: String): List<String> = dbQuery {
        // 1. Trouver les ID de tous ceux qui suivent cet auteur
        val followerIds = UserFollows
            .select { UserFollows.followedId eq authorId }
            .map { it[UserFollows.followerId] }

        if (followerIds.isEmpty()) return@dbQuery emptyList()

        // 2. Récupérer les tokens FCM de ces utilisateurs (en ignorant ceux qui sont nuls)
        Users.select { (Users.firebaseId inList followerIds) and Users.fcmToken.isNotNull() }
            .mapNotNull { it[Users.fcmToken] }
    }
}