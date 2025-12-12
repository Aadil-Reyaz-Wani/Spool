package com.kashmir.spool.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.kashmir.spool.R
import com.kashmir.spool.data.entity.Filament
import com.kashmir.spool.ui.common.GhostCard
import com.kashmir.spool.ui.common.SpoolAppBar
import com.kashmir.spool.ui.common.WeightProgressBar
import com.kashmir.spool.ui.theme.Dimens

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
                title = stringResource(R.string.app_name).uppercase(),
                canNavigateBack = false,
                navigateUp = {},
                modifier = modifier
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onFabClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.btn_fab)
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
            LazyColumn(
                contentPadding = PaddingValues(bottom = 72.dp),
                modifier = Modifier
                    .padding(paddingValues = paddingValues)
            ) {
                items(listOfSpools, key = { it.id }) { spool ->
                    SpoolItemCard(
                        brandName = spool.brand,
                        materialType = spool.material,
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
    totalWeight: String,
    currentWeight: String,
    colorHex: Long,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        elevation = CardDefaults.cardElevation(Dimens.CardElevation),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimens.PaddingSmall)
            .clickable(onClick = onCardClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.PaddingSmall)
        ) {
            Row {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = brandName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = materialType,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Box(
                    modifier = Modifier
                        .size(Dimens.ColorDotSize)
                        .clip(CircleShape)
                        .align(alignment = Alignment.CenterVertically)
                        .background(color = Color(colorHex))
                )

            }
            Spacer(modifier = Modifier.height(Dimens.gapHeight))
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
        brandName = "Brand",
        materialType = "Material Type",
        totalWeight = "1000",
        currentWeight = "230",
        colorHex = 0xFF000000,
        onCardClick = {},
        modifier = Modifier
    )
}