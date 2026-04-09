package com.example.application.features.discovery

import SearchViewModel
import SearchViewModelFactory
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
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.application.model.Place
import com.mapbox.mapboxsdk.WellKnownTileServer
import kotlinx.coroutines.launch
import com.google.gson.JsonPrimitive
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.style.expressions.Expression
import com.mapbox.mapboxsdk.style.expressions.Expression.get
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.*
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import com.google.android.material.bottomsheet.BottomSheetBehavior
import androidx.cardview.widget.CardView
import androidx.core.content.res.ResourcesCompat
import android.graphics.drawable.BitmapDrawable
import com.mapbox.mapboxsdk.style.expressions.Expression.color
import com.mapbox.mapboxsdk.style.expressions.Expression.match
import com.mapbox.mapboxsdk.style.expressions.Expression.stop

class SearchFragment : Fragment(R.layout.fragment_search) {
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<CardView>

    // Injection du ViewModel
    private val viewModel: SearchViewModel by viewModels {
        // Pour l'instant, on crée l'API à la main ici (plus tard on pourra automatiser)
        SearchViewModelFactory(RetrofitInstance.api)
    }

    private var isMapView = false // Par défaut, on est en mode Grille

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // On récupère la clé depuis le BuildConfig généré
        val key = BuildConfig.MAPTILER_API_KEY.replace("\"", "") // On retire les guillemets parasites si besoin

        // Initialisation avec la clé passée explicitement en 2ème argument
        Mapbox.getInstance(
            requireContext(),
            key, // C'est ICI que le moteur native en a besoin
            WellKnownTileServer.MapTiler
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Le layout est déjà "inflaté" ici grâce au constructeur Fragment(R.layout.fragment_search)
        _binding = FragmentSearchBinding.bind(view)
        super.onViewCreated(view, savedInstanceState)

        // Configuration de la BottomSheet
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheetPlace)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        setupRecyclerView()
        setupMap()

        // Observe les lieux depuis le ViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.places.collect { places ->
                updateMapMarkers(places)
            }
        }

        // 1. ACTION DU BOUTON RETOUR
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp() // Revient à l'écran précédent (Feed)
        }

