package com.example.application.services

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64
import kotlinx.serialization.json.*

object AIService {
    // ⚠️ Remplace par ta vraie clé d'API Google AI Studio
    private val GEMINI_API_KEY = System.getenv("GEMINI_API_KEY")
        ?: throw IllegalStateException("La variable d'environnement GEMINI_API_KEY est manquante")
    private val client = HttpClient.newHttpClient()

    data class AnalysisResult(val tags: List<String>, val embedding: List<Float>)

    suspend fun analyzeAndEmbedImage(imageUrl: String): AnalysisResult? {
        try {
            // 1. On télécharge l'image depuis Firebase
            println("-> Téléchargement de l'image...")
            val imageBytes = java.net.URI.create(imageUrl).toURL().readBytes()
            val base64Image = Base64.getEncoder().encodeToString(imageBytes)

            // 2. Appel à Gemini 1.5 Flash (Vision)
            val visionPrompt = "Analyse cette image. Donne moi exactement 5 tags pertinents (un mot chacun) séparés par des virgules, suivis du symbole '|', puis une description très détaillée de 3 phrases pour la recherche vectorielle."

            val visionJson = """
                {
                  "contents": [{
                    "parts": [
                      {"text": "$visionPrompt"},
                      {"inline_data": {"mime_type": "image/jpeg", "data": "$base64Image"}}
                    ]
                  }]
                }
            """.trimIndent()

            val visionRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$GEMINI_API_KEY"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(visionJson))
                .build()

            val visionResponse = client.send(visionRequest, HttpResponse.BodyHandlers.ofString())

            // 👇 LOG D'ERREUR VISION 👇
            if (visionResponse.statusCode() != 200) {
                println("❌ ERREUR API VISION (${visionResponse.statusCode()}) : ${visionResponse.body()}")
                return null
            }

            // Parsing
            val responseText = Json.parseToJsonElement(visionResponse.body())
                .jsonObject["candidates"]?.jsonArray?.get(0)
                ?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray?.get(0)
                ?.jsonObject?.get("text")?.jsonPrimitive?.content ?: return null

            val parts = responseText.split("|")
            val tags = parts.getOrNull(0)?.split(",")?.map { it.trim().lowercase() } ?: emptyList()
            val descriptionForVector = parts.getOrNull(1)?.trim() ?: responseText

            val safeDescription = descriptionForVector.replace("\"", "\\\"").replace("\n", " ")

            // 3. Appel au modèle d'Embedding
            val embedJson = """
                {
                  "model": "models/gemini-embedding-2",
                  "content": { "parts": [{"text": "$safeDescription"}] },
                  "outputDimensionality": 768
                }
            """.trimIndent()

            val embedRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-2:embedContent?key=$GEMINI_API_KEY"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(embedJson))
                .build()

            val embedResponse = client.send(embedRequest, HttpResponse.BodyHandlers.ofString())

            // 👇 LOG D'ERREUR EMBEDDING 👇
            if (embedResponse.statusCode() != 200) {
                println("❌ ERREUR API EMBEDDING (${embedResponse.statusCode()}) : ${embedResponse.body()}")
                return null
            }

            val embeddingArray = Json.parseToJsonElement(embedResponse.body())
                .jsonObject["embedding"]?.jsonObject?.get("values")?.jsonArray?.map { it.jsonPrimitive.float }

            if (embeddingArray != null) {
                return AnalysisResult(tags, embeddingArray)
            }
        } catch (e: Exception) {
            // 👇 LOG D'ERREUR CRITIQUE 👇
            println("❌ EXCEPTION CATCHÉE : ${e.message}")
            e.printStackTrace()
        }
        return null
    }
}