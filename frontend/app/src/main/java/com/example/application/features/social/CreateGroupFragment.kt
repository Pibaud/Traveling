package com.example.application.features.social

import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import coil.load
import com.example.application.R
import com.example.application.databinding.FragmentCreateGroupBinding
import com.example.application.model.CreateGroupRequest
import com.example.application.utils.GroupThemes
import com.google.android.material.chip.Chip
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CreateGroupFragment : Fragment() {

    private var _binding: FragmentCreateGroupBinding? = null
    private val binding get() = _binding!!

    private var selectedPhotoUri: Uri? = null
    private val selectedTags = mutableSetOf<String>()

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedPhotoUri = it
            binding.ivGroupPhoto.load(it) {
                crossfade(true)
                listener(
                    onSuccess = { _, _ -> binding.ivAddPhotoIcon.isVisible = false },
                    onError = { _, _ -> binding.ivAddPhotoIcon.isVisible = true }
                )
            }
            validateForm()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCreateGroupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupTags()
        setupListeners()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.btnPublishGroup.setOnClickListener { if (isFormValid()) createGroup() }
    }

    private fun setupListeners() {
        binding.flGroupPhoto.setOnClickListener { pickImageLauncher.launch("image/*") }

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { validateForm() }
            override fun afterTextChanged(s: Editable?) {}
        }
        binding.etGroupName.addTextChangedListener(textWatcher)
        binding.etGroupDesc.addTextChangedListener(textWatcher)

        binding.switchPrivacy.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.switchPrivacy.text = "Groupe public"
                binding.tvPrivacyDesc.text = "N'importe qui peut trouver et rejoindre ce groupe."
            } else {
                binding.switchPrivacy.text = "Groupe privé"
                binding.tvPrivacyDesc.text = "Les utilisateurs devront envoyer une demande pour rejoindre ce groupe."
            }
        }
    }

    private fun setupTags() {
        GroupThemes.predefinedTags.forEach { (tagName, iconResId) ->
            val chip = Chip(requireContext()).apply {
                text = tagName
                isCheckable = true
                chipIcon = ContextCompat.getDrawable(requireContext(), iconResId)
                chipIconSize = 40f
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        if (selectedTags.size >= 3) {
                            this.isChecked = false
                            Toast.makeText(context, "Vous ne pouvez choisir que 3 thèmes maximum.", Toast.LENGTH_SHORT).show()
                        } else {
                            selectedTags.add(tagName)
                        }
                    } else {
                        selectedTags.remove(tagName)
                    }
                    validateForm()
                }
            }
            binding.chipGroupThemes.addView(chip)
        }
    }

    private fun isFormValid(): Boolean {
        val hasName = binding.etGroupName.text.toString().trim().length >= 3
        val hasDesc = binding.etGroupDesc.text.toString().trim().isNotEmpty()
        val hasPhoto = selectedPhotoUri != null
        val hasTags = selectedTags.isNotEmpty()
        return hasName && hasDesc && hasPhoto && hasTags
    }

    private fun validateForm() {
        val isValid = isFormValid()
        binding.btnPublishGroup.isEnabled = isValid
        binding.btnPublishGroup.setTextColor(android.graphics.Color.parseColor(if (isValid) "#7A1C2A" else "#BDBDBD"))
    }

    private fun createGroup() {
        val currentUser = Firebase.auth.currentUser
        if (currentUser == null) {
            Toast.makeText(requireContext(), "Vous devez être connecté", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnPublishGroup.isEnabled = false
        binding.btnPublishGroup.text = "Création..."

        lifecycleScope.launch {
            try {
                val photoUrl = uploadGroupAvatarToFirebase(selectedPhotoUri!!)
                val request = CreateGroupRequest(
                    name = binding.etGroupName.text.toString().trim(),
                    description = binding.etGroupDesc.text.toString().trim(),
                    isPublic = binding.switchPrivacy.isChecked,
                    tags = selectedTags.toList(),
                    photoUrl = photoUrl,
                    authorId = currentUser.uid
                )

                val response = RetrofitInstance.api.createGroup(request)
                if (response.isSuccessful) {
                    Toast.makeText(requireContext(), "Groupe créé !", Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                } else {
                    Toast.makeText(requireContext(), "Erreur serveur : ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Erreur : ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.btnPublishGroup.isEnabled = true
                binding.btnPublishGroup.text = "Créer"
            }
        }
    }

    private suspend fun uploadGroupAvatarToFirebase(uri: Uri): String {
        val storageRef = Firebase.storage.reference
        val userId = Firebase.auth.currentUser?.uid ?: throw Exception("Non connecté")
        val fileName = "${userId}_${UUID.randomUUID()}.jpg"
        val imageRef = storageRef.child("avatars/groups/$fileName")
        imageRef.putFile(uri).await()
        return imageRef.downloadUrl.await().toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}