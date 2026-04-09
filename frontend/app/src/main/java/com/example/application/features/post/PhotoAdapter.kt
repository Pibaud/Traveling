package com.example.application.features.post

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load // On utilise Coil pour charger les images proprement
import com.example.application.databinding.ItemPhotoCreateBinding

class PhotoAdapter(
    private val photos: MutableList<String>,
    private val onRemoveClick: (Int) -> Unit
) : RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {

    inner class PhotoViewHolder(val binding: ItemPhotoCreateBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemPhotoCreateBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photoUri = photos[position]

        // Chargement de l'image (si c'est un URI réel)
        holder.binding.ivPhoto.load(photoUri) {
            crossfade(true)
            placeholder(android.R.color.darker_gray)
        }

        holder.binding.btnRemove.setOnClickListener {
            onRemoveClick(holder.adapterPosition)
        }
    }

    override fun getItemCount() = photos.size
}