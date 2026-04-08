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

class SearchFragment : Fragment(R.layout.fragment_search) {
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

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
        // On transforme tes objets Place en Features GeoJSON natifs
        val features = places.map { place ->
            val feature = Feature.fromGeometry(Point.fromLngLat(place.longitude, place.latitude))
            // On injecte tes données directement dans le point
            feature.addStringProperty("id", place.id)
            feature.addStringProperty("name", place.name)
            feature.addStringProperty("category", place.category.name)
            feature
        }

        val featureCollection = FeatureCollection.fromFeatures(features)

        // On envoie le nouveau paquet de données à la carte
        binding.mapView.getMapAsync { map ->
            map.style?.let { style ->
                val source = style.getSourceAs<GeoJsonSource>("PLACES_SOURCE")
                source?.setGeoJson(featureCollection)
            }
        }
    }

    private fun switchToGridView() {
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
                // 1. On crée une source vide au démarrage
                style.addSource(GeoJsonSource("PLACES_SOURCE", FeatureCollection.fromFeatures(emptyList())))

                // 2. On crée le calque visuel branché sur cette source
                val symbolLayer = SymbolLayer("PLACES_LAYER", "PLACES_SOURCE")
                    .withProperties(
                        iconImage("marker-15"), // Ton icône
                        iconAllowOverlap(true),
                        textField(get("name")), // Va lire la propriété "name" du GeoJSON
                        textOffset(arrayOf(0f, 1.2f)),
                        textColor(Color.BLACK)
                    )
                style.addLayer(symbolLayer)
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

    private fun toggleView() {
        if (isMapView) {
            binding.rvSearchGrid.visibility = View.GONE
            binding.mapView.visibility = View.VISIBLE
            // Change l'icône du bouton pour indiquer le retour à la grille
            // binding.btnToggleView.setImageResource(R.drawable.ic_grid)
        } else {
            binding.rvSearchGrid.visibility = View.VISIBLE
            binding.mapView.visibility = View.GONE
            // Change l'icône du bouton pour indiquer la carte
            // binding.btnToggleView.setImageResource(R.drawable.ic_map)
        }
    }

    // Le cycle de vie d'Osmdroid doit être respecté
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