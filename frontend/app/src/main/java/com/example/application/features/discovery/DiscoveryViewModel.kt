package com.example.application.features.discovery // Vérifie ton package

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.application.model.Post
import kotlinx.coroutines.launch
import android.util.Log
import com.example.application.model.LikeRequest
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class DiscoveryViewModel : ViewModel() {

    private val _posts = MutableLiveData<List<Post>>()
    val posts: LiveData<List<Post>> = _posts
    private var currentTab = "public"

    init {
        // Au démarrage du ViewModel, on charge le feed
        loadMorePosts()
    }

    fun setTab(tab: String) {
        if (currentTab != tab) {
            currentTab = tab
            _posts.value = emptyList() // On vide la liste visuellement pendant le chargement
            loadMorePosts()
        }
    }

    fun loadMorePosts() {
        val currentUserId = Firebase.auth.currentUser?.uid ?: ""

        viewModelScope.launch {
            try {
                // On passe le currentTab à la requête Retrofit
                val fetchedPosts = RetrofitInstance.api.getFeed(currentUserId, currentTab)
                _posts.value = fetchedPosts
                Log.d("FeedNetwork", "Succès : ${fetchedPosts.size} posts récupérés")
            } catch (e: Exception) {
                Log.e("FeedNetwork", "Erreur lors de la récupération du feed", e)
            }
        }
    }

    fun toggleLikePost(postId: String) {
        // On récupère l'ID Firebase de l'utilisateur actuel
        val userId = Firebase.auth.currentUser?.uid

        if (userId == null) {
            Log.e("FeedNetwork", "Impossible de liker : Utilisateur non connecté")
            return
        }

        viewModelScope.launch {
            try {
                // On prépare le colis
                val request = LikeRequest(postId = postId, userId = userId)

                // On l'envoie au backend !
                val response = RetrofitInstance.api.toggleLike(request)

                Log.d("FeedNetwork", "Like enregistré en DB ! Statut: ${response.liked}")
            } catch (e: Exception) {
                // Si ça plante (plus de wifi, serveur éteint...),
                // idéalement il faudrait annuler le like dans l'UI,
                // mais pour l'instant on se contente de logguer l'erreur.
                Log.e("FeedNetwork", "Erreur lors de l'envoi du like", e)
            }
        }
    }
}