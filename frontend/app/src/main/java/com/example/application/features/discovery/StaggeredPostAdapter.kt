package com.example.application.features.discovery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.application.R
import com.example.application.model.Post

class StaggeredPostAdapter(private val onPostClick: (Post) -> Unit) : RecyclerView.Adapter<StaggeredPostAdapter.PostViewHolder>() {

    private var posts = listOf<Post>()

    fun submitList(newList: List<Post>) {
        posts = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_staggered_post, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = posts[position]
        val imageView = holder.itemView.findViewById<ImageView>(R.id.ivPhoto)

        // Affichage de la première photo avec Coil
        post.imageUrls.firstOrNull()?.let { url ->
            imageView.load(url) {
                crossfade(true)
            }
        }

        holder.itemView.setOnClickListener {
            onPostClick(post)
        }
    }

    override fun getItemCount() = posts.size

    class PostViewHolder(view: View) : RecyclerView.ViewHolder(view)
}