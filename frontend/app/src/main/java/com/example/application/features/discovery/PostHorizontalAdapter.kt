package com.example.application.features.discovery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.application.R
import com.example.application.model.Post

class PostHorizontalAdapter(
    private val onPostClick: (Post) -> Unit
) : RecyclerView.Adapter<PostHorizontalAdapter.HorizontalViewHolder>() {

    private var posts = mutableListOf<Post>()

    fun submitList(newList: List<Post>, isAppending: Boolean = false) {
        if (!isAppending) {
            posts.clear()
            posts.addAll(newList)
            notifyDataSetChanged()
        } else {
            val newItems = newList.drop(posts.size)
            val startPos = posts.size
            posts.addAll(newItems)
            notifyItemRangeInserted(startPos, newItems.size)
        }
    }

    fun getPostAt(position: Int): Post = posts[position]

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HorizontalViewHolder {
        // On recycle ton XML existant !
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_place_horizontal_card, parent, false)
        return HorizontalViewHolder(view)
    }

    override fun onBindViewHolder(holder: HorizontalViewHolder, position: Int) {
        val post = posts[position]

        // Le titre de la carte devient le nom du lieu
        holder.tvName.text = post.place.name

        // La catégorie devient un bout de la description du post (ou "Posté le...")
        val desc = post.description.takeIf { it.isNotBlank() } ?: "Posté par ${post.authorName}"
        holder.tvCategory.text = if (desc.length > 35) desc.take(35) + "..." else desc

        holder.ivPhoto.setImageResource(0)
        holder.ivPhoto.setBackgroundColor(android.graphics.Color.parseColor("#F5F5F5"))

        post.imageUrls.firstOrNull()?.let { photoUrl ->
            holder.ivPhoto.load(photoUrl) {
                crossfade(true)
                placeholder(android.R.color.darker_gray)
            }
        }

        holder.itemView.setOnClickListener { onPostClick(post) }
    }

    override fun getItemCount() = posts.size

    class HorizontalViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPhoto: ImageView = view.findViewById(R.id.ivPhoto)
        val tvName: TextView = view.findViewById(R.id.tvCardName)
        val tvCategory: TextView = view.findViewById(R.id.tvCardCategory)
    }
}