import com.example.application.model.AnalysisResult
import com.example.application.model.CreateGroupRequest
import com.example.application.model.GeneratePathRequest
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
import com.example.application.model.ItineraryResponse
import com.example.application.model.ReportRequest
import com.example.application.model.SavePathRequest
import com.example.application.model.ShareItineraryRequest
import com.example.application.model.UpdateProfileRequest
import com.example.application.model.UserProfileResponse
import com.example.application.model.UserSearchResponse
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Streaming

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

    @GET("share/places/{id}/posts")
    suspend fun getPlacePosts(
        @Path("id") placeId: String,
        @Query("userId") userId: String? = null
    ): List<Post>

    @GET("share/places/category/{category}")
    suspend fun getPlacesByCategory(
        @Path("category") category: String,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): List<Place>

    @POST("share/publish")
    suspend fun publishPost(
        @Body request: CreatePostRequest // <-- Remplacement total du Multipart
    ): Response<Unit>

    @GET("share/feed") // Remplace par ta vraie route de feed si elle est différente
    suspend fun getFeed(
        @Query("userId") userId: String,
        @Query("tab") tab: String,
        @Query("focusPostId") focusPostId: String? = null // 👈 Le nouveau paramètre
    ): List<Post>

    @POST("share/like")
    suspend fun toggleLike(@Body request: LikeRequest): LikeResponse

    @GET("share/posts/author/{uid}")
    suspend fun getPostsByAuthor(
        @Path("uid") uid: String,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): List<Post>

    @POST("users/sync")
    suspend fun syncUser(@Body request: UserSyncRequest): Response<Unit>

    @GET("users/search")
    suspend fun searchUsers(@Query("q") query: String): List<UserSearchResponse>

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

    @GET("share/groups/{groupId}/posts")
    suspend fun getGroupPosts(
        @Path("groupId") groupId: String
    ): List<Post>

    @GET("share/groups/{groupId}/members")
    suspend fun getGroupMembers(
        @Path("groupId") groupId: String
    ): List<GroupMemberResponse>

    @GET("path/list")
    suspend fun getPathList(
        @Query("userId") userId: String,
        @Query("category") category: String
    ): List<ItineraryResponse>

    @POST("path/generate")
    suspend fun generatePath(@Body request: GeneratePathRequest): Response<List<ItineraryResponse>>

    @POST("path/save") // Assure-toi que c'est bien la route que tu as mise dans ton backend Ktor
    suspend fun savePath(@Body request: SavePathRequest): Response<Unit>

    @POST("path/like")
    suspend fun toggleLike(
        @Query("userId") userId: String,
        @Query("itineraryId") itineraryId: Int
    ): Map<String, Boolean>

    @DELETE("path/{id}")
    suspend fun deletePath(
        @Path("id") id: Int,
        @Query("userId") userId: String // On passe l'ID de l'utilisateur pour la sécurité
    ): Response<Unit>

    // NOUVELLE ROUTE POUR LE PDF (Passage en POST pour envoyer la grosse image)
    @Streaming
    @POST("path/export/{id}")
    suspend fun downloadItineraryPdf(
        @Path("id") id: Int,
        @Body base64MapImage: String? // Le Body attendra une simple String
    ): Response<okhttp3.ResponseBody>

    @GET("/users/{uid}/profile")
    suspend fun getUserProfile(@Path("uid") uid: String, @Query("currentUserId") currentUserId: String): Response<UserProfileResponse>

    @POST("/users/{uid}/follow/{targetUid}")
    suspend fun toggleFollow(@Path("uid") uid: String, @Path("targetUid") targetUid: String): Response<Map<String, Boolean>>

    @PUT("/users/{uid}/profile")
    suspend fun updateProfile(
        @Path("uid") uid: String,
        @Body request: UpdateProfileRequest
    ): Response<Map<String, String>>

    @POST("/users/{uid}/fcm-token")
    suspend fun updateFcmToken(
        @Path("uid") uid: String,
        @Body request: Map<String, String>
    ): Response<Unit>

    // Partager un itinéraire dans un groupe
    @POST("/share/groups/share-itinerary")
    suspend fun shareItineraryToGroup(
        @Body request: ShareItineraryRequest
    ): retrofit2.Response<Unit>

    // Récupérer les itinéraires d'un groupe
    @GET("/share/groups/{groupId}/itineraries")
    suspend fun getGroupItineraries(
        @Path("groupId") groupId: String,
        @Query("userId") userId: String
    ): retrofit2.Response<List<ItineraryResponse>>

    @POST("share/places/like")
    suspend fun togglePlaceLike(@Body request: LikeRequest): Response<Map<String, Boolean>>

    @GET("share/places/{id}/like-status")
    suspend fun getPlaceLikeStatus(
        @Path("id") placeId: String,
        @Query("userId") userId: String
    ): Response<Map<String, Boolean>>

    @GET("share/places/liked")
    suspend fun getLikedPlaces(
        @Query("userId") userId: String
    ): Response<List<Place>>

    // Dans TravelingApiService
    @POST("users/{uid}/reports")
    suspend fun submitReport(
        @Path("uid") uid: String,
        @Body request: ReportRequest
    ): Response<Unit>
    @POST("share/analyze-image")
    suspend fun analyzeImage(
        @Body request: Map<String, String>
    ): Response<AnalysisResult>

    @GET("share/posts/{id}/similar")
    suspend fun getSimilarPosts(
        @Path("id") postId: String,
        @Query("userId") userId: String? = null
    ): List<Post>

    @GET("share/posts/date-range")
    suspend fun getPostsByDateRange(
        @Query("start") startMillis: Long,
        @Query("end") endMillis: Long,
        @Query("userId") userId: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): List<Post>
}


