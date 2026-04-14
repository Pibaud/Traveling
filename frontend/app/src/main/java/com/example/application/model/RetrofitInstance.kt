import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit

object RetrofitInstance {
    // REMPLACE PAR TON IP (10.0.2.2 pour l'émulateur ou l'IP de ton PC en Wi-Fi)
    private const val BASE_URL = "http://0.0.0.0:8081/"

    private val json = Json {
        ignoreUnknownKeys = true // Pratique si le back envoie plus d'infos que prévu
    }

    val api: TravelingApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TravelingApiService::class.java)
    }
}