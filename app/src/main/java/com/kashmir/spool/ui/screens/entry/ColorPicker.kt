package com.kashmir.spool.ui.screens.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kashmir.spool.data.FilamentColorData
import com.kashmir.spool.ui.theme.Dimens

@Composable
fun ColorSelectionGrid(
    selectedColor: Long,
    onSelectedColor: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = Modifier
            .padding(Dimens.PaddingSmall)
            .fillMaxWidth(),
    ) {

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            itemVerticalAlignment = Alignment.CenterVertically,
            verticalArrangement = Arrangement.spacedBy(space = Dimens.PaddingSmall),
            horizontalArrangement = Arrangement.spacedBy(
                space = Dimens.PaddingSmall,
                alignment = Alignment.CenterHorizontally
            ),
        ) {
            FilamentColorData.defaultColors.forEach { filamentColor ->
                val isSelected = filamentColor.colorValue == selectedColor

                ColorCircle(
                    colorHex = filamentColor.colorValue,
                    isSelected = isSelected,
                    onClick = { onSelectedColor(filamentColor.colorValue) }
                )

            }
        }
    }
}

@Preview
@Composable
fun ColorSelectionGridPreview() {
    ColorSelectionGrid(
        selectedColor = 0xFF000000,
        onSelectedColor = {}
    )
}

@Composable
fun ColorCircle(
    colorHex: Long,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    Icon(
        imageVector = Icons.Filled.Circle,
        contentDescription = null,
        tint = if (!isSelected) {
            FilamentColorData.getComposeColor(colorHex).copy(alpha = 0.6f)
        } else {
            FilamentColorData.getComposeColor(colorHex)
        },
        modifier = Modifier
            .size(50.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .border(
                width = Dimens.BorderThickness,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape
            )
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = Dimens.ColorDotBorderThickness,
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    )
                } else Modifier
            )
    )
}