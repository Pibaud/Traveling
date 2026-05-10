package com.example.application.features.profile

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.application.R
import com.example.application.databinding.FragmentProfileBinding
// 👇 L'IMPORT QUI MANQUAIT EST ICI 👇
import com.example.application.model.UserProfileResponse
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currentUser = Firebase.auth.currentUser

        if (currentUser != null) {
            binding.btnLogout.visibility = View.VISIBLE
            binding.btnLogout.setOnClickListener {
                Firebase.auth.signOut()
                findNavController().navigate(R.id.action_profile_to_auth)
            }

            // --- LECTURE DU CACHE (Centres d'intérêt) ---
            val prefs = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
            binding.chipPrefCulture.isChecked = prefs.getBoolean("pref_culture", false)
            binding.chipPrefDecouverte.isChecked = prefs.getBoolean("pref_decouverte", false)
            binding.chipPrefLoisirs.isChecked = prefs.getBoolean("pref_loisirs", false)

            binding.chipPrefCulture.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("pref_culture", isChecked).apply() }
            binding.chipPrefDecouverte.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("pref_decouverte", isChecked).apply() }
            binding.chipPrefLoisirs.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("pref_loisirs", isChecked).apply() }

            // --- APPEL API POUR LES INFOS UTILISATEUR & STATS ---
            loadUserProfile(currentUser.uid)

            binding.ivEditProfile.setOnClickListener {
                showEditProfileDialog(currentUser.uid, binding.tvBio.text.toString())
            }

        } else {
            binding.btnLogout.visibility = View.GONE
            findNavController().navigate(R.id.action_profile_to_auth)
        }
    }

    private fun showEditProfileDialog(uid: String, currentBio: String) {
        val context = requireContext()

        // On crée un layout vertical pour mettre nos deux champs
        val layout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
        }

        val bioInput = android.widget.EditText(context).apply {
            hint = "Votre Bio (ex: Fan de voyage 🌍)"
            setText(if (currentBio.startsWith("Aucune bio")) "" else currentBio)
        }

        val avatarInput = android.widget.EditText(context).apply {
            hint = "URL de la photo de profil (https://...)"
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 32 }
        }

        layout.addView(bioInput)
        layout.addView(avatarInput)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
            .setTitle("Modifier le profil")
            .setView(layout)
            .setPositiveButton("Enregistrer") { _, _ ->
                val newBio = bioInput.text.toString()
                val newAvatar = avatarInput.text.toString()
                updateProfileInBackend(uid, newBio, newAvatar)
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun updateProfileInBackend(uid: String, bio: String, avatarUrl: String) {
        lifecycleScope.launch {
            try {
                // On prépare la donnée. Si le champ URL est vide, on envoie null pour ne pas effacer l'ancienne.
                val request = com.example.application.model.UpdateProfileRequest(
                    bio = bio.ifBlank { null },
                    avatarUrl = avatarUrl.ifBlank { null }
                )

                val response = RetrofitInstance.api.updateProfile(uid, request)

                if (response.isSuccessful) {
                    Toast.makeText(requireContext(), "Profil mis à jour !", Toast.LENGTH_SHORT).show()
                    // On recharge le profil pour afficher les nouvelles infos immédiatement
                    loadUserProfile(uid)
                } else {
                    Toast.makeText(requireContext(), "Erreur lors de la mise à jour", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Erreur réseau", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadUserProfile(uid: String) {
        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.getUserProfile(uid)

                if (response.isSuccessful && response.body() != null) {

                    // 👇 L'APPEL EXPLICITE EST LÀ 👇
                    val profile: UserProfileResponse = response.body()!!

                    // 1. Textes du profil
                    binding.tvUsername.text = "@${profile.username}"
                    binding.tvBio.text = profile.bio ?: "Aucune bio pour le moment. Explorez le monde ! 🌍"

                    // 2. Avatar avec Glide
                    if (!profile.avatarUrl.isNullOrEmpty()) {
                        Glide.with(requireContext())
                            .load(profile.avatarUrl)
                            .centerCrop()
                            .into(binding.ivAvatar)
                    }

                    // 3. Les 4 boîtes de statistiques
                    binding.tvStatCreated.text = profile.createdCount.toString()
                    binding.tvStatLiked.text = profile.likedCount.toString()
                    binding.tvStatReceivedLikes.text = profile.totalLikesReceived.toString()
                    binding.tvStatHours.text = profile.totalHours.toString()

                } else {
                    Toast.makeText(requireContext(), "Impossible de charger le profil : ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Erreur de connexion", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}