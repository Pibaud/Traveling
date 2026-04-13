package com.example.application.features.discovery // Vérifie ton package

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.application.model.Post
import kotlinx.coroutines.launch
import android.util.Log

class DiscoveryViewModel : ViewModel() {

    private val _posts = MutableLiveData<List<Post>>()
    val posts: LiveData<List<Post>> = _posts

    init {
        // Au démarrage du ViewModel, on charge le feed
        loadMorePosts()
    }

    fun loadMorePosts() {
        // On lance une coroutine attachée au cycle de vie du ViewModel
        viewModelScope.launch {
            try {
                // 1. Appel magique à ton backend Ktor
                val fetchedPosts = RetrofitInstance.api.getFeed()

                // 2. On met à jour l'UI
                // Pour l'instant, on remplace la liste.
                // (Plus tard, pour le scroll infini, on fera : _posts.value = currentList + fetchedPosts)
                _posts.value = fetchedPosts

                Log.d("FeedNetwork", "Succès : ${fetchedPosts.size} posts récupérés")
            } catch (e: Exception) {
                // En cas d'erreur (serveur éteint, pas de connexion...)
                Log.e("FeedNetwork", "Erreur lors de la récupération du feed", e)
            }
        }
    }
}