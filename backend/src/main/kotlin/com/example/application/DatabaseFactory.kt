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

object Users : Table("users") {
    // La clé primaire logique est simplement l'ID de Firebase
    val firebaseId = varchar("firebase_id", 128)
    val email = varchar("email", 255)
    val username = varchar("username", 100).nullable()

    // CORRECTION : On utilise datetime au lieu de long
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }

    // CORRECTION : La clé primaire ne doit être que l'ID
    override val primaryKey = PrimaryKey(firebaseId)
}

object Groups : Table("groups") {
    val id = uuid("id").clientDefault { java.util.UUID.randomUUID() }
    val name = varchar("name", 50)
    val description = text("description")
    val urlGroupPhoto = text("url_group_photo")
    val nbMembers = integer("nb_members").default(1)
    val nbPosts = integer("nb_posts").default(0)
    val isPublic = bool("is_public").default(true)
    val tags = text("tags")
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }
    override val primaryKey = PrimaryKey(id)
}

object GroupMembers : Table("group_members") {
    val groupId = uuid("group_id").references(Groups.id, onDelete = ReferenceOption.CASCADE)
    val userId = varchar("user_id", 100)
    val role = varchar("role", 20).default("MEMBER")
    val status = varchar("status", 20).default("ACCEPTED")
    val shouldNotify = bool("should_notify").default(false)
    val joinedAt = datetime("joined_at").clientDefault { LocalDateTime.now() }
    override val primaryKey = PrimaryKey(groupId, userId)
}

// Table principale des itinéraires
object Itineraries : Table("itinerary") {
    val id = integer("id").autoIncrement()
    val name = text("name")
    val description = text("description").nullable()
    val hexColor = text("hex_color")
    val totalPrice = integer("total_price")
    val totalDuration = integer("total_duration")
    val avgEffort = double("avg_effort")
    val mealIncluded = bool("meal_included")
    val authorId = varchar("author_id", 128)

    override val primaryKey = PrimaryKey(id)
}

// Table des étapes (Lieux dans l'itinéraire)
object Steps : Table("step") {
    val placeId = varchar("place_id", 50)
    val itineraryId = integer("itinerary_id").references(Itineraries.id)
    val stepOrder = integer("step_order")

    override val primaryKey = PrimaryKey(placeId, itineraryId)
}

// Table des favoris
object ItineraryLikes : Table("itinerary_likes") {
    val userId = varchar("user_id", 128)
    val itineraryId = integer("itinerary_id").references(Itineraries.id)

    override val primaryKey = PrimaryKey(userId, itineraryId)
}

object GroupPosts : Table("group_posts") {
    val groupId = uuid("group_id").references(Groups.id, onDelete = ReferenceOption.CASCADE)
    val postId = uuid("post_id").references(Posts.id, onDelete = ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(groupId, postId)
}