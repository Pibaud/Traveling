package com.example.application.features.path

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.application.R
import com.example.application.model.ItineraryResponse // Vérifie bien cet import selon ton projet !

// Petite structure pour stocker le titre et la liste de la catégorie
data class CategoryRow(
    val title: String,
    val itineraries: List<ItineraryResponse>
)

class CategoryAdapter(
    private val categories: List<CategoryRow>,
    private val onItemClick: (ItineraryResponse) -> Unit,
    private val onLikeClick: (ItineraryResponse) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

    // L'ARME SECRÈTE : La piscine de mémoire partagée !
    // Toutes les catégories vont réutiliser les mêmes cartes (cartes graphiques de tes itinéraires) au lieu d'en recréer.
    private val viewPool = RecyclerView.RecycledViewPool()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvCategoryTitle)
        val rvHorizontal: RecyclerView = view.findViewById(R.id.rvHorizontalItineraries)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_category_row, parent, false)
        val holder = ViewHolder(view)

        // On donne accès à la piscine partagée à la liste horizontale
        holder.rvHorizontal.setRecycledViewPool(viewPool)
        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = categories[position]

        holder.tvTitle.text = category.title

        // On réutilise ton bon vieux ItineraryAdapter pour la liste horizontale !
        holder.rvHorizontal.adapter = ItineraryAdapter(
            items = category.itineraries,
            onItemClick = onItemClick,
            onLikeClick = onLikeClick
        )
    }

    override fun getItemCount() = categories.size
}