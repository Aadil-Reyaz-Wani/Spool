package com.aadil.spool.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import spool.shared.generated.resources.*
import com.aadil.spool.utils.PlatformBackHandler
import com.aadil.spool.ui.common.SpoolAppBar
import com.aadil.spool.ui.screens.dashboard.DashboardScreen
import com.aadil.spool.feature.dashboard.DashboardViewModel
import com.aadil.spool.ui.screens.details.SpoolDetailsScreen
import com.aadil.spool.feature.details.SpoolDetailsViewModel
import com.aadil.spool.ui.screens.entry.SpoolEntryScreen
import com.aadil.spool.feature.entry.SpoolEntryViewModel
import com.aadil.spool.ui.screens.history.PrintHistoryScreen
import com.aadil.spool.feature.history.PrintHistoryViewModel
import com.aadil.spool.ui.screens.settings.AboutScreen
import com.aadil.spool.ui.screens.settings.HelpScreen
import com.aadil.spool.feature.settings.SpoolSettingsViewModel
import com.aadil.spool.ui.screens.splash.SplashScreen
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import com.aadil.spool.core.data.repository.NotificationPreferencesRepository
import com.aadil.spool.core.data.repository.SpoolRepository
import com.aadil.spool.notifications.NotificationPoster
import com.aadil.spool.notifications.runLowStockCheck

@OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
@Composable
fun MySpoolApp(modifier: Modifier = Modifier, initialSpoolId: Int? = null) {

    // Define ViewModels
    val dashboardViewModel: DashboardViewModel = koinViewModel()
    val spoolEntryViewModel: SpoolEntryViewModel = koinViewModel()
    val spoolDetailsViewModel: SpoolDetailsViewModel = koinViewModel()
    val printHistoryViewModel: PrintHistoryViewModel = koinViewModel()
    val spoolSettingsViewModel: SpoolSettingsViewModel = koinViewModel()

    // Dashboard
    val listOfSpools by dashboardViewModel.getAllSpool.collectAsStateWithLifecycle()
    val listOfUniqueBrandStrings by dashboardViewModel.getUniqueBrand.collectAsStateWithLifecycle()
    val listOfUniqueMaterialTypeStrings by dashboardViewModel.getUniqueMaterialType.collectAsStateWithLifecycle()
    val listOfUniqueColorHex by dashboardViewModel.getUniqueColorHex.collectAsStateWithLifecycle()
    val filterAppliedState by dashboardViewModel.filterAppliedState.collectAsStateWithLifecycle()


    // Entry
    val spoolEntryUiState by spoolEntryViewModel.spoolEntryUiState.collectAsStateWithLifecycle()
    val isError by spoolEntryViewModel.isError.collectAsStateWithLifecycle()

    // Details
    val spoolDetails by spoolDetailsViewModel.spoolDetails.collectAsStateWithLifecycle()
    val printUiState by spoolDetailsViewModel.printObjectUiState.collectAsStateWithLifecycle()
    val isPrintErrorState by spoolDetailsViewModel.isError.collectAsStateWithLifecycle()


    // Print History
    val spoolPrintUsageHistoryDetails by printHistoryViewModel.spoolPrintUsageHistoryDetails.collectAsStateWithLifecycle()

    // Settings
    val selectedCurrency by spoolSettingsViewModel.selectedCurrency.collectAsStateWithLifecycle()

    // Navigation
    val backStack = rememberNavBackStack(
        configuration = SavedStateConfiguration {
            serializersModule = SerializersModule {
                polymorphic(baseClass = NavKey::class) {
                    subclass(serializer = Routes.Splash.serializer())
                    subclass(serializer = Routes.Dashboard.serializer())
                    subclass(serializer = Routes.SpoolEntry.serializer())
                    subclass(serializer = Routes.SpoolDetails.serializer())
                    subclass(serializer = Routes.PrintHistory.serializer())
                    subclass(serializer = Routes.Filter.serializer())
                    subclass(serializer = Routes.About.serializer())
                    subclass(serializer = Routes.OpenSourceLicenses.serializer())
                    subclass(serializer = Routes.Help.serializer())
                }
            }
        },
        Routes.Splash
    )

    // It safely removes the screens from the backstack when the back button is pressed.
    // without the crash if the user presses the back button twice.
    val safePopBackStack: () -> Unit = {
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
    }

    var aboutLibsJson by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        try {
            aboutLibsJson = Res.readBytes("files/aboutlibraries.json").decodeToString()
        } catch (e: Exception) {
            aboutLibsJson = ""
        }
    }

    // Low-stock check on every app open (periodic WorkManager check covers background).
    val spoolRepository = koinInject<SpoolRepository>()
    val alertRepository = koinInject<NotificationPreferencesRepository>()
    val poster = koinInject<NotificationPoster>()
    LaunchedEffect(Unit) {
        runLowStockCheck(spoolRepository, alertRepository, poster)
    }

    // Notification tap -> deep link to the spool. Skipped while still on splash;
    // the splash transition handles that case.
    LaunchedEffect(initialSpoolId) {
        val id = initialSpoolId ?: return@LaunchedEffect
        if (backStack.lastOrNull() !is Routes.Splash) {
            backStack.add(Routes.SpoolDetails(id))
        }
    }

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
                    if (initialSpoolId != null) {
                        backStack.add(Routes.SpoolDetails(initialSpoolId))
                    } else {
                        backStack.add(Routes.Dashboard)
                    }
                    backStack.remove(Routes.Splash)
                }
            }

            // Dashboard Screen Entry
            entry<Routes.Dashboard> {
                PlatformBackHandler(enabled = backStack.size == 1)

                DashboardScreen(
                    onFabClick = {
                        backStack.add(Routes.SpoolEntry(id = 0))
                    },
                    onCardClick = { id ->
                        backStack.add(Routes.SpoolDetails(id))
                    },
                    listOfSpools = listOfSpools,
                    listOfUniqueBrandStrings = listOfUniqueBrandStrings,
                    listOfUniqueMaterialTypeStrings = listOfUniqueMaterialTypeStrings,
                    listOfUniqueColorHex = listOfUniqueColorHex,
                    onFilterStringClick = { filterString, filterType ->
                        dashboardViewModel.applyFilter(
                            filterValue = filterString,
                            type = filterType
                        )
                    },
                    onCurrencyStringClick = { currencyCode ->
                        spoolSettingsViewModel.saveCurrency(currencyCode)
                    },
                    selectedOption = filterAppliedState.whichFilter,
                    selectedCurrency = selectedCurrency,
                    onAboutClick = {
                        backStack.add(Routes.About)
                    },
                    onHelpClick = {
                        backStack.add(Routes.Help)
                    }
                )
            }

            // Entry Screen Entry
            entry<Routes.SpoolEntry> { entry ->
                var hasLoadedEntry by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(entry.id) {
                    if (!hasLoadedEntry) {
                        hasLoadedEntry = true
                        spoolEntryViewModel.loadSpool(entry.id)
                    }
                }
                SpoolEntryScreen(
                    onNavigateUp = {
                        safePopBackStack()
                    },
                    uiState = spoolEntryUiState,
                    onBrandValueChange = { newValue ->
                        spoolEntryViewModel.update { copy(brand = newValue) }
                    },
                    onMaterialValueChange = { newValue ->
                        spoolEntryViewModel.update { copy(material = newValue) }
                    },
                    onPriceValueChange = { newValue ->
                        spoolEntryViewModel.update { copy(price = newValue) }
                    },
                    onInitialWeightValueChange = { newValue ->
                        spoolEntryViewModel.update { copy(totalWeight = newValue) }
                    },
                    onColorNameChange = { newValue ->
                        spoolEntryViewModel.update { copy(colorName = newValue) }
                    },
                    onColorValueChange = { newValue ->
                        spoolEntryViewModel.update { copy(colorHex = newValue) }
                    },
                    onCurrentWeightValueChange = { newValue ->
                        spoolEntryViewModel.update { copy(currentWeight = newValue) }
                    },
                    onNozzleTempValueChange = { newValue ->
                        spoolEntryViewModel.update { copy(tempNozzle = newValue) }
                    },
                    onBedTempValueChange = { newValue ->
                        spoolEntryViewModel.update { copy(tempBed = newValue) }
                    },
                    onNoteValueChange = { newValue ->
                        spoolEntryViewModel.update { copy(note = newValue) }
                    },
                    onAddedWeightValueChange = { newValue ->
                        spoolEntryViewModel.update { copy(addedWeight = newValue) }
                    },
                    onAddedPriceValueChange = { newValue ->
                        spoolEntryViewModel.update { copy(addedPrice = newValue) }
                    },
                    onSaveOrUpdateClick = {
                        spoolEntryViewModel.saveOrUpdateSpool(entry.id)
                        if (spoolEntryViewModel.isValid()) {
                            safePopBackStack()
                        }
                    },
                    selectedColor = spoolEntryUiState.colorHex,
                    isError = isError,
                    isEditMode = spoolEntryViewModel.isEditMode(entry.id),
                    resetState = spoolEntryViewModel::resetState,
                    modifier = modifier,
                    selectedCurrency = selectedCurrency
                )
            }

            // Details Screen Entry
            entry<Routes.SpoolDetails> { entry ->
                LaunchedEffect(entry.id) {
                    spoolDetailsViewModel.loadSpool(entry.id)
                    spoolDetailsViewModel.resetPrintObjectUiState()
                }
                SpoolDetailsScreen(
                    spoolDetails = spoolDetails,
                    navigateUp = {
                        safePopBackStack()
                    },
                    onUpdateClick = { id ->
                        backStack.add(Routes.SpoolEntry(id = id))
                    },
                    onConfirmDelete = { filament ->
                        spoolDetailsViewModel.deleteSpool(filament)
                        safePopBackStack()
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
                    },
                    onMarkAsDried = { remaining, tare ->
                        spoolDetailsViewModel.markAsDried(entry.id, remaining, tare)
                    },
                    onWeighNow = { remaining, tare ->
                        spoolDetailsViewModel.updateCurrentWeight(entry.id, remaining, tare)
                    }
                )
            }

            entry<Routes.PrintHistory> { entry ->
                PrintHistoryScreen(
                    navigateUp = { safePopBackStack() },
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
                    selectedCurrency = selectedCurrency
                )
            }

            entry<Routes.About> {
                AboutScreen(
                    navigateUp = { safePopBackStack() },
                    onOpenSourceLicenseClick = {
                        backStack.add(Routes.OpenSourceLicenses)
                    }
                )
            }

            entry<Routes.OpenSourceLicenses> {
                Scaffold(
                    topBar = {
                        SpoolAppBar(
                            title = stringResource(Res.string.open_source_licenses_label),
                            canNavigateBack = true,
                            navigateUp = safePopBackStack
                        )
                    }
                ) { paddingValues ->
                    LibrariesContainer(
                        aboutLibsJson = aboutLibsJson,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                    )
                }
            }

            entry<Routes.Help> {
                HelpScreen(
                    navigateUp = safePopBackStack
                )
            }

        }
    )
}