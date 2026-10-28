package com.aadil.spool

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.aadil.spool.feature.dashboard.FitCheckViewModel
import com.aadil.spool.feature.details.SpoolDetailsViewModel
import com.aadil.spool.feature.entry.SpoolEntryError
import com.aadil.spool.feature.entry.SpoolEntryViewModel
import com.aadil.spool.data.entity.UsageLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InventoryRegressionTest {
    private val stores = mutableListOf<ViewModelStore>()

    @BeforeTest fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun teardown() {
        stores.forEach { it.clear() }
        Dispatchers.resetMain()
    }

    private fun <T : ViewModel> keep(vm: T): T {
        stores += ViewModelStore().also { it.put("test", vm) }
        return vm
    }

    private fun restored(handle: SavedStateHandle) = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })

    @Test fun fitCheckRestoresScalarInputAndRecomputesResultsFromCurrentInventory() = runTest {
        val repo = FakeSpoolRepository(listOf(testSpool()))
        val saved = SavedStateHandle()
        val vm = keep(FitCheckViewModel(repo, saved))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.initialize(0.0)
        vm.updateGrams("500")
        vm.check()
        runCurrent()
        assertTrue(vm.uiState.value.results.single().fits)
        // This is the saved-state boundary that previously contained non-parcelable result objects.
        assertTrue(saved.keys().all { saved.get<Any?>(it) is String || saved.get<Any?>(it) is Double || saved.get<Any?>(it) is Boolean })
        repo.spools.value = listOf(testSpool().copy(currentWeight = 400.0))
        val recreated = keep(FitCheckViewModel(repo, restored(saved)))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { recreated.uiState.collect {} }
        recreated.initialize(0.0)
        runCurrent()
        assertEquals("500", recreated.uiState.value.gramsText)
        assertEquals(500.0, recreated.uiState.value.checkedGrams)
        assertFalse(recreated.uiState.value.results.single().fits)
        assertEquals(-100.0, recreated.uiState.value.results.single().excess)
    }

    @Test fun fitCheckUsesSubmittedWeightUntilNextCheck() = runTest {
        val vm = keep(FitCheckViewModel(FakeSpoolRepository(listOf(testSpool())), SavedStateHandle()))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.initialize(1000.0)
        vm.updateGrams("2000")
        runCurrent()
        assertEquals(1000.0, vm.uiState.value.checkedGrams)
        assertTrue(vm.uiState.value.results.single().fits)
        vm.check()
        runCurrent()
        assertFalse(vm.uiState.value.results.single().fits)
    }

    @Test fun negativeMalformedAndOverflowingRestockNeverWrites() = runTest {
        val cases = listOf("-2000" to "50", "250" to "-300", "abc" to "50", "250" to "NaN", "1e309" to "50", "1.7e308" to "1.7e308")
        for ((weight, price) in cases) {
            val original = testSpool().copy(totalWeight = 1.7e308, currentWeight = 1.7e308)
            val repo = FakeSpoolRepository(listOf(original))
            val vm = keep(SpoolEntryViewModel(repo, SavedStateHandle()))
            vm.loadSpool(7)
            advanceUntilIdle()
            vm.update { copy(addedWeight = weight, addedPrice = price) }
            vm.saveOrUpdateSpool(7)
            advanceUntilIdle()
            assertEquals(SpoolEntryError.INVALID_RESTOCK, vm.error.value, "$weight / $price")
            assertEquals(0, repo.writes)
            assertEquals(original, repo.spools.value.single())
            assertFalse(vm.saveCompleted.value)
        }
    }

    @Test fun positiveRestockInvalidatesOldDrynessMeasurementsAndRepricesHistory() = runTest {
        val original = testSpool()
        val repo = FakeSpoolRepository(listOf(original))
        repo.logs.value = listOf(UsageLog(id = 3, spoolId = 7, title = "Print", gramsUsed = 100.0, timestamp = 1, isFailure = false))
        val vm = keep(SpoolEntryViewModel(repo, SavedStateHandle()))
        vm.loadSpool(7)
        advanceUntilIdle()
        vm.update { copy(addedWeight = "250,5", addedPrice = "50,1") }
        vm.saveOrUpdateSpool(7)
        advanceUntilIdle()
        assertEquals(original.copy(
            totalWeight = 1250.5, currentWeight = 1250.5, price = 250.1,
            dryBaselineWeight = null, dryBaselineTareGrams = null,
            lastWeighedTareGrams = null, lastDriedAt = null,
        ), repo.spools.value.single())
        assertEquals(20.0, repo.logs.value.single().pricePerPrint, 0.00001)
        assertTrue(vm.saveCompleted.value)
    }

    @Test fun explicitZeroRemainingIsSavedAsZero() = runTest {
        val repo = FakeSpoolRepository()
        val vm = keep(SpoolEntryViewModel(repo, SavedStateHandle()))
        vm.loadSpool(0)
        vm.update { copy(brand = "TestCo", material = "PLA", totalWeight = "1000", currentWeight = "0") }
        vm.saveOrUpdateSpool(0)
        advanceUntilIdle()
        assertEquals(0.0, repo.spools.value.single().currentWeight)
        assertTrue(vm.saveCompleted.value)
    }

    @Test fun blankRemainingDefaultsToInitialWeightForNewSpool() = runTest {
        val repo = FakeSpoolRepository()
        val vm = keep(SpoolEntryViewModel(repo, SavedStateHandle()))
        vm.update { copy(brand = "TestCo", material = "PLA", totalWeight = "1000") }
        vm.saveOrUpdateSpool(0)
        advanceUntilIdle()
        assertEquals(1000.0, repo.spools.value.single().currentWeight)
    }

    @Test fun noteEditRetainsDryingDataAndCreationTime() = runTest {
        val original = testSpool()
        val repo = FakeSpoolRepository(listOf(original))
        val vm = keep(SpoolEntryViewModel(repo, SavedStateHandle()))
        vm.loadSpool(7)
        advanceUntilIdle()
        vm.update { copy(note = "Changed note") }
        vm.saveOrUpdateSpool(7)
        advanceUntilIdle()
        assertEquals(original.copy(note = "Changed note"), repo.spools.value.single())
    }

    @Test fun addedPriceWithoutAddedFilamentPreservesDryingData() = runTest {
        val original = testSpool()
        val repo = FakeSpoolRepository(listOf(original))
        val vm = keep(SpoolEntryViewModel(repo, SavedStateHandle()))
        vm.loadSpool(7)
        advanceUntilIdle()
        vm.update { copy(addedWeight = "0", addedPrice = "5") }
        vm.saveOrUpdateSpool(7)
        advanceUntilIdle()
        assertEquals(original.copy(price = 205.0), repo.spools.value.single())
    }

    @Test fun newPrintSavesWeightAndHistoryTogetherAndFailureKeepsDraft() = runTest {
        val repo = FakeSpoolRepository(listOf(testSpool())).apply { failSaving = true }
        val vm = keep(SpoolDetailsViewModel(repo))
        vm.quickDeductionUpdateField("50", "New print", false)
        vm.deductCurrentWeight(7, "50")
        advanceUntilIdle()
        assertEquals(1000.0, repo.spools.value.single().currentWeight)
        assertTrue(repo.logs.value.isEmpty())
        assertEquals("New print", vm.printObjectUiState.value.printTitle)
        repo.failSaving = false
        vm.deductCurrentWeight(7, "50")
        advanceUntilIdle()
        assertEquals(950.0, repo.spools.value.single().currentWeight)
        assertEquals(50.0, repo.logs.value.single().gramsUsed)
        assertEquals(10.0, repo.logs.value.single().pricePerPrint)
        assertEquals("New print", repo.logs.value.single().title)
        assertEquals("", vm.printObjectUiState.value.printTitle)
        assertEquals(1, repo.writes)
    }

    @Test fun editedPrintRecalculatesCostFromNewWeightWithoutDetailsSubscription() = runTest {
        val repo = FakeSpoolRepository(listOf(testSpool().copy(currentWeight = 899.5)))
        val log = UsageLog(id = 3, spoolId = 7, title = "Print", gramsUsed = 100.5, pricePerPrint = 20.10, timestamp = 1, isFailure = false)
        repo.logs.value = listOf(log)
        val vm = keep(SpoolDetailsViewModel(repo))
        vm.prepareEditLog(log)
        vm.quickDeductionUpdateField("50", "Edited", false)
        vm.deductCurrentWeight(7, "50")
        advanceUntilIdle()
        assertEquals(950.0, repo.spools.value.single().currentWeight)
        assertEquals(50.0, repo.logs.value.single().gramsUsed)
        assertEquals(10.0, repo.logs.value.single().pricePerPrint)
        assertEquals("Edited", repo.logs.value.single().title)
        assertEquals(log.uuid, repo.logs.value.single().uuid)
        assertEquals(log.timestamp, repo.logs.value.single().timestamp)
        assertNull(vm.isError.value)
    }

    @Test fun editDraftSurvivesProcessRecreationAndSavesAgainstFreshMetadata() = runTest {
        val repo = FakeSpoolRepository(listOf(testSpool()))
        val saved = SavedStateHandle()
        val vm = keep(SpoolEntryViewModel(repo, saved))
        vm.loadSpool(7)
        advanceUntilIdle()
        vm.update { copy(brand = "Draft brand", note = "Unsaved note") }
        // A later weigh-in must not be erased when the restored draft is saved.
        val latest = testSpool().copy(lastWeighedTareGrams = 200.0, lastDriedAt = 101)
        repo.spools.value = listOf(latest)
        val recreated = keep(SpoolEntryViewModel(repo, restored(saved)))
        recreated.loadSpool(7)
        advanceUntilIdle()
        assertEquals(vm.spoolEntryUiState.value, recreated.spoolEntryUiState.value)
        recreated.saveOrUpdateSpool(7)
        advanceUntilIdle()
        assertEquals(latest.copy(brand = "Draft brand", note = "Unsaved note"), repo.spools.value.single())
    }

    @Test fun newSpoolDraftSurvivesProcessRecreation() = runTest {
        val repo = FakeSpoolRepository()
        val saved = SavedStateHandle()
        val vm = keep(SpoolEntryViewModel(repo, saved))
        vm.loadSpool(0)
        vm.update { copy(brand = "Draft", material = "PLA", totalWeight = "1000", currentWeight = "0") }
        val recreated = keep(SpoolEntryViewModel(repo, restored(saved)))
        recreated.loadSpool(0)
        assertEquals(vm.spoolEntryUiState.value, recreated.spoolEntryUiState.value)
        recreated.saveOrUpdateSpool(0)
        advanceUntilIdle()
        assertEquals(0.0, repo.spools.value.single().currentWeight)
    }

    @Test fun zeroNegativeAndMalformedInitialWeightShowErrorWithoutSuccess() = runTest {
        for (weight in listOf("0", "-1", "abc", "NaN", "Infinity")) {
            val repo = FakeSpoolRepository()
            val vm = keep(SpoolEntryViewModel(repo, SavedStateHandle()))
            vm.update { copy(brand = "TestCo", material = "PLA", totalWeight = weight, currentWeight = "0") }
            vm.saveOrUpdateSpool(0)
            advanceUntilIdle()
            assertEquals(SpoolEntryError.INVALID_INPUT, vm.error.value, weight)
            assertFalse(vm.saveCompleted.value)
            assertEquals(0, repo.writes)
        }
    }

    @Test fun saveSuccessWaitsForPersistenceAndRepeatedTapsDoNotDuplicate() = runTest {
        val repo = FakeSpoolRepository().apply { saveGate = CompletableDeferred() }
        val vm = keep(SpoolEntryViewModel(repo, SavedStateHandle()))
        vm.update { copy(brand = "TestCo", material = "PLA", totalWeight = "1000", currentWeight = "0") }
        vm.saveOrUpdateSpool(0)
        vm.saveOrUpdateSpool(0)
        runCurrent()
        assertTrue(vm.isSaving.value)
        assertFalse(vm.saveCompleted.value)
        assertTrue(repo.spools.value.isEmpty())
        repo.saveGate!!.complete(Unit)
        advanceUntilIdle()
        assertTrue(vm.saveCompleted.value)
        assertEquals(1, repo.writes)
        assertFalse(vm.isSaving.value)
    }

    @Test fun failedSaveRetainsDraftAndCanBeRetried() = runTest {
        val repo = FakeSpoolRepository().apply { failSaving = true }
        val vm = keep(SpoolEntryViewModel(repo, SavedStateHandle()))
        vm.update { copy(brand = "TestCo", material = "PLA", totalWeight = "1000", currentWeight = "0") }
        val draft = vm.spoolEntryUiState.value
        vm.saveOrUpdateSpool(0)
        advanceUntilIdle()
        assertEquals(SpoolEntryError.SAVE_FAILED, vm.error.value)
        assertEquals(draft, vm.spoolEntryUiState.value)
        assertFalse(vm.saveCompleted.value)
        repo.failSaving = false
        vm.saveOrUpdateSpool(0)
        advanceUntilIdle()
        assertTrue(vm.saveCompleted.value)
        assertNull(vm.error.value)
    }

    @Test fun missingSpoolDoesNotBecomeAnEmptyEditableRecord() = runTest {
        val repo = FakeSpoolRepository()
        val vm = keep(SpoolEntryViewModel(repo, SavedStateHandle()))
        vm.loadSpool(7)
        advanceUntilIdle()
        assertEquals(SpoolEntryError.LOAD_FAILED, vm.error.value)
        assertFalse(vm.isLoading.value)
        assertEquals(0, repo.writes)
    }
}
