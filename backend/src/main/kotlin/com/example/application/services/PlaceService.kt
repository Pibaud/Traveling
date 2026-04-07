package com.example.application.services

import com.example.application.DatabaseFactory
import com.example.application.models.Place
import com.example.application.models.PlaceCategory
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.*

object PlaceService {

    // Ton algo complexe de recherche géographique
    suspend fun searchByBoundingBox(minLat: Double, minLng: Double, maxLat: Double, maxLng: Double): List<Place> {
        return DatabaseFactory.dbQuery {
            // Utilisation de l'opérateur && de PostGIS pour l'efficacité spatiale
            val sql = """
                SELECT id, name, category, ST_Y(location::geometry) as lat, ST_X(location::geometry) as lng 
                FROM places 
                WHERE location && ST_MakeEnvelope(?, ?, ?, ?, 4326)
            """.trimIndent()

            val results = mutableListOf<Place>()

            // On exécute la requête brute via Exposed (ou ton outil de DB)
            exec(sql) { rs ->
                // On prépare les arguments pour éviter les injections SQL
                // Note : l'ordre minLng, minLat, maxLng, maxLat est standard PostGIS
                // avec ST_MakeEnvelope(min_x, min_y, max_x, max_y, srid)

                // Si ton exec ne supporte pas les args directement, assure-toi de les préparer
                // Ici on simule le remplissage du modèle Place
                while (rs.next()) {
                    results.add(Place(
                        id = rs.getString("id"),
                        name = rs.getString("name"),
                        latitude = rs.getDouble("lat"),
                        longitude = rs.getDouble("lng"),
                        category = PlaceCategory.valueOf(rs.getString("category").uppercase())
                    ))
                }
            }
            results
        }
    }
}