package com.aadil.spool.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AddTask
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aadil.spool.R
import com.aadil.spool.data.entity.Filament
import com.aadil.spool.ui.common.GhostCard
import com.aadil.spool.ui.common.SpoolAppBar
import com.aadil.spool.ui.common.WeightProgressBar
import com.aadil.spool.ui.components.SpoolTag
import com.aadil.spool.ui.theme.Dimens

@Composable
fun DashboardScreen(
    listOfSpools: List<Filament>,
    onFabClick: () -> Unit,
    onCardClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {

    Scaffold(
        modifier = modifier,
        topBar = {
            SpoolAppBar(
                title = stringResource(R.string.dashboard_title),
                canNavigateBack = false,
                navigateUp = {},
                modifier = modifier
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onFabClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.btn_fab),
                    modifier = Modifier.size(Dimens.IconLarge)
                )
            }
        },
        floatingActionButtonPosition = FabPosition.EndOverlay,
    ) { paddingValues ->

        if (listOfSpools.isEmpty()) {
            Column(
                modifier = Modifier
                    .padding(paddingValues = paddingValues)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GhostCard(
                    text = stringResource(R.string.dashboard_empty),
                    icon = Icons.Outlined.AddTask,
                    modifier = Modifier.padding(Dimens.PaddingMedium)
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(bottom = 72.dp),
                modifier = Modifier
                    .padding(paddingValues = paddingValues)
            ) {
                items(listOfSpools, key = { it.id }) { spool ->
                    SpoolItemCard(
                        brandName = spool.brand,
                        materialType = spool.material,
                        colorName = spool.colorName,
                        totalWeight = spool.totalWeight.toString(),
                        currentWeight = spool.currentWeight.toString(),
                        colorHex = spool.colorHex,
                        onCardClick = { onCardClick(spool.id) }
                    )
                }
            }
        }
    }

}

@Composable
fun SpoolItemCard(
    brandName: String,
    materialType: String,
    colorName: String,
    totalWeight: String,
    currentWeight: String,
    colorHex: Long,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        elevation = CardDefaults.cardElevation(Dimens.CardElevation),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimens.PaddingSmall)
            .clickable(onClick = onCardClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.PaddingMedium),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.Start
        ) {


            SpoolTag(text = materialType.uppercase())
            Text(
                text = brandName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = colorName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.PaddingMedium),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(Dimens.ColorDotSize)
                        .clip(CircleShape)
                        .background(color = Color(colorHex))
                        .border(
                            width = Dimens.ColorDotBorderThickness/2,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                )
            }
            // Remaining filament
            WeightProgressBar(
                totalWeight = totalWeight,
                currentWeight = currentWeight,
                colorHex = colorHex,
                modifier = modifier
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SpoolCardPreview() {
    SpoolItemCard(
        brandName = "HackersSpool",
        materialType = "PETG",
        colorName = "Galazy Mate Black",
        totalWeight = "1000",
        currentWeight = "230",
        colorHex = 0xFF000000,
        onCardClick = {},
        modifier = Modifier
    )
}