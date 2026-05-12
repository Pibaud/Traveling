package com.example.application.features.places

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.application.R
import com.example.application.model.Place

class LikedPlacesAdapter(
    private var places: List<Place>,
    private val onPlaceClick: (Place) -> Unit
) : RecyclerView.Adapter<LikedPlacesAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // On récupère bien les deux éléments de TON fichier xml
        val tvName: TextView = view.findViewById(R.id.tvPlaceName)
        val ivThumbnail: ImageView = view.findViewById(R.id.ivPlaceThumbnail)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // On utilise ton fichier item_liked_place_thumbnail.xml
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_liked_place_thumbnail, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val place = places[position]

        // 1. On met le nom du lieu
        holder.tvName.text = place.name

        // 2. On choisit la bonne icône en fonction de la catégorie du lieu
        val iconRes = when (place.category.name.uppercase()) {
            "CULTURE" -> R.drawable.round_culture_24
            "RESTAURATION" -> R.drawable.round_restaurant_24
            "LOISIRS" -> R.drawable.round_loisirs_24
            "DECOUVERTE" -> R.drawable.round_decouverte_24
            else -> R.drawable.round_decouverte_24
        }

        // 3. On applique l'icône et on l'ajuste pour qu'elle rende bien au centre
        holder.ivThumbnail.setImageResource(iconRes)
        holder.ivThumbnail.scaleType = ImageView.ScaleType.CENTER_INSIDE

        // (Optionnel) Si tu veux teinter l'icône avec ta couleur primaire :
        // holder.ivThumbnail.setColorFilter(android.graphics.Color.parseColor("#7A1C2A"))

        // 4. On gère le clic sur tout l'élément
        holder.itemView.setOnClickListener { onPlaceClick(place) }
    }

    override fun getItemCount() = places.size

    fun updateData(newPlaces: List<Place>) {
        places = newPlaces
        notifyDataSetChanged()
    }
}