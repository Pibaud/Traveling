package com.example.application.services

import com.example.application.DatabaseFactory
import com.example.application.models.Place        // Vérifie bien ton package model
import com.example.application.models.PlaceCategory
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.ResultSet

object PlaceService {

    suspend fun searchByBoundingBox(minLat: Double, minLng: Double, maxLat: Double, maxLng: Double): List<Place> {
        return DatabaseFactory.dbQuery {
            // VRAIE requête PostGIS pour chercher dans une zone (Bounding Box)
            // L'opérateur && vérifie si la localisation intersecte l'enveloppe créée.
            // 4326 est le système de coordonnées standard GPS (WGS 84).
            val sql = """
                SELECT id, name, category, ST_Y(location::geometry) as lat, ST_X(location::geometry) as lng, price, duration, effort 
                FROM places 
                WHERE location && ST_MakeEnvelope(?, ?, ?, ?, 4326)
                LIMIT 100
            """.trimIndent()

            val results = mutableListOf<Place>()

            // L'ordre PostGIS : minLng, minLat, maxLng, maxLat
            org.jetbrains.exposed.sql.transactions.TransactionManager.current().exec(
                sql,
                args = listOf(
                    org.jetbrains.exposed.sql.DoubleColumnType() to minLng,
                    org.jetbrains.exposed.sql.DoubleColumnType() to minLat,
                    org.jetbrains.exposed.sql.DoubleColumnType() to maxLng,
                    org.jetbrains.exposed.sql.DoubleColumnType() to maxLat
                )
            ) { rs ->
                while (rs.next()) {
                    results.add(Place(
                        id = rs.getString("id"),
                        name = rs.getString("name"),
                        latitude = rs.getDouble("lat"),
                        longitude = rs.getDouble("lng"),
                        category = try { PlaceCategory.valueOf(rs.getString("category").uppercase()) } catch (e: Exception) { PlaceCategory.CULTURE },
                        price = rs.getInt("price"),
                        duration = rs.getInt("duration"),
                        effort = rs.getInt("effort")
                    ))
                }
            }
            results
        }
    }

    // Ajoute cette fonction sous searchByBoundingBox
    suspend fun searchByName(query: String, limit: Int = 10): List<Place> {
        return DatabaseFactory.dbQuery {
            val sql = """
                SELECT id, name, category, ST_Y(location::geometry) as lat, ST_X(location::geometry) as lng, price, duration, effort 
                FROM places 
                WHERE name ILIKE ? 
                LIMIT ?
            """.trimIndent()

            val results = mutableListOf<Place>()

            org.jetbrains.exposed.sql.transactions.TransactionManager.current().exec(
                sql,
                args = listOf(
                    org.jetbrains.exposed.sql.VarCharColumnType() to "%$query%", // Le % permet de chercher n'importe où dans le nom
                    org.jetbrains.exposed.sql.IntegerColumnType() to limit
                )
            ) { rs ->
                while (rs.next()) {
                    results.add(Place(
                        id = rs.getString("id"),
                        name = rs.getString("name"),
                        latitude = rs.getDouble("lat"),
                        longitude = rs.getDouble("lng"),
                        category = try { PlaceCategory.valueOf(rs.getString("category").uppercase()) } catch (e: Exception) { PlaceCategory.CULTURE },
                        price = rs.getInt("price"),
                        duration = rs.getInt("duration"),
                        effort = rs.getInt("effort")
                    ))
                }
            }
            results
        }
    }

    suspend fun getPlacesByCategory(category: String, limit: Int = 20, offset: Int = 0): List<Place> {
        return DatabaseFactory.dbQuery {
            val sql = """
                SELECT id, name, category, ST_Y(location::geometry) as lat, ST_X(location::geometry) as lng, price, duration, effort 
                FROM places 
                WHERE category ILIKE ? 
                ORDER BY id ASC
                LIMIT ? OFFSET ?
            """.trimIndent()

            val results = mutableListOf<Place>()
            org.jetbrains.exposed.sql.transactions.TransactionManager.current().exec(
                sql,
                args = listOf(
                    org.jetbrains.exposed.sql.VarCharColumnType() to category,
                    org.jetbrains.exposed.sql.IntegerColumnType() to limit,
                    org.jetbrains.exposed.sql.IntegerColumnType() to offset // 👈 Ajout
                )
            ) { rs ->
                while (rs.next()) {
                    results.add(Place(
                        id = rs.getString("id"),
                        name = rs.getString("name"),
                        latitude = rs.getDouble("lat"),
                        longitude = rs.getDouble("lng"),
                        category = try { PlaceCategory.valueOf(rs.getString("category").uppercase()) } catch (e: Exception) { PlaceCategory.CULTURE },
                        price = rs.getInt("price"),
                        duration = rs.getInt("duration"),
                        effort = rs.getInt("effort")
                    ))
                }
            }
            results
        }
    }

    suspend fun toggleLike(placeIdStr: String, userIdStr: String): Boolean = DatabaseFactory.dbQuery {
        val existingLike = com.example.application.PlaceLikes.select {
            (com.example.application.PlaceLikes.placeId eq placeIdStr) and (com.example.application.PlaceLikes.userId eq userIdStr)
        }.singleOrNull()

        if (existingLike != null) {
            com.example.application.PlaceLikes.deleteWhere {
                (com.example.application.PlaceLikes.placeId eq placeIdStr) and (com.example.application.PlaceLikes.userId eq userIdStr)
            }
            false // C'est un-liké
        } else {
            com.example.application.PlaceLikes.insert {
                it[placeId] = placeIdStr
                it[userId] = userIdStr
            }
            true // C'est liké
        }
    }

    suspend fun getLikedPlaces(userIdStr: String): List<Place> = DatabaseFactory.dbQuery {
        val sql = """
            SELECT p.id, p.name, p.category, ST_Y(p.location::geometry) as lat, ST_X(p.location::geometry) as lng, p.price, p.duration, p.effort 
            FROM places p
            JOIN place_likes pl ON p.id = pl.place_id
            WHERE pl.user_id = ?
        """.trimIndent()

        val results = mutableListOf<Place>()
        org.jetbrains.exposed.sql.transactions.TransactionManager.current().exec(
            sql, args = listOf(org.jetbrains.exposed.sql.VarCharColumnType() to userIdStr)
        ) { rs ->
            while (rs.next()) {
                results.add(Place(
                    id = rs.getString("id"),
                    name = rs.getString("name"),
                    latitude = rs.getDouble("lat"),
                    longitude = rs.getDouble("lng"),
                    category = try { PlaceCategory.valueOf(rs.getString("category").uppercase()) } catch (e: Exception) { PlaceCategory.CULTURE },
                    price = rs.getInt("price"),
                    duration = rs.getInt("duration"),
                    effort = rs.getInt("effort")
                ))
            }
        }
        results
    }

    suspend fun isPlaceLiked(placeIdStr: String, userIdStr: String): Boolean = DatabaseFactory.dbQuery {
        com.example.application.PlaceLikes.select {
            (com.example.application.PlaceLikes.placeId eq placeIdStr) and (com.example.application.PlaceLikes.userId eq userIdStr)
        }.count() > 0
    }
}