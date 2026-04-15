package com.example.application.utils

object GroupThemes {
    val predefinedTags = listOf(
        Pair("Aventure", android.R.drawable.ic_menu_compass),
        Pair("Paysage", android.R.drawable.ic_menu_gallery),
        Pair("Urbain", android.R.drawable.ic_menu_mapmode),
        Pair("Nuit", android.R.drawable.ic_menu_view),
        Pair("Portrait", android.R.drawable.ic_menu_camera),
        Pair("Macro", android.R.drawable.ic_menu_zoom),
        Pair("Animaux", android.R.drawable.ic_menu_mylocation),
        Pair("Chill", android.R.drawable.ic_menu_day),
        Pair("Expert", android.R.drawable.star_on),
        Pair("Débutant", android.R.drawable.star_off)
    )

    fun getIconForTag(tagName: String): Int? {
        return predefinedTags.find { it.first.equals(tagName, ignoreCase = true) }?.second
    }
}