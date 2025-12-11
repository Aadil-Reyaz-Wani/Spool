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
        FilamentColor("Transparent", 0xFFE0E0E0),
        FilamentColor("Cyan", 0xFF00FFFF),
        FilamentColor("Magenta", 0xFFFF00FF),
        FilamentColor("Lime", 0xFF00FF00),
        FilamentColor("Teal", 0xFF008080),
        FilamentColor("Indigo", 0xFF4B0082),
        FilamentColor("Maroon", 0xFF800000),
        FilamentColor("Olive", 0xFF808000),
        FilamentColor("Silver", 0xFFC0C0C0),
        FilamentColor("Navy", 0xFF000080),
        FilamentColor("Fuchsia", 0xFFFF00FF),
        FilamentColor("Aqua", 0xFF00FFFF),
        FilamentColor("Beige", 0xFFF5F5DC),
        FilamentColor("Lavender", 0xE6E6FA),
        FilamentColor("Coral", 0xFFF08080),
        FilamentColor("Salmon", 0xFA8072),
        FilamentColor("Khaki", 0xF0E68C),
        FilamentColor("Gold", 0xFF228B22),
    )

    // Helper to get a Color object for Compose
    fun getComposeColor(hex: Long): Color {
        return Color(hex)
    }
}
