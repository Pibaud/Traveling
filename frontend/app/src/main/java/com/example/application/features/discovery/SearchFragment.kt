package com.example.application.features.discovery

import SearchViewModel
import SearchViewModelFactory
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.example.application.R
import com.example.application.databinding.FragmentSearchBinding
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.geometry.LatLng
import com.example.application.BuildConfig
import androidx.core.content.ContextCompat
import android.graphics.Color
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.application.model.Place
import com.mapbox.mapboxsdk.WellKnownTileServer
import kotlinx.coroutines.launch
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.style.expressions.Expression.get
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.*
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import com.mapbox.mapboxsdk.style.expressions.Expression.color
import com.mapbox.mapboxsdk.style.expressions.Expression.match
import com.mapbox.mapboxsdk.style.expressions.Expression.stop
import com.example.application.utils.setupPlaceAutocomplete
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.offline.OfflineManager
import androidx.core.widget.addTextChangedListener
import com.example.application.features.places.PlaceDetailsBottomSheet
import kotlinx.coroutines.isActive

import com.example.application.model.PlaceCategory
import com.example.application.model.RetrofitInstance
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SearchFragment : Fragment(R.layout.fragment_search) {
    private var _binding: FragmentSearchBinding? = null
    private var currentSelectedPlace: Place? = null
    private lateinit var staggeredAdapter: StaggeredPostAdapter
    private val binding get() = _binding!!
    private lateinit var staggeredPlaceAdapter: StaggeredPlaceAdapter
    private lateinit var staggeredPostAdapter: StaggeredPostAdapter
    private lateinit var horizontalMapAdapter: PlaceHorizontalAdapter
    private val snapHelper = androidx.recyclerview.widget.PagerSnapHelper()
    private var currentCategory: String? = null
    var previousSize = 0
    var previousAuthorSize = 0
    private var currentAuthorId: String? = null
    private lateinit var horizontalPostAdapter: PostHorizontalAdapter
    // Injection du ViewModel
    private val viewModel: SearchViewModel by viewModels {
        SearchViewModelFactory(RetrofitInstance.api)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val key = BuildConfig.MAPTILER_API_KEY.replace("\"", "")
        Mapbox.getInstance(requireContext(), key, WellKnownTileServer.MapTiler)
        OfflineManager.getInstance(requireContext()).setOfflineMapboxTileCountLimit(20000)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSearchBinding.bind(view)

        // 1. Initialisation des composants
        setupRecyclerView()
        setupMap()

        // 2. Initialisation des écouteurs (clics, recherche...)
        setupListeners()

        // 3. Observation des données du ViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.places.collect { places ->
                updateMapMarkers(places)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.placePosts.collect { posts ->
                staggeredPostAdapter.submitList(posts)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.categoryPlaces.collect { places ->
                if (places.isNotEmpty()) {
                    if (binding.rvSearchGrid.adapter != staggeredPlaceAdapter) {
                        binding.rvSearchGrid.adapter = staggeredPlaceAdapter
                    }

                    if (binding.rvMapPlaces.adapter != horizontalMapAdapter) {
                        binding.rvMapPlaces.adapter = horizontalMapAdapter
                    }

                    val isAppending = places.size > previousSize  // C'est un ajout, pas un reset

                    staggeredPlaceAdapter.submitList(places, isAppending)
                    horizontalMapAdapter.submitList(places, isAppending)
                    updateMapMarkers(places)
                    previousSize = places.size  // On mémorise la taille actuelle

                    if (binding.mapView.visibility == View.VISIBLE) {
                        binding.rvMapPlaces.visibility = View.VISIBLE

                        if (currentSelectedPlace == null || !places.any { it.id == currentSelectedPlace?.id }) {
                            val firstPlace = places[0]
                            currentSelectedPlace = firstPlace

                            // On utilise .post pour laisser le temps au RecyclerView d'apparaître
                            binding.rvMapPlaces.post {
                                binding.rvMapPlaces.scrollToPosition(0)
                                moveMapToPlace(firstPlace)
                            }
                        }
                    }
                } else {
                    binding.rvMapPlaces.visibility = View.GONE
                    currentSelectedPlace = null
                    // Reset catégorie → on remet aussi previousSize à 0
                    previousSize = 0
                    staggeredPlaceAdapter.submitList(emptyList())  // 👈 On vide l'adapter
                    horizontalMapAdapter.submitList(emptyList())

                }
            }
        }

        // Observation des posts par auteur
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.authorPosts.collect { posts ->
                if (posts.isNotEmpty()) {
                    if (binding.rvSearchGrid.adapter != staggeredPostAdapter) {
                        binding.rvSearchGrid.adapter = staggeredPostAdapter
                    }
                    staggeredPostAdapter.submitList(posts)

                    // 👇 1. On change l'adapter du carrousel !
                    if (binding.rvMapPlaces.adapter != horizontalPostAdapter) {
                        binding.rvMapPlaces.adapter = horizontalPostAdapter
                    }

                    // 👇 2. On passe LES POSTS au carrousel
                    val isAppending = posts.size > previousAuthorSize
                    horizontalPostAdapter.submitList(posts, isAppending)

                    // 👇 3. Les marqueurs de la carte ont toujours besoin des lieux uniques
                    val placesFromPosts = posts.map { it.place }.distinctBy { it.id }
                    updateMapMarkers(placesFromPosts)

                    previousAuthorSize = posts.size

                    if (binding.mapView.visibility == View.VISIBLE) {
                        binding.rvMapPlaces.visibility = View.VISIBLE

                        // Focus sur le PREMIER POST
                        val firstPost = posts.firstOrNull()
                        if (firstPost != null && (currentSelectedPlace == null || firstPost.place.id != currentSelectedPlace?.id)) {
                            currentSelectedPlace = firstPost.place
                            binding.rvMapPlaces.post {
                                binding.rvMapPlaces.scrollToPosition(0)
                                moveMapToPlace(firstPost.place)
                            }
                        }
                    }
                } else {
                    previousAuthorSize = 0
                    staggeredPostAdapter.submitList(emptyList())
                    horizontalPostAdapter.submitList(emptyList()) // On vide le nouveau
                    updateMapMarkers(emptyList())
                    binding.rvMapPlaces.visibility = View.GONE
                    currentSelectedPlace = null
                }
            }
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
        // Gestion du basculement entre Grille et Map
        binding.btnToggleGrid.setOnClickListener { switchToGridView() }
        binding.btnToggleMap.setOnClickListener { switchToMapView() }

        // Gestion de la sélection de catégorie
        binding.chipCategory.setOnClickListener {
            // Si une catégorie est déjà active, on l'annule (Toggle)
            if (currentCategory != null) {
                resetCategoryFilter()
            } else {
                showCategorySelectionDialog()
            }
        }

        binding.chipAuthor.setOnClickListener {
            if (currentAuthorId != null) {
                resetAuthorFilterUI()
            } else {
                showAuthorSelectionDialog()
            }
        }

        // Configuration de la barre de recherche (Autocomplétion)
        binding.etSearchPlace.setupPlaceAutocomplete(
            coroutineScope = viewLifecycleOwner.lifecycleScope,
            apiService = RetrofitInstance.api
        ) { selectedPlace ->
            handlePlaceSelection(selectedPlace)
        }

        // On écoute la fermeture de la BottomSheet pour vider la variable
        childFragmentManager.registerFragmentLifecycleCallbacks(object : androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewDestroyed(fm: androidx.fragment.app.FragmentManager, f: androidx.fragment.app.Fragment) {
                super.onFragmentViewDestroyed(fm, f)
                // Si le fragment qui vient d'être détruit est notre BottomSheet
                if (f.tag == "PlaceDetails") {
                    currentSelectedPlace = null
                }
            }
        }, false)
    }

    private fun resetCategoryFilter() {
        currentCategory = null
        binding.chipCategory.text = "Type de lieu"

        // 1. On remet la couleur de texte par défaut (souvent noir ou gris foncé)
        val typedValue = android.util.TypedValue()
        requireContext().theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
        binding.chipCategory.setTextColor(typedValue.data)

        // 2. MAGIE : En mettant "null", le Chip récupère automatiquement le style de ton XML !
        binding.chipCategory.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#dedede"))

        binding.rvSearchGrid.adapter = staggeredPostAdapter
        binding.rvMapPlaces.visibility = View.GONE
    }

    private fun showCategorySelectionDialog() {
        val categories = PlaceCategory.values().map { it.name }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Choisir une catégorie")
            .setItems(categories) { _, which ->
                val selectedCat = categories[which]

                // Mise à jour visuelle du Chip
                currentCategory = selectedCat
                binding.chipCategory.text = selectedCat
                binding.chipCategory.setTextColor(Color.WHITE)
                binding.chipCategory.chipBackgroundColor = ContextCompat.getColorStateList(requireContext(), R.color.primary_color)

                loadCategoryPlaces(selectedCat)
            }
            .show()
    }

    private fun handlePlaceSelection(selectedPlace: Place) {
        // 1. On mémorise le lieu sélectionné
        currentSelectedPlace = selectedPlace

        // 2. On met à jour l'affichage de la barre de recherche
        binding.etSearchPlace.setText(selectedPlace.name, false)
        binding.etSearchPlace.clearFocus()

        // 3. On déclenche l'action appropriée selon la vue active (Grille ou Map)
        if (binding.rvSearchGrid.visibility == View.VISIBLE) {
            // Si on est sur la grille, on charge les photos de ce lieu
            viewModel.fetchPostsForPlace(selectedPlace.id)
        } else {
            // Si on est sur la carte, on ouvre le panneau des détails...
            val bottomSheet = PlaceDetailsBottomSheet(selectedPlace)
            bottomSheet.show(childFragmentManager, "PlaceDetails")

            // ...et on déplace la caméra vers le lieu
            binding.mapView.getMapAsync { map ->
                val position = com.mapbox.mapboxsdk.camera.CameraPosition.Builder()
                    .target(com.mapbox.mapboxsdk.geometry.LatLng(selectedPlace.latitude, selectedPlace.longitude))
                    .zoom(15.0)
                    .build()
                map.animateCamera(com.mapbox.mapboxsdk.camera.CameraUpdateFactory.newCameraPosition(position), 1000)
            }
        }
    }

    private fun updateMapMarkers(places: List<Place>) {
        val features = places.map { place ->
            val feature = Feature.fromGeometry(Point.fromLngLat(place.longitude, place.latitude))
            feature.addStringProperty("id", place.id)
            feature.addStringProperty("name", place.name)
            feature.addStringProperty("category", place.category.name)
            feature.addStringProperty("icon", "icon-${place.category.name.lowercase()}")
            feature
        }

        val featureCollection = FeatureCollection.fromFeatures(features)
        binding.mapView.getMapAsync { map ->
            map.style?.let { style ->
                val source = style.getSourceAs<GeoJsonSource>("PLACES_SOURCE")
                source?.setGeoJson(featureCollection)
            }
        }
    }

    private fun switchToGridView() {
        binding.rvSearchGrid.visibility = View.VISIBLE
        binding.mapView.visibility = View.GONE
        binding.rvMapPlaces.visibility = View.GONE

        // On allume Grid (Fond Primaire, Icône Blanche)
        binding.btnToggleGrid.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.primary_color))
        binding.btnToggleGrid.setColorFilter(Color.WHITE)

        // On éteint Map (Fond Blanc, Icône Primaire)
        binding.btnToggleMap.setBackgroundColor(Color.WHITE)
        binding.btnToggleMap.setColorFilter(ContextCompat.getColor(requireContext(), R.color.primary_color))

        if (currentCategory == null && currentAuthorId == null) {
            currentSelectedPlace?.let { viewModel.fetchPostsForPlace(it.id) }
        }
    }

    private fun switchToMapView() {
        binding.rvSearchGrid.visibility = View.GONE
        binding.mapView.visibility = View.VISIBLE

        // 1. On vérifie si l'adapter ACTUEL (Lieux ou Posts) contient des données
        val currentAdapter = binding.rvMapPlaces.adapter
        val hasItems = currentAdapter != null && currentAdapter.itemCount > 0

        if (hasItems) {
            binding.rvMapPlaces.visibility = View.VISIBLE
        }

        // On allume Map (Fond Primaire, Icône Blanche)
        binding.btnToggleMap.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.primary_color))
        binding.btnToggleMap.setColorFilter(Color.WHITE)

        // On éteint Grid (Fond Blanc, Icône Primaire)
        binding.btnToggleGrid.setBackgroundColor(Color.WHITE)
        binding.btnToggleGrid.setColorFilter(ContextCompat.getColor(requireContext(), R.color.primary_color))

        if (currentSelectedPlace != null) {
            // Si un lieu était déjà focus, on va dessus
            moveMapToPlace(currentSelectedPlace!!)
        } else if (hasItems) {
            // 2. Si aucun lieu n'est focus, on récupère le premier élément du BON adapter
            val firstPlace = if (currentAdapter == horizontalPostAdapter) {
                horizontalPostAdapter.getPostAt(0).place
            } else {
                horizontalMapAdapter.getPlaceAt(0)
            }

            currentSelectedPlace = firstPlace

            // On s'assure que la liste est bien calée au début
            binding.rvMapPlaces.scrollToPosition(0)
            moveMapToPlace(firstPlace)
        }
    }

    private fun moveMapToPlace(place: Place) {
        binding.mapView.getMapAsync { map ->
            val position = CameraPosition.Builder()
                .target(LatLng(place.latitude, place.longitude))
                .zoom(15.0)
                .build()
            map.animateCamera(CameraUpdateFactory.newCameraPosition(position), 1000)
        }
    }

    private fun setupRecyclerView() {
        val fetchCover: suspend (String) -> String? = { placeId ->
            try {
                val posts = RetrofitInstance.api.getPlacePosts(placeId, null)
                posts.firstOrNull()?.imageUrls?.firstOrNull()
            } catch (e: Exception) { null }
        }

        staggeredPlaceAdapter = StaggeredPlaceAdapter(viewLifecycleOwner.lifecycleScope, fetchCover) { clickedPlace ->
            currentSelectedPlace = clickedPlace
            PlaceDetailsBottomSheet(clickedPlace).show(childFragmentManager, "PlaceDetails")
        }

        horizontalMapAdapter = PlaceHorizontalAdapter(viewLifecycleOwner.lifecycleScope, fetchCover) { clickedPlace ->
            currentSelectedPlace = clickedPlace
            PlaceDetailsBottomSheet(clickedPlace).show(childFragmentManager, "PlaceDetails")
        }

        // Adapter pour les POSTS (Carrousel horizontal sur la Map)
        horizontalPostAdapter = PostHorizontalAdapter { clickedPost ->
            // On utilise exactement la même clé et la même destination que ta grille
            val bundle = Bundle().apply {
                putString("scrollToPostId", clickedPost.id)
            }
            findNavController().navigate(R.id.feedFragment, bundle)
        }

        // 2. Adapter pour les POSTS (Grille Staggered - Pinterest)
        staggeredPostAdapter = StaggeredPostAdapter { clickedPost ->
            val bundle = Bundle().apply { putString("scrollToPostId", clickedPost.id) }
            findNavController().navigate(R.id.feedFragment, bundle)
        }

        // Config de base du RecyclerView de la grille
        binding.rvSearchGrid.layoutManager = StaggeredGridLayoutManager(
            2,
            StaggeredGridLayoutManager.VERTICAL
        ).apply {
            gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
        }
        // Par défaut, on affiche les posts
        binding.rvSearchGrid.adapter = staggeredPostAdapter

        // Config du carrousel Map
        binding.rvMapPlaces.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvMapPlaces.adapter = horizontalMapAdapter
        snapHelper.attachToRecyclerView(binding.rvMapPlaces)

        // Écouteur de scroll pour la Map
        binding.rvMapPlaces.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dx > 0) {
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    if (layoutManager.findLastVisibleItemPosition() >= layoutManager.itemCount - 3) {
                        // 👇 On pagine selon le filtre actif
                        if (currentCategory != null) loadCategoryPlaces(currentCategory!!)
                        else if (currentAuthorId != null) viewModel.fetchPostsByAuthor(currentAuthorId!!)
                    }
                }
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val centerView = snapHelper.findSnapView(recyclerView.layoutManager) ?: return
                    val position = recyclerView.layoutManager?.getPosition(centerView) ?: return

                    // 👇 On récupère le "Place" depuis le bon adapter
                    val place = if (binding.rvMapPlaces.adapter == horizontalPostAdapter) {
                        horizontalPostAdapter.getPostAt(position).place
                    } else {
                        horizontalMapAdapter.getPlaceAt(position)
                    }

                    currentSelectedPlace = place

                    binding.mapView.getMapAsync { map ->
                        val cameraPosition = CameraPosition.Builder()
                            .target(LatLng(place.latitude, place.longitude))
                            .zoom(15.0)
                            .build()
                        map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 800)
                    }
                }
            }
        })

        binding.rvSearchGrid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) {
                    val layoutManager = recyclerView.layoutManager as StaggeredGridLayoutManager
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val pastVisibleItems = layoutManager.findFirstVisibleItemPositions(null).maxOrNull() ?: 0

                    if ((visibleItemCount + pastVisibleItems) >= totalItemCount - 4) {
                        // 👇 Correction : on gère les deux types de filtres
                        if (currentCategory != null) {
                            loadCategoryPlaces(currentCategory!!)
                        } else if (currentAuthorId != null) {
                            viewModel.fetchPostsByAuthor(currentAuthorId!!)
                        }
                    }
                }
            }
        })
    }

    private fun setupMap() {
        val key = BuildConfig.MAPTILER_API_KEY
        val mapId = "streets-v2"
        val styleUrl = "https://api.maptiler.com/maps/$mapId/style.json?key=$key"

        binding.mapView.getMapAsync { map ->
            map.setStyle(styleUrl) { style ->
                drawableToBitmap(R.drawable.round_culture_24)?.let { style.addImage("icon-culture", it, true) }
                drawableToBitmap(R.drawable.round_restaurant_24)?.let { style.addImage("icon-restauration", it, true) }
                drawableToBitmap(R.drawable.round_loisirs_24)?.let { style.addImage("icon-loisirs", it, true) }
                drawableToBitmap(R.drawable.round_decouverte_24)?.let { style.addImage("icon-decouverte", it, true) }

                style.addSource(GeoJsonSource("PLACES_SOURCE", FeatureCollection.fromFeatures(emptyList())))

                val symbolLayer = SymbolLayer("PLACES_LAYER", "PLACES_SOURCE")
                    .withProperties(
                        iconImage(get("icon")),
                        iconAllowOverlap(false),
                        iconPadding(10f),
                        iconSize(0.6f),
                        iconColor(
                            match(
                                get("category"),
                                color(Color.BLACK),
                                stop("CULTURE", color(ContextCompat.getColor(requireContext(), R.color.culture_color))),
                                stop("RESTAURATION", color(ContextCompat.getColor(requireContext(), R.color.restauration_color))),
                                stop("LOISIRS", color(ContextCompat.getColor(requireContext(), R.color.sport_color))),
                                stop("DECOUVERTE", color(ContextCompat.getColor(requireContext(), R.color.discovery_color)))
                            )
                        )
                    )
                style.addLayer(symbolLayer)
            }

            map.addOnMapClickListener { point ->
                val screenPoint = map.projection.toScreenLocation(point)
                val features = map.queryRenderedFeatures(screenPoint, "PLACES_LAYER")

                if (features.isNotEmpty()) {
                    val clickedFeature = features.first()
                    val placeId = clickedFeature.getStringProperty("id")
                    val clickedPlace = viewModel.places.value.find { it.id == placeId }

                    if (clickedPlace != null) {
                        currentSelectedPlace = clickedPlace
                        // 👇 AFFICHAGE DU NOUVEAU BOTTOM SHEET MODULAIRE 👇
                        val bottomSheet = PlaceDetailsBottomSheet(clickedPlace)
                        bottomSheet.show(childFragmentManager, "PlaceDetailsBottomSheet")
                    }
                    return@addOnMapClickListener true
                }
                false
            }

            map.cameraPosition = CameraPosition.Builder()
                .target(LatLng(43.6107, 3.8767))
                .zoom(12.0)
                .build()

            map.addOnCameraIdleListener {
                val bounds = map.projection.visibleRegion.latLngBounds
                viewModel.fetchPlaces(
                    bounds.latitudeSouth,
                    bounds.longitudeWest,
                    bounds.latitudeNorth,
                    bounds.longitudeEast
                )
            }
        }
    }

    private fun loadCategoryPlaces(category: String) {
        viewModel.fetchPlacesByCategory(category)
    }

    private fun showAuthorSelectionDialog() {
        val context = requireContext()
        val textInputLayout = com.google.android.material.textfield.TextInputLayout(context).apply {
            hint = "Nom de l'utilisateur"
            setPadding(40, 20, 40, 0)
        }
        val autoCompleteTextView = com.google.android.material.textfield.MaterialAutoCompleteTextView(context)
        textInputLayout.addView(autoCompleteTextView)

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
            .setTitle("Filtrer par auteur")
            .setView(textInputLayout)
            .setNegativeButton("Annuler", null)
            .show()

        var searchJob: kotlinx.coroutines.Job? = null
        var isSelecting = false

        autoCompleteTextView.addTextChangedListener { text ->
            if (isSelecting) return@addTextChangedListener

            val query = text.toString()
            if (query.length >= 2) {
                searchJob?.cancel() // On annule la recherche précédente

                searchJob = viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val users = RetrofitInstance.api.searchUsers(query)

                        // On vérifie si la coroutine est toujours active
                        // (si l'utilisateur n'a pas fermé le dialog ou tapé une autre lettre)
                        if (!isActive) return@launch

                        val adapter = android.widget.ArrayAdapter(
                            context,
                            android.R.layout.simple_dropdown_item_1line,
                            users.map { it.username }
                        )
                        autoCompleteTextView.setAdapter(adapter)

                        // On affiche seulement si le champ a toujours le focus
                        if (autoCompleteTextView.hasFocus()) {
                            autoCompleteTextView.showDropDown()
                        }

                        autoCompleteTextView.setOnItemClickListener { _, _, position, _ ->
                            isSelecting = true
                            searchJob?.cancel() // Sécurité ultime

                            val selectedUser = users[position]
                            applyAuthorFilter(selectedUser.uid, selectedUser.username)

                            autoCompleteTextView.dismissDropDown()
                            dialog.dismiss()
                        }
                    } catch (e: Exception) {
                        // Les erreurs d'annulation sont normales ici
                    }
                }
            }
        }
    }

    private fun applyAuthorFilter(uid: String, username: String) {
        currentAuthorId = uid
        // 1. Visuel
        binding.chipAuthor.text = username
        binding.chipAuthor.setTextColor(Color.WHITE)
        binding.chipAuthor.chipBackgroundColor = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.primary_color))

        // 2. Désactiver le filtre catégorie s'il existe (pour éviter les conflits)
        resetCategoryFilter()

        // 3. Charger les données
        viewModel.fetchPostsByAuthor(uid)
    }

    private fun resetAuthorFilterUI() {
        currentAuthorId = null
        binding.chipAuthor.text = "Auteur"

        // On remet la couleur de texte par défaut (OnSurface)
        val typedValue = android.util.TypedValue()
        requireContext().theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
        binding.chipAuthor.setTextColor(typedValue.data)

        // On remet le fond gris
        binding.chipAuthor.chipBackgroundColor = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#dedede"))

        // On vide les données dans le ViewModel
        viewModel.resetAuthorFilter()

        // On s'assure qu'on est bien sur l'affichage classique des posts
        binding.rvSearchGrid.adapter = staggeredPostAdapter
    }

    private fun drawableToBitmap(drawableId: Int): android.graphics.Bitmap? {
        val drawable = ContextCompat.getDrawable(requireContext(), drawableId)?.mutate() ?: return null
        androidx.core.graphics.drawable.DrawableCompat.setTint(drawable, android.graphics.Color.WHITE)
        val size = 64
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    override fun onStart() { super.onStart(); binding.mapView.onStart() }
    override fun onResume() { super.onResume(); binding.mapView.onResume() }
    override fun onPause() { super.onPause(); binding.mapView.onPause() }
    override fun onStop() { super.onStop(); binding.mapView.onStop() }
    override fun onLowMemory() { super.onLowMemory(); binding.mapView.onLowMemory() }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.mapView.onDestroy()
        _binding = null
    }
}