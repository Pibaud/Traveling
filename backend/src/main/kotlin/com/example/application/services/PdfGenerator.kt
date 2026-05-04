package com.example.application.services

import com.example.application.models.ItineraryResponse
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Text
import java.io.ByteArrayOutputStream
import java.util.Base64

object PdfGenerator {

    // On accepte l'image Base64 en paramètre
    fun generateItineraryPdf(itinerary: ItineraryResponse, base64MapImage: String?): ByteArray {

        val baos = ByteArrayOutputStream()
        val writer = PdfWriter(baos)
        val pdf = PdfDocument(writer)
        val document = Document(pdf)

        // --- TITRE PRINCIPAL ---
        document.add(Paragraph("Voyage : ${itinerary.name}").setBold().setFontSize(24f))

        // --- LA CARTE ENVOYÉE PAR ANDROID ---
        if (base64MapImage != null) {
            try {
                // On décode le texte Base64 pour retrouver l'image binaire
                val imageBytes = Base64.getDecoder().decode(base64MapImage)
                val imageData = ImageDataFactory.create(imageBytes)
                val mapImage = Image(imageData)

                mapImage.setAutoScale(true)
                mapImage.setMarginBottom(20f)

                document.add(mapImage)
            } catch (e: Exception) {
                println("Erreur lors de l'insertion de l'image Android dans le PDF : ${e.message}")
            }
        }

        // --- INFORMATIONS GLOBALES ---
        val repasStr = if (itinerary.mealIncluded) "Oui" else "Non"
        val firstStepTime = itinerary.steps.firstOrNull()?.arrivalTime ?: "09:30"

        val infos = Paragraph()
            .add(Text("Départ de la première étape : ").setBold()).add("$firstStepTime\n")
            .add(Text("Budget total : ").setBold()).add("${itinerary.totalPrice} €\n")
            .add(Text("Durée estimée : ").setBold()).add("${itinerary.totalDuration} heures\n")
            .add(Text("Repas inclus : ").setBold()).add("$repasStr\n")

        infos.setMarginBottom(20f)
        document.add(infos)

        // --- LISTE DES ÉTAPES ---
        document.add(Paragraph("Détail de votre parcours").setBold().setFontSize(18f))

        itinerary.steps.forEachIndexed { index, place ->
            val timeStr = place.arrivalTime ?: "Heure inconnue"

            val stepTitle = Paragraph("${index + 1}. ${place.name} - Prévu à $timeStr")
                .setBold()
                .setFontSize(14f)
                .setMarginTop(10f)
            document.add(stepTitle)

            val stepDetails = Paragraph()
                .add("Catégorie : ${place.category}\n")
                .add("Prix : ${place.price} € | Durée : ${place.duration}h\n")
            document.add(stepDetails)
        }

        document.add(Paragraph("\nL'équipe Traveling vous souhaite une excellente journée !").setItalic())
        document.close()

        return baos.toByteArray()
    }
}