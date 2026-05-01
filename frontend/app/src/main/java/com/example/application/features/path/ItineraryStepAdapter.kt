package com.example.application.features.path

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.application.R
import com.example.application.model.Place
import com.google.android.material.card.MaterialCardView

class ItineraryStepAdapter(
    private val steps: List<Place>,
    private val onStepClick: (Place) -> Unit
) : RecyclerView.Adapter<ItineraryStepAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.cardStep)
        val tvStepNumber: TextView = view.findViewById(R.id.tvStepNumber)
        val tvStepName: TextView = view.findViewById(R.id.tvStepName)
        val tvStepCategory: TextView = view.findViewById(R.id.tvStepCategory)
        val tvStepDuration: TextView = view.findViewById(R.id.tvStepDuration)
        val tvStepPrice: TextView = view.findViewById(R.id.tvStepPrice) // Added Price
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_itinerary_step, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val place = steps[position]

        holder.tvStepNumber.text = "ÉTAPE ${position + 1}"
        holder.tvStepName.text = place.name
        holder.tvStepCategory.text = place.category.name.lowercase().replaceFirstChar { it.uppercase() }

        holder.tvStepDuration.text = "⏳ ${place.duration}h"

        // Point 2: Format and show price
        val priceText = if (place.price == 0) "Gratuit" else "${place.price} €"
        holder.tvStepPrice.text = "💶 $priceText"

        holder.card.setCardBackgroundColor(Color.parseColor("#F8F9FA"))

        holder.itemView.setOnClickListener {
            // Point 3: This will now trigger the print instead of moving the camera
            onStepClick(place)
        }
    }

    override fun getItemCount() = steps.size
}