// 2. ACTIONS DU TOGGLE
        binding.btnToggleGrid.setOnClickListener {
            switchToGridView()
        }

        binding.btnToggleMap.setOnClickListener {
            switchToMapView()
        }
    }

    private fun updateMapMarkers(places: List<Place>) {
        val features = places.map { place ->
            val feature = Feature.fromGeometry(Point.fromLngLat(place.longitude, place.latitude))
            feature.addStringProperty("id", place.id)
            feature.addStringProperty("name", place.name)
            feature.addStringProperty("category", place.category.name)

            // On construit le nom de l'image (ex: "icon-culture" ou "icon-restauration")
            val iconName = "icon-${place.category.name.lowercase()}"
            feature.addStringProperty("icon", iconName)

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
        // 1. On cache la BottomSheet si elle était ouverte
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        // Affiche la grille, cache la carte
        binding.rvSearchGrid.visibility = View.VISIBLE
        binding.mapView.visibility = View.GONE

        // Met la grille en "Actif" (Fond rouge, icône blanche)
        binding.btnToggleGrid.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.primary_color))
        binding.btnToggleGrid.setColorFilter(Color.WHITE)

        // Met la carte en "Inactif" (Fond transparent, icône rouge)
        binding.btnToggleMap.setBackgroundColor(Color.WHITE)
        binding.btnToggleMap.setColorFilter(ContextCompat.getColor(requireContext(), R.color.primary_color))
    }

    private fun switchToMapView() {
        // Affiche la carte, cache la grille
        binding.rvSearchGrid.visibility = View.GONE
        binding.mapView.visibility = View.VISIBLE

        // Met la carte en "Actif" (Fond rouge, icône blanche)
        binding.btnToggleMap.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.primary_color))
        binding.btnToggleMap.setColorFilter(Color.WHITE)

        // Met la grille en "Inactif" (Fond transparent, icône rouge)
        binding.btnToggleGrid.setBackgroundColor(Color.WHITE)
        binding.btnToggleGrid.setColorFilter(ContextCompat.getColor(requireContext(), R.color.primary_color))
    }

    private fun setupRecyclerView() {
        // Layout en grille (2 colonnes) pour ressembler à la maquette
        binding.rvSearchGrid.layoutManager = GridLayoutManager(requireContext(), 2)
        // binding.rvSearchGrid.adapter = TonAdapterDeGrille()
    }

    private fun setupMap() {
        val key = BuildConfig.MAPTILER_API_KEY

        // --- ICI : REMPLACE PAR TON STYLE ID PERSO ---
        val mapId = "streets-v2"
        val styleUrl = "https://api.maptiler.com/maps/$mapId/style.json?key=$key"

        binding.mapView.getMapAsync { map ->
            map.setStyle(styleUrl) { style ->
                // 1. Ajouter tes images Android au style de la carte MapLibre
                // ATTENTION : Remplace R.drawable.ic_... par tes vrais noms d'icônes
                drawableToBitmap(R.drawable.round_culture_24)?.let { style.addImage("icon-culture", it, true) }
                drawableToBitmap(R.drawable.round_restaurant_24)?.let { style.addImage("icon-restauration", it, true) }
                drawableToBitmap(R.drawable.round_loisirs_24)?.let { style.addImage("icon-loisirs", it, true) }
                drawableToBitmap(R.drawable.round_decouverte_24)?.let { style.addImage("icon-decouverte", it, true) }

                // 2. Source vide
                style.addSource(GeoJsonSource("PLACES_SOURCE", FeatureCollection.fromFeatures(emptyList())))

                // 3. Le calque avec la coloration GPU (iconColor)
                val symbolLayer = SymbolLayer("PLACES_LAYER", "PLACES_SOURCE")
                    .withProperties(
                        iconImage(get("icon")),
                        iconAllowOverlap(false), // Garde l'espacement intelligent
                        iconPadding(10f), // L'espace vital autour de chaque icône
                        iconSize(0.6f), // Ajuste la taille (64px * 0.6 = environ 38px sur l'écran)

                        // LA MAGIE DES COULEURS EST ICI
                        iconColor(
                            match(
                                get("category"), // On lit le champ "category" du GeoJSON
                                color(Color.BLACK), // Couleur par défaut en cas d'erreur
                                stop("CULTURE", color(ContextCompat.getColor(requireContext(), R.color.culture_color))),
                                stop("RESTAURATION", color(ContextCompat.getColor(requireContext(), R.color.restauration_color))),
                                stop("LOISIRS", color(ContextCompat.getColor(requireContext(), R.color.sport_color))),
                                stop("DECOUVERTE", color(ContextCompat.getColor(requireContext(), R.color.discovery_color)))
                            )
                        )
                    )
                style.addLayer(symbolLayer)
            }

            // --- GESTION DU CLIC SUR LA CARTE ---
            map.addOnMapClickListener { point ->
                // Convertit le clic GPS en pixels écran
                val screenPoint = map.projection.toScreenLocation(point)

                // Cherche si on a cliqué sur un élément de "PLACES_LAYER"
                val features = map.queryRenderedFeatures(screenPoint, "PLACES_LAYER")

                if (features.isNotEmpty()) {
                    val clickedFeature = features.first()
                    val placeId = clickedFeature.getStringProperty("id")

                    // Retrouve le lieu complet depuis ton ViewModel
                    val clickedPlace = viewModel.places.value.find { it.id == placeId }

                    if (clickedPlace != null) {
                        showBottomSheet(clickedPlace)
                    }
                    return@addOnMapClickListener true // On a consommé le clic
                }

                // Si on clique dans le vide, on cache la BottomSheet
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                false
            }

            // On centre sur Montpellier
            map.cameraPosition = CameraPosition.Builder()
                .target(LatLng(43.6107, 3.8767))
                .zoom(12.0)
                .build()

            map.addOnCameraIdleListener {
                val bounds = map.projection.visibleRegion.latLngBounds
                viewModel.fetchPlaces(
                    bounds.latitudeSouth, // Au lieu de latSouth
                    bounds.longitudeWest, // Au lieu de lonWest
                    bounds.latitudeNorth, // Au lieu de latNorth
                    bounds.longitudeEast  // Au lieu de lonEast
                )
            }
        }
    }

    private fun showBottomSheet(place: Place) {
        binding.apply {
            tvSheetName.text = place.name

            // Mettre en majuscule la première lettre (ex: CULTURE -> Culture)
            val categoryText = place.category.name.lowercase().replaceFirstChar { it.uppercase() }
            tvSheetCategory.text = categoryText

            // Tu pourras changer la couleur du texte selon la catégorie ici si tu veux
        }

        // Fait glisser la vue vers le haut
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun drawableToBitmap(drawableId: Int): android.graphics.Bitmap? {
        val drawable = ContextCompat.getDrawable(requireContext(), drawableId)?.mutate() ?: return null

        // On force l'icône en blanc pur (c'est le masque de base)
        androidx.core.graphics.drawable.DrawableCompat.setTint(drawable, android.graphics.Color.WHITE)

        // On force une taille fixe en pixels pour éviter les bugs d'icônes géantes
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