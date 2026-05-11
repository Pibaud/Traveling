package com.example.application.features.places // Ajuste ton package

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.application.R
import com.example.application.model.Place
// Assure-toi que PlacePostsAdapter est bien importé s'il est dans un autre package
import com.example.application.features.discovery.PlacePostsAdapter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class PlaceDetailsBottomSheet(
    private val place: Place
) : BottomSheetDialogFragment() {

    private lateinit var placePostsAdapter: PlacePostsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_place_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvName = view.findViewById<TextView>(R.id.tvSheetName)
        val tvCategory = view.findViewById<TextView>(R.id.tvSheetCategory)
        val btnDetails = view.findViewById<Button>(R.id.btnSheetDetails)
        val rvPosts = view.findViewById<RecyclerView>(R.id.rvPlacePosts)

        // 1. Remplissage des infos textuelles
        tvName.text = place.name
        val categoryText = place.category.name.lowercase().replaceFirstChar { it.uppercase() }
        tvCategory.text = categoryText

        // 2. Configuration de la liste de photos (identique au SearchFragment)
        placePostsAdapter = PlacePostsAdapter()
        rvPosts.layoutManager = GridLayoutManager(requireContext(), 3) // 3 photos par ligne
        rvPosts.adapter = placePostsAdapter

        // 3. Récupération des posts
        loadPlacePosts(place.id)

        // 4. Action du bouton
        btnDetails.setOnClickListener {
            Toast.makeText(context, "Ouverture de la page complète de ${place.name}...", Toast.LENGTH_SHORT).show()
            // Plus tard, tu mettras ici ta navigation : findNavController().navigate(...)
        }
    }

    private fun loadPlacePosts(placeId: String) {
        lifecycleScope.launch {
            try {
                // Adapte le nom de la fonction Retrofit ("getPostsForPlace") selon ton API réelle
                /* val response = RetrofitInstance.api.getPostsForPlace(placeId)
                if (response.isSuccessful && response.body() != null) {
                    placePostsAdapter.submitList(response.body()!!)
                }
                */
            } catch (e: Exception) {
                // Erreur silencieuse pour ne pas bloquer l'affichage du nom du lieu
            }
        }
    }
}