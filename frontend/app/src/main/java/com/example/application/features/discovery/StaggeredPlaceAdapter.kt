package com.example.application.features.discovery

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.application.R
import com.example.application.model.Place
import kotlinx.coroutines.*

class StaggeredPlaceAdapter(
    private val scope: CoroutineScope, // Pour lancer les requêtes
    private val fetchCoverPhoto: suspend (String) -> String?, // Fonction magique
    private val onPlaceClick: (Place) -> Unit
) : RecyclerView.Adapter<StaggeredPlaceAdapter.PlaceViewHolder>() {

    private var places = mutableListOf<Place>()

    fun submitList(newList: List<Place>, isAppending: Boolean = false) {
        Log.d("ADAPTER", "submitList → isAppending=$isAppending | newList.size=${newList.size} | places.size avant=${places.size}")

        if (!isAppending) {
            places.clear()
            places.addAll(newList)
            Log.d("ADAPTER", "🔄 Reset complet → ${places.size} items")
            notifyDataSetChanged()
        } else {
            // ⚠️ newList est la liste COMPLÈTE, on prend seulement les nouveaux
            val newItems = newList.drop(places.size)
            Log.d("ADAPTER", "➕ Append → ${newItems.size} nouveaux items (drop ${places.size})")
            val startPos = places.size
            places.addAll(newItems)
            notifyItemRangeInserted(startPos, newItems.size)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = PlaceViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_staggered_place, parent, false)
    )

    override fun onBindViewHolder(holder: PlaceViewHolder, position: Int) {
        val place = places[position]
        holder.itemView.findViewById<TextView>(R.id.tvPlaceName).text = place.name

        val imageView = holder.itemView.findViewById<ImageView>(R.id.ivPhoto)
        imageView.setImageResource(0) // On vide l'ancienne image
        imageView.setBackgroundColor(android.graphics.Color.parseColor("#E0E0E0"))

        // On annule la requête précédente si la vue a été recyclée vite
        holder.fetchJob?.cancel()
        holder.fetchJob = scope.launch {
            val photoUrl = fetchCoverPhoto(place.id)
            if (photoUrl != null) {
                imageView.load(photoUrl) { crossfade(true) }
            }
        }

        holder.itemView.setOnClickListener { onPlaceClick(place) }
    }

    override fun getItemCount() = places.size

    class PlaceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var fetchJob: Job? = null // Permet d'annuler si on scroll très vite
    }
}