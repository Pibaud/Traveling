package com.example.application.features.main

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.application.R
import com.example.application.databinding.MainActivityBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: MainActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MainActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // --- 1. LOGIQUE DE REDIRECTION (ROUTING) ---
        // On récupère le graphe de navigation actuel
        val navInflater = navController.navInflater
        val navGraph = navInflater.inflate(R.navigation.nav_graph)

        // On lit les SharedPreferences pour savoir si l'onboarding est fini
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val onboardingFinished = prefs.getBoolean("onboarding_finished", false)

        // Si l'onboarding n'est pas fini, on force le démarrage sur l'Onboarding
        if (!onboardingFinished) {
            navGraph.setStartDestination(R.id.onboardingFragment)
        } else {
            // Sinon, on démarre normalement sur l'accueil (Feed)
            // Assure-toi que R.id.explorerFragment est bien l'ID de ton accueil dans le nav_graph.xml
            navGraph.setStartDestination(R.id.explorerFragment)
        }

        // On applique le graphe modifié au NavController
        navController.graph = navGraph

        // --- 2. GESTION DE LA BOTTOM NAV ---
        binding.bottomNav.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                // Écrans où la barre du bas doit être visible
                R.id.explorerFragment, R.id.socialFragment, R.id.itineraryFragment, R.id.profileFragment -> {
                    binding.bottomNav.visibility = View.VISIBLE
                    binding.fabAdd.visibility = View.VISIBLE
                }
                // Pour Onboarding, Auth, etc... on cache la barre
                else -> {
                    binding.bottomNav.visibility = View.GONE
                    binding.fabAdd.visibility = View.GONE
                }
            }
        }
    }
}