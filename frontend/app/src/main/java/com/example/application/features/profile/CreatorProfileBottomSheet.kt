package com.example.application.features.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.application.R
import com.example.application.features.path.ItineraryAdapter
import com.example.application.features.path.ItineraryDetailsBottomSheet
import com.example.application.model.RetrofitInstance
import com.example.application.model.UserProfileResponse
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class CreatorProfileBottomSheet(private val creatorId: String) : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bottom_sheet_creator_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currentUserId = Firebase.auth.currentUser?.uid ?: return

        // --- DÉCLARATION DES VUES ---
        val btnFollow = view.findViewById<MaterialButton>(R.id.btnFollowCreator)
        val rvItineraries = view.findViewById<RecyclerView>(R.id.rvCreatorItineraries)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        val profileContainer = view.findViewById<LinearLayout>(R.id.profileContainer)
        val ivAvatar = view.findViewById<ImageView>(R.id.ivCreatorAvatar)

        val tvUsername = view.findViewById<TextView>(R.id.tvCreatorUsername)
        val tvBio = view.findViewById<TextView>(R.id.tvCreatorBio)
        val tvStatCreated = view.findViewById<TextView>(R.id.tvCreatorStatCreated)
        val tvStatLiked = view.findViewById<TextView>(R.id.tvCreatorStatLiked)
        val tvStatReceived = view.findViewById<TextView>(R.id.tvCreatorStatReceived)
        val tvStatFollowers = view.findViewById<TextView>(R.id.tvCreatorStatHours) // Ton ID XML actuel

        var isCurrentlyFollowing = false

        // 1. Charger le profil complet et les stats
        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.getUserProfile(creatorId, currentUserId)
                if (response.isSuccessful && response.body() != null) {
                    val profile: UserProfileResponse = response.body()!!

                    tvUsername.text = "@${profile.username}"
                    tvBio.text = profile.bio ?: "Voyageur mystère 🌍"

                    tvStatCreated.text = profile.createdCount.toString()
                    tvStatLiked.text = profile.likedCount.toString()
                    tvStatReceived.text = profile.totalLikesReceived.toString()
                    tvStatFollowers.text = profile.followerCount.toString()

                    // Gestion du bouton Follow (Masqué si c'est notre propre profil)
                    if (creatorId == currentUserId) {
                        btnFollow.visibility = View.GONE
                    } else {
                        btnFollow.visibility = View.VISIBLE
                        isCurrentlyFollowing = profile.isFollowing
                        updateFollowButton(btnFollow, isCurrentlyFollowing)
                    }

                    if (!profile.avatarUrl.isNullOrEmpty()) {
                        Glide.with(requireContext()).load(profile.avatarUrl).centerCrop().into(ivAvatar)
                    }

                    progressBar.visibility = View.GONE
                    profileContainer.visibility = View.VISIBLE
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        // 2. Bouton S'abonner
        btnFollow.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val resp = RetrofitInstance.api.toggleFollow(currentUserId, creatorId)
                    if (resp.isSuccessful) {
                        isCurrentlyFollowing = resp.body()?.get("isFollowing") ?: !isCurrentlyFollowing
                        updateFollowButton(btnFollow, isCurrentlyFollowing)

                        // Update visuelle du compteur
                        val currentCount = tvStatFollowers.text.toString().toIntOrNull() ?: 0
                        tvStatFollowers.text = (if(isCurrentlyFollowing) currentCount + 1 else currentCount - 1).toString()
                    }
                } catch (e: Exception) { }
            }
        }

        // 3. Charger la liste des itinéraires
        lifecycleScope.launch {
            try {
                val itineraries = RetrofitInstance.api.getPathList(currentUserId, "AUTHOR_$creatorId")
                if (itineraries.isNotEmpty()) {
                    rvItineraries.adapter = ItineraryAdapter(
                        items = itineraries,
                        onItemClick = { selected ->
                            val sheet = ItineraryDetailsBottomSheet(selected)
                            sheet.show(parentFragmentManager, "ItineraryDetails")
                        },
                        onLikeClick = { clicked ->
                            lifecycleScope.launch { RetrofitInstance.api.toggleLike(currentUserId, clicked.id!!) }
                        }
                    )
                }
            } catch (e: Exception) { }
        }
    }

    private fun updateFollowButton(btn: MaterialButton, isFollowing: Boolean) {
        if (isFollowing) {
            btn.text = "Abonné"
            btn.setBackgroundColor(android.graphics.Color.parseColor("#E0E0E0"))
            btn.setTextColor(android.graphics.Color.BLACK)
        } else {
            btn.text = "S'abonner"
            btn.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            btn.setTextColor(android.graphics.Color.parseColor("#884154"))
        }
    }
}