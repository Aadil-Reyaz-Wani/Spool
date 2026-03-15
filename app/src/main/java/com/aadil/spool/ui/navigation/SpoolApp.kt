package com.aadil.spool.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.aadil.spool.ui.screens.dashboard.DashboardScreen
import com.aadil.spool.ui.screens.dashboard.DashboardViewModel
import com.aadil.spool.ui.screens.details.SpoolDetailsScreen
import com.aadil.spool.ui.screens.details.SpoolDetailsViewModel
import com.aadil.spool.ui.screens.entry.SpoolEntryScreen
import com.aadil.spool.ui.screens.entry.SpoolEntryViewModel
import com.aadil.spool.ui.screens.history.PrintHistoryScreen
import com.aadil.spool.ui.screens.history.PrintHistoryViewModel
import com.aadil.spool.ui.screens.splash.SplashScreen
import kotlinx.coroutines.delay

@Composable
fun MySpoolApp(modifier: Modifier = Modifier) {

    // Define ViewModels
    val dashboardViewModel: DashboardViewModel = hiltViewModel()
    val spoolEntryViewModel: SpoolEntryViewModel = hiltViewModel()
    val spoolDetailsViewModel: SpoolDetailsViewModel = hiltViewModel()
    val printHistoryViewModel: PrintHistoryViewModel = hiltViewModel()

    // Dashboard
    val listOfSpools by dashboardViewModel.getAllSpool.collectAsStateWithLifecycle()

    // Entry
    val spoolEntryUiState by spoolEntryViewModel.spoolEntryUiState.collectAsStateWithLifecycle()
    val isError by spoolEntryViewModel.isError.collectAsStateWithLifecycle()

    // Details
    val spoolDetails by spoolDetailsViewModel.spoolDetails.collectAsStateWithLifecycle()
    val printUiState by spoolDetailsViewModel.printObjectUiState.collectAsStateWithLifecycle()
    val isPrintErrorState by spoolDetailsViewModel.isError.collectAsStateWithLifecycle()


    // Print History
    val spoolPrintUsageHistoryDetails by printHistoryViewModel.spoolPrintUsageHistoryDetails.collectAsStateWithLifecycle()


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
                            newPrice = spoolEntryUiState.price,
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
                            newPrice = spoolEntryUiState.price,
                            newTotalWeight = spoolEntryUiState.totalWeight,
                            newColorHex = spoolEntryUiState.colorHex,
                            newColorName = spoolEntryUiState.colorName,
                            newCurrentWeight = spoolEntryUiState.currentWeight,
                            newTempNozzle = spoolEntryUiState.tempNozzle,
                            newTempBed = spoolEntryUiState.tempBed,
                            newNote = spoolEntryUiState.note
                        )
                    },
                    onPriceValueChange = { newValue ->
                        spoolEntryViewModel.updateTextField(
                            newBrand = spoolEntryUiState.brand,
                            newMaterial = spoolEntryUiState.material,
                            newPrice = newValue,
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
                            newPrice = spoolEntryUiState.price,
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
                            newPrice = spoolEntryUiState.price,
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
                            newPrice = spoolEntryUiState.price,
                            newTotalWeight = spoolEntryUiState.totalWeight,
                            newColorHex = newValue,
                            newColorName = spoolEntryUiState.colorName,
                            newCurrentWeight = spoolEntryUiState.currentWeight,
                            newTempNozzle = spoolEntryUiState.tempNozzle,
                            newTempBed = spoolEntryUiState.tempBed,
                            newNote = spoolEntryUiState.note
                        )
                    },
                    onCurrentWeightValueChange = { newValue ->
                        spoolEntryViewModel.updateTextField(
                            newBrand = spoolEntryUiState.brand,
                            newMaterial = spoolEntryUiState.material,
                            newPrice = spoolEntryUiState.price,
                            newTotalWeight = spoolEntryUiState.totalWeight,
                            newColorHex = spoolEntryUiState.colorHex,
                            newColorName = spoolEntryUiState.colorName,
                            newCurrentWeight = newValue,
                            newTempNozzle = spoolEntryUiState.tempNozzle,
                            newTempBed = spoolEntryUiState.tempBed,
                            newNote = spoolEntryUiState.note
                        )
                    },
                    onNozzleTempValueChange = { newValue ->
                        spoolEntryViewModel.updateTextField(
                            newBrand = spoolEntryUiState.brand,
                            newMaterial = spoolEntryUiState.material,
                            newPrice = spoolEntryUiState.price,
                            newTotalWeight = spoolEntryUiState.totalWeight,
                            newColorHex = spoolEntryUiState.colorHex,
                            newColorName = spoolEntryUiState.colorName,
                            newCurrentWeight = spoolEntryUiState.currentWeight,
                            newTempNozzle = newValue,
                            newTempBed = spoolEntryUiState.tempBed,
                            newNote = spoolEntryUiState.note
                        )
                    },
                    onBedTempValueChange = { newValue ->
                        spoolEntryViewModel.updateTextField(
                            newBrand = spoolEntryUiState.brand,
                            newMaterial = spoolEntryUiState.material,
                            newPrice = spoolEntryUiState.price,
                            newTotalWeight = spoolEntryUiState.totalWeight,
                            newColorHex = spoolEntryUiState.colorHex,
                            newColorName = spoolEntryUiState.colorName,
                            newCurrentWeight = spoolEntryUiState.currentWeight,
                            newTempNozzle = spoolEntryUiState.tempNozzle,
                            newTempBed = newValue,
                            newNote = spoolEntryUiState.note
                        )
                    },
                    onNoteValueChange = { newValue ->
                        spoolEntryViewModel.updateTextField(
                            newBrand = spoolEntryUiState.brand,
                            newMaterial = spoolEntryUiState.material,
                            newPrice = spoolEntryUiState.price,
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
                    uiState = printUiState,
                    onPrintWeightValueChange = { newGramsUsed ->
                        spoolDetailsViewModel.quickDeductionUpdateField(
                            gramsUsed = newGramsUsed,
                            printTitle = printUiState.printTitle,
                            isFailed = printUiState.isFailed,
                        )
                    },
                    onPrintTitleValueChange = { newPrintTitle ->
                        spoolDetailsViewModel.quickDeductionUpdateField(
                            gramsUsed = printUiState.gramsUsed,
                            printTitle = newPrintTitle,
                            isFailed = printUiState.isFailed
                        )
                    },
                    onCheckedChange = { newChecked ->
                        spoolDetailsViewModel.quickDeductionUpdateField(
                            gramsUsed = printUiState.gramsUsed,
                            printTitle = printUiState.printTitle,
                            isFailed = newChecked
                        )
                    },
                    onPrintWeightClick = { id, weight ->
                        spoolDetailsViewModel.validateInputErrorsOfPrintObjectUiState()
                        spoolDetailsViewModel.deductCurrentWeight(id, weight)
                    },
                    isPrintErrorState = isPrintErrorState,
                    onPrintHistoryClick = {
                        backStack.add(Routes.PrintHistory(entry.id))
                        printHistoryViewModel.triggerId(entry.id)
                    }
                )
            }

            entry<Routes.PrintHistory> { entry ->
                PrintHistoryScreen(
                    navigateUp = { backStack.removeLastOrNull() },
                    usageLog = spoolPrintUsageHistoryDetails,
                    onEditClick = { log ->
                        spoolDetailsViewModel.prepareEditLog(log)
                    },
                    onDeleteClick = { usageLog ->
                        printHistoryViewModel.deletePrintItem(usageLog)
                    },
                    uiState = printUiState,
                    onGramsUsedValueChange = { newGramsUsed ->
                        spoolDetailsViewModel.quickDeductionUpdateField(
                            gramsUsed = newGramsUsed,
                            printTitle = printUiState.printTitle,
                            isFailed = printUiState.isFailed,
                        )
                    },
                    onPrintTitleValueChange = { newPrintTitle ->
                        spoolDetailsViewModel.quickDeductionUpdateField(
                            gramsUsed = printUiState.gramsUsed,
                            printTitle = newPrintTitle,
                            isFailed = printUiState.isFailed
                        )
                    },
                    onCheckedChange = { newChecked ->
                        spoolDetailsViewModel.quickDeductionUpdateField(
                            gramsUsed = printUiState.gramsUsed,
                            printTitle = printUiState.printTitle,
                            isFailed = newChecked
                        )
                    },
                    isPrintErrorState = isPrintErrorState,
                    onConfirm = { id, weight ->
                        spoolDetailsViewModel.deductCurrentWeight(id = id, inputWeight = weight)
                    },
                )
            }

        }
    )
}