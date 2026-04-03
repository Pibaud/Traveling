package com.example.application.features.onboarding

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.example.application.R
import com.example.application.databinding.Step3PathFragmentBinding

class Step3PathFragment : Fragment() {

    private var _binding: Step3PathFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = Step3PathFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val viewPager = activity?.findViewById<ViewPager2>(R.id.viewPager)

        // Retour à la page 2 (Travel Share)
        binding.btnBack.setOnClickListener {
            viewPager?.currentItem = 1
        }

        // Fin de l'Onboarding
        binding.btnFinish.setOnClickListener {
            // 1. On sauvegarde l'état pour ne plus jamais revoir cet écran
            val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("onboarding_finished", true).apply()

            // 2. Navigation vers la suite (l'écran d'authentification)
            // findNavController().navigate(R.id.action_onboarding_to_auth)

            // Pour tester en attendant d'avoir fait l'écran d'Auth :
            Toast.makeText(requireContext(), "Onboarding terminé ! Direction Auth...", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}