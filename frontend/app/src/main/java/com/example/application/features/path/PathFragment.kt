package com.example.application.features.path

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.application.R
import com.example.application.databinding.FragmentPathBinding // Vérifie le nom de ton XML
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class PathFragment : Fragment(R.layout.fragment_path) { // Assure-toi d'avoir fragment_path.xml

    private var _binding: FragmentPathBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPathBinding.bind(view)

        val userId = Firebase.auth.currentUser?.uid ?: ""

        lifecycleScope.launch {
            try {
                // 1. Récupérer "Mes itinéraires" [cite: 287]
                val myPaths = RetrofitInstance.api.getPathList(userId, "MINE")
                binding.rvMyItineraries.adapter = ItineraryAdapter(myPaths)

                // 2. Récupérer "Enregistrés" [cite: 291]
                val savedPaths = RetrofitInstance.api.getPathList(userId, "SAVED")
                binding.rvSaved.adapter = ItineraryAdapter(savedPaths)

            } catch (e: Exception) {
                // Gérer l'erreur
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}