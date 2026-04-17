package com.example.application.utils // Ajuste ton package

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.findNavController
import com.example.application.R
import com.example.application.databinding.BottomSheetGuestUpsellBinding // Ajuste selon ton nommage
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class GuestUpsellBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetGuestUpsellBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetGuestUpsellBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Redirection vers l'AuthFragment
        binding.btnUpsellLogin.setOnClickListener {
            dismiss() // On ferme la popup
            // On utilise l'activité pour trouver le nav contrôleur global
            requireActivity().findNavController(R.id.nav_host_fragment).navigate(R.id.authFragment)
        }

        binding.btnUpsellCancel.setOnClickListener {
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}