package com.example.application.services

import com.example.application.DatabaseFactory.dbQuery
import com.example.application.models.CommentResponse
import java.util.UUID

object CommentService {

    suspend fun getCommentsForPost(postIdStr: String): List<CommentResponse> = dbQuery {
        val sql = """
            SELECT 
                c.id, c.post_id, c.user_id, c.content,
                CAST(EXTRACT(EPOCH FROM c.created_at) * 1000 AS BIGINT) as timestamp,
                u.username, u.avatar_url
            FROM comments c
            JOIN users u ON c.user_id = u.firebase_id
            WHERE c.post_id = ?
            ORDER BY c.created_at ASC
        """.trimIndent()

        val args = listOf(
            org.jetbrains.exposed.sql.UUIDColumnType() to UUID.fromString(postIdStr)
        )

        val results = mutableListOf<CommentResponse>()
        org.jetbrains.exposed.sql.transactions.TransactionManager.current().exec(sql, args = args) { rs ->
            while (rs.next()) {
                results.add(
                    CommentResponse(
                        id = rs.getString("id"),
                        postId = rs.getString("post_id"),
                        authorId = rs.getString("user_id"),
                        authorName = rs.getString("username") ?: "Utilisateur",
                        authorAvatarUrl = rs.getString("avatar_url"),
                        content = rs.getString("content"),
                        timestamp = rs.getLong("timestamp")
                    )
                )
            }
        }
        results
    }

    suspend fun addComment(postIdStr: String, userIdStr: String, content: String): Boolean = dbQuery {
        val sql = """
            INSERT INTO comments (post_id, user_id, content) 
            VALUES (?, ?, ?)
        """.trimIndent()

        val args = listOf(
            org.jetbrains.exposed.sql.UUIDColumnType() to UUID.fromString(postIdStr),
            org.jetbrains.exposed.sql.VarCharColumnType() to userIdStr,
            org.jetbrains.exposed.sql.TextColumnType() to content
        )

        try {
            org.jetbrains.exposed.sql.transactions.TransactionManager.current().exec(sql, args = args)
            true
        } catch (e: Exception) {
            false
        }
    }
}