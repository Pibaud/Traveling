package com.example.application

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.Transaction

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