package com.example.application.features.onboarding

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.application.databinding.ItemGridImageBinding

class ImageGridAdapter(private val imageUrls: List<String>) :
    RecyclerView.Adapter<ImageGridAdapter.GridViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GridViewHolder {
        val binding = ItemGridImageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GridViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GridViewHolder, position: Int) {
        // L'astuce du modulo (%) permet de boucler à l'infini sur notre petite liste
        val actualPosition = position % imageUrls.size
        holder.binding.ivGridPhoto.load(imageUrls[actualPosition]) {
            crossfade(true)
        }
    }

    // On renvoie un chiffre immense pour simuler un scroll infini
    override fun getItemCount(): Int = Int.MAX_VALUE

    class GridViewHolder(val binding: ItemGridImageBinding) : RecyclerView.ViewHolder(binding.root)
}