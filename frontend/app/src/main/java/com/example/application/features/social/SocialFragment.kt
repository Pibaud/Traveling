package com.example.application.features.social

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.application.databinding.FragmentSocialBinding

class SocialFragment : Fragment() {

    private var _binding: FragmentSocialBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSocialBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // C'est ici que nous lierons les RecyclerViews et les clics plus tard

        binding.btnCreateGroup.setOnClickListener {
            // Navigation vers l'écran de création de groupe (à faire plus tard)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}