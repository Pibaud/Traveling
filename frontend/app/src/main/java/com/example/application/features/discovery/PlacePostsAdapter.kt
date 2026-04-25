package com.example.application.features.discovery

import coil.load
import com.example.application.model.Post

class PlacePostsAdapter : androidx.recyclerview.widget.RecyclerView.Adapter<PlacePostsAdapter.PhotoViewHolder>() {
    private var posts = listOf<Post>()

    fun submitList(newList: List<Post>) {
        posts = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): PhotoViewHolder {
        val view = android.view.LayoutInflater.from(parent.context).inflate(com.example.application.R.layout.item_square_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val post = posts[position]
        val imageView = holder.itemView.findViewById<android.widget.ImageView>(com.example.application.R.id.ivPhoto)

        // On affiche uniquement la première photo du post
        post.imageUrls.firstOrNull()?.let { url ->
            imageView.load(url) { crossfade(true) }
        }
    }

    override fun getItemCount() = posts.size
    class PhotoViewHolder(view: android.view.View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view)
}