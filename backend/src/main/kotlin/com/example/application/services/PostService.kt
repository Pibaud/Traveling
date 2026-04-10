package com.example.application.services

import com.example.application.DatabaseFactory.dbQuery
import com.example.application.Posts
import com.example.application.PostTags
import com.example.application.Tags
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select

object PostService {

    suspend fun createNewPost(
        description: String,
        placeId: String,
        isPublic: Boolean,
        tags: List<String>,
        imageUrls: List<String>,
        authorId: String = "anonymous" // Tu récupéreras l'ID Firebase plus tard
    ): Boolean = dbQuery {
        try {
            // 1. Insertion du Post
            val insertedPostId = Posts.insert {
                it[Posts.description] = description
                it[Posts.placeId] = placeId
                it[Posts.isPublic] = isPublic
                it[Posts.imageUrls] = imageUrls.joinToString(",") // On joint les URLs par une virgule
                it[Posts.authorId] = authorId
            } get Posts.id

            // 2. Gestion des Tags
            tags.forEach { tagName ->
                val cleanTagName = tagName.lowercase().trim()

                // On cherche si le tag existe déjà
                val tagId = Tags.select { Tags.name eq cleanTagName }
                    .map { it[Tags.id] }
                    .singleOrNull() ?: (Tags.insert { it[Tags.name] = cleanTagName } get Tags.id)

                // On crée le lien dans la table de jointure
                PostTags.insert {
                    it[PostTags.postId] = insertedPostId
                    it[PostTags.tagId] = tagId // Ici, PostTags.tagId est la colonne, tagId est ta variable
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}