package com.example.application.features.profile

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.application.R
import com.example.application.databinding.FragmentProfileBinding
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

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

            // 👇 LECTURE ET SAUVEGARDE DES PRÉFÉRENCES EN CACHE 👇
            val prefs = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)

            // 1. On restaure l'état précédent (coché ou non)
            binding.chipPrefCulture.isChecked = prefs.getBoolean("pref_culture", false)
            binding.chipPrefDecouverte.isChecked = prefs.getBoolean("pref_decouverte", false)
            binding.chipPrefLoisirs.isChecked = prefs.getBoolean("pref_loisirs", false)

            // 2. On écoute les clics pour sauvegarder en temps réel
            binding.chipPrefCulture.setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("pref_culture", isChecked).apply()
            }
            binding.chipPrefDecouverte.setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("pref_decouverte", isChecked).apply()
            }
            binding.chipPrefLoisirs.setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("pref_loisirs", isChecked).apply()
            }

        } else {
            binding.btnLogout.visibility = View.GONE
            findNavController().navigate(R.id.action_profile_to_auth)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}