import com.example.application.model.Place
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.Response

interface TravelingApiService {
    @GET("share/places/searchbbox")
    suspend fun searchPlaces(
        @Query("minLat") minLat: Double,
        @Query("minLng") minLng: Double,
        @Query("maxLat") maxLat: Double,
        @Query("maxLng") maxLng: Double
    ): List<Place> // On utilise directement ta classe Place !

    // Ajoute cette route dans ton interface
    @GET("share/places/search")
    suspend fun searchPlacesByName(
        @Query("q") query: String
    ): List<Place>

    @POST("share/publish")
    suspend fun publishPost(
        @Body request: CreatePostRequest // <-- Remplacement total du Multipart
    ): Response<Unit>
}