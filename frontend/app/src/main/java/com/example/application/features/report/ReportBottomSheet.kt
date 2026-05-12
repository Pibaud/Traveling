package com.example.application.features.report

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.application.databinding.BottomSheetReportBinding
import com.example.application.model.RetrofitInstance
import com.example.application.model.ReportRequest
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class ReportBottomSheet(private val postId: String? = null) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetReportBinding? = null
    private val binding get() = _binding!!



    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvTitle.text = if (postId != null) "Signaler ce post" else "Signaler un problème"

        binding.btnSubmit.setOnClickListener {
            val description = binding.etDescription.text.toString().trim()
            if (description.isNotEmpty()) {
                submitReport(description)
            } else {
                Toast.makeText(context, "Veuillez décrire le problème", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun submitReport(description: String) {
        binding.btnSubmit.isEnabled = false
        binding.btnSubmit.text = "Envoi..."

        val currentUser = Firebase.auth.currentUser
        if (currentUser == null) {
            Toast.makeText(context, "Vous devez être connecté", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val request = ReportRequest(description, postId)
                // On passe le currentUser.uid en paramètre
                val response = RetrofitInstance.api.submitReport(currentUser.uid, request)

                if (response.isSuccessful) {
                    Toast.makeText(context, "Merci pour votre signalement", Toast.LENGTH_SHORT).show()
                    dismiss()
                } else {
                    Toast.makeText(context, "Erreur lors de l'envoi", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Erreur de connexion", Toast.LENGTH_SHORT).show()
            } finally {
                binding.btnSubmit.isEnabled = true
                binding.btnSubmit.text = "Envoyer"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}