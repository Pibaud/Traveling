package com.example.application.features.path // Ajuste ton package

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.application.R
import com.example.application.model.Place
import com.example.application.features.discovery.PlacePostsAdapter
// Assure-toi d'importer ton instance Retrofit si elle n'est pas dans le même package
// import com.example.application.network.RetrofitInstance
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

        // Récupération des vues
        val ivCategoryIcon = view.findViewById<ImageView>(R.id.ivCategoryIcon)
        val tvName = view.findViewById<TextView>(R.id.tvSheetName)
        val tvCategory = view.findViewById<TextView>(R.id.tvSheetCategory)
        val btnDetails = view.findViewById<Button>(R.id.btnSheetDetails)
        val rvPosts = view.findViewById<RecyclerView>(R.id.rvPlacePosts)

        // 1. Textes
        tvName.text = place.name
        tvCategory.text = place.category.name.lowercase().replaceFirstChar { it.uppercase() }

        // 2. Icônes dynamiques (Basé exactement sur les drawables de ton SearchFragment !)
        val iconRes = when (place.category.name.uppercase()) {
            "CULTURE" -> R.drawable.round_culture_24
            "RESTAURATION" -> R.drawable.round_restaurant_24
            "LOISIRS" -> R.drawable.round_loisirs_24
            "DECOUVERTE" -> R.drawable.round_decouverte_24
            else -> R.drawable.round_decouverte_24 // Par défaut
        }
        ivCategoryIcon.setImageResource(iconRes)

        // 3. Configuration de la grille de photos
        placePostsAdapter = PlacePostsAdapter()
        rvPosts.layoutManager = GridLayoutManager(requireContext(), 3)
        rvPosts.adapter = placePostsAdapter

        // 4. Lancement de la requête réseau pour les photos
        loadPlacePosts()

        // 5. Action du bouton "Voir les détails"
        btnDetails.setOnClickListener {
            Toast.makeText(context, "Ouverture de la page de ${place.name}...", Toast.LENGTH_SHORT).show()
            // Plus tard : findNavController().navigate(R.id.action_vers_details_lieu)
        }
    }

    private fun loadPlacePosts() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // 👇 ON UTILISE TA ROUTE EXISTANTE 👇
                val posts = RetrofitInstance.api.getPlacePosts(placeId = place.id)

                // On remplit la petite galerie du bas directement
                placePostsAdapter.submitList(posts)

                // On cherche la toute première image disponible
                if (posts.isNotEmpty()) {
                    val firstImageUrl = posts.firstOrNull()?.imageUrls?.firstOrNull()

                    if (!firstImageUrl.isNullOrEmpty()) {
                        view?.findViewById<ImageView>(R.id.ivSheetPlaceImage)?.let { ivCover ->
                            Glide.with(this@PlaceDetailsBottomSheet)
                                .load(firstImageUrl)
                                .transition(DrawableTransitionOptions.withCrossFade())
                                .centerCrop()
                                .into(ivCover)
                        }
                    }
                }
            } catch (e: Exception) {
                // Échec réseau : on l'ignore silencieusement
                e.printStackTrace()
            }
        }
    }
}