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

        FilamentColor("Yellow", 0xFFFFFF00),
        FilamentColor("Green", 0xFF008000),
        FilamentColor("Orange", 0xFFFFA500),
        FilamentColor("Purple", 0xFF800080),
        FilamentColor("Pink", 0xFFFFC0CB),
        FilamentColor("Brown", 0xFFA52A2A),
        FilamentColor("Gold", 0xFFFFD700),
        FilamentColor("Cyan", 0xFF008080),
        FilamentColor("Magenta", 0xFFFF00FF),
        FilamentColor("Lime Green", 0xFF32CD32),
        FilamentColor("Navy Blue", 0xFF000080),
        FilamentColor("Olive Green", 0xFF808000),
        FilamentColor("Beige", 0xFFF5F5DC),
        FilamentColor("Clear", 0x80FFFFFF), // 50% Alpha for transparency
        FilamentColor("Glow Green", 0xFFE0FFCC),

        FilamentColor("Galaxy Black", 0xFF282828),
        FilamentColor("Prusa Orange", 0xFFF16436),
        FilamentColor("Bambu Green", 0xFF00AE42),
        FilamentColor("Electric Blue", 0xFF007BFF),

        FilamentColor("Copper", 0xFFB87333),
        FilamentColor("Bronze", 0xFFCD7F32),
        FilamentColor("Wood", 0xFFDEB887),
        FilamentColor("Marble", 0xFFF2F0E6), // Off-white stone look
        FilamentColor("Silver", 0xFFC0C0C0),
    )

    // Helper to get a Color object for Compose
    fun getComposeColor(hex: Long): Color {
        return Color(hex)
    }
}
