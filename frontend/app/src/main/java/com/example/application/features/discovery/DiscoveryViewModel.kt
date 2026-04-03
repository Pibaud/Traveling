package com.example.application.features.discovery

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.application.model.Place
import com.example.application.model.Post
import kotlin.random.Random

class DiscoveryViewModel : ViewModel() {

    // Instance unique de Random pour garantir des probabilités réelles
    private val random = Random.Default

    // On observe désormais une liste de POSTS
    private val _posts = MutableLiveData<List<Post>>(emptyList())
    val posts: LiveData<List<Post>> = _posts

    private var currentPage = 0
    private val pageSize = 10

    init {
        loadMorePosts()
    }

    fun loadMorePosts() {
        val currentList = _posts.value ?: emptyList()
        val newPosts = mutableListOf<Post>()

        val startId = currentPage * pageSize
        val endId = startId + pageSize

        for (i in startId until endId) {
            // Le lieu associé à ce post
            val fakePlace = Place(
                id = "place_$i",
                name = "Spot incroyable n°$i",
                latitude = 43.6107, // Coordonnées factices
                longitude = 3.8767
            )

            val imageUrls = mutableListOf<String>()

            // Probabilité : 70% de chance d'avoir 1 seule photo
            if (random.nextFloat() > 0.30f) {
                imageUrls.add("https://picsum.photos/seed/${i}_main/400/800")
            } else {
                // 30% de chance d'avoir un carrousel (3 à 8 photos)
                val photoCount = random.nextInt(3, 9)
                for (j in 0 until photoCount) {
                    imageUrls.add("https://picsum.photos/seed/${i}_album_$j/400/800")
                }
            }

            // Création du post final
            newPosts.add(
                Post(
                    id = "post_$i",
                    authorId = "user_$i",
                    authorName = "Auteur $i",
                    authorAvatarUrl = "https://picsum.photos/seed/user_$i/100",
                    description = "C'était une journée incroyable, je recommande !",
                    imageUrls = imageUrls,
                    likesCount = (10..500).random(), // Faux compte de likes
                    commentsCount = (0..50).random(),
                    tags = listOf("#Voyage", "#Découverte"),
                    place = fakePlace
                )
            )
        }

        _posts.value = currentList + newPosts
        currentPage++
    }
}