package com.example.application.features.path

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.application.databinding.FragmentCreatePathBinding
import com.example.application.model.GeneratePathRequest
import androidx.lifecycle.lifecycleScope
import com.example.application.R
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch
import androidx.navigation.fragment.findNavController


class CreatePathFragment : Fragment(R.layout.fragment_create_path) {
    private var _binding: FragmentCreatePathBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCreatePathBinding.bind(view)

        binding.btnGenerate.setOnClickListener {
            val request = GeneratePathRequest(
                activities = getSelectedActivities(),
                budgetMax = binding.etBudget.text.toString().toIntOrNull() ?: 100,
                durationDays = binding.sliderDuration.value.toInt(),
                effortLevel = binding.ratingEffort.rating.toInt(),
                weatherTolerance = binding.sliderWeather.value.toInt()
            )

            lifecycleScope.launch {
                val response = RetrofitInstance.api.generatePath(request)
                    if (response.isSuccessful) {
                        PathResultsFragment.tempResults = response.body() ?: emptyList()
                        findNavController().navigate(R.id.action_createPath_to_results)
                    }
            }
        }
    }

    private fun getSelectedActivities(): List<String> {
        return binding.chipGroupActivities.checkedChipIds.map { id ->
            binding.root.findViewById<Chip>(id).text.toString()
        }
    }
}