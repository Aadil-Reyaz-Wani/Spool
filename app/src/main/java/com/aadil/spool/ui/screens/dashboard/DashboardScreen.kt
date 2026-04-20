package com.aadil.spool.ui.screens.dashboard

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AddTask
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aadil.spool.R
import com.aadil.spool.data.entity.Filament
import com.aadil.spool.ui.common.GhostCard
import com.aadil.spool.ui.common.SpoolAppBar
import com.aadil.spool.ui.common.WeightProgressBar
import com.aadil.spool.ui.common.lazyVerticalScrollbar
import com.aadil.spool.ui.common.verticalScrollbar
import com.aadil.spool.ui.components.SpoolButton
import com.aadil.spool.ui.components.SpoolHeadingText
import com.aadil.spool.ui.components.SpoolHorizontalDivider
import com.aadil.spool.ui.components.SpoolTag
import com.aadil.spool.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    listOfSpools: List<Filament>,
    listOfUniqueBrandStrings: List<String>,
    onFabClick: () -> Unit,
    onCardClick: (Int) -> Unit,
    onFilterStringClick: (String) -> Unit,
    selectedBrand: String,
    modifier: Modifier = Modifier,
) {

    val adaptiveMinSize = when {
        isTablet() -> 180.dp
        else -> 140.dp
    }
    var showBottomSheet by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    Scaffold(
        modifier = modifier,
        topBar = {
            SpoolAppBar(
                title = stringResource(R.string.dashboard_title),
                canNavigateBack = false,
                navigateUp = {},
                modifier = modifier,
                isDashboardScreen = true,
                onFilterClick = { showBottomSheet = !showBottomSheet }
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

        if (showBottomSheet) {
            ModalBottomSheet(
                modifier = Modifier
                    .fillMaxWidth(),
                sheetState = sheetState,
                onDismissRequest = { showBottomSheet = false },
                shape = MaterialTheme.shapes.medium
            ) {
                FilterBottomSheet(
                    modifier = modifier.padding(horizontal = 12.dp),
                    listOfUniqueBrandStrings = listOfUniqueBrandStrings,
                    onFilterStringClick = { brandName ->
                        onFilterStringClick(brandName)
//                        showBottomSheet = false
                    },
                    selectedBrand = selectedBrand
                )
            }
        }

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
                modifier = Modifier
                    .padding(paddingValues = paddingValues)
                    .fillMaxWidth(),
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(bottom = 72.dp)
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
                            width = Dimens.ColorDotBorderThickness / 2,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                )
            }
            // Remaining filament
            WeightProgressBar(
                totalWeight = totalWeight,
                currentWeight = currentWeight,
                modifier = modifier
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    modifier: Modifier = Modifier,
    listOfUniqueBrandStrings: List<String>,
    onFilterStringClick: (String) -> Unit,
    selectedBrand: String,
) {

    val scrollState = rememberScrollState()
    val clearAllFilter = stringResource(R.string.clear_all_filters_button_label)


    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SpoolHeadingText(
                text = stringResource(R.string.filter_by_label),
                icon = Icons.Outlined.FilterList,
            )
            OutlinedButton(
                onClick = { onFilterStringClick(clearAllFilter) },
                modifier = Modifier.height(Dimens.ClearAllButtonHeight),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = stringResource(R.string.clear_all_button_label),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

        }
        SpoolHorizontalDivider(
            modifier = Modifier.padding(top = Dimens.PaddingMedium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Brand Card Implementation
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.PaddingMedium),
            shape = MaterialTheme.shapes.small
        ) {
            var showFilterOptions by rememberSaveable { mutableStateOf(false) }
            // Top header row -> Brand & Icon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.PaddingSmall),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.brand_card_label),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(
                    onClick = { showFilterOptions = !showFilterOptions },
                ) {
                    Icon(
                        imageVector = if (!showFilterOptions) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                        contentDescription = stringResource(R.string.filter_by_label),
                        modifier = Modifier.size(Dimens.IconLarge)
                    )
                }
            }

            if (showFilterOptions) {
                SpoolHorizontalDivider(
                    modifier = Modifier.padding(horizontal = Dimens.PaddingSmall),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(
                    modifier = Modifier
                        .heightIn(max = Dimens.ScrollableCardHeight)
                        .padding(vertical = Dimens.PaddingTiny, horizontal = Dimens.PaddingSmall)
                ) {
                    items(listOfUniqueBrandStrings) { brand ->
                        val isSelected = brand == selectedBrand

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Dimens.PaddingTiny)
                                .clip(MaterialTheme.shapes.small)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = MaterialTheme.shapes.small
                                )
                                .clickable(onClick = {
                                    onFilterStringClick(brand)
                                }
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = brand,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = Dimens.PaddingTiny, horizontal = Dimens.PaddingSmall)
                                        .weight(1f)
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Outlined.Done,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .padding(vertical = Dimens.PaddingTiny, horizontal = Dimens.PaddingSmall)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}


@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun isTablet(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.screenWidthDp >= 600
}


@Preview(showBackground = true)
@Composable
private fun SpoolCardPreview() {
    SpoolItemCard(
        brandName = "HackersSpool",
        materialType = "PETG",
        colorName = "Galaxy Mate Black",
        totalWeight = "1000",
        currentWeight = "230",
        colorHex = 0xFF000000,
        onCardClick = {},
        modifier = Modifier
    )
}