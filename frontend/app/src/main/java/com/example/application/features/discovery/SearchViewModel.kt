import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.application.model.Place
import com.example.application.model.Post
import com.example.application.model.RetrofitInstance
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel(private val apiService: TravelingApiService) : ViewModel() {

    private val _places = MutableStateFlow<List<Place>>(emptyList())
    val places: StateFlow<List<Place>> = _places

    fun fetchPlaces(minLat: Double, minLng: Double, maxLat: Double, maxLng: Double) {
        viewModelScope.launch {
            try {
                val result = apiService.searchPlaces(minLat, minLng, maxLat, maxLng)
                _places.value = result
            } catch (e: Exception) {
                e.printStackTrace() // À remplacer par une gestion d'erreur UI plus tard
            }
        }
    }

    private val _placePosts = MutableStateFlow<List<Post>>(emptyList())
    val placePosts: StateFlow<List<Post>> = _placePosts

    fun fetchPostsForPlace(placeId: String) {
        val userId = Firebase.auth.currentUser?.uid // Permet de savoir si on a liké les posts

        viewModelScope.launch {
            try {
                _placePosts.value = emptyList() // On vide la grille pendant le chargement
                val result = apiService.getPlacePosts(placeId, userId)
                _placePosts.value = result
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val _categoryPlaces = MutableStateFlow<List<Place>>(emptyList())
    val categoryPlaces: StateFlow<List<Place>> = _categoryPlaces

    private var currentCategory: String? = null
    private var currentOffset = 0
    private val pageSize = 20
    private var isPaginating = false

    fun fetchPlacesByCategory(category: String) {
        if (category != currentCategory) {
            currentCategory = category
            currentOffset = 0
            _categoryPlaces.value = emptyList()
            Log.d("PAGINATION", "🔄 Nouvelle catégorie '$category' → reset offset")
        }

        if (isPaginating) {
            Log.d("PAGINATION", "⏳ Déjà en chargement, appel ignoré")
            return
        }

        viewModelScope.launch {
            isPaginating = true
            Log.d("PAGINATION", "📤 Requête envoyée → category=$category, offset=$currentOffset, limit=$pageSize")
            try {
                val result = apiService.getPlacesByCategory(
                    category = category,
                    limit = pageSize,
                    offset = currentOffset
                )
                Log.d("PAGINATION", "📥 Réponse reçue → ${result.size} lieux | IDs: ${result.map { it.id }}")
                _categoryPlaces.value = _categoryPlaces.value + result
                currentOffset += result.size
                Log.d("PAGINATION", "✅ Nouvel offset = $currentOffset | Total en mémoire = ${_categoryPlaces.value.size}")
            } catch (e: Exception) {
                Log.e("PAGINATION", "❌ Erreur réseau : ${e.message}")
            } finally {
                isPaginating = false
            }
        }
    }

    private val _authorPosts = MutableStateFlow<List<Post>>(emptyList())
    val authorPosts: StateFlow<List<Post>> = _authorPosts

    private var currentAuthorId: String? = null
    private var authorOffset = 0

    fun fetchPostsByAuthor(authorId: String) {
        if (authorId != currentAuthorId) {
            currentAuthorId = authorId
            authorOffset = 0
            _authorPosts.value = emptyList()
        }

        if (isPaginating) return

        viewModelScope.launch {
            isPaginating = true
            try {
                val result = apiService.getPostsByAuthor(authorId, pageSize, authorOffset)
                _authorPosts.value = _authorPosts.value + result
                authorOffset += result.size
            } catch (e: Exception) {
                Log.e("PAGINATION", "Erreur auteur: ${e.message}")
            } finally {
                isPaginating = false
            }
        }
    }

    fun resetAuthorFilter() {
        currentAuthorId = null
        authorOffset = 0
        _authorPosts.value = emptyList()
    }

    private val _searchResults = MutableStateFlow<List<Post>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    fun loadSimilarPosts(postId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val currentUserId = Firebase.auth.currentUser?.uid
                // Appel à l'API Ktor
                val posts = RetrofitInstance.api.getSimilarPosts(postId, currentUserId)
                _searchResults.value = posts
            } catch (e: Exception) {
                // Gérer l'erreur (Toast, etc.)
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private val _datePosts = MutableStateFlow<List<Post>>(emptyList())
    val datePosts: StateFlow<List<Post>> = _datePosts.asStateFlow()

    fun fetchPostsByDateRange(startMillis: Long, endMillis: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val currentUserId = Firebase.auth.currentUser?.uid
                val posts = RetrofitInstance.api.getPostsByDateRange(startMillis, endMillis, currentUserId)
                _datePosts.value = posts
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private val _nearbyPosts = MutableStateFlow<List<Post>>(emptyList())
    val nearbyPosts: StateFlow<List<Post>> = _nearbyPosts.asStateFlow()

    fun fetchNearbyPosts(lat: Double, lng: Double, radiusKm: Double) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val currentUserId = Firebase.auth.currentUser?.uid
                val posts = apiService.getPostsNearby(lat, lng, radiusKm, currentUserId) // Remplace apiService par le nom de ta variable retrofit
                _nearbyPosts.value = posts
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun resetNearbyFilter() {
        _nearbyPosts.value = emptyList()
    }

    fun resetDateFilter() {
        _datePosts.value = emptyList()
    }
}