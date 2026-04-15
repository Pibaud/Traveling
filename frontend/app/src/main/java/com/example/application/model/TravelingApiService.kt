import com.example.application.model.CreateGroupRequest
import com.example.application.model.Group
import com.example.application.model.JoinGroupRequest
import com.example.application.model.LikeRequest
import com.example.application.model.LikeResponse
import com.example.application.model.NotificationToggleRequest
import com.example.application.model.Place
import com.example.application.model.UserSyncRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import com.example.application.model.Post
import com.example.application.models.ItineraryResponse
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

    @POST("share/groups/create")
    suspend fun createGroup(@Body request: CreateGroupRequest): Response<Unit>

    @GET("share/groups/popular")
    suspend fun getPopularGroups(@Query("userId") userId: String?): List<Group>

    @GET("share/groups/my")
    suspend fun getMyGroups(@Query("userId") userId: String): List<Group>

    @POST("share/groups/notifications")
    suspend fun toggleGroupNotifications(@Body request: NotificationToggleRequest): Response<Unit>

    @POST("share/groups/join")
    suspend fun joinGroup(@Body request: JoinGroupRequest): Response<Map<String, String>>

    @GET("path/list")
    suspend fun getPathList(
        @Query("userId") userId: String,
        @Query("category") category: String
    ): List<ItineraryResponse>
}