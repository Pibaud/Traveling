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
import androidx.navigation.fragment.findNavController
import com.example.application.utils.GuestUpsellBottomSheet
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

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
        placePostsAdapter = PlacePostsAdapter { clickedPost ->
            val bundle = Bundle().apply { putString("scrollToPostId", clickedPost.id) }
            findNavController().navigate(R.id.feedFragment, bundle)
            dismiss() // On ferme le bottom sheet pour faire propre
        }
        rvPosts.layoutManager = GridLayoutManager(requireContext(), 3)
        rvPosts.adapter = placePostsAdapter

        // 4. Chargement des données
        loadPlacePosts()

        // 5. Configuration des clics (Les fameux Toasts)
        btnViewItineraries.setOnClickListener {
            // URI spéciale pour lancer directement la navigation GPS vers la destination
            val gmmIntentUri = android.net.Uri.parse("google.navigation:q=${place.latitude},${place.longitude}")

            // Création de l'Intent (l'action d'afficher quelque chose)
            val mapIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, gmmIntentUri)

            // On essaie de forcer l'ouverture avec l'application native Google Maps
            mapIntent.setPackage("com.google.android.apps.maps")

            try {
                // On lance l'application
                startActivity(mapIntent)
            } catch (e: android.content.ActivityNotFoundException) {
                // Fallback (Plan B) : Si l'utilisateur n'a pas l'application Google Maps installée,
                // on ouvre la page d'itinéraire dans son navigateur web classique.
                val fallbackUri = android.net.Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${place.latitude},${place.longitude}")
                val webIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, fallbackUri)
                startActivity(webIntent)
            }
        }

        btnAddPost.setOnClickListener {
            // 1. Vérification du statut de l'utilisateur
            val isGuest = Firebase.auth.currentUser?.isAnonymous == true

            if (isGuest) {
                // 2. Si invité, on affiche la popup d'upsell
                GuestUpsellBottomSheet().show(childFragmentManager, "GuestUpsell")
            } else {
                // 3. Si connecté, on prépare le paquet de données (Bundle)
                val bundle = Bundle().apply {
                    putString("placeId", place.id)
                    putString("placeName", place.name)
                    putDouble("placeLat", place.latitude)
                    putDouble("placeLng", place.longitude)
                    putString("placeCategory", place.category.name)
                }

                // 4. On navigue vers CreatePostFragment en lui passant le paquet
                // (Assure-toi que l'ID correspond bien à la destination dans ton nav_graph.xml)
                findNavController().navigate(R.id.createPostFragment, bundle)

                // Optionnel : fermer le BottomSheet actuel pour que l'utilisateur
                // retombe sur la carte/grille après avoir publié son post
                dismiss()
            }
        }

        btnGenerateWith.setOnClickListener {
            val isGuest = Firebase.auth.currentUser?.isAnonymous == true

            if (isGuest) {
                GuestUpsellBottomSheet().show(childFragmentManager, "GuestUpsell")
            } else {
                val bundle = Bundle().apply {
                    putString("forcedPlaceId", place.id)
                    putString("forcedPlaceName", place.name)
                }
                // Navigation vers le fragment de création de parcours
                findNavController().navigate(R.id.createPathFragment, bundle)
                dismiss()
            }
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