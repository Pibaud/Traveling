package com.example.application.features.authentication

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.application.R
import com.example.application.databinding.FragmentAuthBinding
import com.example.application.model.UserSyncRequest
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class AuthFragment : Fragment(R.layout.fragment_auth) {
    private var _binding: FragmentAuthBinding? = null
    private val binding get() = _binding!!
    private val auth = Firebase.auth

    // État de l'interface (Connexion par défaut)
    private var isLoginMode = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAuthBinding.bind(view)

        updateUiMode() // Initialisation

        // 1. Basculer entre Connexion et Inscription
        binding.tvSwitchMode.setOnClickListener {
            isLoginMode = !isLoginMode
            updateUiMode()
        }

        // 2. Action principale (Connexion OU Inscription)
        binding.btnMainAuth.setOnClickListener {
            if (isLoginMode) handleLogin()
            else handleRegistration()
        }

        // 3. Mode Anonyme (Inchangé)
        binding.btnAnonymous.setOnClickListener {
            handleAnonymousLogin()
        }
    }

    private fun updateUiMode() {
        if (isLoginMode) {
            binding.tvAuthTitle.text = "Connexion"
            binding.btnMainAuth.text = "Se connecter"
            binding.tvSwitchMode.text = "Nouveau ? Créer un compte"
            binding.etConfirmPasswordLayout.visibility = View.GONE
        } else {
            binding.tvAuthTitle.text = "Inscription"
            binding.btnMainAuth.text = "S'inscrire"
            binding.tvSwitchMode.text = "J'ai déjà un compte. Se connecter"
            binding.etConfirmPasswordLayout.visibility = View.VISIBLE
        }
    }

    private fun handleLogin() {
        val email = binding.etEmail.text.toString()
        val psw = binding.etPassword.text.toString()
        if (email.isNotEmpty() && psw.isNotEmpty()) {
            auth.signInWithEmailAndPassword(email, psw)
                .addOnSuccessListener { navigateToFeed() }
                .addOnFailureListener { /* Toast erreur */ }
        }
    }

    private fun handleRegistration() {
        val email = binding.etEmail.text.toString()
        val psw = binding.etPassword.text.toString()
        val confirmPsw = binding.etConfirmPassword.text.toString()

        if (email.isEmpty() || psw.isEmpty()) return
        if (psw != confirmPsw) {
            // Toast: "Les mots de passe ne correspondent pas"
            return
        }

        // Dans AuthFragment.kt (simplifié)
        auth.createUserWithEmailAndPassword(email, psw)
            .addOnSuccessListener { authResult ->
                val firebaseUser = authResult.user
                if (firebaseUser != null) {
                    // Lancement de la synchronisation vers Ktor
                    lifecycleScope.launch {
                        try {
                            val request = UserSyncRequest(
                                uid = firebaseUser.uid,
                                email = firebaseUser.email ?: ""
                            )
                            val response = RetrofitInstance.api.syncUser(request)

                            if (response.isSuccessful) {
                                navigateToFeed()
                            } else {
                                Toast.makeText(requireContext(), "Erreur synchro base", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(requireContext(), "Erreur réseau : ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .addOnFailureListener { /* Toast erreur */ }
    }

    // Dans AuthFragment.kt

    private fun handleAnonymousLogin() {
        auth.signInAnonymously()
            .addOnSuccessListener { authResult ->
                val user = authResult.user
                if (user != null) {
                    // On synchronise cet utilisateur anonyme avec Ktor
                    lifecycleScope.launch {
                        try {
                            val request = UserSyncRequest(
                                uid = user.uid,
                                email = "guest_${user.uid.take(5)}@traveling.com" // E-mail fictif pour satisfaire la DB
                            )
                            val response = RetrofitInstance.api.syncUser(request)

                            if (response.isSuccessful) {
                                navigateToFeed()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(requireContext(), "Erreur de synchro invité", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Échec : ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun navigateToFeed() {
        findNavController().navigate(R.id.action_auth_to_feed)
    }
}