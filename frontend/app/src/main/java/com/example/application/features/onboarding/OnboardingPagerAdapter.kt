package com.example.application.features.onboarding

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class OnboardingPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    // On a 3 étapes distinctes selon ton plan
    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> Step1CoverFragment()   // Écran Titre + "Commencer"
            1 -> Step2SocialFragment()  // Grille inclinée "Travel Share"
            2 -> Step3PathFragment()    // Itinéraire "Travel Path"
            else -> Step1CoverFragment()
        }
    }
}