package com.example.application.features.post

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.application.R
import com.example.application.databinding.FragmentCreatePostBinding
import com.example.application.model.Place
import com.example.application.utils.setupPlaceAutocomplete
import java.util.Collections
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class CreatePostFragment : Fragment(R.layout.fragment_create_post) {

    private var _binding: FragmentCreatePostBinding? = null
    private val binding get() = _binding!!
    private lateinit var photoAdapter: PhotoAdapter
    private var selectedPlace: Place? = null

    // Données simulées pour l'instant (les URIs de tes photos)
    private val photosList = mutableListOf<String>()

    // --- LES LAUNCHERS (INDISPENSABLES POUR RÉSOUDRE L'ERREUR) ---

    // 1. Pour la Galerie
    private val pickImageLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        uri?.let { addPhotoToList(it.toString()) }
    }

    // 2. Pour la Caméra
    private val takePictureLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            // Pour l'instant on simule l'ajout car transformer un Bitmap en URI demande un stockage temporaire
            addPhotoToList("camera_placeholder")
            Toast.makeText(requireContext(), "Photo capturée !", Toast.LENGTH_SHORT).show()
        }
    }

    // 3. Pour les Permissions
    private val requestPermissionLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[android.Manifest.permission.CAMERA] == true) {
            takePictureLauncher.launch(null)
        } else {
            Toast.makeText(requireContext(), "Permission caméra refusée", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() {
        photoAdapter = PhotoAdapter(photosList) { position ->
            // Logique de suppression
            photosList.removeAt(position)
            photoAdapter.notifyItemRemoved(position)
            updatePhotoCount()

            // Réafficher le bouton ajouter s'il était caché
            binding.btnAddPhoto.visibility = View.VISIBLE
        }

        binding.rvPhotos.adapter = photoAdapter
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCreatePostBinding.bind(view)

        setupRecyclerView()
        setupToolbar()
        setupListeners()
        setupDragAndDrop()

        // --- AJOUTE CETTE LIGNE ---
        binding.btnAddPhoto.setOnClickListener {
            if (photosList.size >= 5) {
                Toast.makeText(requireContext(), "Limite de 5 photos atteinte", Toast.LENGTH_SHORT).show()
            } else {
                showPhotoOptionsDialog()
            }
        }

        binding.etLocation.setupPlaceAutocomplete(
            coroutineScope = viewLifecycleOwner.lifecycleScope,
            apiService = RetrofitInstance.api
        ) { place ->
            // Ce bloc est exécuté quand l'utilisateur clique sur un résultat de la liste
            selectedPlace = place
            Toast.makeText(requireContext(), "Lieu lié : ${place.name}", Toast.LENGTH_SHORT).show()
            validatePublishState()
        }

        validatePublishState()
    }

    private fun isPostValid(): Boolean {
        val hasPhotos = photosList.isNotEmpty()
        val hasDescription = binding.etDescription.text.toString().trim().isNotEmpty()
        val hasVisibility = binding.switchPublic.isChecked || binding.switchGroups.isChecked
        val hasLocation = selectedPlace != null // On vérifie qu'un lieu est bien lié

        return hasPhotos && hasDescription && hasVisibility && hasLocation
    }

    private fun openGallery() {
        pickImageLauncher.launch("image/*")
    }

    private fun openCamera() {
        requestPermissionLauncher.launch(arrayOf(android.Manifest.permission.CAMERA))
    }

    private fun showPhotoOptionsDialog() {
        val options = arrayOf("Prendre une photo", "Choisir depuis la galerie")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Ajouter une photo")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openCamera()
                    1 -> openGallery()
                }
            }
            .show()
    }

    private fun addPhotoToList(photoUri: String) {
        photosList.add(photoUri)
        updatePhotoCount()

        // On prévient l'adapter et on scrolle
        photoAdapter.notifyItemInserted(photosList.size - 1)
        binding.rvPhotos.scrollToPosition(photosList.size - 1)

        // Cache le bouton si on a 5 photos
        if (photosList.size >= 5) {
            binding.btnAddPhoto.visibility = View.GONE
        }
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnPublish.setOnClickListener {
            if (isPostValid()) {
                // Action de publication ici
            }
        }
    }

    private fun setupListeners() {
        // Surveille la saisie de texte
        binding.etDescription.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validatePublishState()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Surveille les switches
        binding.switchPublic.setOnCheckedChangeListener { _, _ -> validatePublishState() }
        binding.switchGroups.setOnCheckedChangeListener { _, _ -> validatePublishState() }
    }

    // --- LE MOTEUR DE DRAG & DROP ---
    private fun setupDragAndDrop() {
        // On configure le comportement de l'ItemTouchHelper
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, // Sens du déplacement (Horizontal)
            0 // Pas de suppression par glissement (swipe to dismiss)
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition

                // Met à jour la liste de données
                Collections.swap(photosList, fromPosition, toPosition)

                // Notifie l'adaptateur de l'animation de déplacement
                binding.rvPhotos.adapter?.notifyItemMoved(fromPosition, toPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Inutilisé ici
            }

            // Permet de donner un léger effet visuel (zoom ou élévation) lors de la saisie
            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.alpha = 0.7f
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.alpha = 1.0f
            }
        }

        // On attache la logique au RecyclerView
        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(binding.rvPhotos)
    }

    private fun validatePublishState() {
        val isValid = isPostValid()

        binding.btnPublish.isEnabled = isValid

        if (isValid) {
            // Actif : Couleur principale (rouge foncé de ta maquette)
            binding.btnPublish.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary_color))
        } else {
            // Inactif : Gris
            binding.btnPublish.setTextColor(android.graphics.Color.parseColor("#BDBDBD"))
        }
    }

    private fun updatePhotoCount() {
        binding.tvPhotoCount.text = "(${photosList.size}/5)"
        validatePublishState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}