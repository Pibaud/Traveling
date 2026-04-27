package com.example.application.features.path

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.application.R
import com.example.application.databinding.FragmentPathResultsBinding
import com.example.application.model.ItineraryResponse
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.navigation.fragment.findNavController

class PathResultsFragment : Fragment(R.layout.fragment_path_results) {
    private var _binding: FragmentPathResultsBinding? = null
    private val binding get() = _binding!!

    // On simulera le passage de données via une liste statique ou un ViewModel pour l'instant
    companion object {
        var tempResults: List<ItineraryResponse> = emptyList()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentPathResultsBinding.bind(view)

        val firstResult = tempResults.firstOrNull()
        if (firstResult?.errorMessage != null) {
            // Afficher une alerte ou un texte d'erreur
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Oups !")
                .setMessage(firstResult.errorMessage)
                .setPositiveButton("Modifier mes choix") { _, _ -> findNavController().popBackStack() }
                .show()
        } else {
            setupRecyclerView()
        }
    }

    private fun setupRecyclerView() {
        binding.rvResults.layoutManager = LinearLayoutManager(requireContext())

        val adapter = ItineraryAdapter(tempResults) { selectedItinerary ->
            android.widget.Toast.makeText(
                requireContext(),
                "Clic ! Étapes: ${selectedItinerary.steps.size}",
                android.widget.Toast.LENGTH_LONG
            ).show()

            val detailsSheet = ItineraryDetailsBottomSheet(selectedItinerary)
            detailsSheet.show(parentFragmentManager, "ItineraryDetails")
        }

        binding.rvResults.adapter = adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}