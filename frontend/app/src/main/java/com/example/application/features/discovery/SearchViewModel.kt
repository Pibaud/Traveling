import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.application.model.Place
import com.example.application.model.Post
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
}