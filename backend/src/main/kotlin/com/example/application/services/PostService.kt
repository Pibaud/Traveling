package com.example.application.services

import com.example.application.DatabaseFactory.dbQuery
import com.example.application.GroupPosts
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
import com.example.application.Groups
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.update

object PostService {

    suspend fun createNewPost(
        description: String,
        placeId: String,
        isPublic: Boolean,
        tags: List<String>,
        imageUrls: List<String>,
        authorId: String = "anonymous",
        groupIds: List<String> = emptyList(),
        embedding: List<Float>? = null
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

            if (embedding != null && embedding.isNotEmpty()) {
                val vectorString = embedding.joinToString(prefix = "[", postfix = "]", separator = ",")
                org.jetbrains.exposed.sql.transactions.TransactionManager.current().exec(
                    "UPDATE posts SET embedding = '$vectorString'::vector WHERE id = '${insertedPostId}'"
                )
            }

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
            // 3. NOUVEAU : Lier le post aux groupes sélectionnés
            groupIds.forEach { groupIdStr ->
                val groupUuid = java.util.UUID.fromString(groupIdStr)

                // On insère la liaison dans la table Many-to-Many
                GroupPosts.insert {
                    it[groupId] = groupUuid
                    it[postId] = insertedPostId
                    it[sharedAt] = System.currentTimeMillis()
                }

                // --- NOUVEAU : On incrémente le compteur nb_posts du groupe ---
                Groups.update({ Groups.id eq groupUuid }) {
                    with(SqlExpressionBuilder) {
                        it.update(nbPosts, nbPosts + 1)
                    }
                }
            }
            try {
                // 1. Récupérer le nom de l'auteur et du lieu pour faire un beau message
                val authorProfile = UserService.getUserProfile(authorId, null)
                val authorName = authorProfile?.username ?: "Un voyageur"

                // On récupère le nom du lieu avec une requête rapide
                var placeName = "une destination"
                org.jetbrains.exposed.sql.transactions.TransactionManager.current().exec(
                    "SELECT name FROM places WHERE id = '$placeId'"
                ) { rs -> if (rs.next()) placeName = rs.getString("name") }

                // 2. On récupère nos tokens fusionnés !
                val tokens = UserService.getTokensForNewPost(authorId, placeId, groupIds)

                // 3. On envoie
                NotificationService.notifyNewPostPublished(authorName, placeName, tokens)

            } catch (e: Exception) {
                println("Erreur lors de l'envoi des notifications de post : ${e.message}")
            }

            return@dbQuery true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // 1. On ajoute currentUserId en paramètre
    suspend fun getFeed(currentUserId: String?, isGroupsOnly: Boolean = false, focusPostId: String?): List<Post> = dbQuery {
        val baseSql = """
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
        """.trimIndent()

        val sql: String
        val args: List<Pair<org.jetbrains.exposed.sql.IColumnType, Any>>

        if (isGroupsOnly && currentUserId != null) {
            // AJOUT D'UN ESPACE AVANT LE WHERE
            sql = baseSql + " WHERE EXISTS (" + """
            SELECT 1 FROM group_posts gp
            JOIN group_members gm ON gp.group_id = gm.group_id
            WHERE gp.post_id = p.id AND gm.user_id = ? AND gm.status = 'ACCEPTED'
        )
        ORDER BY p.created_at DESC
        LIMIT 20
    """.trimIndent()

            args = listOf(
                org.jetbrains.exposed.sql.VarCharColumnType() to currentUserId,
                org.jetbrains.exposed.sql.VarCharColumnType() to currentUserId
            )
        } else {
            // AJOUT D'UN ESPACE AVANT LE WHERE
            sql = baseSql + " WHERE p.is_public = true " + """
        ORDER BY p.created_at DESC
        LIMIT 20
    """.trimIndent()

            args = listOf(
                org.jetbrains.exposed.sql.VarCharColumnType() to (currentUserId ?: "") // Pour "is_liked_by_me"
            )
        }

        val results = mutableListOf<Post>()

        org.jetbrains.exposed.sql.transactions.TransactionManager.current().exec(sql, args = args) { rs ->
            while (rs.next()) {
                val imageUrlsStr = rs.getString("image_urls") ?: ""
                val tagsStr = rs.getString("tags_list") ?: ""
                val isLikedByMe = rs.getBoolean("is_liked_by_me")

                val place = Place(
                    id = rs.getString("place_id") ?: "",
                    name = rs.getString("place_name") ?: "Lieu inconnu",
                    latitude = rs.getDouble("place_lat"),
                    longitude = rs.getDouble("place_lng"),
                    category = try {
                        PlaceCategory.valueOf(rs.getString("place_category")?.uppercase() ?: "CULTURE")
                    } catch (e: Exception) { PlaceCategory.CULTURE }
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
                        isLikedByMe = isLikedByMe
                    )
                )
            }
        }

        if (focusPostId != null) {
            // On cherche le post dans la liste
            val focusedPost = results.find { it.id == focusPostId }

            if (focusedPost != null) {
                // On le retire de sa position actuelle...
                results.remove(focusedPost)
                // ...et on le remet en tout premier ! (Index 0)
                results.add(0, focusedPost)
            } else {
                // Bonus Pro : si le post est trop vieux et n'était pas dans 
                // la première requête SQL, tu fais une requête spéciale pour aller 
                // le chercher en base et tu l'ajoutes à l'index 0.
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

    suspend fun getPostsForPlace(placeIdStr: String, currentUserId: String? = null): List<Post> = dbQuery {
        val sql = """
            SELECT 
                p.id as post_id, 
                p.author_id, 
                p.description, 
                p.image_urls, 
                (SELECT COUNT(*) FROM post_likes WHERE post_id = p.id) as likes_count,
                EXISTS(SELECT 1 FROM post_likes WHERE post_id = p.id AND user_id = ?) as is_liked_by_me,
                EXTRACT(EPOCH FROM p.created_at) * 1000 as timestamp,
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
            WHERE p.place_id = ? AND p.is_public = true
            ORDER BY p.created_at DESC
        """.trimIndent()

        val args = listOf(
            org.jetbrains.exposed.sql.VarCharColumnType() to (currentUserId ?: ""),
            org.jetbrains.exposed.sql.VarCharColumnType() to placeIdStr
        )

        val results = mutableListOf<Post>()
        org.jetbrains.exposed.sql.transactions.TransactionManager.current().exec(sql, args = args) { rs ->
            while (rs.next()) {
                val imageUrlsStr = rs.getString("image_urls") ?: ""
                val tagsStr = rs.getString("tags_list") ?: ""

                // On recrée l'objet Place rapidement
                val place = Place(
                    id = rs.getString("place_id") ?: "",
                    name = rs.getString("place_name") ?: "",
                    latitude = rs.getDouble("place_lat"),
                    longitude = rs.getDouble("place_lng"),
                    category = try { PlaceCategory.valueOf(rs.getString("place_category")?.uppercase() ?: "CULTURE") } catch (e: Exception) { PlaceCategory.CULTURE }
                )

                results.add(
                    Post(
                        id = rs.getString("post_id"),
                        authorId = rs.getString("author_id") ?: "",
                        authorName = rs.getString("author_name") ?: "Anonyme",
                        authorAvatarUrl = "",
                        description = rs.getString("description") ?: "",
                        imageUrls = if (imageUrlsStr.isNotBlank()) imageUrlsStr.split(",") else emptyList(),
                        likesCount = rs.getInt("likes_count"),
                        commentsCount = 0,
                        tags = if (tagsStr.isNotBlank()) tagsStr.split(",") else emptyList(),
                        timestamp = rs.getLong("timestamp"),
                        place = place,
                        isLikedByMe = rs.getBoolean("is_liked_by_me")
                    )
                )
            }
        }
        results
    }

    suspend fun getPostsForGroup(groupIdStr: String, currentUserId: String? = null): List<Post> = dbQuery {
        val baseSql = """
        SELECT 
            p.id as post_id, 
            p.author_id, 
            p.description, 
            p.image_urls, 
            (SELECT COUNT(*) FROM post_likes WHERE post_id = p.id) as likes_count,
            EXISTS(SELECT 1 FROM post_likes WHERE post_id = p.id AND user_id = ?) as is_liked_by_me,
            CAST(EXTRACT(EPOCH FROM p.created_at) * 1000 AS BIGINT) as timestamp,
            u.username as author_name,
            u.avatar_url as author_avatar,
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
        JOIN group_posts gp ON p.id = gp.post_id
        LEFT JOIN users u ON p.author_id = u.firebase_id
        LEFT JOIN places pl ON p.place_id = pl.id
        WHERE gp.group_id = ?
        ORDER BY p.created_at DESC
    """.trimIndent()

        val args = listOf(
            org.jetbrains.exposed.sql.VarCharColumnType() to (currentUserId ?: ""),
            org.jetbrains.exposed.sql.UUIDColumnType() to java.util.UUID.fromString(groupIdStr)
        )

        val results = mutableListOf<Post>()
        org.jetbrains.exposed.sql.transactions.TransactionManager.current().exec(baseSql, args = args) { rs ->
            while (rs.next()) {
                val imageUrlsStr = rs.getString("image_urls") ?: ""
                val tagsStr = rs.getString("tags_list") ?: ""

                val place = Place(
                    id = rs.getString("place_id") ?: "",
                    name = rs.getString("place_name") ?: "Lieu inconnu",
                    latitude = rs.getDouble("place_lat"),
                    longitude = rs.getDouble("place_lng"),
                    category = try { PlaceCategory.valueOf(rs.getString("place_category")?.uppercase() ?: "CULTURE") } catch (e: Exception) { PlaceCategory.CULTURE }
                )

                results.add(
                    Post(
                        id = rs.getString("post_id"),
                        authorId = rs.getString("author_id") ?: "",
                        authorName = rs.getString("author_name") ?: "Utilisateur inconnu",
                        authorAvatarUrl = rs.getString("author_avatar") ?: "",
                        description = rs.getString("description") ?: "",
                        imageUrls = if (imageUrlsStr.isNotBlank()) imageUrlsStr.split(",") else emptyList(),
                        likesCount = rs.getInt("likes_count"),
                        commentsCount = 0,
                        tags = if (tagsStr.isNotBlank()) tagsStr.split(",") else emptyList(),
                        timestamp = rs.getLong("timestamp"),
                        place = place,
                        isLikedByMe = rs.getBoolean("is_liked_by_me")
                    )
                )
            }
        }
        results
    }

    suspend fun getPostsByAuthor(authorIdStr: String, limit: Int = 20, offset: Int = 0, currentUserId: String? = null): List<Post> = dbQuery {
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
            WHERE p.author_id = ? AND p.is_public = true
            ORDER BY p.created_at DESC
            LIMIT ? OFFSET ?
        """.trimIndent()

        val args = listOf(
            org.jetbrains.exposed.sql.VarCharColumnType() to (currentUserId ?: ""),
            org.jetbrains.exposed.sql.VarCharColumnType() to authorIdStr,
            org.jetbrains.exposed.sql.IntegerColumnType() to limit,
            org.jetbrains.exposed.sql.IntegerColumnType() to offset
        )

        val results = mutableListOf<Post>()
        org.jetbrains.exposed.sql.transactions.TransactionManager.current().exec(sql, args = args) { rs ->
            while (rs.next()) {
                val imageUrlsStr = rs.getString("image_urls") ?: ""
                val tagsStr = rs.getString("tags_list") ?: ""

                val place = Place(
                    id = rs.getString("place_id") ?: "",
                    name = rs.getString("place_name") ?: "",
                    latitude = rs.getDouble("place_lat"),
                    longitude = rs.getDouble("place_lng"),
                    category = try { PlaceCategory.valueOf(rs.getString("place_category")?.uppercase() ?: "CULTURE") } catch (e: Exception) { PlaceCategory.CULTURE }
                )

                results.add(
                    Post(
                        id = rs.getString("post_id"),
                        authorId = rs.getString("author_id") ?: "",
                        authorName = rs.getString("author_name") ?: "Anonyme",
                        authorAvatarUrl = "",
                        description = rs.getString("description") ?: "",
                        imageUrls = if (imageUrlsStr.isNotBlank()) imageUrlsStr.split(",") else emptyList(),
                        likesCount = rs.getInt("likes_count"),
                        commentsCount = 0,
                        tags = if (tagsStr.isNotBlank()) tagsStr.split(",") else emptyList(),
                        timestamp = rs.getLong("timestamp"),
                        place = place,
                        isLikedByMe = rs.getBoolean("is_liked_by_me")
                    )
                )
            }
        }
        results
    }

    suspend fun getSimilarPosts(postIdStr: String, currentUserId: String? = null): List<Post> = dbQuery {
        val postUuid = java.util.UUID.fromString(postIdStr)

        // La requête magique de similarité cosinus (<=>)
        val sql = """
            SELECT 
                p.id as post_id, 
                p.author_id, 
                p.description, 
                p.image_urls, 
                -- 👇 ON CALCULE LA DISTANCE EXACTE (0 = clone, 1 = opposé) 👇
                (p.embedding <=> (SELECT embedding FROM posts WHERE id = ?)) as distance,
                (SELECT COUNT(*) FROM post_likes WHERE post_id = p.id) as likes_count,
                EXISTS(SELECT 1 FROM post_likes WHERE post_id = p.id AND user_id = ?) as is_liked_by_me,
                CAST(EXTRACT(EPOCH FROM p.created_at) * 1000 AS BIGINT) as timestamp,
                u.username as author_name,
                u.avatar_url as author_avatar,
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
            WHERE p.id != ? 
              AND p.embedding IS NOT NULL 
              AND p.is_public = true
              -- 👇 LE FAMEUX SEUIL DE TOLÉRANCE (0.4 est un bon début pour Gemini) 👇
              AND (p.embedding <=> (SELECT embedding FROM posts WHERE id = ?)) < 0.5
            ORDER BY distance ASC
            LIMIT 15
        """.trimIndent()

        // ⚠️ Attention, nous avons maintenant 4 points d'interrogation (?) dans le SQL,
        // il faut donc passer 4 arguments dans l'ordre exact :
        val args = listOf(
            org.jetbrains.exposed.sql.UUIDColumnType() to postUuid,                 // 1. Pour calculer la distance
            org.jetbrains.exposed.sql.VarCharColumnType() to (currentUserId ?: ""), // 2. Pour le is_liked
            org.jetbrains.exposed.sql.UUIDColumnType() to postUuid,                 // 3. Pour exclure le post actuel (id != ?)
            org.jetbrains.exposed.sql.UUIDColumnType() to postUuid                  // 4. Pour le seuil (< 0.4)
        )

        val results = mutableListOf<Post>()

        org.jetbrains.exposed.sql.transactions.TransactionManager.current().exec(sql, args = args) { rs ->
            while (rs.next()) {
                val imageUrlsStr = rs.getString("image_urls") ?: ""
                val tagsStr = rs.getString("tags_list") ?: ""

                val place = Place(
                    id = rs.getString("place_id") ?: "",
                    name = rs.getString("place_name") ?: "Lieu inconnu",
                    latitude = rs.getDouble("place_lat"),
                    longitude = rs.getDouble("place_lng"),
                    category = try {
                        PlaceCategory.valueOf(rs.getString("place_category")?.uppercase() ?: "CULTURE")
                    } catch (e: Exception) { PlaceCategory.CULTURE }
                )

                results.add(
                    Post(
                        id = rs.getString("post_id"),
                        authorId = rs.getString("author_id") ?: "",
                        authorName = rs.getString("author_name") ?: "Utilisateur inconnu",
                        authorAvatarUrl = rs.getString("author_avatar") ?: "",
                        description = rs.getString("description") ?: "",
                        imageUrls = if (imageUrlsStr.isNotBlank()) imageUrlsStr.split(",") else emptyList(),
                        likesCount = rs.getInt("likes_count"),
                        commentsCount = 0, // A ajuster si tu as une table commentaires
                        tags = if (tagsStr.isNotBlank()) tagsStr.split(",") else emptyList(),
                        timestamp = rs.getLong("timestamp"),
                        place = place,
                        isLikedByMe = rs.getBoolean("is_liked_by_me")
                    )
                )

                println("Post ${rs.getString("post_id")} - Distance: ${rs.getDouble("distance")}")
            }
        }
        results
    }

    suspend fun getPostsByDateRange(
        startMillis: Long,
        endMillis: Long,
        limit: Int = 20,
        offset: Int = 0,
        currentUserId: String? = null
    ): List<Post> = dbQuery {
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
              AND p.created_at >= to_timestamp(? / 1000.0) 
              AND p.created_at <= to_timestamp(? / 1000.0)
            ORDER BY p.created_at DESC
            LIMIT ? OFFSET ?
        """.trimIndent()

        val args = listOf(
            org.jetbrains.exposed.sql.VarCharColumnType() to (currentUserId ?: ""),
            org.jetbrains.exposed.sql.LongColumnType() to startMillis,
            // On ajoute 23h59m59s (86399999 ms) à la date de fin pour inclure toute la journée !
            org.jetbrains.exposed.sql.LongColumnType() to (endMillis + 86399999L),
            org.jetbrains.exposed.sql.IntegerColumnType() to limit,
            org.jetbrains.exposed.sql.IntegerColumnType() to offset
        )

        val results = mutableListOf<Post>()
        org.jetbrains.exposed.sql.transactions.TransactionManager.current().exec(sql, args = args) { rs ->
            while (rs.next()) {
                // ... (C'est exactement la même boucle while que dans tes autres fonctions, copie-colle la création du Place et du Post ici)
                val imageUrlsStr = rs.getString("image_urls") ?: ""
                val tagsStr = rs.getString("tags_list") ?: ""

                val place = Place(
                    id = rs.getString("place_id") ?: "",
                    name = rs.getString("place_name") ?: "",
                    latitude = rs.getDouble("place_lat"),
                    longitude = rs.getDouble("place_lng"),
                    category = try { PlaceCategory.valueOf(rs.getString("place_category")?.uppercase() ?: "CULTURE") } catch (e: Exception) { PlaceCategory.CULTURE }
                )

                results.add(
                    Post(
                        id = rs.getString("post_id"),
                        authorId = rs.getString("author_id") ?: "",
                        authorName = rs.getString("author_name") ?: "Anonyme",
                        authorAvatarUrl = "",
                        description = rs.getString("description") ?: "",
                        imageUrls = if (imageUrlsStr.isNotBlank()) imageUrlsStr.split(",") else emptyList(),
                        likesCount = rs.getInt("likes_count"),
                        commentsCount = 0,
                        tags = if (tagsStr.isNotBlank()) tagsStr.split(",") else emptyList(),
                        timestamp = rs.getLong("timestamp"),
                        place = place,
                        isLikedByMe = rs.getBoolean("is_liked_by_me")
                    )
                )
            }
        }
        results
    }

    suspend fun getPostsNearby(
        latitude: Double,
        longitude: Double,
        radiusKm: Double,
        limit: Int = 20,
        offset: Int = 0,
        currentUserId: String? = null
    ): List<Post> = dbQuery {
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
              -- 👇 Requête spatiale PostGIS (Rayon converti en mètres)
              AND ST_DWithin(pl.location::geography, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)
            ORDER BY p.created_at DESC
            LIMIT ? OFFSET ?
        """.trimIndent()

        // En PostGIS, ST_MakePoint prend (Longitude, Latitude) dans cet ordre !
        val args = listOf(
            org.jetbrains.exposed.sql.VarCharColumnType() to (currentUserId ?: ""),
            org.jetbrains.exposed.sql.DoubleColumnType() to longitude,
            org.jetbrains.exposed.sql.DoubleColumnType() to latitude,
            org.jetbrains.exposed.sql.DoubleColumnType() to (radiusKm * 1000.0), // km -> mètres
            org.jetbrains.exposed.sql.IntegerColumnType() to limit,
            org.jetbrains.exposed.sql.IntegerColumnType() to offset
        )

        val results = mutableListOf<Post>()
        org.jetbrains.exposed.sql.transactions.TransactionManager.current().exec(sql, args = args) { rs ->
            while (rs.next()) {
                val imageUrlsStr = rs.getString("image_urls") ?: ""
                val tagsStr = rs.getString("tags_list") ?: ""

                val place = Place(
                    id = rs.getString("place_id") ?: "",
                    name = rs.getString("place_name") ?: "",
                    latitude = rs.getDouble("place_lat"),
                    longitude = rs.getDouble("place_lng"),
                    category = try { PlaceCategory.valueOf(rs.getString("place_category")?.uppercase() ?: "CULTURE") } catch (e: Exception) { PlaceCategory.CULTURE }
                )

                results.add(
                    Post(
                        id = rs.getString("post_id"),
                        authorId = rs.getString("author_id") ?: "",
                        authorName = rs.getString("author_name") ?: "Anonyme",
                        authorAvatarUrl = "",
                        description = rs.getString("description") ?: "",
                        imageUrls = if (imageUrlsStr.isNotBlank()) imageUrlsStr.split(",") else emptyList(),
                        likesCount = rs.getInt("likes_count"),
                        commentsCount = 0,
                        tags = if (tagsStr.isNotBlank()) tagsStr.split(",") else emptyList(),
                        timestamp = rs.getLong("timestamp"),
                        place = place,
                        isLikedByMe = rs.getBoolean("is_liked_by_me")
                    )
                )
            }
        }
        results
    }
}