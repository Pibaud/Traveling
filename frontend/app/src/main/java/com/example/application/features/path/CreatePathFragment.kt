package com.example.application.features.path

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.example.application.R
import com.example.application.databinding.FragmentCreatePathBinding
import com.example.application.features.places.LikedPlacesAdapter
import com.example.application.features.places.PlaceDetailsBottomSheet
import com.example.application.model.GeneratePathRequest
import com.example.application.model.Place
import com.example.application.model.RetrofitInstance
import com.google.android.material.chip.Chip
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class CreatePathFragment : Fragment(R.layout.fragment_create_path) {

    companion object {
        var draftRequest: GeneratePathRequest? = null
        var draftPlaces: List<Place>? = null
    }

    private var _binding: FragmentCreatePathBinding? = null
    private val binding get() = _binding!!

    private val currentForcedPlaces = mutableListOf<Place>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCreatePathBinding.bind(view)

        // --- 1. GESTION DES LIEUX IMPOSÉS ---
        currentForcedPlaces.clear()
        binding.chipGroupForcedPlaces.removeAllViews()

        arguments?.getString("forcedPlaceId")?.let { id ->
            val name = arguments?.getString("forcedPlaceName") ?: "Ce lieu"
            currentForcedPlaces.add(Place(id = id, name = name))
        }

        draftPlaces?.let { places ->
            currentForcedPlaces.addAll(places)
            draftPlaces = null
        }

        if (currentForcedPlaces.isNotEmpty()) {
            binding.llForcedPlaceIndicator.visibility = View.VISIBLE
            currentForcedPlaces.forEach { place ->
                addPlaceChip(place)
            }
        } else {
            binding.llForcedPlaceIndicator.visibility = View.GONE
        }

        // --- 2. GESTION DES LIEUX FAVORIS ---
        val userId = Firebase.auth.currentUser?.uid
        val likedPlacesAdapter = LikedPlacesAdapter(emptyList()) { selectedPlace ->
            // On vérifie que le lieu n'est pas déjà dans la bulle d'imposition
            if (!currentForcedPlaces.any { it.id == selectedPlace.id }) {
                currentForcedPlaces.add(selectedPlace)
                binding.llForcedPlaceIndicator.visibility = View.VISIBLE
                addPlaceChip(selectedPlace)
            }
        }
        binding.rvLikedPlaces.adapter = likedPlacesAdapter

        if (userId != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val response = RetrofitInstance.api.getLikedPlaces(userId)
                    if (response.isSuccessful) {
                        val places = response.body() ?: emptyList()
                        if (places.isNotEmpty()) {
                            binding.tvLikedPlacesTitle.visibility = View.VISIBLE
                            binding.rvLikedPlaces.visibility = View.VISIBLE
                            likedPlacesAdapter.updateData(places)
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }

        // --- 3. CONFIGURATION DES VISUELS (Heure, curseurs...) ---
        setupDynamicVisuals()

        binding.etStartTime.setOnClickListener {
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(9)
                .setMinute(30)
                .setTitleText("Heure de départ")
                .build()

            picker.addOnPositiveButtonClickListener {
                val pickedTime = String.format("%02d:%02d", picker.hour, picker.minute)
                binding.etStartTime.setText(pickedTime)
            }
            picker.show(parentFragmentManager, "TIME_PICKER")
        }

        draftRequest?.let { request ->
            binding.etBudget.setText(request.budgetMax.toString())

            val h = (request.startTimeMinutes / 60) % 24
            val m = request.startTimeMinutes % 60
            binding.etStartTime.setText(String.format("%02d:%02d", h, m))

            binding.sliderEffort.value = request.effortLevel.toFloat()
            binding.sliderWeather.value = request.weatherTolerance.toFloat()

            if (request.mealIncluded) {
                binding.radioMealYes.isChecked = true
            } else {
                binding.radioMealNo.isChecked = true
            }

            binding.chipCulture.isChecked = request.categories.contains("CULTURE")
            binding.chipDecouverte.isChecked = request.categories.contains("DECOUVERTE")
            binding.chipLoisirs.isChecked = request.categories.contains("LOISIRS")

            val durationId = when (request.durationHours) {
                1 -> R.id.chipDur1h
                2 -> R.id.chipDur2h
                3 -> R.id.chipDur3h
                4 -> R.id.chipDurHalf
                24 -> R.id.chipDurDay
                48 -> R.id.chipDurWeekend
                else -> R.id.chipDur1h
            }
            binding.chipGroupDuration.check(durationId)

            draftRequest = null
        }

        // --- 4. ACTION DU BOUTON GÉNÉRER ---
        binding.btnGenerate.setOnClickListener {
            val selectedIds = currentForcedPlaces.map { it.id }

            val timeString = binding.etStartTime.text.toString()
            var startMinutes = 9 * 60 + 30
            if (timeString.isNotBlank()) {
                val timeParts = timeString.split(":")
                if (timeParts.size == 2) {
                    val h = timeParts[0].toIntOrNull() ?: 9
                    val m = timeParts[1].toIntOrNull() ?: 30
                    startMinutes = h * 60 + m
                }
            }

            val request = GeneratePathRequest(
                categories = getDbMappedActivities(),
                selectedPlaceIds = selectedIds,
                budgetMax = binding.etBudget.text.toString().toIntOrNull() ?: 100,
                durationHours = getSelectedDurationInHours(),
                effortLevel = binding.sliderEffort.value.toInt(),
                weatherTolerance = binding.sliderWeather.value.toInt(),
                mealIncluded = binding.radioMealYes.isChecked,
                startTimeMinutes = startMinutes
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
                                "Paramètres de génération non compatibles, veuillez modifier vos choix.",
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

    private fun addPlaceChip(place: Place) {
        val chip = Chip(requireContext()).apply {
            text = place.name
            isCloseIconVisible = true

            setOnCloseIconClickListener {
                binding.chipGroupForcedPlaces.removeView(this)
                currentForcedPlaces.remove(place)
                if (currentForcedPlaces.isEmpty()) {
                    binding.llForcedPlaceIndicator.visibility = View.GONE
                }
            }

            setOnLongClickListener {
                val placeDetailsSheet = PlaceDetailsBottomSheet(place)
                placeDetailsSheet.show(parentFragmentManager, "PlaceDetailsSheet")
                true
            }
        }
        binding.chipGroupForcedPlaces.addView(chip)
    }

    private fun setupDynamicVisuals() {
        binding.tvEffortIcon.text = "🚶"
        binding.tvEffortText.text = "Balade tranquille"
        binding.tvWeatherIcon.text = "☀️"
        binding.tvWeatherText.text = "Grand soleil"

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

    private fun getSelectedDurationInHours(): Int {
        return when (binding.chipGroupDuration.checkedChipId) {
            R.id.chipDur1h -> 1
            R.id.chipDur2h -> 2
            R.id.chipDur3h -> 3
            R.id.chipDurHalf -> 4
            R.id.chipDurDay -> 24
            R.id.chipDurWeekend -> 48
            else -> 1
        }
    }

    private fun getDbMappedActivities(): List<String> {
        return binding.chipGroupActivities.checkedChipIds.mapNotNull { id ->
            when (id) {
                R.id.chipCulture -> "CULTURE"
                R.id.chipDecouverte -> "DECOUVERTE"
                R.id.chipLoisirs -> "LOISIRS"
                else -> null
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}