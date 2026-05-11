package com.example.application.features.social

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.application.R
import com.example.application.databinding.ItemGroupPostBinding
import com.example.application.model.Post // Assure-toi d'avoir ce modèle

class GroupPostAdapter : RecyclerView.Adapter<GroupPostAdapter.PostViewHolder>() {

    private var posts: List<Post> = emptyList()

    fun submitList(newList: List<Post>) {
        posts = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = ItemGroupPostBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PostViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(posts[position])
    }

    override fun getItemCount() = posts.size

    inner class PostViewHolder(private val binding: ItemGroupPostBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(post: Post) {
            binding.tvAuthorName.text = post.authorName
            binding.tvDescription.text = post.description
            binding.tvLikesCount.text = post.likesCount.toString()

            // Calcul du temps écoulé (ex: "Il y a 2h")
            val timeAgo = DateUtils.getRelativeTimeSpanString(
                post.timestamp,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            )
            binding.tvTimestamp.text = timeAgo

            // Chargement de l'avatar de l'auteur
            binding.ivAuthorAvatar.load(post.authorAvatarUrl) {
                placeholder(R.drawable.round_account_circle_24)
                error(R.drawable.round_account_circle_24)
                transformations(CircleCropTransformation())
            }

            // Chargement de l'image principale du post
            if (post.imageUrls.isNotEmpty()) {
                binding.ivPostImage.load(post.imageUrls.first()) {
                    crossfade(true)
                }
            }

            // Gestion du coeur like/unlike visuel
            binding.ivLike.setImageResource(
                if (post.isLikedByMe) R.drawable.ic_heart_filled else R.drawable.ic_heart_empty // Adapte selon tes drawables
            )
        }
    }
}