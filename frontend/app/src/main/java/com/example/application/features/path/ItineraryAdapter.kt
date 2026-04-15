package com.example.application.features.path

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.application.R
import com.example.application.models.ItineraryResponse
import com.google.android.material.card.MaterialCardView

class ItineraryAdapter(private val items: List<ItineraryResponse>) :
    RecyclerView.Adapter<ItineraryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.cardItinerary)
        val name: TextView = view.findViewById(R.id.tvItineraryName)
        val price: TextView = view.findViewById(R.id.tvPrice)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_itinerary, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        holder.price.text = "${item.totalPrice} €"
        try {
            val color = Color.parseColor(item.hexColor)
            holder.card.setStrokeColor(ColorStateList.valueOf(color))
            holder.card.setCardBackgroundColor(color)
        } catch (e: Exception) {}
    }

    override fun getItemCount() = items.size
}