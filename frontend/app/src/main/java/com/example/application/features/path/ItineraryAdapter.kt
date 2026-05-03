package com.example.application.features.path

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.application.R
import com.example.application.model.ItineraryResponse
import com.google.android.material.card.MaterialCardView

class ItineraryAdapter(
    private val items: List<ItineraryResponse>,
    private val onItemClick: (ItineraryResponse) -> Unit,
    private val onLikeClick: (ItineraryResponse) -> Unit
) : RecyclerView.Adapter<ItineraryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.cardItinerary)
        val name: TextView = view.findViewById(R.id.tvItineraryName)
        val price: TextView = view.findViewById(R.id.tvPrice)
        val duration: TextView = view.findViewById(R.id.tvDuration)
        val meal: TextView = view.findViewById(R.id.tvMeal)
        val effort: TextView = view.findViewById(R.id.tvEffort)

        // Nouvelles vues pour la mosaïque
        val row1: View = view.findViewById(R.id.row1)
        val row2: View = view.findViewById(R.id.row2)
        val ivCover1: ImageView = view.findViewById(R.id.ivCover1)
        val ivCover2: ImageView = view.findViewById(R.id.ivCover2)
        val ivCover3: ImageView = view.findViewById(R.id.ivCover3)
        val ivCover4: ImageView = view.findViewById(R.id.ivCover4)
        val ivLike: ImageView = view.findViewById(R.id.ivLike)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_itinerary, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.name.text = item.name
        holder.price.text = "${item.totalPrice} €"
        holder.duration.text = "⏱ Durée : ${item.totalDuration}h"
        holder.meal.text = if (item.mealIncluded) "🍽 Repas compris" else "🍽 Repas non compris"
        holder.effort.text = "💪 Effort : ${item.avgEffort}/5"

        // On met toujours la couleur de fond générée (ça sert si 0 image, ou pendant le chargement)
        try {
            holder.card.setCardBackgroundColor(Color.parseColor(item.hexColor))
        } catch (e: Exception) {
            holder.card.setCardBackgroundColor(Color.DKGRAY)
        }

        // --- LA LOGIQUE DE LA MOSAÏQUE (0 à 4 photos) ---
        val images = item.coverImages ?: emptyList()
        val count = images.size

        // On masque les lignes si on n'a pas assez d'images
        holder.row1.visibility = if (count > 0) View.VISIBLE else View.GONE
        holder.row2.visibility = if (count > 2) View.VISIBLE else View.GONE

        // On masque les cases si on n'a pas assez d'images
        holder.ivCover1.visibility = if (count > 0) View.VISIBLE else View.GONE
        holder.ivCover2.visibility = if (count > 1) View.VISIBLE else View.GONE
        holder.ivCover3.visibility = if (count > 2) View.VISIBLE else View.GONE
        holder.ivCover4.visibility = if (count > 3) View.VISIBLE else View.GONE

        // On charge les images avec Glide
        val imageViews = listOf(holder.ivCover1, holder.ivCover2, holder.ivCover3, holder.ivCover4)
        for (i in 0 until minOf(4, count)) {
            Glide.with(holder.itemView.context)
                .load(images[i])
                .centerCrop()
                .into(imageViews[i])
        }

        if (item.isLiked) {
            holder.ivLike.setImageResource(R.drawable.ic_heart_filled)
            holder.ivLike.setColorFilter(Color.RED) // Cœur rouge si liké
        } else {
            holder.ivLike.setImageResource(R.drawable.ic_heart_empty)
            holder.ivLike.setColorFilter(Color.WHITE) // Cœur blanc vide sinon
        }

        // 2. Action au clic sur le cœur (UI Optimiste)
        holder.ivLike.setOnClickListener {
            // On inverse l'état localement tout de suite
            item.isLiked = !item.isLiked

            // On met à jour le visuel instantanément
            if (item.isLiked) {
                holder.ivLike.setImageResource(R.drawable.ic_heart_filled)
                holder.ivLike.setColorFilter(Color.RED)
            } else {
                holder.ivLike.setImageResource(R.drawable.ic_heart_empty)
                holder.ivLike.setColorFilter(Color.WHITE)
            }

            // On prévient le Fragment pour qu'il fasse l'appel réseau
            onLikeClick(item)
        }

        holder.card.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount() = items.size
}