package com.example.application.features.profile // Ajuste ton package

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.application.R
import com.example.application.model.UserProfileResponse
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class CreatorProfileBottomSheet(
    private val creatorId: String
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_creator_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        val profileContainer = view.findViewById<LinearLayout>(R.id.profileContainer)

        // Textes
        val tvUsername = view.findViewById<TextView>(R.id.tvCreatorUsername)
        val tvBio = view.findViewById<TextView>(R.id.tvCreatorBio)
        val tvStatCreated = view.findViewById<TextView>(R.id.tvCreatorStatCreated)
        val tvStatLiked = view.findViewById<TextView>(R.id.tvCreatorStatLiked)
        val tvStatReceived = view.findViewById<TextView>(R.id.tvCreatorStatReceived)
        val tvStatHours = view.findViewById<TextView>(R.id.tvCreatorStatHours)
        val ivAvatar = view.findViewById<ImageView>(R.id.ivCreatorAvatar)

        // On lance la requête avec l'ID du créateur
        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.getUserProfile(creatorId)

                if (response.isSuccessful && response.body() != null) {
                    val profile: UserProfileResponse = response.body()!!

                    tvUsername.text = "@${profile.username}"
                    tvBio.text = profile.bio ?: "Ce voyageur préfère le mystère... 🌍"

                    tvStatCreated.text = profile.createdCount.toString()
                    tvStatLiked.text = profile.likedCount.toString()
                    tvStatReceived.text = profile.totalLikesReceived.toString()
                    tvStatHours.text = profile.totalHours.toString()

                    if (!profile.avatarUrl.isNullOrEmpty()) {
                        Glide.with(requireContext())
                            .load(profile.avatarUrl)
                            .centerCrop()
                            .into(ivAvatar)
                    }

                    // On cache le chargement et on affiche le profil
                    progressBar.visibility = View.GONE
                    profileContainer.visibility = View.VISIBLE

                } else {
                    Toast.makeText(context, "Profil introuvable", Toast.LENGTH_SHORT).show()
                    dismiss()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Erreur réseau", Toast.LENGTH_SHORT).show()
                dismiss()
            }
        }
    }
}