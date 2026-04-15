package com.example.application.services

import com.example.application.DatabaseFactory.dbQuery
import com.example.application.Users
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.update

object UserService {
    suspend fun syncUser(uid: String, email: String) = dbQuery {
        try {
            // "insertIgnore" évite les erreurs si l'utilisateur existe déjà
            Users.insertIgnore {
                it[Users.firebaseId] = uid
                it[Users.email] = email
                it[Users.username] = email.substringBefore("@") // Username par défaut
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}