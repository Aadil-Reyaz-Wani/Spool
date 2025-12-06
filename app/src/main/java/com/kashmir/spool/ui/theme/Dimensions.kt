package com.kashmir.spool.ui.theme

import androidx.compose.ui.unit.dp

object Dimens {
    // Standard Grid (Multiples of 4/8)
    val PaddingTiny = 4.dp
    val PaddingSmall = 8.dp
    val PaddingMedium = 16.dp  // Standard screen edge padding
    val PaddingLarge = 24.dp
    val PaddingExtraLarge = 32.dp

    // Component Specifics
    val CardElevation = 2.dp
    val BorderThickness = 2.dp // Thicker borders for that "technical" look

    // Icon Sizes
    val IconSmall = 16.dp
    val IconMedium = 24.dp
    val IconLarge = 32.dp

    // Filament Specifics
    val ColorDotSize = 24.dp         // The circle showing filament color
    val ProgressBarHeight = 12.dp    // Thick enough to read easily
    val SpoolCardMinHeight = 80.dp   // Minimum touch target for list items
}