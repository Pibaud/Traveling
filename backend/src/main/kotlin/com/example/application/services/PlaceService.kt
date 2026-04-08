package com.example.application.services

import com.example.application.DatabaseFactory
import com.example.application.models.Place        // Vérifie bien ton package model
import com.example.application.models.PlaceCategory
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.ResultSet

object PlaceService {

    suspend fun searchByBoundingBox(minLat: Double, minLng: Double, maxLat: Double, maxLng: Double): List<Place> {
        return DatabaseFactory.dbQuery {
            val sql = """
                SELECT id, name, category, ST_Y(location::geometry) as lat, ST_X(location::geometry) as lng 
                FROM places 
                WHERE location && ST_MakeEnvelope(?, ?, ?, ?, 4326)
            """.trimIndent()

            val results = mutableListOf<Place>()

            // On utilise transaction { exec(...) } pour Exposed
            // L'ordre PostGIS : minLon, minLat, maxLon, maxLat
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
                        category = try {
                            PlaceCategory.valueOf(rs.getString("category").uppercase())
                        } catch (e: Exception) {
                            PlaceCategory.CULTURE
                        }
                    ))
                }
            }
            results
        }
    }
}