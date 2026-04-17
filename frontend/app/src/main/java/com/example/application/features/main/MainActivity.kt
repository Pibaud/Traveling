package com.example.application.features.main

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.application.R
import com.example.application.databinding.MainActivityBinding
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.example.application.utils.GuestUpsellBottomSheet // N'oublie pas l'import
import androidx.navigation.ui.NavigationUI

class MainActivity : AppCompatActivity() {
    private lateinit var binding: MainActivityBinding

    // État du menu FAB
    private var isFabMenuOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MainActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // --- 1. LOGIQUE DE REDIRECTION ---
        val navInflater = navController.navInflater
        val navGraph = navInflater.inflate(R.navigation.nav_graph)

        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val onboardingFinished = prefs.getBoolean("onboarding_finished", false)
        val currentUser = Firebase.auth.currentUser

        when {
            !onboardingFinished -> navGraph.setStartDestination(R.id.onboardingFragment)
            currentUser == null -> navGraph.setStartDestination(R.id.authFragment)
            else -> navGraph.setStartDestination(R.id.feedFragment)
        }
        navController.graph = navGraph

        // --- 2. GESTION DE LA BOTTOM NAV ---
        binding.bottomNav.setOnItemSelectedListener { item ->
            val isGuest = Firebase.auth.currentUser?.isAnonymous == true

            // Si c'est un invité et qu'il clique sur Profil ou Itinéraires
            if (isGuest && (item.itemId == R.id.profileFragment || item.itemId == R.id.itineraryFragment)) {
                GuestUpsellBottomSheet().show(supportFragmentManager, "GuestUpsell")
                return@setOnItemSelectedListener false // Bloque le changement d'onglet
            }

            // Sinon, on laisse Android gérer la navigation normale
            NavigationUI.onNavDestinationSelected(item, navController)
            true
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            // Si on change d'écran, on referme le menu s'il était ouvert
            if (isFabMenuOpen) toggleFabMenu()

            when (destination.id) {
                R.id.feedFragment, R.id.socialFragment, R.id.itineraryFragment, R.id.profileFragment -> {
                    binding.bottomNav.visibility = View.VISIBLE
                    binding.fabAdd.visibility = View.VISIBLE
                }
                else -> {
                    binding.bottomNav.visibility = View.GONE
                    binding.fabAdd.visibility = View.GONE
                }
            }
        }

        // --- 3. CONFIGURATION DU MENU FAB ---
        setupFabMenu()
    }

    private fun setupFabMenu() {
        // Clic sur le bouton principal
        binding.fabAdd.setOnClickListener {
            val isGuest = Firebase.auth.currentUser?.isAnonymous == true
            if (isGuest) {
                GuestUpsellBottomSheet().show(supportFragmentManager, "GuestUpsell")
                return@setOnClickListener // On arrête le code ici
            }
            toggleFabMenu()
        }

        // Clic dans le vide (sur le calque flouté) pour fermer le menu
        binding.overlayDim.setOnClickListener {
            if (isFabMenuOpen) toggleFabMenu()
        }

        // Clic sur Créer un Post
        binding.fabPost.setOnClickListener {
            toggleFabMenu()
            // Navigation globale vers le nouveau fragment
            val navController = (supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController
            navController.navigate(R.id.createPostFragment)
        }

        // Clic sur Créer un Itinéraire
        binding.fabItinerary.setOnClickListener {
            toggleFabMenu()
            // TODO: Naviguer vers la création d'itinéraire
        }
    }

    private fun toggleFabMenu() {
        isFabMenuOpen = !isFabMenuOpen
        val duration = 250L

        if (isFabMenuOpen) {
            // 1. Appliquer le flou (Uniquement sur Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                binding.contentContainer.setRenderEffect(
                    RenderEffect.createBlurEffect(15f, 15f, Shader.TileMode.CLAMP)
                )
            }

            // 2. Afficher et animer le calque d'assombrissement
            binding.overlayDim.visibility = View.VISIBLE
            binding.overlayDim.animate().alpha(1f).setDuration(duration).start()

            // 3. Tourner le bouton principal à 45° (Le + devient un X)
            binding.fabAdd.animate().rotation(45f).setDuration(duration).start()

            // 4. Faire apparaître les sous-boutons (Alpha + translation)
            binding.fabPost.apply {
                visibility = View.VISIBLE
                translationY = 50f // Départ un peu plus bas
                animate().alpha(1f).translationY(0f).setDuration(duration).start()
            }

            binding.fabItinerary.apply {
                visibility = View.VISIBLE
                translationY = 50f
                animate().alpha(1f).translationY(0f).setDuration(duration).setStartDelay(50).start()
            }

            binding.tvLabelPost.apply {
                visibility = View.VISIBLE
                translationY = 50f
                animate().alpha(1f).translationY(0f).setDuration(duration).start()
            }

            binding.tvLabelItinerary.apply {
                visibility = View.VISIBLE
                translationY = 50f
                animate().alpha(1f).translationY(0f).setDuration(duration).setStartDelay(50).start()
            }

        } else {
            // 1. Retirer le flou
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                binding.contentContainer.setRenderEffect(null)
            }

            // 2. Cacher le calque d'assombrissement
            binding.overlayDim.animate().alpha(0f).setDuration(duration).withEndAction {
                binding.overlayDim.visibility = View.GONE
            }.start()

            // 3. Remettre le bouton principal droit
            binding.fabAdd.animate().rotation(0f).setDuration(duration).start()

            // 4. Faire disparaître les sous-boutons
            binding.fabPost.animate().alpha(0f).translationY(50f).setDuration(duration).withEndAction {
                binding.fabPost.visibility = View.GONE
            }.start()

            binding.fabItinerary.animate().alpha(0f).translationY(50f).setDuration(duration).withEndAction {
                binding.fabItinerary.visibility = View.GONE
            }.start()

            binding.tvLabelPost.animate().alpha(0f).translationY(50f).setDuration(duration).withEndAction {
                binding.tvLabelPost.visibility = View.GONE
            }.start()

            binding.tvLabelItinerary.animate().alpha(0f).translationY(50f).setDuration(duration).withEndAction {
                binding.tvLabelItinerary.visibility = View.GONE
            }.start()
        }
    }
}