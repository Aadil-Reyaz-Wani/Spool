package com.kashmir.spool.ui.screens.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kashmir.spool.data.FilamentColorData
import com.kashmir.spool.ui.theme.Dimens

@Composable
fun ColorSelectionGrid(
    selectedColor: Long,
    onSelectedColor: (Long) -> Unit,
    modifier: Modifier = Modifier
) {

    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        verticalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium),
        horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium),
    ) {
        items(FilamentColorData.defaultColors) {filamentColor->
            val isSelected = filamentColor.colorValue == selectedColor

            ColorCircle(
                colorHex = filamentColor.colorValue,
                isSelected = isSelected,
                onClick = { onSelectedColor(filamentColor.colorValue) }
            )

        }
    }
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
            .size(40.dp)
            .clip(CircleShape)
            .background(color = FilamentColorData.getComposeColor(colorHex))
            .clickable(onClick = onClick)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = Dimens.BorderThickness,
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    )
                }else Modifier
            )
    )
}