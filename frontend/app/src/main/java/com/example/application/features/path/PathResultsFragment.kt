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

        // C'EST ICI QUE TOUT CHANGE : On nomme explicitement les paramètres
        val adapter = ItineraryAdapter(
            items = tempResults,

            onItemClick = { selectedItinerary ->
                // Ce qui se passe quand on clique sur la carte (On garde ton Toast !)
                android.widget.Toast.makeText(
                    requireContext(),
                    "Clic ! Étapes: ${selectedItinerary.steps.size}",
                    android.widget.Toast.LENGTH_LONG
                ).show()

                val detailsSheet = ItineraryDetailsBottomSheet(selectedItinerary)
                detailsSheet.show(parentFragmentManager, "ItineraryDetails")
            },

            onLikeClick = { clickedItinerary ->
                // Ce qui se passe quand on clique sur le cœur des résultats générés
                android.widget.Toast.makeText(
                    requireContext(),
                    "Sauvegardez d'abord le parcours pour l'ajouter à vos favoris !",
                    android.widget.Toast.LENGTH_SHORT
                ).show()

                // Note : On pourrait forcer item.isLiked = false ici dans l'adapter si
                // l'UI optimiste a rendu le cœur rouge à tort, mais pour des parcours temporaires
                // le Toast fait très bien l'affaire pour prévenir l'utilisateur.
            }
        )

        binding.rvResults.adapter = adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}