package com.example.application.features.path

import android.os.Bundle
import android.view.View
import android.widget.TextView
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

    // On prépare notre base de données locale et Gson
    private lateinit var db: AppDatabase
    private val gson = Gson()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPathBinding.bind(view)

        val userId = Firebase.auth.currentUser?.uid ?: ""

        // Initialisation de la BDD
        db = AppDatabase.getDatabase(requireContext())

        lifecycleScope.launch {
            // 1. Récupérer les données
            val myPaths = fetchOrLoadCache(userId, "MINE")
            val likedPaths = fetchOrLoadCache(userId, "LIKED")
            // 👇 NOUVEL APPEL POUR LE TOP 10 👇
            val popularPaths = fetchOrLoadCache(userId, "POPULAR")

            // 2. Assigner l'affichage intelligent (cache si vide)
            setupSection(
                titleView = binding.tvMyItinerariesTitle,
                recyclerView = binding.rvMyItineraries,
                data = myPaths,
                userId = userId
            )

            setupSection(
                titleView = binding.tvSavedTitle,
                recyclerView = binding.rvSaved,
                data = likedPaths,
                userId = userId
            )

            // 👇 ON AJOUTE LA NOUVELLE SECTION À L'INTERFACE 👇
            setupSection(
                titleView = binding.tvPopularTitle,
                recyclerView = binding.rvPopular,
                data = popularPaths,
                userId = userId
            )
        }
    }

    // --- FONCTION INTELLIGENTE : CACHE OU AFFICHE LA SECTION ---
    private fun setupSection(
        titleView: TextView,
        recyclerView: RecyclerView,
        data: List<ItineraryResponse>,
        userId: String
    ) {
        if (data.isEmpty()) {
            // Liste vide : on fait disparaître le titre et la liste
            titleView.visibility = View.GONE
            recyclerView.visibility = View.GONE
        } else {
            // Liste remplie : on affiche le titre, la liste, et on met l'adapteur
            titleView.visibility = View.VISIBLE
            recyclerView.visibility = View.VISIBLE

            recyclerView.adapter = ItineraryAdapter(
                items = data,
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