package com.kashmir.spool.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.kashmir.spool.ui.screens.AppViewModelProvider
import com.kashmir.spool.ui.screens.dashboard.DashboardScreen
import com.kashmir.spool.ui.screens.dashboard.DashboardViewModel
import com.kashmir.spool.ui.screens.details.SpoolDetailsScreen
import com.kashmir.spool.ui.screens.details.SpoolDetailsViewModel
import com.kashmir.spool.ui.screens.entry.SpoolEntryScreen
import com.kashmir.spool.ui.screens.entry.SpoolEntryViewModel

@Composable
fun MySpoolApp(modifier: Modifier = Modifier) {
    val dashboardViewModel: DashboardViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val spoolEntryViewModel: SpoolEntryViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val spoolDetailsViewModel: SpoolDetailsViewModel = viewModel(factory = AppViewModelProvider.Factory)


    val listOfSpools by dashboardViewModel.getAllSpool.collectAsState()


    val spoolEntryUiState by spoolEntryViewModel.spoolEntryUiState.collectAsState()
    val isWeightValid by spoolEntryViewModel.isError.collectAsState()

    val spoolDetails by spoolDetailsViewModel.spoolDetails.collectAsState()

    val backStack = rememberNavBackStack(Routes.Dashboard)
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            // Dashboard Screen Entry
            entry<Routes.Dashboard> {
                DashboardScreen(
                    onFabClick = {
                        backStack.add(Routes.SpoolEntry)
                    },
                    onCardClick = {id->
                        backStack.add(Routes.SpoolDetails(id))
                    },
                    listOfSpools = listOfSpools
                )
            }

            // Entry Screen Entry
            entry<Routes.SpoolEntry> {
                SpoolEntryScreen(
                    onNavigateUp = {
                        backStack.removeLastOrNull()
                    },
                    uiState = spoolEntryUiState,
                    onBrandValueChange = { newValue ->
                        spoolEntryViewModel.updateTextField(
                            newBrand = newValue,
                            newMaterial = spoolEntryUiState.material,
                            newTotalWeight = spoolEntryUiState.totalWeight,
                            newColorHex = spoolEntryUiState.colorHex,
                            newColorName = spoolEntryUiState.colorName
                        )
                    },
                    onMaterialValueChange = { newValue ->
                        spoolEntryViewModel.updateTextField(
                            newBrand = spoolEntryUiState.brand,
                            newMaterial = newValue,
                            newTotalWeight = spoolEntryUiState.totalWeight,
                            newColorHex = spoolEntryUiState.colorHex,
                            newColorName = spoolEntryUiState.colorName
                        )
                    },
                    onInitialWeightValueChange = { newValue ->
                        spoolEntryViewModel.updateTextField(
                            newBrand = spoolEntryUiState.brand,
                            newMaterial = spoolEntryUiState.material,
                            newTotalWeight = newValue,
                            newColorHex = spoolEntryUiState.colorHex,
                            newColorName = spoolEntryUiState.colorName
                        )
                    },
                    onColorNameChange = { newValue ->
                        spoolEntryViewModel.updateTextField(
                            newBrand = spoolEntryUiState.brand,
                            newMaterial = spoolEntryUiState.material,
                            newTotalWeight = spoolEntryUiState.totalWeight,
                            newColorHex = spoolEntryUiState.colorHex,
                            newColorName = newValue
                        )
                    },
                    onColorValueChange = { newValue ->
                        spoolEntryViewModel.updateTextField(
                            newBrand = spoolEntryUiState.brand,
                            newMaterial = spoolEntryUiState.material,
                            newTotalWeight = spoolEntryUiState.totalWeight,
                            newColorHex = newValue,
                            newColorName = spoolEntryUiState.colorName
                        )
                    },
                    selectedColor = spoolEntryUiState.colorHex,
                    onSaveClick = {
                        spoolEntryViewModel.saveSpool()
                        backStack.removeLastOrNull()
                    },
                    isValid = spoolEntryViewModel.isValid(),
                    isError = isWeightValid,
                    modifier = modifier
                )
            }

            // Details Screen Entry
            entry<Routes.SpoolDetails> { entry->
                LaunchedEffect(entry.id) {
                    spoolDetailsViewModel.loadSpool(entry.id)
                }
                SpoolDetailsScreen(
                    spoolDetails = spoolDetails,
                    navigateUp = {
                        backStack.removeLastOrNull()
                    }
                )
            }

        }
    )
}