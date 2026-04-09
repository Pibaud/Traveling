package com.example.application.utils

import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import com.example.application.model.Place
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import kotlinx.coroutines.*
import TravelingApiService

/**
 * Fonction magique applicable sur n'importe quel MaterialAutoCompleteTextView de l'app.
 */
fun MaterialAutoCompleteTextView.setupPlaceAutocomplete(
    coroutineScope: CoroutineScope,
    apiService: TravelingApiService,
    onPlaceSelected: (Place) -> Unit
) {
    var searchJob: Job? = null
    var currentPlaces = listOf<Place>()

    // Adaptateur simple pour afficher les noms dans la liste déroulante
    val adapter = ArrayAdapter<String>(context, android.R.layout.simple_dropdown_item_1line, mutableListOf())
    this.setAdapter(adapter)

    this.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable?) {
            val query = s?.toString()?.trim() ?: ""

            searchJob?.cancel() // Annule la recherche précédente si l'utilisateur tape vite

            if (query.length < 2) {
                adapter.clear()
                return
            }

            // Démarre une nouvelle recherche avec un léger délai (Debounce)
            searchJob = coroutineScope.launch(Dispatchers.Main) {
                delay(300) // Attend 300ms après la dernière frappe avant d'appeler l'API
                try {
                    currentPlaces = apiService.searchPlacesByName(query)
                    adapter.clear()
                    adapter.addAll(currentPlaces.map { it.name })
                    adapter.notifyDataSetChanged()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    })

    // Quand l'utilisateur clique sur une suggestion de la liste
    this.setOnItemClickListener { _, _, position, _ ->
        val selectedPlace = currentPlaces.getOrNull(position)
        selectedPlace?.let {
            onPlaceSelected(it)
            // Facultatif: On cache le clavier après sélection
            val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(this.windowToken, 0)
        }
    }
}