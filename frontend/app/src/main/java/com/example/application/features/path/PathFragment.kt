package com.example.application.features.path

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.application.R
import com.example.application.databinding.FragmentPathBinding
import com.example.application.model.ItineraryResponse
import com.example.application.data.local.AppDatabase
import com.example.application.data.local.CachedItinerary
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch

class PathFragment : Fragment(R.layout.fragment_path) {

    private var _binding: FragmentPathBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: AppDatabase
    private val gson = Gson()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPathBinding.bind(view)

        val userId = Firebase.auth.currentUser?.uid ?: ""
        db = AppDatabase.getDatabase(requireContext())

        lifecycleScope.launch {
            // 1. Récupérer toutes les listes
            val myPaths = fetchOrLoadCache(userId, "MINE")
            val likedPaths = fetchOrLoadCache(userId, "LIKED")
            val popularPaths = fetchOrLoadCache(userId, "POPULAR")

            val categoriesList = mutableListOf<CategoryRow>()

            // 2. Sections de base
            if (myPaths.isNotEmpty()) {
                categoriesList.add(CategoryRow("Mes itinéraires", myPaths))
            }
            if (likedPaths.isNotEmpty()) {
                categoriesList.add(CategoryRow("Itinéraires likés", likedPaths))
            }
            if (popularPaths.isNotEmpty()) {
                categoriesList.add(CategoryRow("Les plus populaires", popularPaths))
            }

            // 👇 3. LECTURE DU CACHE ET CRÉATION DES CATÉGORIES DYNAMIQUES 👇
            val prefs = requireContext().getSharedPreferences("UserPrefs", android.content.Context.MODE_PRIVATE)
            val wantsCulture = prefs.getBoolean("pref_culture", false)
            val wantsDecouverte = prefs.getBoolean("pref_decouverte", false)
            val wantsLoisirs = prefs.getBoolean("pref_loisirs", false)

            if (wantsCulture) {
                // On cherche les itinéraires qui contiennent AU MOINS UNE étape "CULTURE"
                val culturePaths = popularPaths.filter { itin -> itin.steps.any { it.category.name == "CULTURE" } }
                if (culturePaths.isNotEmpty()) {
                    categoriesList.add(CategoryRow("Les meilleures sorties Culture", culturePaths))
                }
            }

            if (wantsDecouverte) {
                val decouvertePaths = popularPaths.filter { itin -> itin.steps.any { it.category.name == "DECOUVERTE" } }
                if (decouvertePaths.isNotEmpty()) {
                    categoriesList.add(CategoryRow("Les meilleures sorties Découverte", decouvertePaths))
                }
            }

            if (wantsLoisirs) {
                val loisirsPaths = popularPaths.filter { itin -> itin.steps.any { it.category.name == "LOISIRS" } }
                if (loisirsPaths.isNotEmpty()) {
                    categoriesList.add(CategoryRow("Les meilleures sorties Loisirs", loisirsPaths))
                }
            }

            // 4. On donne tout à notre super adapteur
            binding.rvMainCategories.adapter = CategoryAdapter(
                categories = categoriesList,
                onItemClick = { selectedItinerary ->
                    val detailsSheet = ItineraryDetailsBottomSheet(selectedItinerary)
                    detailsSheet.show(parentFragmentManager, "ItineraryDetails")
                },
                onLikeClick = { clickedItinerary ->
                    toggleLikeNetwork(userId, clickedItinerary)
                }
            )
        }
    }

    private suspend fun fetchOrLoadCache(userId: String, category: String): List<ItineraryResponse> {
        val dao = db.itineraryDao()
        return try {
            val networkData = RetrofitInstance.api.getPathList(userId, category)

            val cacheEntities = networkData.map { itinerary ->
                CachedItinerary(
                    id = itinerary.id ?: 0,
                    category = category,
                    jsonPayload = gson.toJson(itinerary)
                )
            }
            dao.clearCategory(category)
            dao.insertItineraries(cacheEntities)

            networkData
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Mode hors-ligne activé", Toast.LENGTH_SHORT).show()

            val cachedData = dao.getCachedItineraries(category)
            cachedData.map { entity ->
                gson.fromJson(entity.jsonPayload, object : TypeToken<ItineraryResponse>() {}.type)
            }
        }
    }

    private fun toggleLikeNetwork(userId: String, clickedItinerary: ItineraryResponse) {
        lifecycleScope.launch {
            try {
                RetrofitInstance.api.toggleLike(userId, clickedItinerary.id!!)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Impossible de modifier le like hors-ligne", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}