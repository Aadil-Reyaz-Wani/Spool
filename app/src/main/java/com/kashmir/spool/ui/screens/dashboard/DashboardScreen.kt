package com.kashmir.spool.ui.screens.dashboard

import android.view.RoundedCorner
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kashmir.spool.R
import com.kashmir.spool.data.entity.Filament
import com.kashmir.spool.ui.common.SpoolAppBar
import com.kashmir.spool.ui.theme.Dimens

@Composable
fun DashboardScreen(
    listOfSpools: List<Filament>,
    onFabClick: () -> Unit,
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

        LazyColumn(
            modifier = Modifier.padding(paddingValues = paddingValues)
        ) {
            items(listOfSpools, key = { it.id }) { spool ->
                SpoolItemCard(
                    brandName = spool.brand,
                    materialType = spool.material,
                    totalWeight = spool.totalWeight.toString(),
                    colorHex = spool.colorHex
                )
            }
        }

    }

}

@Composable
fun SpoolItemCard(
    brandName: String,
    materialType: String,
    totalWeight: String,
    colorHex: Long,
    modifier: Modifier = Modifier
) {
    Card(
        elevation = CardDefaults.cardElevation(Dimens.CardElevation),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimens.PaddingSmall)
            .clickable(onClick = {})
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
            CustomProgressIndicator(
                totalWeight = totalWeight,
                colorHex = colorHex,
                modifier = modifier
            )
        }
    }
}

@Composable
fun CustomProgressIndicator(
    totalWeight: String,
    colorHex: Long,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "750g",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${totalWeight}g",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(Dimens.gapHeight))
        LinearProgressIndicator(
            progress = { 20f / 100 },
            modifier = Modifier
                .fillMaxWidth()
                .size(Dimens.ProgressBarHeight),
            color = Color(colorHex),
            trackColor = MaterialTheme.colorScheme.secondaryContainer,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
        )
    }

}

@Preview(showBackground = true)
@Composable
private fun SpoolCardPreview() {
    SpoolItemCard(
        brandName = "Brand",
        materialType = "Material Type",
        totalWeight = "1000",
        colorHex = 0xFF000000,
        modifier = Modifier
    )
}