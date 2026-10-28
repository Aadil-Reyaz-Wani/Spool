package com.aadil.spool

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.aadil.spool.core.data.repository.InventoryScope
import com.aadil.spool.core.data.sync.*
import com.aadil.spool.core.model.*
import com.aadil.spool.data.entity.InventoryConflictRecord
import com.aadil.spool.data.entity.InventoryUploadBatch
import com.aadil.spool.feature.settings.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class InventoryConflictsViewModelTest {
    private val scope = InventoryScope("a", 7)
    private val stores = mutableListOf<ViewModelStore>()
    @BeforeTest fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun teardown() { stores.forEach { it.clear() }; Dispatchers.resetMain() }
    private fun review(): InventoryConflictReview {
        val spool = testSpool().copy(ownerId = "a")
        val remote = RemoteInventorySpool("a", spool.uuid, 2, null, spool.brand, spool.material, spool.colorName,
            spool.colorHex, spool.totalWeight, 800.0, spool.tempNozzle, spool.tempBed, spool.note, spool.price,
            spool.timestamp, null, null, null, null)
        val batch = InventoryUploadBatch("a", spool.uuid, "batch", 1, """{"p_spool":{"brand":"TestCo"}}""", "[]", "{}")
        val record = InventoryConflictRecord(batch, spool, emptyList(), "{}", "[]", "[]")
        return InventoryConflictReview(scope, record, RemoteInventorySnapshot(remote.copy(currentWeight = 900.0), emptyList()), RemoteInventorySnapshot(remote, emptyList()))
    }
    private inner class Repository : InventoryConflictRepository {
        val value = review()
        val rows = MutableStateFlow(listOf(value.record.batch))
        var reviews = 0
        var resolutions = 0
        var nextReview: InventoryConflictReviewResult = InventoryConflictReviewResult.Ready(value)
        var nextResolve = InventoryConflictResolveResult.Resolved
        var reviewHandler: (suspend () -> InventoryConflictReviewResult)? = null
        override fun observeConflicts(scope: InventoryScope) = rows
        override suspend fun review(scope: InventoryScope, spoolUuid: String): InventoryConflictReviewResult {
            reviews++
            return reviewHandler?.invoke() ?: nextReview
        }
        override suspend fun resolve(review: InventoryConflictReview, choice: InventoryConflictChoice): InventoryConflictResolveResult {
            resolutions++
            if (nextResolve == InventoryConflictResolveResult.Resolved) rows.value = emptyList()
            return nextResolve
        }
    }
    private fun vm(repo: Repository, saved: SavedStateHandle = SavedStateHandle(), retry: () -> Unit = {}): InventoryConflictsViewModel =
        InventoryConflictsViewModel(repo, scope, saved, flowOf("USD"), retry).also { viewModel ->
            stores += ViewModelStore().also { it.put("conflicts", viewModel) }
        }

    @Test fun previewAndCancelledConfirmationPerformNoWritesAndSuccessWakesSync() = runTest {
        val repo = Repository(); var retries = 0
        val vm = vm(repo, retry = { retries++ })
        vm.open(repo.value.record.batch.spoolUuid)
        runCurrent()
        assertNotNull(vm.state.value.review)
        assertEquals(0, repo.resolutions)
        vm.requestChoice(InventoryConflictChoice.Local)
        assertEquals(InventoryConflictChoice.Local, vm.state.value.confirmation)
        vm.dismissConfirmation()
        vm.confirm()
        assertEquals(0, repo.resolutions)
        vm.requestChoice(InventoryConflictChoice.Cloud)
        vm.confirm()
        runCurrent()
        assertEquals(1, repo.resolutions)
        assertEquals(1, retries)
        assertNull(vm.state.value.review)
        assertNull(vm.state.value.selected)
    }

    @Test fun staleChoiceClearsPreviewAndMustBeRefreshedBeforeAnotherConfirmation() = runTest {
        val repo = Repository(); val vm = vm(repo)
        vm.open(repo.value.record.batch.spoolUuid)
        runCurrent()
        repo.nextResolve = InventoryConflictResolveResult.Changed
        vm.requestChoice(InventoryConflictChoice.Local); vm.confirm()
        runCurrent()
        assertEquals(InventoryConflictError.Changed, vm.state.value.error)
        assertNull(vm.state.value.review)
        vm.requestChoice(InventoryConflictChoice.Local); vm.confirm()
        assertEquals(1, repo.resolutions)
        vm.refresh(); runCurrent()
        assertNotNull(vm.state.value.review)
        assertNull(vm.state.value.error)
    }

    @Test fun offlineResolutionRetainsPreviewAndDoesNotPretendChoiceSucceeded() = runTest {
        val repo = Repository(); var retries = 0
        val vm = vm(repo, retry = { retries++ })
        vm.open(repo.value.record.batch.spoolUuid); runCurrent()
        repo.nextResolve = InventoryConflictResolveResult.Unavailable
        vm.requestChoice(InventoryConflictChoice.Cloud); vm.confirm(); runCurrent()
        assertEquals(InventoryConflictError.Unavailable, vm.state.value.error)
        assertNotNull(vm.state.value.review)
        assertEquals(0, retries)
        assertFalse(vm.state.value.busy)
    }

    @Test fun processRecreationRefetchesComparisonButNeverReplaysConfirmation() = runTest {
        val repo = Repository(); val saved = SavedStateHandle(); val first = vm(repo, saved)
        first.open(repo.value.record.batch.spoolUuid); runCurrent()
        first.requestChoice(InventoryConflictChoice.Local)
        val restored = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
        val second = vm(repo, restored); runCurrent()
        assertEquals(2, repo.reviews)
        assertNotNull(second.state.value.review)
        assertNull(second.state.value.confirmation)
        second.confirm()
        assertEquals(0, repo.resolutions)
    }

    @Test fun leavingAnInFlightReviewCancelsItsResultAndDoesNotReopenScreen() = runTest {
        val repo = Repository(); val waiting = CompletableDeferred<InventoryConflictReviewResult>()
        repo.reviewHandler = { waiting.await() }
        val vm = vm(repo)
        vm.open(repo.value.record.batch.spoolUuid)
        assertTrue(vm.state.value.busy)
        vm.closeReview()
        waiting.complete(InventoryConflictReviewResult.Ready(repo.value)); runCurrent()
        assertNull(vm.state.value.selected)
        assertNull(vm.state.value.review)
        assertFalse(vm.state.value.busy)
    }
}
