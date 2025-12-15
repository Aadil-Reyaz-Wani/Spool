package com.kashmir.spool.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import com.kashmir.spool.ui.theme.Shapes


val Shapes = Shapes(
    // Used for: Tags
    extraSmall = RoundedCornerShape(4.dp),
    // Used for: Chips, Small Buttons
    small = RoundedCornerShape(8.dp),

    // Used for: Cards, Dialogs, Date Pickers
    medium = RoundedCornerShape(20.dp),

    // Used for: Text Fields (The Pill Look), Large Modal Drawers
    large = RoundedCornerShape(24.dp),

    // Used for: FABs, Large Action Buttons
    extraLarge = RoundedCornerShape(50) // 50% = Full Circle
)