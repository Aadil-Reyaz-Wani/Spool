package com.kashmir.spool.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp


val Shapes = Shapes(
    // Used for: Text Fields, Tags, Small Buttons
    small = RoundedCornerShape(4.dp),

    // Used for: Cards, Dialogs, Floating Action Buttons (FAB)
    medium = RoundedCornerShape(8.dp),

    // Used for: Bottom Sheets, Navigation Drawers
    large = RoundedCornerShape(12.dp),

    // Used for: Large modal surfaces
    extraLarge = RoundedCornerShape(16.dp)
)