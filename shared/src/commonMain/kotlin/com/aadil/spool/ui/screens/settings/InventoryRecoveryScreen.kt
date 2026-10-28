package com.aadil.spool.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aadil.spool.feature.settings.InventoryRecoveryViewModel
import com.aadil.spool.ui.common.SpoolAppBar
import com.aadil.spool.ui.components.SpoolButton
import com.aadil.spool.ui.components.SpoolHeadingText
import com.aadil.spool.ui.theme.Dimens
import com.aadil.spool.utils.toReadableDate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import spool.shared.generated.resources.*

@Composable
fun InventoryRecoveryScreen(navigateUp: () -> Unit) {
    val viewModel: InventoryRecoveryViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val preview = state.preview
    val listState = rememberSaveable(state.selected, saver = LazyListState.Saver) { LazyListState() }
    Scaffold(topBar = {
        SpoolAppBar(title = stringResource(Res.string.recovery_title), canNavigateBack = true,
            navigateUp = { if (state.selected != null) viewModel.closePreview() else navigateUp() })
    }) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Dimens.PaddingLarge),
            contentPadding = PaddingValues(vertical = Dimens.PaddingLarge),
            verticalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium)) {
            item { Text(stringResource(Res.string.recovery_intro), style = MaterialTheme.typography.bodyLarge) }
            if (state.busy) item { CircularProgressIndicator() }
            if (state.error) item { Text(stringResource(Res.string.recovery_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            if (state.restored) item { Text(stringResource(Res.string.recovery_success), style = MaterialTheme.typography.bodyMedium) }
            if (state.removed) item { Text(stringResource(Res.string.recovery_removed), style = MaterialTheme.typography.bodyMedium) }
            if (state.selected == null) {
                if (state.items.isEmpty() && !state.busy) item { Text(stringResource(Res.string.recovery_empty), style = MaterialTheme.typography.bodyMedium) }
                items(state.items, key = { it.id }) { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall)) {
                        Text(recoveryKind(item.kind), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(Res.string.recovery_saved_at, item.savedAt.toReadableDate()), style = MaterialTheme.typography.bodySmall)
                        if (item.restored) Text(stringResource(Res.string.recovery_already_restored), style = MaterialTheme.typography.bodyMedium)
                        SpoolButton(text = item.brand?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.conflict_unnamed),
                            enabled = !state.busy, onClick = { viewModel.open(item.id) })
                        TextButton(enabled = !state.busy, onClick = { viewModel.requestRemoval(item.id) }) {
                            Text(stringResource(Res.string.recovery_remove))
                        }
                    }
                }
            } else if (preview == null) {
                item { TextButton(enabled = !state.busy, onClick = { state.selected?.let(viewModel::requestRemoval) }) {
                    Text(stringResource(Res.string.recovery_remove))
                } }
                item { SpoolButton(text = stringResource(Res.string.recovery_reload), enabled = !state.busy, onClick = { state.selected?.let(viewModel::open) }) }
            } else {
                item { Text(recoveryKind(preview.source.kind), style = MaterialTheme.typography.titleMedium) }
                item { Text(stringResource(Res.string.recovery_saved_at, preview.source.createdAt.toReadableDate()), style = MaterialTheme.typography.bodySmall) }
                item { Text(stringResource(Res.string.recovery_history_notice), style = MaterialTheme.typography.bodyMedium) }
                item { SpoolButton(text = stringResource(Res.string.recovery_restore), enabled = !state.busy && preview.source.restoredUuid == null, onClick = viewModel::requestRestore) }
                item { TextButton(enabled = !state.busy, onClick = { viewModel.requestRemoval(preview.source.id) }) {
                    Text(stringResource(Res.string.recovery_remove))
                } }
                if (preview.source.restoredUuid != null) item { Text(stringResource(Res.string.recovery_already_restored), style = MaterialTheme.typography.bodyMedium) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium)) {
                        inventoryVersionFields(preview.version.spool, state.currency).forEach { (label, value) ->
                            SpoolHeadingText(text = label, icon = Icons.Outlined.Info)
                            Text(value, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                item { Text(stringResource(Res.string.recovery_print_count, preview.version.logs.size), style = MaterialTheme.typography.titleMedium) }
                items(preview.version.logs, key = { it.uuid }) { InventoryPrintVersion(it, state.currency) }
            }
            item { Text(stringResource(Res.string.recovery_retention), style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (state.confirm && preview != null) {
        AlertDialog(onDismissRequest = viewModel::dismissConfirmation, shape = MaterialTheme.shapes.medium,
            title = { Text(stringResource(Res.string.recovery_confirm_title)) },
            text = { Text(stringResource(Res.string.recovery_confirm_body, preview.version.logs.size)) },
            confirmButton = { TextButton(onClick = viewModel::confirmRestore) { Text(stringResource(Res.string.recovery_restore)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissConfirmation) { Text(stringResource(Res.string.btn_cancel)) } })
    }
    state.removal?.let { item ->
        AlertDialog(onDismissRequest = viewModel::dismissRemoval, shape = MaterialTheme.shapes.medium,
            title = { Text(stringResource(Res.string.recovery_remove_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium)) {
                    Text(item.brand?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.conflict_unnamed))
                    Text(recoveryKind(item.kind))
                    Text(stringResource(Res.string.recovery_saved_at, item.savedAt.toReadableDate()))
                    Text(stringResource(Res.string.recovery_remove_body))
                }
            },
            confirmButton = { TextButton(onClick = viewModel::confirmRemoval) { Text(stringResource(Res.string.recovery_remove)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissRemoval) { Text(stringResource(Res.string.btn_cancel)) } })
    }
}

@Composable
private fun recoveryKind(kind: String): String = stringResource(when (kind) {
    "conflict_local" -> Res.string.recovery_conflict_local
    "conflict_cloud" -> Res.string.recovery_conflict_cloud
    "deleted_cloud" -> Res.string.recovery_deleted_cloud
    else -> Res.string.recovery_deleted_device
})
