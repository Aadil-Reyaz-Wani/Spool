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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Help
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AddTask
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Paid
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import spool.shared.generated.resources.*
import com.aadil.spool.feature.dashboard.FilterType
import com.aadil.spool.core.model.SpoolLists
import com.aadil.spool.data.entity.Filament
import com.aadil.spool.ui.common.GhostCard
import com.aadil.spool.ui.common.SpoolAppBar
import com.aadil.spool.ui.common.WeightProgressBar
import com.aadil.spool.ui.components.FilterBottomSheet
import com.aadil.spool.ui.components.SpoolTag
import com.aadil.spool.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    listOfSpools: List<Filament>,
    listOfUniqueBrandStrings: List<String>,
    listOfUniqueMaterialTypeStrings: List<String>,
    listOfUniqueColorHex: List<Long>,
    onFabClick: () -> Unit,
    onCardClick: (Int) -> Unit,
    onFilterStringClick: (String, FilterType) -> Unit,
    onCurrencyStringClick: (String) -> Unit,
    selectedOption: String,
    selectedCurrency: String,
    modifier: Modifier = Modifier,
    onAboutClick: () -> Unit,
    onHelpClick: () -> Unit,
) {

    var showBottomSheet by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var isSettingsMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var isCurrencyClicked by rememberSaveable { mutableStateOf(false)}

    Scaffold(
        modifier = modifier,
        topBar = {
            SpoolAppBar(
                title = stringResource(Res.string.dashboard_title),
                canNavigateBack = false,
                navigateUp = {},
                modifier = modifier,
                isDashboardScreen = true,
                onSettingsClick = { isSettingsMenuExpanded = !isSettingsMenuExpanded },
                dropDownMenu = {
                    DropdownMenuWithDetails(
                        expanded = isSettingsMenuExpanded,
                        onDismissRequest = { isSettingsMenuExpanded = !isSettingsMenuExpanded },
                        onFilterClick = {
                            showBottomSheet = !showBottomSheet
                            isSettingsMenuExpanded = false
                            isCurrencyClicked = false
                        },
                        onCurrencyClick = {
                            showBottomSheet = !showBottomSheet
                            isSettingsMenuExpanded = false
                            isCurrencyClicked = true
                        },
                        onAboutClick = {
                            onAboutClick()
                            isSettingsMenuExpanded = false
                        },
                        onHelpClick = {
                            onHelpClick()
                            isSettingsMenuExpanded = false
                        }
                    )
                },
                isSettingsMenuExpanded = isSettingsMenuExpanded
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
                    contentDescription = stringResource(Res.string.btn_fab),
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
                if (!isCurrencyClicked) {
                    FilterBottomSheet(
                        modifier = modifier.padding(horizontal = 16.dp),
                        bottomSheetHeader = stringResource(Res.string.filter_by_label),
                        listOfUniqueBrandStrings = listOfUniqueBrandStrings,
                        listOfUniqueMaterialTypeStrings = listOfUniqueMaterialTypeStrings,
                        listOfUniqueColorHex = listOfUniqueColorHex,
                        onFilterStringClick = { filterOption, filterType ->
                            onFilterStringClick(filterOption, filterType)
//                        showBottomSheet = false
                        },
                        selectedOption = selectedOption
                    )
                } else {
                    FilterBottomSheet(
                        modifier = modifier.padding(horizontal = 16.dp),
                        listOfCurrencyStrings = SpoolLists.currencyType,
                        bottomSheetHeader = "Currency",
                        listOfUniqueBrandStrings = listOfUniqueBrandStrings,
                        onFilterStringClick = { filterOption, filterType ->
                            onFilterStringClick(filterOption, filterType)
//                        showBottomSheet = false
                        },
                        selectedOption = selectedCurrency,
                        onCurrencyStringClick = { currencyCode->
                            onCurrencyStringClick(currencyCode)
                        },
                        isCurrencyTab = true
                    )
                }
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
                    text = stringResource(Res.string.dashboard_empty),
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
                items(
                    listOfSpools,
                    key = { it.id },
                    // A lone spool takes the full width; otherwise 2-column grid.
                    span = { GridItemSpan(if (listOfSpools.size == 1) maxLineSpan else 1) }
                ) { spool ->
                    SpoolItemCard(
                        brandName = spool.brand,
                        materialType = spool.material,
                        colorName = spool.colorName,
                        totalWeight = spool.totalWeight,
                        currentWeight = spool.currentWeight,
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
    totalWeight: Double,
    currentWeight: Double,
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



@Preview
@Composable
private fun SpoolCardPreview() {
    SpoolItemCard(
        brandName = "HackersSpool",
        materialType = "PETG",
        colorName = "Galaxy Mate Black",
        totalWeight = 1000.0,
        currentWeight = 230.0,
        colorHex = 0xFF000000,
        onCardClick = {},
        modifier = Modifier
    )
}


@Composable
fun DropdownMenuWithDetails(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onFilterClick: () -> Unit,
    onCurrencyClick: () -> Unit,
    onAboutClick: () -> Unit,
    onHelpClick: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .widthIn(min = Dimens.MenuBarWidth)
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.filter_by_label)) },
            trailingIcon = { Icon(Icons.Outlined.FilterList, contentDescription = null) },
            onClick = onFilterClick
        )
        DropdownMenuItem(
            text = { Text("Currency") },
            trailingIcon = { Icon(Icons.Outlined.Wallet, contentDescription = null) },
            onClick = onCurrencyClick
        )
        DropdownMenuItem(
            text = { Text("About") },
            trailingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
            onClick = onAboutClick
        )
        DropdownMenuItem(
            text = { Text("Help") },
            trailingIcon = { Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null) },
            onClick = onHelpClick
        )
    }
}