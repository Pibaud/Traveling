package com.example.application.features.path

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.example.application.BuildConfig
import com.example.application.databinding.FragmentItineraryDetailsSheetBinding
import com.example.application.model.ItineraryResponse
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.annotations.PolylineOptions
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.geometry.LatLngBounds
import com.mapbox.mapboxsdk.WellKnownTileServer
import com.mapbox.mapboxsdk.camera.CameraPosition
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class ItineraryDetailsBottomSheet(
    private val itinerary: ItineraryResponse
) : BottomSheetDialogFragment() {

    private var _binding: FragmentItineraryDetailsSheetBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val key = BuildConfig.MAPTILER_API_KEY.replace("\"", "")
        Mapbox.getInstance(
            requireContext(),
            key,
            WellKnownTileServer.MapTiler
        )
        _binding = FragmentItineraryDetailsSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    @Suppress("DEPRECATION")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialisation de la carte Mapbox (OBLIGATOIRE)
        binding.mapViewDetails.onCreate(savedInstanceState)

        binding.mapViewDetails.getMapAsync { mapboxMap ->

            // 1. ON RÉCUPÈRE LA CLÉ ET L'URL COMME TON AMI
            val key = BuildConfig.MAPTILER_API_KEY.replace("\"", "")
            val styleUrl = "https://api.maptiler.com/maps/streets-v2/style.json?key=$key"

            // 2. ON CHARGE LE STYLE MAPTILER
            mapboxMap.setStyle(styleUrl) { style ->

                // 3. ON DESSINE SEULEMENT QUAND LE STYLE EST CHARGÉ
                val points = itinerary.steps.map { LatLng(it.latitude, it.longitude) }

                if (points.isNotEmpty()) {
                    // Ajouter des marqueurs pour chaque étape
                    itinerary.steps.forEachIndexed { index, place ->
                        mapboxMap.addMarker(
                            MarkerOptions()
                                .position(LatLng(place.latitude, place.longitude))
                                .title("${index + 1}. ${place.name}")
                        )
                    }

                    // Dessiner la ligne (Polyline)
                    mapboxMap.addPolyline(
                        PolylineOptions()
                            .addAll(points)
                            .color(Color.BLACK)
                            .width(5f)
                    )

                    // Zoomer intelligemment (CORRECTION DU CRASH ICI)
                    if (points.size > 1) {
                        // S'il y a plusieurs points, on fait une boîte (Bounds)
                        val bounds = LatLngBounds.Builder().includes(points).build()
                        mapboxMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
                    } else if (points.size == 1) {
                        // S'il n'y a qu'un point, on zoome simplement dessus
                        mapboxMap.animateCamera(CameraUpdateFactory.newLatLngZoom(points[0], 14.0))
                    }
                }
            }
        }

        // Remplissage des données
        binding.tvDetailName.text = itinerary.name
        binding.tvDetailPrice.text = "${itinerary.totalPrice} €"
        binding.tvDetailInfos.text = "${itinerary.totalDuration} heures\n" +
                (if (itinerary.mealIncluded) "Repas compris" else "Repas non compris")

        try {
            binding.cvHeader.setCardBackgroundColor(Color.parseColor(itinerary.hexColor))
        } catch (e: Exception) {}

        binding.rvSteps.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
            requireContext(),
            androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL,
            false
        )

        // On passe les lieux à l'adaptateur
        binding.rvSteps.adapter = ItineraryStepAdapter(itinerary.steps) { clickedPlace ->
            // Action au clic sur une bulle de lieu
            // Ex: recentrer la carte sur le lieu
            val position = CameraPosition.Builder()
                .target(LatLng(clickedPlace.latitude, clickedPlace.longitude))
                .zoom(16.0)
                .build()
            binding.mapViewDetails.getMapAsync { map ->
                map.animateCamera(CameraUpdateFactory.newCameraPosition(position), 1000)
            }
        }

        // Gestion des boutons
        binding.btnSave.setOnClickListener {
            showSaveDialog()
        }
    }

    private fun showSaveDialog() {
        val context = requireContext()

        // 1. Créer un champ de texte pour que l'utilisateur tape le nom
        val editText = EditText(context).apply {
            hint = "Ex: Mon super week-end"
            setPadding(50, 40, 50, 40)
        }

        // 2. Afficher la popup
        MaterialAlertDialogBuilder(context)
            .setTitle("Sauvegarder l'itinéraire")
            .setMessage("Donnez un nom à votre parcours :")
            .setView(editText)
            .setPositiveButton("Sauvegarder") { _, _ ->
                val itineraryName = editText.text.toString()
                if (itineraryName.isNotBlank()) {
                    saveItineraryToDatabase(itineraryName)
                } else {
                    Toast.makeText(context, "Le nom ne peut pas être vide", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun saveItineraryToDatabase(name: String) {
        val userId = Firebase.auth.currentUser?.uid ?: return

        // On choisit une couleur aléatoire
        val colors = listOf("#2E7D32", "#1565C0", "#C62828", "#EF6C00", "#00838F", "#6A1B9A")
        val randomColor = colors.random()

        lifecycleScope.launch {
            try {
                // 1. On prépare la requête avec les bonnes infos
                val request = com.example.application.model.SavePathRequest(
                    userId = userId, // Assure-toi d'avoir bien accès à userId ici
                    name = name,
                    hexColor = randomColor,
                    totalPrice = itinerary.totalPrice,
                    totalDuration = itinerary.totalDuration,
                    avgEffort = itinerary.avgEffort,
                    mealIncluded = itinerary.mealIncluded,
                    placeIds = itinerary.steps.map { it.id }
                )

                // 2. On l'envoie au serveur !
                val response = RetrofitInstance.api.savePath(request)

                // 3. On vérifie si le serveur a bien répondu
                if (response.isSuccessful) {
                    Toast.makeText(requireContext(), "Itinéraire sauvegardé avec succès !", Toast.LENGTH_SHORT).show()
                    dismiss() // Ferme le BottomSheet
                } else {
                    Toast.makeText(requireContext(), "Erreur du serveur : ${response.code()}", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Impossible de joindre le serveur : ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // --- Configuration pour que ça prenne 80% de l'écran ---
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) as FrameLayout
            val behavior = BottomSheetBehavior.from(bottomSheet)
            val layoutParams = bottomSheet.layoutParams

            // Prendre 80% de la hauteur de l'écran
            val windowHeight = requireActivity().resources.displayMetrics.heightPixels
            if (layoutParams != null) {
                layoutParams.height = (windowHeight * 0.8).toInt()
            }
            bottomSheet.layoutParams = layoutParams
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        return dialog
    }

    // --- CYCLE DE VIE MAPBOX (TRÈS IMPORTANT) ---
    override fun onStart() {
        super.onStart()
        binding.mapViewDetails.onStart()
    }
    override fun onResume() {
        super.onResume()
        binding.mapViewDetails.onResume()
    }
    override fun onPause() {
        super.onPause()
        binding.mapViewDetails.onPause()
    }
    override fun onStop() {
        super.onStop()
        binding.mapViewDetails.onStop()
    }
    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapViewDetails.onLowMemory()
    }
    override fun onDestroyView() {
        super.onDestroyView()
        binding.mapViewDetails.onDestroy()
        _binding = null
    }
}