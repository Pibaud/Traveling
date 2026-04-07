import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.application.model.Place
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
}