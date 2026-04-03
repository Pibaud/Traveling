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

class DiscoveryAdapter : RecyclerView.Adapter<DiscoveryAdapter.PostViewHolder>() {

    private var posts: List<Post> = emptyList()

    fun submitList(newList: List<Post>) {
        posts = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = ItemPlaceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PostViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(posts[position])
    }

    override fun getItemCount(): Int = posts.size

    class PostViewHolder(private val binding: ItemPlaceBinding) : RecyclerView.ViewHolder(binding.root) {
        private var isLiked = false
        private var currentLikes = 0
        private var onPageChangeCallback: ViewPager2.OnPageChangeCallback? = null
        fun bind(post: Post) {
            val context = itemView.context
            val photoAdapter = PhotoCarouselAdapter(post.imageUrls)
            binding.vpPlacePhotos.adapter = photoAdapter

            // --- NETTOYAGE OBLIGATOIRE (pour recyclage sain) ---
            onPageChangeCallback?.let { binding.vpPlacePhotos.unregisterOnPageChangeCallback(it) }

            // Si mono-photo
            if (post.imageUrls.size <= 1) {
                // Masquer les deux indicateurs
                binding.tlDots.visibility = View.GONE
                binding.tvPageIndicator.visibility = View.GONE
            } else {
                // Si multi-photos
                binding.tlDots.visibility = View.VISIBLE
                binding.tvPageIndicator.visibility = View.VISIBLE

                // 1. Liaison avec les POINTS (TabLayoutMediator)
                // Se lie automatiquement au cycle de vie de la vue, pas besoin de nettoyer
                TabLayoutMediator(binding.tlDots, binding.vpPlacePhotos) { _, _ -> }.attach()

                // 2. Liaison avec le TEXTE "X/Y photos"
                // Initialisation immédiate
                binding.tvPageIndicator.text = context.getString(
                    R.string.page_indicator_text, // Défini dans strings.xml
                    1, // Première page
                    post.imageUrls.size
                )

                // Enregistrement de l'écouteur de changement de page
                onPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        super.onPageSelected(position)
                        // Met à jour le texte (position commence à 0, donc +1)
                        binding.tvPageIndicator.text = context.getString(
                            R.string.page_indicator_text,
                            position + 1,
                            post.imageUrls.size
                        )
                    }
                }

                binding.vpPlacePhotos.registerOnPageChangeCallback(onPageChangeCallback!!)
            }

            // Initialisation des données
            currentLikes = post.likesCount
            binding.tvLikeCount.text = formatCount(currentLikes)

            // On s'assure que l'icône est l'outline par défaut au chargement
            binding.ivLike.setImageResource(R.drawable.round_favorite_48)
            binding.ivLike.setColorFilter(null) // Pas de couleur particulière

            // --- LOGIQUE DU CLIC SUR LE LIKE ---
            binding.ivLike.setOnClickListener {
                isLiked = !isLiked // Inverse l'état

                if (isLiked) {
                    // Passage au rouge + icône remplie
                    binding.ivLike.setImageResource(R.drawable.round_favorite_filled_48)
                    binding.ivLike.setColorFilter(
                        androidx.core.content.ContextCompat.getColor(itemView.context, R.color.primary_color)
                    )
                    currentLikes++
                } else {
                    // Retour à l'état normal
                    binding.ivLike.setImageResource(R.drawable.round_favorite_48)
                    binding.ivLike.setColorFilter(
                        android.graphics.Color.WHITE // Ou la couleur par défaut de tes icônes
                    )
                    currentLikes--
                }

                // Mise à jour du texte avec animation ou simple changement
                binding.tvLikeCount.text = formatCount(currentLikes)

                // Animation de "rebond" (Scale) pour donner du feedback
                animateLikeButton()
            }

            // 1. Photos et Avatar (inchangé)
            binding.ivUserAvatar.load(post.authorAvatarUrl) {
                crossfade(true)
                transformations(CircleCropTransformation())
            }

            // 2. Textes existants
            binding.tvLocationName.text = post.place.name
            binding.tvUsername.text = "par ${post.authorName}"

            // 3. NOUVEAU : Formatage et affichage des compteurs
            binding.tvLikeCount.text = formatCount(post.likesCount)
            binding.tvCommentCount.text = formatCount(post.commentsCount)

            // 4. NOUVEAU : Action du bouton Localisation (Google Maps)
            binding.ivLocation.setOnClickListener { view ->
                // Création de l'URL géographique avec un marqueur (q=) et un label
                val geoUri = "geo:0,0?q=${post.place.latitude},${post.place.longitude}(${Uri.encode(post.place.name)})"
                val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse(geoUri))

                // Optionnel : Forcer l'ouverture dans l'app Google Maps plutôt que le navigateur
                mapIntent.setPackage("com.google.android.apps.maps")

                // Lancement de l'activité (on utilise try/catch au cas où Maps n'est pas installé)
                try {
                    view.context.startActivity(mapIntent)
                } catch (e: Exception) {
                    // Fallback si l'app Maps n'est pas installée : on laisse Android choisir (navigateur web, etc.)
                    val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(geoUri))
                    view.context.startActivity(fallbackIntent)
                }
            }
        }

        // Fonction utilitaire pour formater les chiffres (ex: 1500 devient 1.5k)
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