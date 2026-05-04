package com.example.application.features.path

import android.app.Dialog
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.application.BuildConfig
import com.example.application.databinding.FragmentItineraryDetailsSheetBinding
import com.example.application.model.ItineraryResponse
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.WellKnownTileServer
import com.mapbox.mapboxsdk.annotations.IconFactory
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.annotations.PolylineOptions
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.geometry.LatLngBounds
import kotlinx.coroutines.launch
import com.example.application.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class ItineraryDetailsBottomSheet(
    private val itinerary: ItineraryResponse // PLUS DE PARAMÈTRE SUPPLÉMENTAIRE !
) : BottomSheetDialogFragment() {

    private var _binding: FragmentItineraryDetailsSheetBinding? = null
    private val binding get() = _binding!!

    private var mapboxMapRef: com.mapbox.mapboxsdk.maps.MapboxMap? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val key = BuildConfig.MAPTILER_API_KEY.replace("\"", "")
        Mapbox.getInstance(requireContext(), key, WellKnownTileServer.MapTiler)
        _binding = FragmentItineraryDetailsSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    @Suppress("DEPRECATION")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.mapViewDetails.onCreate(savedInstanceState)

        binding.mapContainer.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> binding.nestedScrollView.requestDisallowInterceptTouchEvent(
                    true
                )

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> binding.nestedScrollView.requestDisallowInterceptTouchEvent(
                    false
                )
            }
            false
        }

        binding.mapViewDetails.getMapAsync { mapboxMap ->
            mapboxMapRef = mapboxMap
            val key = BuildConfig.MAPTILER_API_KEY.replace("\"", "")
            val styleUrl = "https://api.maptiler.com/maps/streets-v2/style.json?key=$key"

            mapboxMap.setStyle(styleUrl) { style ->
                val points = itinerary.steps.map { LatLng(it.latitude, it.longitude) }

                if (points.isNotEmpty()) {
                    itinerary.steps.forEachIndexed { index, place ->
                        val number = (index + 1).toString()
                        val icon = IconFactory.getInstance(requireContext())
                            .fromBitmap(createNumberedPin(number))
                        mapboxMap.addMarker(
                            MarkerOptions().position(LatLng(place.latitude, place.longitude))
                                .title("${index + 1}. ${place.name}").icon(icon)
                        )
                    }

                    mapboxMap.addPolyline(
                        PolylineOptions().addAll(points).color(Color.BLACK).width(5f)
                    )

                    if (points.size > 1) {
                        val bounds = LatLngBounds.Builder().includes(points).build()
                        mapboxMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
                    } else if (points.size == 1) {
                        mapboxMap.animateCamera(CameraUpdateFactory.newLatLngZoom(points[0], 14.0))
                    }
                }
            }
        }

        binding.tvDetailName.text = itinerary.name
        binding.tvDetailPrice.text = "${itinerary.totalPrice} €"
        binding.tvDetailInfos.text = "${itinerary.totalDuration} heures\n" +
                (if (itinerary.mealIncluded) "Repas compris" else "Repas non compris")

        try {
            binding.cvHeader.setCardBackgroundColor(Color.parseColor(itinerary.hexColor))
        } catch (e: Exception) {
        }

        // =======================================================
        // === LA DÉTECTION INTELLIGENTE DE LA SITUATION 1 OU 2 ===
        // =======================================================
        val isGenerated = (itinerary.id == null || itinerary.id == 0) // Pas d'ID = Situation 1 !
        val density = resources.displayMetrics.density

        if (isGenerated) {
            // SITUATION 1: On vient de générer
            binding.topActionBar.visibility = View.GONE
            binding.bottomActionBar.visibility = View.VISIBLE
            binding.btnShare.visibility = View.GONE

            // On ajoute 80dp de vide à la fin de la page pour ne pas cacher le contenu sous la barre
            binding.nestedScrollView.setPadding(0, 0, 0, (80 * density).toInt())

            binding.btnSave.setOnClickListener { showSaveDialog() }
            binding.btnRegenerate.setOnClickListener { handleRegenerateAction() }

        } else {
            // SITUATION 2: On vient de la liste
            binding.topActionBar.visibility = View.VISIBLE
            binding.bottomActionBar.visibility = View.GONE

            // On libère la place en bas pour que le fragment prenne tout l'écran ! (Juste 16dp de marge propre)
            binding.nestedScrollView.setPadding(0, 0, 0, (16 * density).toInt())

            // Logique du Like en haut
            val ivLike = binding.ivDetailLike
            val userId = Firebase.auth.currentUser?.uid ?: ""

            val ivDelete = binding.ivDetailDelete

            // Si l'utilisateur actuel est le créateur de l'itinéraire, on affiche la poubelle
            if (itinerary.userId == userId) {
                ivDelete.visibility = View.VISIBLE

                ivDelete.setOnClickListener {
                    // On demande confirmation avant de supprimer définitivement
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Supprimer l'itinéraire")
                        .setMessage("Êtes-vous sûr de vouloir supprimer définitivement ce parcours ? Cette action est irréversible.")
                        .setPositiveButton("Supprimer") { _, _ ->
                            deleteItinerary()
                        }
                        .setNegativeButton("Annuler", null)
                        .show()
                }
            } else {
                ivDelete.visibility = View.GONE
            }

            if (itinerary.isLiked) {
                ivLike.setImageResource(R.drawable.ic_heart_filled)
                ivLike.setColorFilter(Color.RED)
            } else {
                ivLike.setImageResource(R.drawable.ic_heart_empty)
                ivLike.setColorFilter(Color.GRAY)
            }

            ivLike.setOnClickListener {
                if (itinerary.id != null) {
                    itinerary.isLiked = !itinerary.isLiked
                    if (itinerary.isLiked) {
                        ivLike.setImageResource(R.drawable.ic_heart_filled)
                        ivLike.setColorFilter(Color.RED)
                    } else {
                        ivLike.setImageResource(R.drawable.ic_heart_empty)
                        ivLike.setColorFilter(Color.GRAY)
                    }
                    lifecycleScope.launch {
                        try {
                            RetrofitInstance.api.toggleLike(userId, itinerary.id!!)
                        } catch (e: Exception) {
                        }
                    }
                }
            }

            // Logique du bouton Régénérer en haut
            binding.ivDetailRegenerate.setOnClickListener { handleRegenerateAction() }
        }

        binding.ivDetailDownload.setOnClickListener {
            if (itinerary.id != null) {
                startPdfDownloadProcess(itinerary.id, itinerary.name)
            }
        }

        val adapter = ItineraryStepAdapter(itinerary.steps) { clickedPlace ->
            Toast.makeText(
                requireContext(),
                "Ouverture des détails de ${clickedPlace.name}...",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.vpSteps.adapter = adapter
        binding.vpSteps.offscreenPageLimit = 1
        val recyclerView = binding.vpSteps.getChildAt(0) as RecyclerView
        recyclerView.setPadding(0, 0, 100, 0)
        recyclerView.clipToPadding = false

        binding.vpSteps.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                val currentPlace = itinerary.steps[position]
                val cameraPosition = CameraPosition.Builder()
                    .target(LatLng(currentPlace.latitude, currentPlace.longitude))
                    .zoom(15.0)
                    .build()

                mapboxMapRef?.animateCamera(
                    CameraUpdateFactory.newCameraPosition(cameraPosition),
                    800
                )
            }
        })
    }

    private fun deleteItinerary() {
        lifecycleScope.launch {
            try {
                // Assure-toi que l'itinéraire a bien un ID
                val itineraryId = itinerary.id ?: return@launch
                val userId = Firebase.auth.currentUser?.uid ?: ""

                // Appel réseau vers ton backend Ktor
                val response = RetrofitInstance.api.deletePath(itineraryId, userId)

                if (response.isSuccessful) {
                    Toast.makeText(requireContext(), "Itinéraire supprimé", Toast.LENGTH_SHORT)
                        .show()
                    dismiss() // On ferme le panneau
                    // 💡 À NOTER : Quand tu fermeras le panneau, il faudra idéalement que
                    // ton Fragment principal (PathFragment) rafraîchisse sa liste pour voir l'itinéraire disparaître !
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Erreur lors de la suppression",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Impossible de joindre le serveur",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun handleRegenerateAction() {
        val categories = itinerary.steps.map { it.category.name.uppercase() }.distinct()

        val firstStepTime = itinerary.steps.firstOrNull()?.arrivalTime ?: "09:30"
        val timeParts = firstStepTime.split(":")
        val startMin = if (timeParts.size == 2) {
            (timeParts[0].toIntOrNull() ?: 9) * 60 + (timeParts[1].toIntOrNull() ?: 30)
        } else {
            540
        }

        val request = com.example.application.model.GeneratePathRequest(
            categories = categories,
            selectedPlaceIds = emptyList(),
            budgetMax = itinerary.totalPrice,
            durationHours = itinerary.totalDuration,
            effortLevel = itinerary.avgEffort.toInt().coerceIn(1, 3),
            weatherTolerance = 2,
            mealIncluded = itinerary.mealIncluded,
            startTimeMinutes = startMin
        )

        CreatePathFragment.draftRequest = request

        val navController = parentFragment?.findNavController()
        dismiss()

        try {
            navController?.navigate(R.id.createPathFragment)
        } catch (e: Exception) {
            navController?.popBackStack(R.id.createPathFragment, false)
        }
    }

    private fun createNumberedPin(number: String): Bitmap {
        val size = 80
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paintCircle = Paint().apply {
            color = Color.parseColor("#E53935")
            isAntiAlias = true
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paintCircle)

        val paintBorder = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }
        canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - 2f, paintBorder)

        val paintText = Paint().apply {
            color = Color.WHITE
            textSize = 40f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }

        val xPos = (canvas.width / 2).toFloat()
        val yPos = (canvas.height / 2 - (paintText.descent() + paintText.ascent()) / 2)
        canvas.drawText(number, xPos, yPos, paintText)

        return bitmap
    }

    private fun showSaveDialog() {
        val context = requireContext()

        val editText = EditText(context).apply {
            hint = "Ex: Mon super week-end"
            setPadding(50, 40, 50, 40)
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("Sauvegarder l'itinéraire")
            .setMessage("Donnez un nom à votre parcours :")
            .setView(editText)
            .setPositiveButton("Sauvegarder") { _, _ ->
                val itineraryName = editText.text.toString()
                if (itineraryName.isNotBlank()) {
                    saveItineraryToDatabase(itineraryName)
                } else {
                    Toast.makeText(context, "Le nom ne peut pas être vide", Toast.LENGTH_SHORT)
                        .show()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun saveItineraryToDatabase(name: String) {
        val userId = Firebase.auth.currentUser?.uid ?: return

        val colors = listOf("#2E7D32", "#1565C0", "#C62828", "#EF6C00", "#00838F", "#6A1B9A")
        val randomColor = colors.random()

        lifecycleScope.launch {
            try {
                val request = com.example.application.model.SavePathRequest(
                    userId = userId,
                    name = name,
                    hexColor = randomColor,
                    totalPrice = itinerary.totalPrice,
                    totalDuration = itinerary.totalDuration,
                    avgEffort = itinerary.avgEffort,
                    mealIncluded = itinerary.mealIncluded,
                    placeIds = itinerary.steps.map { it.id }
                )

                val response = RetrofitInstance.api.savePath(request)

                if (response.isSuccessful) {
                    Toast.makeText(
                        requireContext(),
                        "Itinéraire sauvegardé avec succès !",
                        Toast.LENGTH_SHORT
                    ).show()
                    dismiss()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Erreur du serveur : ${response.code()}",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Impossible de joindre le serveur : ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        binding.mapViewDetails.onStart()

        val bottomSheet = requireView().parent as? View
        bottomSheet?.let { sheet ->
            val behavior = BottomSheetBehavior.from(sheet)
            val layoutParams = sheet.layoutParams

            val windowHeight = requireActivity().resources.displayMetrics.heightPixels
            layoutParams.height = (windowHeight * 0.8).toInt()
            sheet.layoutParams = layoutParams

            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapViewDetails.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapViewDetails.onPause()
    }

    override fun onStop() {
        super.onStop()
        binding.mapViewDetails.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapViewDetails.onLowMemory()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.mapViewDetails.onDestroy()
        _binding = null
    }

    // 1. Le clic sur le bouton déclenche ceci : on demande la photo à la carte
    private fun startPdfDownloadProcess(itineraryId: Int, itineraryName: String) {
        Toast.makeText(requireContext(), "Préparation de la carte...", Toast.LENGTH_SHORT).show()

        // Si la carte n'est pas prête, on télécharge sans image
        if (mapboxMapRef == null) {
            downloadPdf(itineraryId, itineraryName, null)
            return
        }

        // On prend une "photo" de la carte visible à l'écran
        mapboxMapRef?.snapshot { snapshotBitmap ->
            if (snapshotBitmap != null) {
                // On transforme l'image en texte Base64
                val base64Image = bitmapToBase64(snapshotBitmap)
                downloadPdf(itineraryId, itineraryName, base64Image)
            } else {
                // Échec de la capture, on génère sans image
                downloadPdf(itineraryId, itineraryName, null)
            }
        }
    }

    // 2. Utilitaire pour convertir l'image
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        // On compresse en JPEG pour que l'envoi au serveur soit plus rapide
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    // 3. L'envoi au serveur (modifié pour inclure l'image Base64)
    private fun downloadPdf(itineraryId: Int, itineraryName: String, base64MapImage: String?) {
        lifecycleScope.launch {
            try {
                Toast.makeText(requireContext(), "Génération du PDF...", Toast.LENGTH_SHORT).show()

                // NOUVEAU APPEL (On va modifier Retrofit juste après)
                val response = RetrofitInstance.api.downloadItineraryPdf(itineraryId, base64MapImage)

                if (response.isSuccessful && response.body() != null) {
                    val isSaved = savePdfToDownloads(response.body()!!, itineraryName)
                    if (isSaved) {
                        Toast.makeText(requireContext(), "PDF sauvegardé dans vos Téléchargements !", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(requireContext(), "Erreur lors de l'enregistrement du fichier.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Erreur du serveur lors de la génération du PDF.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Impossible de télécharger le PDF : ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    private suspend fun savePdfToDownloads(
        body: okhttp3.ResponseBody,
        itineraryName: String
    ): Boolean {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val safeName = itineraryName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
                val fileName = "Itineraire_${safeName}.pdf"

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    // --- POUR ANDROID 10 ET PLUS (API 29+) ---
                    val resolver = requireContext().contentResolver
                    val contentValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                        put(
                            android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                            android.os.Environment.DIRECTORY_DOWNLOADS
                        )
                    }

                    val uri = resolver.insert(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        contentValues
                    )
                    if (uri != null) {
                        resolver.openOutputStream(uri).use { output ->
                            body.byteStream().use { input ->
                                input.copyTo(output!!)
                            }
                        }
                        return@withContext true
                    }
                } else {
                    // --- POUR ANDROID 9 ET MOINS (API 28-) ---
                    @Suppress("DEPRECATION")
                    val downloadsDir =
                        android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)

                    if (!downloadsDir.exists()) {
                        downloadsDir.mkdirs()
                    }

                    val file = java.io.File(downloadsDir, fileName)
                    java.io.FileOutputStream(file).use { output ->
                        body.byteStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    return@withContext true
                }
                false
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
}