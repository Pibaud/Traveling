package com.example.application.features.discovery

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

class PlaceHorizontalAdapter(
    private val scope: CoroutineScope,
    private val fetchCoverPhoto: suspend (String) -> String?,
    private val onPlaceClick: (Place) -> Unit
) : RecyclerView.Adapter<PlaceHorizontalAdapter.HorizontalViewHolder>() {

    private var places = mutableListOf<Place>()

    fun submitList(newList: List<Place>, isAppending: Boolean = false) {
        if (!isAppending) {
            places.clear()
            places.addAll(newList)
            notifyDataSetChanged()
        } else {
            val newItems = newList.drop(places.size)  // 👈 Même fix
            val startPos = places.size
            places.addAll(newItems)
            notifyItemRangeInserted(startPos, newItems.size)
        }
    }

    fun getPlaceAt(position: Int): Place = places[position]

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HorizontalViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_place_horizontal_card, parent, false)
        return HorizontalViewHolder(view)
    }

    override fun onBindViewHolder(holder: HorizontalViewHolder, position: Int) {
        val place = places[position]

        // Liaison des textes
        holder.tvName.text = place.name
        holder.tvCategory.text = place.category.name.lowercase().replaceFirstChar { it.uppercase() }

        // Réinitialisation de l'image (évite les clignotements au recyclage)
        holder.ivPhoto.setImageResource(0)
        holder.ivPhoto.setBackgroundColor(android.graphics.Color.parseColor("#F5F5F5"))

        // Gestion du chargement asynchrone de la photo de couverture
        holder.fetchJob?.cancel()
        holder.fetchJob = scope.launch {
            val photoUrl = fetchCoverPhoto(place.id)
            if (photoUrl != null) {
                holder.ivPhoto.load(photoUrl) {
                    crossfade(true)
                    placeholder(android.R.color.darker_gray)
                }
            }
        }

        holder.itemView.setOnClickListener { onPlaceClick(place) }
    }

    override fun getItemCount() = places.size

    class HorizontalViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPhoto: ImageView = view.findViewById(R.id.ivPhoto)
        val tvName: TextView = view.findViewById(R.id.tvCardName)
        val tvCategory: TextView = view.findViewById(R.id.tvCardCategory)
        var fetchJob: Job? = null
    }
}