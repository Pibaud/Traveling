package com.example.application.features.path

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.application.R
import com.example.application.databinding.FragmentPathBinding
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class PathFragment : Fragment(R.layout.fragment_path) {

    private var _binding: FragmentPathBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPathBinding.bind(view)

        val userId = Firebase.auth.currentUser?.uid ?: ""

        lifecycleScope.launch {
            try {
                // 1. Récupérer "Mes itinéraires"
                val myPaths = RetrofitInstance.api.getPathList(userId, category = "MINE")
                binding.rvMyItineraries.adapter = ItineraryAdapter(
                    items = myPaths,
                    onItemClick = { selectedItinerary ->
                        val detailsSheet = ItineraryDetailsBottomSheet(selectedItinerary)
                        detailsSheet.show(parentFragmentManager, "ItineraryDetails")
                    },
                    onLikeClick = { clickedItinerary ->
                        // Appel réseau pour Liker/Déliker
                        lifecycleScope.launch {
                            try {
                                // ⚠️ N'oublie pas d'ajouter cette fonction dans RetrofitInstance.api !
                                RetrofitInstance.api.toggleLike(userId, clickedItinerary.id!!)
                            } catch (e: Exception) {
                                // Gérer l'erreur silencieusement (le cœur s'est déjà rempli visuellement)
                            }
                        }
                    }
                )

                // 2. Récupérer les "Likés" (Anciennement "SAVED")
                val likedPaths = RetrofitInstance.api.getPathList(userId, category = "LIKED")
                binding.rvSaved.adapter = ItineraryAdapter(
                    items = likedPaths,
                    onItemClick = { selectedItinerary ->
                        val detailsSheet = ItineraryDetailsBottomSheet(selectedItinerary)
                        detailsSheet.show(parentFragmentManager, "ItineraryDetails")
                    },
                    onLikeClick = { clickedItinerary ->
                        // Appel réseau pour Liker/Déliker
                        lifecycleScope.launch {
                            try {
                                RetrofitInstance.api.toggleLike(userId, clickedItinerary.id!!)
                            } catch (e: Exception) {
                                // Gérer l'erreur
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                // Gérer l'erreur globale (ex: pas de connexion internet)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}