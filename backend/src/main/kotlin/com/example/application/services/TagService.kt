package com.example.application.services

import com.example.application.DatabaseFactory

object TagService {
    suspend fun searchTags(query: String): List<String> = DatabaseFactory.dbQuery {
        val sql = "SELECT name FROM tags WHERE name LIKE ? LIMIT 5"
        val results = mutableListOf<String>()

        org.jetbrains.exposed.sql.transactions.TransactionManager.current().exec(
            sql,
            args = listOf(org.jetbrains.exposed.sql.VarCharColumnType() to "${query.lowercase()}%")
        ) { rs ->
            while (rs.next()) { results.add(rs.getString("name")) }
        }
        results
    }
}