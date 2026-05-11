package com.example.application.features.path

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
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
import com.example.application.model.RetrofitInstance
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

        val ivCategoryIcon = view.findViewById<ImageView>(R.id.ivCategoryIcon)
        val tvName = view.findViewById<TextView>(R.id.tvSheetName)
        val tvCategory = view.findViewById<TextView>(R.id.tvSheetCategory)
        val rvPosts = view.findViewById<RecyclerView>(R.id.rvPlacePosts)

        // Les 3 nouveaux boutons
        val btnViewItineraries = view.findViewById<View>(R.id.btnViewItineraries)
        val btnAddPost = view.findViewById<View>(R.id.btnAddPost)
        val btnGenerateWith = view.findViewById<View>(R.id.btnGenerateWith)

        // 1. Textes de base
        tvName.text = place.name
        tvCategory.text = place.category.name.lowercase().replaceFirstChar { it.uppercase() }

        // 2. Icône de catégorie
        val iconRes = when (place.category.name.uppercase()) {
            "CULTURE" -> R.drawable.round_culture_24
            "RESTAURATION" -> R.drawable.round_restaurant_24
            "LOISIRS" -> R.drawable.round_loisirs_24
            "DECOUVERTE" -> R.drawable.round_decouverte_24
            else -> R.drawable.round_decouverte_24
        }
        ivCategoryIcon.setImageResource(iconRes)

        // 3. Configuration de la grille de photos
        placePostsAdapter = PlacePostsAdapter()
        rvPosts.layoutManager = GridLayoutManager(requireContext(), 3)
        rvPosts.adapter = placePostsAdapter

        // 4. Chargement des données
        loadPlacePosts()

        // 5. Configuration des clics (Les fameux Toasts)
        btnViewItineraries.setOnClickListener {
            Toast.makeText(context, "Voir les itinéraires passant par ${place.name}", Toast.LENGTH_SHORT).show()
        }

        btnAddPost.setOnClickListener {
            Toast.makeText(context, "Ajouter une photo pour ${place.name}", Toast.LENGTH_SHORT).show()
        }

        btnGenerateWith.setOnClickListener {
            Toast.makeText(context, "Génération d'un itinéraire incluant ${place.name}...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadPlacePosts() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val posts = RetrofitInstance.api.getPlacePosts(placeId = place.id)
                placePostsAdapter.submitList(posts)

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
                e.printStackTrace()
            }
        }
    }
}