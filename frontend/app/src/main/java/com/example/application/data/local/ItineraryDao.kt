package com.example.application.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ItineraryDao {
    @Query("SELECT * FROM cached_itineraries WHERE category = :category ORDER BY timestamp DESC")
    suspend fun getCachedItineraries(category: String): List<CachedItinerary>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItineraries(itineraries: List<CachedItinerary>)

    @Query("DELETE FROM cached_itineraries WHERE category = :category")
    suspend fun clearCategory(category: String)
}