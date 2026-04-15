package com.example.application.features.profile

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

        // Récupération de l'utilisateur Firebase
        val currentUser = Firebase.auth.currentUser

        if (currentUser != null) {
            binding.btnLogout.visibility = View.VISIBLE

            binding.btnLogout.setOnClickListener {
                // 1. Déconnexion locale via Firebase
                Firebase.auth.signOut()

                // 2. Redirection vers l'écran d'authentification
                findNavController().navigate(R.id.action_profile_to_auth)
            }
        } else {
            // Par sécurité, si un utilisateur non connecté arrive ici, on le renvoie
            binding.btnLogout.visibility = View.GONE
            findNavController().navigate(R.id.action_profile_to_auth)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}