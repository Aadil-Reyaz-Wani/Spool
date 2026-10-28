package com.aadil.spool

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.aadil.spool.core.data.repository.InventoryScope
import com.aadil.spool.core.data.sync.*
import com.aadil.spool.core.model.*
import com.aadil.spool.data.entity.InventoryRecoverySource
import com.aadil.spool.feature.settings.InventoryRecoveryViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class InventoryRecoveryViewModelTest {
    private val scope = InventoryScope("a", 7)
    private val stores = mutableListOf<ViewModelStore>()
    @BeforeTest fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun teardown() { stores.forEach { it.clear() }; Dispatchers.resetMain() }
    private class Repository : InventoryRecoveryRepository {
        val rows = MutableStateFlow(listOf(InventoryRecoveryItem("copy", "deleted_device", "Test", 123, false)))
        val snapshot = RemoteInventorySnapshot(RemoteInventorySpool("a", "uuid", 1, null,
            "Test", "PLA", "Black", 123, 1000.0, 900.0, 200, 60, "Note", 20.0, 123, null, null, null, null), emptyList())
        var value = InventoryRecoveryPreview(InventoryRecoverySource("a", "copy", "deleted_device", "{}", 123, null), snapshot)
        var previews = 0
        var restores = 0
        var removals = 0
        var removalResult = InventoryRecoveryRemovalResult.Removed
        var removeHandler: (suspend () -> InventoryRecoveryRemovalResult)? = null
        var result = InventoryRecoveryResult.Restored
        var handler: (suspend () -> InventoryRecoveryPreview?)? = null
        var restoreHandler: (suspend () -> InventoryRecoveryResult)? = null
        override suspend fun remove(scope: InventoryScope, id: String): InventoryRecoveryRemovalResult {
            removals++; return removeHandler?.invoke() ?: removalResult
        }
        override fun observe(scope: InventoryScope) = rows
        override suspend fun preview(scope: InventoryScope, id: String): InventoryRecoveryPreview? { previews++; return handler?.invoke() ?: value }
        override suspend fun restore(scope: InventoryScope, preview: InventoryRecoveryPreview): InventoryRecoveryResult {
            restores++; return restoreHandler?.invoke() ?: result
        }
    }
    private fun vm(repo: Repository, saved: SavedStateHandle = SavedStateHandle(), retry: () -> Unit = {}) =
        InventoryRecoveryViewModel(repo, scope, saved, flowOf("USD"), retry).also { model ->
            stores += ViewModelStore().also { it.put("recovery", model) }
        }

    @Test fun previewCancelAndExplicitConfirmationOnlyRestoreOnceAndWakeSync() = runTest {
        val repo = Repository(); var retries = 0; val vm = vm(repo, retry = { retries++ })
        vm.open("copy"); runCurrent()
        assertNotNull(vm.state.value.preview); assertEquals(0, repo.restores)
        vm.requestRestore(); vm.dismissConfirmation(); vm.confirmRestore()
        assertEquals(0, repo.restores)
        vm.requestRestore(); vm.confirmRestore(); runCurrent(); vm.confirmRestore()
        assertEquals(1, repo.restores); assertEquals(1, retries)
        assertTrue(vm.state.value.restored); assertNull(vm.state.value.preview)
    }

    @Test fun recreatedScreenReloadsPreviewWithoutReplayingRestore() = runTest {
        val repo = Repository(); val saved = SavedStateHandle(); val first = vm(repo, saved)
        first.open("copy"); runCurrent(); first.requestRestore()
        val second = vm(repo, SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })); runCurrent()
        assertEquals(2, repo.previews); assertNotNull(second.state.value.preview)
        assertFalse(second.state.value.confirm); second.confirmRestore(); assertEquals(0, repo.restores)
    }

    @Test fun failureRequiresReopeningPreviewAndDoesNotShowSuccess() = runTest {
        val repo = Repository(); repo.result = InventoryRecoveryResult.Unavailable
        var retries = 0; val vm = vm(repo, retry = { retries++ })
        vm.open("copy"); runCurrent(); vm.requestRestore(); vm.confirmRestore(); runCurrent()
        assertTrue(vm.state.value.error); assertFalse(vm.state.value.restored)
        assertNull(vm.state.value.preview); assertFalse(vm.state.value.busy); assertEquals(0, retries)
        vm.requestRestore(); vm.confirmRestore(); assertEquals(1, repo.restores)
        vm.open("copy"); runCurrent(); assertNotNull(vm.state.value.preview)
    }

    @Test fun restoredReceiptDisablesConfirmationAndScopeLossClearsPreview() = runTest {
        val repo = Repository(); repo.value = repo.value.copy(source = repo.value.source.copy(restoredUuid = "new"))
        val vm = vm(repo); vm.open("copy"); runCurrent(); vm.requestRestore(); vm.confirmRestore()
        assertFalse(vm.state.value.confirm); assertEquals(0, repo.restores)
        repo.rows.value = emptyList(); runCurrent(); assertNull(vm.state.value.selected)
    }

    @Test fun cancelledReadCannotReopenPreviewAndBusyRestoreIgnoresDuplicateTap() = runTest {
        val repo = Repository(); val read = CompletableDeferred<InventoryRecoveryPreview>()
        repo.handler = { read.await() }; val vm = vm(repo)
        vm.open("copy"); vm.closePreview(); read.complete(repo.value); runCurrent()
        assertNull(vm.state.value.preview); assertFalse(vm.state.value.busy)
        repo.handler = null; vm.open("copy"); runCurrent()
        val write = CompletableDeferred<InventoryRecoveryResult>(); repo.restoreHandler = { write.await() }
        vm.requestRestore(); vm.confirmRestore(); vm.confirmRestore()
        assertTrue(vm.state.value.busy); assertEquals(1, repo.restores)
        write.complete(InventoryRecoveryResult.Restored); runCurrent(); assertTrue(vm.state.value.restored)
    }
    @Test fun removalRequiresExplicitConfirmationAndDoesNotWakeSyncOrRestore() = runTest {
        val repo = Repository(); var retries = 0; val vm = vm(repo, retry = { retries++ })
        vm.requestRemoval("missing"); vm.confirmRemoval(); assertEquals(0, repo.removals)
        vm.requestRemoval("copy"); vm.dismissRemoval(); vm.confirmRemoval(); assertEquals(0, repo.removals)
        vm.open("copy"); runCurrent(); vm.requestRemoval("copy"); vm.confirmRemoval(); runCurrent(); vm.confirmRemoval()
        assertEquals(1, repo.removals); assertTrue(vm.state.value.removed)
        assertNull(vm.state.value.preview); assertNull(vm.state.value.selected)
        assertEquals(0, retries); assertEquals(0, repo.restores)
    }

    @Test fun removalConfirmationSurvivesSameViewModelButIsNotReplayedAfterRecreation() = runTest {
        val repo = Repository(); val saved = SavedStateHandle(); val first = vm(repo, saved)
        first.open("copy"); runCurrent(); first.requestRemoval("copy")
        assertEquals("copy", first.state.value.removal?.id)
        val second = vm(repo, SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })); runCurrent()
        assertNull(second.state.value.removal); second.confirmRemoval(); assertEquals(0, repo.removals)
        repo.rows.value = emptyList(); runCurrent(); assertNull(first.state.value.removal)
    }

    @Test fun busyRemovalIgnoresDuplicateTapAndFailureNeedsAnotherConfirmation() = runTest {
        val repo = Repository(); val vm = vm(repo); val write = CompletableDeferred<InventoryRecoveryRemovalResult>()
        repo.removeHandler = { write.await() }; vm.requestRemoval("copy"); vm.confirmRemoval()
        vm.requestRemoval("copy"); vm.confirmRemoval(); assertEquals(1, repo.removals); assertTrue(vm.state.value.busy)
        write.complete(InventoryRecoveryRemovalResult.Unavailable); runCurrent()
        assertTrue(vm.state.value.error); assertFalse(vm.state.value.removed); assertFalse(vm.state.value.busy)
        vm.confirmRemoval(); assertEquals(1, repo.removals)
        repo.removeHandler = null; vm.requestRemoval("copy"); vm.confirmRemoval(); runCurrent()
        assertTrue(vm.state.value.removed); assertEquals(2, repo.removals)
    }

    @Test fun staleScopeRemovalClosesPreviewWithoutSuccess() = runTest {
        val repo = Repository(); repo.removalResult = InventoryRecoveryRemovalResult.StaleScope
        val vm = vm(repo); vm.open("copy"); runCurrent(); vm.requestRemoval("copy"); vm.confirmRemoval(); runCurrent()
        assertNull(vm.state.value.selected); assertNull(vm.state.value.removal)
        assertFalse(vm.state.value.removed); assertFalse(vm.state.value.busy)
    }

}
