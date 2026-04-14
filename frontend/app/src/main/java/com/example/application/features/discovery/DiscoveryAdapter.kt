import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil.load
import coil.transform.CircleCropTransformation
import com.example.application.R
import com.example.application.databinding.ItemPlaceBinding
import com.example.application.model.Post
import com.google.android.material.tabs.TabLayoutMediator

class DiscoveryAdapter(
    private val onLikeClicked: (String) -> Unit
) : RecyclerView.Adapter<DiscoveryAdapter.PostViewHolder>() {

    private var posts: List<Post> = emptyList()

    fun submitList(newList: List<Post>) {
        posts = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = ItemPlaceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        // NOUVEAU 2 : On passe cette fonction au ViewHolder quand on le crée
        return PostViewHolder(binding, onLikeClicked)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(posts[position])
    }

    override fun getItemCount(): Int = posts.size

    // NOUVEAU 3 : Le ViewHolder accepte cette fonction dans son constructeur
    class PostViewHolder(
        private val binding: ItemPlaceBinding,
        private val onLikeClicked: (String) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var isLiked = false
        private var currentLikes = 0
        private var onPageChangeCallback: ViewPager2.OnPageChangeCallback? = null

        fun bind(post: Post) {
            val context = itemView.context
            val photoAdapter = PhotoCarouselAdapter(post.imageUrls)
            binding.vpPlacePhotos.adapter = photoAdapter

            // --- GESTION DU CARROUSEL ---
            onPageChangeCallback?.let { binding.vpPlacePhotos.unregisterOnPageChangeCallback(it) }

            if (post.imageUrls.size <= 1) {
                binding.tlDots.visibility = View.GONE
                binding.tvPageIndicator.visibility = View.GONE
            } else {
                binding.tlDots.visibility = View.VISIBLE
                binding.tvPageIndicator.visibility = View.VISIBLE

                TabLayoutMediator(binding.tlDots, binding.vpPlacePhotos) { _, _ -> }.attach()

                binding.tvPageIndicator.text = context.getString(
                    R.string.page_indicator_text, 1, post.imageUrls.size
                )

                onPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        super.onPageSelected(position)
                        binding.tvPageIndicator.text = context.getString(
                            R.string.page_indicator_text, position + 1, post.imageUrls.size
                        )
                    }
                }
                binding.vpPlacePhotos.registerOnPageChangeCallback(onPageChangeCallback!!)
            }

            // --- LIKES ET COMPTEURS ---
            currentLikes = post.likesCount
            binding.tvLikeCount.text = formatCount(currentLikes)
            binding.tvCommentCount.text = formatCount(post.commentsCount)

            binding.ivLike.setImageResource(R.drawable.round_favorite_48)
            binding.ivLike.setColorFilter(null)

            binding.ivLike.setOnClickListener {
                isLiked = !isLiked
                if (isLiked) {
                    binding.ivLike.setImageResource(R.drawable.round_favorite_filled_48)
                    binding.ivLike.setColorFilter(
                        androidx.core.content.ContextCompat.getColor(itemView.context, R.color.primary_color)
                    )
                    currentLikes++
                } else {
                    binding.ivLike.setImageResource(R.drawable.round_favorite_48)
                    binding.ivLike.setColorFilter(android.graphics.Color.WHITE)
                    currentLikes--
                }
                binding.tvLikeCount.text = formatCount(currentLikes)
                animateLikeButton()

                // NOUVEAU 4 : On déclenche l'alarme en envoyant l'ID du post !
                onLikeClicked(post.id)
            }

            // --- NOUVELLES DONNÉES DU BACKEND ---
            binding.tvLocationName.text = post.place.name
            binding.tvUsername.text = "par ${post.authorName}"

            if (post.description.isNotBlank()) {
                binding.tvDescription.visibility = View.VISIBLE
                binding.tvDescription.text = post.description
            } else {
                binding.tvDescription.visibility = View.GONE
            }

            if (post.tags.isNotEmpty()) {
                binding.tvTags.visibility = View.VISIBLE
                binding.tvTags.text = post.tags.joinToString(" ") { "#$it" }
            } else {
                binding.tvTags.visibility = View.GONE
            }

            val avatarToLoad = post.authorAvatarUrl.ifBlank {
                R.drawable.round_account_circle_24
            }
            binding.ivUserAvatar.load(avatarToLoad) {
                crossfade(true)
                transformations(CircleCropTransformation())
                error(R.drawable.round_account_circle_24)
            }

            // --- GOOGLE MAPS ---
            binding.ivLocation.setOnClickListener { view ->
                val geoUri = "geo:0,0?q=${post.place.latitude},${post.place.longitude}(${Uri.encode(post.place.name)})"
                val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse(geoUri))
                mapIntent.setPackage("com.google.android.apps.maps")
                try {
                    view.context.startActivity(mapIntent)
                } catch (e: Exception) {
                    val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(geoUri))
                    view.context.startActivity(fallbackIntent)
                }
            }
        }

        private fun formatCount(count: Int): String {
            return when {
                count >= 1000000 -> String.format("%.1fM", count / 1000000.0)
                count >= 1000 -> String.format("%.1fk", count / 1000.0)
                else -> count.toString()
            }
        }

        private fun animateLikeButton() {
            binding.ivLike.animate()
                .scaleX(1.2f)
                .scaleY(1.2f)
                .setDuration(100)
                .withEndAction {
                    binding.ivLike.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100)
                }
        }
    }
}

class PhotoCarouselAdapter(private val imageUrls: List<String>) :
    RecyclerView.Adapter<PhotoCarouselAdapter.PhotoViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val imageView = ImageView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        return PhotoViewHolder(imageView)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.imageView.load(imageUrls[position]) {
            crossfade(true)
        }
    }

    override fun getItemCount(): Int = imageUrls.size

    class PhotoViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)
}