package com.example.application.features.onboarding

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.example.application.R
import com.example.application.databinding.Step2SocialFragmentBinding

class Step2SocialFragment : Fragment() {

    private var _binding: Step2SocialFragmentBinding? = null
    private val binding get() = _binding!!

    // Le moteur de l'animation
    private val scrollHandler = Handler(Looper.getMainLooper())
    private val autoScrollRunnable = object : Runnable {
        override fun run() {
            // Vitesse de défilement (3 pixels)
            binding.rvColumn1.scrollBy(0, 3)  // Descend
            binding.rvColumn2.scrollBy(0, -3) // Monte (sens contraire)
            binding.rvColumn3.scrollBy(0, 3)  // Descend

            // On relance la fonction dans 16ms
            scrollHandler.postDelayed(this, 16)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = Step2SocialFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupGrids()
        setupNavigation()
    }

    private fun setupGrids() {
        // Fausse liste d'images pour le mock
        val images = listOf(
            "https://picsum.photos/seed/a/400/600",
            "https://picsum.photos/seed/b/400/600",
            "https://picsum.photos/seed/c/400/600",
            "https://picsum.photos/seed/d/400/600",
            "https://picsum.photos/seed/e/400/600"
        )

        val layoutManager1 = object : LinearLayoutManager(context) { override fun canScrollVertically() = false }
        val layoutManager2 = object : LinearLayoutManager(context) { override fun canScrollVertically() = false }
        val layoutManager3 = object : LinearLayoutManager(context) { override fun canScrollVertically() = false }

        binding.rvColumn1.layoutManager = layoutManager1
        binding.rvColumn2.layoutManager = layoutManager2
        binding.rvColumn3.layoutManager = layoutManager3

        binding.rvColumn1.adapter = ImageGridAdapter(images.shuffled())
        binding.rvColumn2.adapter = ImageGridAdapter(images.shuffled())
        binding.rvColumn3.adapter = ImageGridAdapter(images.shuffled())

        // Astuce : On place la colonne 2 très loin dans la liste pour qu'elle puisse scroller vers le haut à l'infini
        binding.rvColumn2.scrollToPosition(10000)
    }

    private fun setupNavigation() {
        val viewPager = activity?.findViewById<ViewPager2>(R.id.viewPager)

        // Bouton Retour (Page 1)
        binding.btnBack.setOnClickListener {
            viewPager?.currentItem = 0
        }

        // Bouton Suivant (Page 3)
        binding.btnNext.setOnClickListener {
            viewPager?.currentItem = 2
        }
    }

    // On démarre l'animation quand l'écran est visible
    override fun onResume() {
        super.onResume()
        scrollHandler.post(autoScrollRunnable)
    }

    // On arrête l'animation pour économiser la batterie quand on quitte l'écran
    override fun onPause() {
        super.onPause()
        scrollHandler.removeCallbacks(autoScrollRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}