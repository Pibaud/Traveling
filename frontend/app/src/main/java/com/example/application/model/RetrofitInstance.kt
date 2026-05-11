package com.example.application.model

import TravelingApiService
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import com.example.application.BuildConfig // 👈 L'import généré par Gradle

object RetrofitInstance {

    // On utilise directement la variable générée, elle contient déjà l'URL complète avec le port !
    private val BASE_URL = BuildConfig.API_BASE_URL

    private val json = Json {
        ignoreUnknownKeys = true
    }

    val api: TravelingApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TravelingApiService::class.java)
    }
}