package com.example.application.features.path

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import com.example.application.R
import com.example.application.databinding.FragmentCreatePathBinding
import com.example.application.model.GeneratePathRequest
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch

class CreatePathFragment : Fragment(R.layout.fragment_create_path) {
    private var _binding: FragmentCreatePathBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCreatePathBinding.bind(view)

        setupDynamicVisuals()

        binding.btnGenerate.setOnClickListener {
            val request = GeneratePathRequest(
                categories = getDbMappedActivities(), // 👈 Utilisation de la nouvelle fonction de mapping
                selectedPlaceIds = emptyList(),
                budgetMax = binding.etBudget.text.toString().toIntOrNull() ?: 100,
                durationHours = getSelectedDurationInHours(),
                effortLevel = binding.sliderEffort.value.toInt(),
                weatherTolerance = binding.sliderWeather.value.toInt(),
                mealIncluded = binding.radioMealYes.isChecked // 👈 On récupère le "Oui" ou "Non"
            )

            lifecycleScope.launch {
                try {
                    val response = RetrofitInstance.api.generatePath(request)
                    if (response.isSuccessful) {
                        val itineraries = response.body() ?: emptyList()

                        val isResponseEmpty = itineraries.isEmpty() || itineraries.all { it.steps.isEmpty() }

                        if (isResponseEmpty) {
                            Toast.makeText(
                                requireContext(),
                                "Paramètres de générations non compatibles veuillez changer de paramètres pour avoir des résultats",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            PathResultsFragment.tempResults = itineraries
                            findNavController().navigate(R.id.action_createPath_to_results)
                        }
                    } else {
                        Toast.makeText(requireContext(), "Erreur serveur : ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(requireContext(), "Erreur de connexion", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // --- Fonction pour l'animation des Emojis ---
    private fun setupDynamicVisuals() {
        // Initialisation par défaut
        binding.tvEffortIcon.text = "🚶"
        binding.tvEffortText.text = "Balade tranquille"
        binding.tvWeatherIcon.text = "☀️"
        binding.tvWeatherText.text = "Grand soleil"

        // Quand on bouge le curseur d'effort
        binding.sliderEffort.addOnChangeListener { _, value, _ ->
            when (value.toInt()) {
                1 -> {
                    binding.tvEffortIcon.text = "🚶"
                    binding.tvEffortText.text = "Balade tranquille"
                }
                2 -> {
                    binding.tvEffortIcon.text = "🥾"
                    binding.tvEffortText.text = "Marche active"
                }
                3 -> {
                    binding.tvEffortIcon.text = "🧗"
                    binding.tvEffortText.text = "Sportif intense"
                }
            }
        }

        // Quand on bouge le curseur météo
        binding.sliderWeather.addOnChangeListener { _, value, _ ->
            when (value.toInt()) {
                0 -> {
                    binding.tvWeatherIcon.text = "☀️"
                    binding.tvWeatherText.text = "Grand soleil uniquement"
                }
                1 -> {
                    binding.tvWeatherIcon.text = "⛅"
                    binding.tvWeatherText.text = "Grisaille tolérée"
                }
                2 -> {
                    binding.tvWeatherIcon.text = "🌧️"
                    binding.tvWeatherText.text = "Pluie ? Pas un problème !"
                }
            }
        }
    }

    // --- Fonction pour convertir les choix de durée en Int (Heures) ---
    private fun getSelectedDurationInHours(): Int {
        return when (binding.chipGroupDuration.checkedChipId) {
            R.id.chipDur1h -> 1
            R.id.chipDur2h -> 2
            R.id.chipDur3h -> 3
            R.id.chipDurHalf -> 4  // Considérons 4h pour une demi-journée
            R.id.chipDurDay -> 24  // 24h pour 1 journée
            R.id.chipDurWeekend -> 48 // 48h pour un week-end
            else -> 1 // Valeur par défaut de sécurité
        }
    }

    private fun getDbMappedActivities(): List<String> {
        return binding.chipGroupActivities.checkedChipIds.mapNotNull { id ->
            when (id) {
                R.id.chipCulture -> "CULTURE"
                R.id.chipNature -> "DECOUVERTE"
                R.id.chipSport -> "LOISIRS"
                else -> null
            }
        }
    }

    private fun getSelectedActivities(): List<String> {
        return binding.chipGroupActivities.checkedChipIds.map { id ->
            binding.root.findViewById<Chip>(id).text.toString()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}