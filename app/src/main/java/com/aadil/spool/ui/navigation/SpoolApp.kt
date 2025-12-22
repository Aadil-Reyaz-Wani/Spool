package com.aadil.spool.ui.navigation

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
import com.aadil.spool.ui.screens.AppViewModelProvider
import com.aadil.spool.ui.screens.dashboard.DashboardScreen
import com.aadil.spool.ui.screens.dashboard.DashboardViewModel
import com.aadil.spool.ui.screens.details.SpoolDetailsScreen
import com.aadil.spool.ui.screens.details.SpoolDetailsViewModel
import com.aadil.spool.ui.screens.entry.SpoolEntryScreen
import com.aadil.spool.ui.screens.entry.SpoolEntryViewModel
import com.aadil.spool.ui.splash.SplashScreen
import kotlinx.coroutines.delay

@Composable
fun MySpoolApp(modifier: Modifier = Modifier) {
    val dashboardViewModel: DashboardViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val spoolEntryViewModel: SpoolEntryViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val spoolDetailsViewModel: SpoolDetailsViewModel =
        viewModel(factory = AppViewModelProvider.Factory)


    val listOfSpools by dashboardViewModel.getAllSpool.collectAsState()


    val spoolEntryUiState by spoolEntryViewModel.spoolEntryUiState.collectAsState()
    val isError by spoolEntryViewModel.isError.collectAsState()
//    val isWeightValid by spoolEntryViewModel.isWeightValid.collectAsState()

    val spoolDetails by spoolDetailsViewModel.spoolDetails.collectAsState()
    val printWeight by spoolDetailsViewModel.printWeight.collectAsState()



    val backStack = rememberNavBackStack(Routes.Splash)
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {

            // Splash Screen Entry
            entry<Routes.Splash> {
                SplashScreen()
                LaunchedEffect(Unit) {
                    delay(1500L)
                    backStack.add(Routes.Dashboard)
                    backStack.remove(Routes.Splash)
                }
            }

            // Dashboard Screen Entry
            entry<Routes.Dashboard> {
                DashboardScreen(
                    onFabClick = {
                        backStack.add(Routes.SpoolEntry(id = 0))
                    },
                    onCardClick = { id ->
                        backStack.add(Routes.SpoolDetails(id))
                    },
                    listOfSpools = listOfSpools
                )
            }

            // Entry Screen Entry
            entry<Routes.SpoolEntry> { entry ->
                LaunchedEffect(entry.id) {
                    spoolEntryViewModel.loadSpool(entry.id)
                }
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
                            newColorName = spoolEntryUiState.colorName,
                            newCurrentWeight = spoolEntryUiState.currentWeight,
                            newTempNozzle = spoolEntryUiState.tempNozzle,
                            newTempBed = spoolEntryUiState.tempBed,
                            newNote = spoolEntryUiState.note
                        )
                    },
                    onMaterialValueChange = { newValue ->
                        spoolEntryViewModel.updateTextField(
                            newBrand = spoolEntryUiState.brand,
                            newMaterial = newValue,
                            newTotalWeight = spoolEntryUiState.totalWeight,
                            newColorHex = spoolEntryUiState.colorHex,
                            newColorName = spoolEntryUiState.colorName,
                            newCurrentWeight = spoolEntryUiState.currentWeight,
                            newTempNozzle = spoolEntryUiState.tempNozzle,
                            newTempBed = spoolEntryUiState.tempBed,
                            newNote = spoolEntryUiState.note
                        )
                    },
                    onInitialWeightValueChange = { newValue ->
                        spoolEntryViewModel.updateTextField(
                            newBrand = spoolEntryUiState.brand,
                            newMaterial = spoolEntryUiState.material,
                            newTotalWeight = newValue,
                            newColorHex = spoolEntryUiState.colorHex,
                            newColorName = spoolEntryUiState.colorName,
                            newCurrentWeight = spoolEntryUiState.currentWeight,
                            newTempNozzle = spoolEntryUiState.tempNozzle,
                            newTempBed = spoolEntryUiState.tempBed,
                            newNote = spoolEntryUiState.note
                        )
                    },
                    onColorNameChange = { newValue ->
                        spoolEntryViewModel.updateTextField(
                            newBrand = spoolEntryUiState.brand,
                            newMaterial = spoolEntryUiState.material,
                            newTotalWeight = spoolEntryUiState.totalWeight,
                            newColorHex = spoolEntryUiState.colorHex,
                            newColorName = newValue,
                            newCurrentWeight = spoolEntryUiState.currentWeight,
                            newTempNozzle = spoolEntryUiState.tempNozzle,
                            newTempBed = spoolEntryUiState.tempBed,
                            newNote = spoolEntryUiState.note
                        )
                    },
                    onColorValueChange = { newValue ->
                        spoolEntryViewModel.updateTextField(
                            newBrand = spoolEntryUiState.brand,
                            newMaterial = spoolEntryUiState.material,
                            newTotalWeight = spoolEntryUiState.totalWeight,
                            newColorHex = newValue,
                            newColorName = spoolEntryUiState.colorName,
                            newCurrentWeight = spoolEntryUiState.currentWeight,
                            newTempNozzle = spoolEntryUiState.tempNozzle,
                            newTempBed = spoolEntryUiState.tempBed,
                            newNote = spoolEntryUiState.note
                        )
                    },
                    onCurrentWeightValueChange = {newValue ->
                        spoolEntryViewModel.updateTextField(
                            newBrand = spoolEntryUiState.brand,
                            newMaterial = spoolEntryUiState.material,
                            newTotalWeight = spoolEntryUiState.totalWeight,
                            newColorHex = spoolEntryUiState.colorHex,
                            newColorName = spoolEntryUiState.colorName,
                            newCurrentWeight = newValue,
                            newTempNozzle = spoolEntryUiState.tempNozzle,
                            newTempBed = spoolEntryUiState.tempBed,
                            newNote = spoolEntryUiState.note
                        )
                    },
                    onNozzleTempValueChange = {newValue ->
                        spoolEntryViewModel.updateTextField(
                            newBrand = spoolEntryUiState.brand,
                            newMaterial = spoolEntryUiState.material,
                            newTotalWeight = spoolEntryUiState.totalWeight,
                            newColorHex = spoolEntryUiState.colorHex,
                            newColorName = spoolEntryUiState.colorName,
                            newCurrentWeight = spoolEntryUiState.currentWeight,
                            newTempNozzle = newValue,
                            newTempBed = spoolEntryUiState.tempBed,
                            newNote = spoolEntryUiState.note
                        )
                    },
                    onBedTempValueChange = {newValue ->
                        spoolEntryViewModel.updateTextField(
                            newBrand = spoolEntryUiState.brand,
                            newMaterial = spoolEntryUiState.material,
                            newTotalWeight = spoolEntryUiState.totalWeight,
                            newColorHex = spoolEntryUiState.colorHex,
                            newColorName = spoolEntryUiState.colorName,
                            newCurrentWeight = spoolEntryUiState.currentWeight,
                            newTempNozzle = spoolEntryUiState.tempNozzle,
                            newTempBed = newValue,
                            newNote = spoolEntryUiState.note
                        )
                    },
                    onNoteValueChange = {newValue ->
                        spoolEntryViewModel.updateTextField(
                            newBrand = spoolEntryUiState.brand,
                            newMaterial = spoolEntryUiState.material,
                            newTotalWeight = spoolEntryUiState.totalWeight,
                            newColorHex = spoolEntryUiState.colorHex,
                            newColorName = spoolEntryUiState.colorName,
                            newCurrentWeight = spoolEntryUiState.currentWeight,
                            newTempNozzle = spoolEntryUiState.tempNozzle,
                            newTempBed = spoolEntryUiState.tempBed,
                            newNote = newValue
                        )
                    },
                    onSaveOrUpdateClick = {
                        spoolEntryViewModel.saveOrUpdateSpool(entry.id)
                        if (spoolEntryViewModel.isValid()) {
                            backStack.removeLastOrNull()
                        }
                    },
                    selectedColor = spoolEntryUiState.colorHex,
                    isError = isError,
                    isEditMode = spoolEntryViewModel.isEditMode(entry.id),
                    resetState = spoolEntryViewModel::resetState,
                    modifier = modifier
                )
            }

            // Details Screen Entry
            entry<Routes.SpoolDetails> { entry ->
                LaunchedEffect(entry.id) {
                    spoolDetailsViewModel.loadSpool(entry.id)
                }
                SpoolDetailsScreen(
                    spoolDetails = spoolDetails,
                    navigateUp = {
                        backStack.removeLastOrNull()
                    },
                    onUpdateClick = { id ->
                        backStack.add(Routes.SpoolEntry(id = id))
                    },
                    onConfirmDelete = { filament ->
                        spoolDetailsViewModel.deleteSpool(filament)
                        backStack.removeLastOrNull()
                    },
                    printWeight = printWeight,
                    onPrintWeightValueChange = { newValue->
                        spoolDetailsViewModel.quickDeductionUpdateField(newValue = newValue)
                    },
                    onPrintWeightClick = {id, weight ->
                        spoolDetailsViewModel.deductCurrentWeight(id, weight)
                    }
                )
            }

        }
    )
}