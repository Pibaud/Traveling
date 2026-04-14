package com.example.application.services

import com.example.application.DatabaseFactory.dbQuery
import com.example.application.Posts
import com.example.application.PostTags
import com.example.application.Tags
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import com.example.application.models.Post
import com.example.application.models.Place
import com.example.application.PostLikes
import com.example.application.models.PlaceCategory
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import java.util.UUID

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

    // 1. On ajoute currentUserId en paramètre
    suspend fun getFeed(currentUserId: String?): List<Post> = dbQuery {
        val sql = """
            SELECT 
                p.id as post_id, 
                p.author_id, 
                p.description, 
                p.image_urls, 
                (SELECT COUNT(*) FROM post_likes WHERE post_id = p.id) as likes_count,
                EXISTS(SELECT 1 FROM post_likes WHERE post_id = p.id AND user_id = ?) as is_liked_by_me,
                CAST(EXTRACT(EPOCH FROM p.created_at) * 1000 AS BIGINT) as timestamp,
                u.username as author_name,
                pl.id as place_id, 
                pl.name as place_name, 
                pl.category as place_category, 
                ST_Y(pl.location::geometry) as place_lat, 
                ST_X(pl.location::geometry) as place_lng,
                (
                    SELECT STRING_AGG(t.name, ',') 
                    FROM post_tags pt 
                    JOIN tags t ON pt.tag_id = t.id 
                    WHERE pt.post_id = p.id
                ) as tags_list
            FROM posts p
            LEFT JOIN users u ON p.author_id = u.firebase_id
            LEFT JOIN places pl ON p.place_id = pl.id
            WHERE p.is_public = true
            ORDER BY p.created_at DESC
            LIMIT 20
        """.trimIndent()

        val results = mutableListOf<Post>()

        // 2. On passe l'argument (currentUserId) à la requête pour remplacer le '?'
        org.jetbrains.exposed.sql.transactions.TransactionManager.current().exec(
            sql,
            args = listOf(org.jetbrains.exposed.sql.VarCharColumnType() to (currentUserId ?: ""))
        ) { rs ->
            while (rs.next()) {
                val imageUrlsStr = rs.getString("image_urls") ?: ""
                val tagsStr = rs.getString("tags_list") ?: ""

                // 3. On extrait le boolean de la base de données
                val isLikedByMe = rs.getBoolean("is_liked_by_me")

                val place = Place(
                    id = rs.getString("place_id") ?: "",
                    name = rs.getString("place_name") ?: "Lieu inconnu",
                    latitude = rs.getDouble("place_lat"),
                    longitude = rs.getDouble("place_lng"),
                    category = try {
                        PlaceCategory.valueOf(rs.getString("place_category")?.uppercase() ?: "CULTURE")
                    } catch (e: Exception) {
                        PlaceCategory.CULTURE
                    }
                )

                results.add(
                    Post(
                        id = rs.getString("post_id"),
                        authorId = rs.getString("author_id") ?: "",
                        authorName = rs.getString("author_name") ?: "Utilisateur inconnu",
                        authorAvatarUrl = "",
                        description = rs.getString("description") ?: "",
                        imageUrls = if (imageUrlsStr.isNotBlank()) imageUrlsStr.split(",") else emptyList(),
                        likesCount = rs.getInt("likes_count"),
                        commentsCount = 0,
                        tags = if (tagsStr.isNotBlank()) tagsStr.split(",") else emptyList(),
                        timestamp = rs.getLong("timestamp"),
                        place = place,
                        isLikedByMe = isLikedByMe // 4. On l'injecte dans l'objet Kotlin
                    )
                )
            }
        }
        results
    }

    suspend fun toggleLike(postIdStr: String, userIdStr: String): Boolean = dbQuery {
        val postUuid = try {
            UUID.fromString(postIdStr)
        } catch (e: Exception) {
            throw IllegalArgumentException("ID de post invalide")
        }

        // 1. On cherche si le like existe déjà
        val existingLike = PostLikes.select {
            (PostLikes.postId eq postUuid) and (PostLikes.userId eq userIdStr)
        }.singleOrNull()

        if (existingLike != null) {
            // 2. Il existe -> L'utilisateur "Un-like"
            PostLikes.deleteWhere {
                (PostLikes.postId eq postUuid) and (PostLikes.userId eq userIdStr)
            }
            false // On retourne false pour dire "Ce n'est plus liké"
        } else {
            // 3. Il n'existe pas -> L'utilisateur "Like"
            PostLikes.insert {
                it[postId] = postUuid
                it[userId] = userIdStr
            }
            true // On retourne true pour dire "C'est liké"
        }
    }
}