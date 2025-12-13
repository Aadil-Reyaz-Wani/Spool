package com.kashmir.spool.ui.screens.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kashmir.spool.data.FilamentColorData
import com.kashmir.spool.ui.components.HeadingText
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
            verticalArrangement = Arrangement.spacedBy(space = Dimens.PaddingMedium),
            horizontalArrangement = Arrangement.spacedBy(
                space = Dimens.PaddingMedium,
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
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(color = FilamentColorData.getComposeColor(colorHex))
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
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