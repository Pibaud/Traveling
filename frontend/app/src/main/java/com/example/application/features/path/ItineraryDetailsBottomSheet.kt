package com.example.application.features.path

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.example.application.R
import com.example.application.databinding.FragmentItineraryDetailsSheetBinding
import com.example.application.models.ItineraryResponse
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ItineraryDetailsBottomSheet(
    private val itinerary: ItineraryResponse
) : BottomSheetDialogFragment() {

    private var _binding: FragmentItineraryDetailsSheetBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentItineraryDetailsSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialisation de la carte Mapbox (OBLIGATOIRE)
        binding.mapViewDetails.onCreate(savedInstanceState)
        binding.mapViewDetails.getMapAsync { mapboxMap ->
            // TODO: Centrer la carte et afficher le tracé
        }

        // Remplissage des données
        binding.tvDetailName.text = itinerary.name
        binding.tvDetailPrice.text = "${itinerary.totalPrice} €"
        binding.tvDetailInfos.text = "${itinerary.totalDuration} heures\n" +
                (if (itinerary.mealIncluded) "Repas compris" else "Repas non compris")

        try {
            binding.cvHeader.setCardBackgroundColor(Color.parseColor(itinerary.hexColor))
        } catch (e: Exception) {}

        // Gestion des boutons
        binding.btnSave.setOnClickListener {
            // TODO: Afficher la popup de sauvegarde (nom, couleur)
            dismiss()
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