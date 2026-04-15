import com.example.application.model.LikeRequest
import com.example.application.model.LikeResponse
import com.example.application.model.Place
import com.example.application.model.UserSyncRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import com.example.application.model.Post
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

    @GET("share/feed")
    suspend fun getFeed(@Query("userId") userId: String): List<Post>

    @POST("share/like")
    suspend fun toggleLike(@Body request: LikeRequest): LikeResponse

    @POST("users/sync")
    suspend fun syncUser(@Body request: UserSyncRequest): Response<Unit>
}