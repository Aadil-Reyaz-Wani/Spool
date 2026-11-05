package com.aadil.spool.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aadil.spool.core.data.sync.InventoryConflictChoice
import com.aadil.spool.core.model.RemoteInventorySpool
import com.aadil.spool.feature.settings.InventoryConflictError
import com.aadil.spool.feature.settings.InventoryConflictsViewModel
import com.aadil.spool.ui.common.SpoolAppBar
import com.aadil.spool.ui.components.SpoolButton
import com.aadil.spool.ui.components.SpoolHeadingText
import com.aadil.spool.ui.theme.Dimens
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import spool.shared.generated.resources.*

@Composable
fun InventoryConflictsScreen(navigateUp: () -> Unit) {
    val viewModel: InventoryConflictsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val review = state.review
    val listState = rememberSaveable(state.selected, saver = LazyListState.Saver) { LazyListState() }
    Scaffold(topBar = {
        SpoolAppBar(title = stringResource(Res.string.conflict_title), canNavigateBack = true,
            navigateUp = { if (state.selected != null) viewModel.closeReview() else navigateUp() })
    }) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Dimens.PaddingLarge),
            contentPadding = PaddingValues(vertical = Dimens.PaddingLarge),
            verticalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium)) {
            item { Text(stringResource(Res.string.conflict_intro), style = MaterialTheme.typography.bodyLarge) }
            if (state.busy) item { CircularProgressIndicator() }
            state.error?.let { error -> item {
                Text(stringResource(if (error == InventoryConflictError.Changed) Res.string.conflict_changed else Res.string.conflict_unavailable),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            } }
            if (state.selected == null) {
                if (state.conflicts.isEmpty()) item { Text(stringResource(Res.string.conflict_none), style = MaterialTheme.typography.bodyMedium) }
                items(state.conflicts, key = { it.uuid }) { item ->
                    SpoolButton(text = item.brand?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.conflict_unnamed),
                        enabled = !state.busy, onClick = { viewModel.open(item.uuid) })
                }
            } else {
                item { SpoolButton(text = stringResource(Res.string.conflict_refresh), enabled = !state.busy, onClick = viewModel::refresh) }
                if (review != null) {
                    item { Text(stringResource(Res.string.conflict_compare_notice), style = MaterialTheme.typography.bodyMedium) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium)) {
                            Column(Modifier.weight(1f)) {
                                SpoolHeadingText(text = stringResource(Res.string.conflict_local), icon = Icons.Outlined.PhoneAndroid)
                                Text(stringResource(if (review.local.spool.deletedAt == null) Res.string.conflict_available else Res.string.conflict_deleted), style = MaterialTheme.typography.bodyMedium)
                            }
                            Column(Modifier.weight(1f)) {
                                SpoolHeadingText(text = stringResource(Res.string.conflict_cloud), icon = Icons.Outlined.Cloud)
                                Text(stringResource(if (review.cloud.spool.deletedAt == null) Res.string.conflict_available else Res.string.conflict_deleted), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    item { SpoolComparison(review.local.spool, review.cloud.spool, state.currency) }
                    item { Text(stringResource(Res.string.conflict_archive_notice), style = MaterialTheme.typography.bodySmall) }
                    item { SpoolButton(text = stringResource(Res.string.conflict_keep_local), enabled = !state.busy,
                        onClick = { viewModel.requestChoice(InventoryConflictChoice.Local) }) }
                    item { SpoolButton(text = stringResource(Res.string.conflict_keep_cloud), enabled = !state.busy,
                        onClick = { viewModel.requestChoice(InventoryConflictChoice.Cloud) }) }
                    item { SpoolHeadingText(text = stringResource(Res.string.conflict_local_history), icon = Icons.Outlined.History) }
                    item { Text(stringResource(Res.string.conflict_history_counts, review.local.logs.count { it.deletedAt == null }, review.local.logs.count { it.deletedAt != null }), style = MaterialTheme.typography.bodyMedium) }
                    items(review.local.logs, key = { "local_" + it.uuid }) { InventoryPrintVersion(it, state.currency) }
                    item { SpoolHeadingText(text = stringResource(Res.string.conflict_cloud_history), icon = Icons.Outlined.History) }
                    item { Text(stringResource(Res.string.conflict_history_counts, review.cloud.logs.count { it.deletedAt == null }, review.cloud.logs.count { it.deletedAt != null }), style = MaterialTheme.typography.bodyMedium) }
                    items(review.cloud.logs, key = { "cloud_" + it.uuid }) { InventoryPrintVersion(it, state.currency) }

                }

            }
        }
    }
    val choice = state.confirmation
    if (choice != null && review != null) {
        val localDeleted = review.local.spool.deletedAt != null
        val cloudDeleted = review.cloud.spool.deletedAt != null
        val message = when {
            localDeleted && cloudDeleted -> Res.string.conflict_both_deleted_body
            choice == InventoryConflictChoice.Cloud && cloudDeleted -> Res.string.conflict_cloud_deleted_body
            choice == InventoryConflictChoice.Cloud -> Res.string.conflict_confirm_cloud_body
            localDeleted -> Res.string.conflict_local_deleted_body
            cloudDeleted -> Res.string.conflict_copy_spool_body
            else -> Res.string.conflict_confirm_local_body
        }
        AlertDialog(onDismissRequest = viewModel::dismissConfirmation, shape = MaterialTheme.shapes.medium,
            title = { Text(stringResource(if (choice == InventoryConflictChoice.Local) Res.string.conflict_confirm_local else Res.string.conflict_confirm_cloud)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium)) {
                    Text(stringResource(message), style = MaterialTheme.typography.bodyMedium)
                    if (choice == InventoryConflictChoice.Local && !localDeleted && !cloudDeleted &&
                        review.local.logs.any { local -> local.deletedAt == null && review.cloud.logs.any { it.uuid == local.uuid && it.deletedAt != null } }) {
                        Text(stringResource(Res.string.conflict_copy_prints_notice), style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(stringResource(Res.string.conflict_archive_notice), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = viewModel::confirm) { Text(stringResource(Res.string.conflict_confirm)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissConfirmation) { Text(stringResource(Res.string.btn_cancel)) } })
    }
}

@Composable
private fun SpoolComparison(local: RemoteInventorySpool, cloud: RemoteInventorySpool, currency: String) {
    val left = inventoryVersionFields(local, currency)
    val right = inventoryVersionFields(cloud, currency)
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium)) {
        left.zip(right).forEach { (a, b) ->
            SpoolHeadingText(text = a.first, icon = Icons.Outlined.Info)
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium)) {
                Text(a.second, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(b.second, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
