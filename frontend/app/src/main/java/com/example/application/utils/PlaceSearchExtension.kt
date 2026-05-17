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

    // 👇 1. CRÉATION D'UN ADAPTER PERSONNALISÉ SANS FILTRE LOCAL
    val adapter = object : ArrayAdapter<String>(context, android.R.layout.simple_dropdown_item_1line, mutableListOf()) {
        override fun getFilter(): android.widget.Filter {
            return object : android.widget.Filter() {
                // On désactive le filtre natif d'Android : on accepte TOUT ce que le backend envoie
                override fun performFiltering(constraint: CharSequence?): FilterResults {
                    val results = FilterResults()
                    results.values = currentPlaces.map { it.name }
                    results.count = currentPlaces.size
                    return results
                }

                override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                    notifyDataSetChanged()
                }
            }
        }
    }

    this.setAdapter(adapter)

    this.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable?) {
            val query = s?.toString()?.trim() ?: ""

            searchJob?.cancel() // Annule la recherche précédente si l'utilisateur tape vite

            if (query.length < 2) {
                currentPlaces = emptyList() // On vide aussi notre liste de référence
                adapter.clear()
                return
            }

            // Démarre une nouvelle recherche avec un léger délai (Debounce)
            searchJob = coroutineScope.launch(Dispatchers.Main) {
                delay(300) // Attend 300ms après la dernière frappe avant d'appeler l'API
                try {
                    currentPlaces = apiService.searchPlacesByName(query)

                    adapter.clear()
                    if (currentPlaces.isNotEmpty()) {
                        adapter.addAll(currentPlaces.map { it.name })
                        adapter.notifyDataSetChanged()

                        // 👇 2. FORCER L'AFFICHAGE (Si le champ a toujours le focus)
                        if (hasFocus()) {
                            showDropDown()
                        }
                    } else {
                        adapter.notifyDataSetChanged()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    })

    // Quand l'utilisateur clique sur une suggestion de la liste
    this.setOnItemClickListener { _, _, position, _ ->
        // 👇 1. ANNULATION DE LA RECHERCHE FANTÔME
        // On annule le job coroutine qui vient d'être déclenché par l'auto-remplissage du texte
        searchJob?.cancel()

        val selectedPlace = currentPlaces.getOrNull(position)
        selectedPlace?.let {
            onPlaceSelected(it)

            // 👇 2. NETTOYAGE VISUEL
            // On vide l'adapter et on force la fermeture du menu
            adapter.clear()
            adapter.notifyDataSetChanged()
            dismissDropDown()

            // On cache le clavier après sélection
            val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(this.windowToken, 0)
        }
    }
}