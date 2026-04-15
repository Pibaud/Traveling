package com.example.application.features.social

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.application.model.Group
import com.example.application.model.JoinGroupRequest
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

    fun loadGroups() {
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
        val userId = Firebase.auth.currentUser?.uid ?: return

        viewModelScope.launch {
            try {
                val request = JoinGroupRequest(group.id, userId)
                val response = RetrofitInstance.api.joinGroup(request)

                if (response.isSuccessful) {
                    val status = response.body()?.get("status")

                    if (status == "ACCEPTED") {
                        // Succès public : on recharge tout pour mettre les listes à jour !
                        loadGroups()
                    } else if (status == "PENDING") {
                        // C'était privé : on pourrait juste griser le bouton ou afficher un Toast
                        Log.d("SocialViewModel", "Demande envoyée, en attente de validation.")
                    }
                }
            } catch (e: Exception) {
                Log.e("SocialViewModel", "Erreur lors de la demande de participation", e)
            }
        }
    }

    fun onNotificationToggleClicked(group: Group, enabled: Boolean) {
        val userId = Firebase.auth.currentUser?.uid ?: return

        _popularGroups.value = _popularGroups.value?.map {
            if (it.id == group.id) it.copy(isNotificationEnabled = enabled) else it
        }

        _myGroups.value = _myGroups.value?.map {
            if (it.id == group.id) it.copy(isNotificationEnabled = enabled) else it
        }
        viewModelScope.launch {
            try {
                val request = NotificationToggleRequest(
                    groupId = group.id,
                    userId = userId,
                    shouldNotify = enabled
                )

                val response = RetrofitInstance.api.toggleGroupNotifications(request)

                if (!response.isSuccessful) {
                    Log.e("SocialViewModel", "Échec du serveur lors du toggle : ${response.code()}")
                    // Optionnel : Si ça échoue, on pourrait refaire l'opération inverse ici pour annuler
                }
            } catch (e: Exception) {
                Log.e("SocialViewModel", "Erreur réseau toggle notification", e)
            }
        }
    }
}