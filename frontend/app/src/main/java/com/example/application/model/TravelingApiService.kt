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
import com.example.application.model.SavePathRequest
import com.example.application.model.UpdateProfileRequest
import com.example.application.model.UserProfileResponse
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

}


