package com.example.application.features.social

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.application.model.Group
import com.example.application.model.NotificationToggleRequest
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class SocialViewModel : ViewModel() {

    private val _popularGroups = MutableLiveData<List<Group>>()
    val popularGroups: LiveData<List<Group>> = _popularGroups

    private val _myGroups = MutableLiveData<List<Group>>()
    val myGroups: LiveData<List<Group>> = _myGroups

    init {
        loadGroups()
    }

    private fun loadGroups() {
        val userId = Firebase.auth.currentUser?.uid

        viewModelScope.launch {
            try {
                // 1. Charger les groupes populaires
                val popular = RetrofitInstance.api.getPopularGroups(userId)
                _popularGroups.value = popular

                // 2. Charger MES groupes
                if (userId != null) {
                    val my = RetrofitInstance.api.getMyGroups(userId)
                    _myGroups.value = my
                }
            } catch (e: Exception) {
                Log.e("SocialViewModel", "Erreur lors de la récupération des groupes", e)
            }
        }
    }

    fun onJoinGroupClicked(group: Group) {
        // TODO: Implémenter l'appel réseau pour rejoindre un groupe plus tard
        Log.d("SocialViewModel", "Demande pour rejoindre le groupe : ${group.name}")
    }

    fun onNotificationToggleClicked(group: Group, enabled: Boolean) {
        val userId = Firebase.auth.currentUser?.uid ?: return

        viewModelScope.launch {
            try {
                val request = NotificationToggleRequest(
                    groupId = group.id,
                    userId = userId,
                    shouldNotify = enabled
                )

                val response = RetrofitInstance.api.toggleGroupNotifications(request)

                if (!response.isSuccessful) {
                    // Si le serveur plante, on pourrait annuler le changement optimiste ici.
                    // Pour simplifier, on loggue juste l'erreur pour l'instant.
                    Log.e("SocialViewModel", "Échec du serveur lors du toggle : ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("SocialViewModel", "Erreur réseau toggle notification", e)
            }
        }
    }
}