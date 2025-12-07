package com.kashmir.spool.data

import androidx.compose.ui.graphics.Color

data class FilamentColor(
    val name: String,
    val colorValue: Long
)

object FilamentColorData {
    val defaultColors = listOf(
        FilamentColor("Black", 0xFF000000),
        FilamentColor("White", 0xFFFFFFFF),
        FilamentColor("Grey", 0xFF808080),
        FilamentColor("Red", 0xFFFF0000),
        FilamentColor("Blue", 0xFF0000FF),
        FilamentColor("Green", 0xFF00FF00),
        FilamentColor("Orange", 0xFFFFA500),
        FilamentColor("Yellow", 0xFFFFFF00),
        FilamentColor("Purple", 0xFF800080),
        FilamentColor("Pink", 0xFFFFC0CB),
        FilamentColor("Brown", 0xFFA52A2A),
        FilamentColor("Transparent", 0xFFE0E0E0)
    )

    // Helper to get a Color object for Compose
    fun getComposeColor(hex: Long): Color {
        return Color(hex)
    }
}
