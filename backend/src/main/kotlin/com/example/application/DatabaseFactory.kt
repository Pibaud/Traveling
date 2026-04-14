package com.example.application

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object DatabaseFactory {

    /**
     * Initialise la connexion à la base de données PostgreSQL.
     * À appeler dans Application.kt au démarrage.
     */
    fun init() {
        val config = HikariConfig().apply {
            driverClassName = "org.postgresql.Driver"
            // Remplace par tes vrais accès Google Console / DataConnect
            jdbcUrl = "jdbc:postgresql://34.155.14.145:5432/travelingdb"
            username = "postgres"
            password = "bQy49G8FnNQGCdh6"
            maximumPoolSize = 3
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }
        val dataSource = HikariDataSource(config)
        Database.connect(dataSource)
    }

    /**
     * Utilitaire pour exécuter des requêtes suspendues (non-bloquantes).
     * C'est ce que ton PlaceService utilise.
     */
    suspend fun <T> dbQuery(block: suspend Transaction.() -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}

object Posts : Table("posts") {
    val id = uuid("id").clientDefault { java.util.UUID.randomUUID() }
    val description = text("description")
    val placeId = varchar("place_id", 50)
    val isPublic = bool("is_public").default(true)
    val imageUrls = text("image_urls")
    val authorId = varchar("author_id", 100)
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }

    override val primaryKey = PrimaryKey(id)
}

// AJOUT DE LA TABLE TAGS
object Tags : Table("tags") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 50).uniqueIndex()

    override val primaryKey = PrimaryKey(id)
}

object PostTags : Table("post_tags") {
    val postId = uuid("post_id").references(Posts.id)
    val tagId = integer("tag_id").references(Tags.id)

    override val primaryKey = PrimaryKey(postId, tagId)
}

object PostLikes : Table("post_likes") {
    val userId = varchar("user_id", 100)
    // On précise explicitement le ON DELETE CASCADE pour Exposed
    val postId = uuid("post_id").references(Posts.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }

    override val primaryKey = PrimaryKey(userId, postId)
}