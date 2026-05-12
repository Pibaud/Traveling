package com.example.application.features.social

import android.graphics.Color
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.application.R
import com.example.application.databinding.ItemGroupPostBinding
import com.example.application.model.ItineraryResponse
import com.example.application.model.Post
import com.google.android.material.card.MaterialCardView

// 👇 L'astuce magique : un objet qui peut être SOIT un Post, SOIT un Itinéraire
sealed class GroupFeedItem {
    data class PostItem(val post: Post) : GroupFeedItem()
    data class ItineraryItem(val itinerary: ItineraryResponse) : GroupFeedItem()
}

class GroupFeedAdapter(
    private val onItineraryClick: (ItineraryResponse) -> Unit,
    private val onItineraryLikeClick: (ItineraryResponse) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<GroupFeedItem> = emptyList()

    companion object {
        private const val TYPE_POST = 1
        private const val TYPE_ITINERARY = 2
    }

    fun submitList(newList: List<GroupFeedItem>) {
        items = newList
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is GroupFeedItem.PostItem -> TYPE_POST
            is GroupFeedItem.ItineraryItem -> TYPE_ITINERARY
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_POST) {
            val binding = ItemGroupPostBinding.inflate(inflater, parent, false)
            PostViewHolder(binding)
        } else {
            val view = inflater.inflate(R.layout.item_itinerary, parent, false)
            ItineraryViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is GroupFeedItem.PostItem -> (holder as PostViewHolder).bind(item.post)
            is GroupFeedItem.ItineraryItem -> (holder as ItineraryViewHolder).bind(item.itinerary)
        }
    }

    override fun getItemCount() = items.size

    // --- VIEWHOLDER DES POSTS ---
    inner class PostViewHolder(private val binding: ItemGroupPostBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(post: Post) {
            binding.tvAuthorName.text = post.authorName
            binding.tvDescription.text = post.description
            binding.tvLikesCount.text = post.likesCount.toString()
            binding.tvTimestamp.text = DateUtils.getRelativeTimeSpanString(post.timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)

            binding.ivAuthorAvatar.load(post.authorAvatarUrl) {
                placeholder(R.drawable.round_account_circle_24)
                error(R.drawable.round_account_circle_24)
                transformations(CircleCropTransformation())
            }

            if (post.imageUrls.isNotEmpty()) {
                binding.ivPostImage.load(post.imageUrls.first()) { crossfade(true) }
            }

            binding.ivLike.setImageResource(if (post.isLikedByMe) R.drawable.ic_heart_filled else R.drawable.ic_heart_empty)
        }
    }

    // --- VIEWHOLDER DES ITINÉRAIRES ---
    inner class ItineraryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card: MaterialCardView = view.findViewById(R.id.cardItinerary)
        private val name: TextView = view.findViewById(R.id.tvItineraryName)
        private val price: TextView = view.findViewById(R.id.tvPrice)
        private val duration: TextView = view.findViewById(R.id.tvDuration)
        private val meal: TextView = view.findViewById(R.id.tvMeal)
        private val effort: TextView = view.findViewById(R.id.tvEffort)
        private val row1: View = view.findViewById(R.id.row1)
        private val row2: View = view.findViewById(R.id.row2)
        private val ivCover1: ImageView = view.findViewById(R.id.ivCover1)
        private val ivCover2: ImageView = view.findViewById(R.id.ivCover2)
        private val ivCover3: ImageView = view.findViewById(R.id.ivCover3)
        private val ivCover4: ImageView = view.findViewById(R.id.ivCover4)
        private val ivLike: ImageView = view.findViewById(R.id.ivLike)
        private val tvLikeCount: TextView = view.findViewById(R.id.tvLikeCount)

        fun bind(item: ItineraryResponse) {
            name.text = item.name
            price.text = "${item.totalPrice} €"
            duration.text = "⏱ Durée : ${item.totalDuration}h"
            meal.text = if (item.mealIncluded) "🍽 Repas compris" else "🍽 Repas non compris"
            effort.text = "💪 Effort : ${item.avgEffort}/5"

            try { card.setCardBackgroundColor(Color.parseColor(item.hexColor)) } catch (e: Exception) { card.setCardBackgroundColor(Color.DKGRAY) }

            val images = item.coverImages ?: emptyList()
            val count = images.size
            row1.visibility = if (count > 0) View.VISIBLE else View.GONE
            row2.visibility = if (count > 2) View.VISIBLE else View.GONE
            ivCover1.visibility = if (count > 0) View.VISIBLE else View.GONE
            ivCover2.visibility = if (count > 1) View.VISIBLE else View.GONE
            ivCover3.visibility = if (count > 2) View.VISIBLE else View.GONE
            ivCover4.visibility = if (count > 3) View.VISIBLE else View.GONE

            val imageViews = listOf(ivCover1, ivCover2, ivCover3, ivCover4)
            for (i in 0 until minOf(4, count)) {
                Glide.with(itemView.context).load(images[i]).centerCrop().diskCacheStrategy(DiskCacheStrategy.ALL).into(imageViews[i])
            }

            tvLikeCount.text = item.likeCount.toString()
            if (item.isLiked) {
                ivLike.setImageResource(R.drawable.ic_heart_filled)
                ivLike.setColorFilter(Color.RED)
            } else {
                ivLike.setImageResource(R.drawable.ic_heart_empty)
                ivLike.setColorFilter(Color.WHITE)
            }

            ivLike.setOnClickListener {
                item.isLiked = !item.isLiked
                if (item.isLiked) {
                    item.likeCount++
                    ivLike.setImageResource(R.drawable.ic_heart_filled)
                    ivLike.setColorFilter(Color.RED)
                } else {
                    item.likeCount--
                    ivLike.setImageResource(R.drawable.ic_heart_empty)
                    ivLike.setColorFilter(Color.WHITE)
                }
                tvLikeCount.text = item.likeCount.toString()
                onItineraryLikeClick(item)
            }

            card.setOnClickListener { onItineraryClick(item) }
        }
    }
}