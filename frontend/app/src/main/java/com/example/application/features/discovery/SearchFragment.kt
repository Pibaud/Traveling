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

import com.example.application.features.path.PlaceDetailsBottomSheet

class SearchFragment : Fragment(R.layout.fragment_search) {
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

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
        _binding = FragmentSearchBinding.bind(view)
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupMap()

        // Observe les lieux depuis le ViewModel pour mettre à jour la carte
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.places.collect { places ->
                updateMapMarkers(places)
            }
        }

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnToggleGrid.setOnClickListener {
            switchToGridView()
        }

        binding.btnToggleMap.setOnClickListener {
            switchToMapView()
        }

        binding.etSearchPlace.setupPlaceAutocomplete(
            coroutineScope = viewLifecycleOwner.lifecycleScope,
            apiService = RetrofitInstance.api
        ) { selectedPlace ->

            if (binding.rvSearchGrid.visibility == View.VISIBLE) {
                switchToMapView()
            }

            val bottomSheet = PlaceDetailsBottomSheet(selectedPlace)
            bottomSheet.show(childFragmentManager, "PlaceDetailsBottomSheet")

            binding.mapView.getMapAsync { map ->
                val position = CameraPosition.Builder()
                    .target(LatLng(selectedPlace.latitude, selectedPlace.longitude))
                    .zoom(15.0)
                    .build()
                map.animateCamera(CameraUpdateFactory.newCameraPosition(position), 1500)
            }

            binding.etSearchPlace.setText(selectedPlace.name, false)
            binding.etSearchPlace.clearFocus()
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

        binding.btnToggleGrid.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.primary_color))
        binding.btnToggleGrid.setColorFilter(Color.WHITE)

        binding.btnToggleMap.setBackgroundColor(Color.WHITE)
        binding.btnToggleMap.setColorFilter(ContextCompat.getColor(requireContext(), R.color.primary_color))
    }

    private fun switchToMapView() {
        binding.rvSearchGrid.visibility = View.GONE
        binding.mapView.visibility = View.VISIBLE

        binding.btnToggleMap.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.primary_color))
        binding.btnToggleMap.setColorFilter(Color.WHITE)

        binding.btnToggleGrid.setBackgroundColor(Color.WHITE)
        binding.btnToggleGrid.setColorFilter(ContextCompat.getColor(requireContext(), R.color.primary_color))
    }

    private fun setupRecyclerView() {
        binding.rvSearchGrid.layoutManager = GridLayoutManager(requireContext(), 2)
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