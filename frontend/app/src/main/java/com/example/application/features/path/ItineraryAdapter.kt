package com.example.application.features.path

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.application.R
import com.example.application.model.ItineraryResponse
import com.google.android.material.card.MaterialCardView

class ItineraryAdapter(private val items: List<ItineraryResponse>) :
    RecyclerView.Adapter<ItineraryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.cardItinerary)
        val name: TextView = view.findViewById(R.id.tvItineraryName)
        val price: TextView = view.findViewById(R.id.tvPrice)
        val duration: TextView = view.findViewById(R.id.tvDuration)
        val meal: TextView = view.findViewById(R.id.tvMeal)
        val effort: TextView = view.findViewById(R.id.tvEffort)
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

        try {
            val color = Color.parseColor(item.hexColor)
            holder.card.setCardBackgroundColor(color)
        } catch (e: Exception) {
            holder.card.setCardBackgroundColor(Color.DKGRAY)
        }

        holder.card.setOnClickListener {
            val bottomSheet = ItineraryDetailsBottomSheet(item)
            // Il faut passer le fragment manager de l'activité, on verra comment faire proprement !
        }
    }

    override fun getItemCount() = items.size
}