package com.example.application.features.main

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.application.R
import com.example.application.databinding.MainActivityBinding
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {
    private lateinit var binding: MainActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MainActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // --- 1. LOGIQUE DE REDIRECTION DYNAMIQUE (ROUTING) ---
        val navInflater = navController.navInflater
        val navGraph = navInflater.inflate(R.navigation.nav_graph)

        // Récupération des états (Onboarding et Auth)
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val onboardingFinished = prefs.getBoolean("onboarding_finished", false)
        val currentUser = Firebase.auth.currentUser

        // Détermination de la destination de départ
        when {
            !onboardingFinished -> {
                // Premier lancement : on force l'Onboarding
                navGraph.setStartDestination(R.id.onboardingFragment)
            }
            currentUser == null -> {
                // Onboarding fait mais pas connecté : direction Auth
                navGraph.setStartDestination(R.id.authFragment)
            }
            else -> {
                // Déjà connecté : direction le Feed
                navGraph.setStartDestination(R.id.feedFragment)
            }
        }

        // On applique le graphe configuré au NavController
        navController.graph = navGraph

        // --- 2. GESTION DE LA BOTTOM NAV ---
        binding.bottomNav.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                // Liste des écrans où la barre du bas et le FAB sont visibles
                R.id.feedFragment, R.id.socialFragment, R.id.itineraryFragment, R.id.profileFragment -> {
                    binding.bottomNav.visibility = View.VISIBLE
                    binding.fabAdd.visibility = View.VISIBLE
                }
                // On cache tout pour l'Onboarding et l'Auth
                else -> {
                    binding.bottomNav.visibility = View.GONE
                    binding.fabAdd.visibility = View.GONE
                }
            }
        }
    }
}