package com.example.application.features.path

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.application.R
import com.example.application.databinding.FragmentPathResultsBinding
import com.example.application.model.ItineraryResponse

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

        binding.rvResults.layoutManager = LinearLayoutManager(requireContext())
        binding.rvResults.adapter = ItineraryAdapter(tempResults)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}