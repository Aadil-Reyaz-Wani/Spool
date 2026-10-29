package com.aadil.spool.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Refresh
import com.aadil.spool.core.data.sync.InventoryUploadMode
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aadil.spool.feature.settings.AccountError
import com.aadil.spool.feature.settings.AccountViewModel
import com.aadil.spool.ui.auth.rememberGoogleSignIn
import com.aadil.spool.ui.common.SpoolAppBar
import com.aadil.spool.ui.components.SpoolOutlinedTextField
import com.aadil.spool.core.network.MAX_DISPLAY_NAME_LENGTH
import com.aadil.spool.ui.components.SpoolButton
import com.aadil.spool.ui.components.SpoolHeadingText
import com.aadil.spool.ui.theme.Dimens
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import spool.shared.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(navigateUp: () -> Unit, reviewConflicts: () -> Unit, openRecovery: () -> Unit) {
    val viewModel: AccountViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val google = rememberGoogleSignIn(viewModel.googleWebClientId)
    Scaffold(topBar = {
        SpoolAppBar(title = stringResource(Res.string.account_title), canNavigateBack = !state.deletionPending, navigateUp = navigateUp)
    }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(Dimens.PaddingLarge),
            verticalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium),
        ) {
            Text(stringResource(Res.string.account_local_inventory_notice), style = MaterialTheme.typography.bodyLarge)
            if (state.session.initializing || state.busy) CircularProgressIndicator()
            val account = state.session.account
            if (account != null) {
                SpoolHeadingText(text = stringResource(Res.string.account_name), icon = Icons.Outlined.Badge)
                Text(account.name ?: stringResource(Res.string.account_name_unavailable), style = MaterialTheme.typography.bodyLarge)
                TextButton(enabled = !state.busy && !state.deletionPending && !state.session.initializing, onClick = viewModel::editName) {
                    Text(stringResource(Res.string.account_edit_name))
                }
                if (state.nameSaved) Text(stringResource(Res.string.account_name_saved), style = MaterialTheme.typography.bodyMedium)
                if (state.editingName) {
                    BasicAlertDialog(onDismissRequest = viewModel::cancelNameEdit) {
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            // Keep the whole form scrollable when the landscape keyboard leaves little height.
                            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(Dimens.PaddingLarge),
                                verticalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium)) {
                                Text(stringResource(Res.string.account_edit_name), style = MaterialTheme.typography.headlineSmall)
                                SpoolOutlinedTextField(
                                    value = state.nameDraft, onValueChange = viewModel::changeName,
                                    label = stringResource(Res.string.account_name), leadingIcon = Icons.Outlined.Badge,
                                    enabled = !state.busy,
                                    isError = state.error == AccountError.NameInvalid,
                                    supportingText = stringResource(Res.string.account_name_rules, MAX_DISPLAY_NAME_LENGTH),
                                )
                                Text(stringResource(Res.string.account_name_save_notice), style = MaterialTheme.typography.bodySmall)
                                if (state.error == AccountError.Profile) Text(stringResource(Res.string.account_name_save_error),
                                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(enabled = !state.busy, onClick = viewModel::cancelNameEdit) { Text(stringResource(Res.string.btn_cancel)) }
                                    TextButton(enabled = !state.busy, onClick = viewModel::saveName) { Text(stringResource(Res.string.account_save_name)) }
                                }
                            }
                        }
                    }
                }
                SpoolHeadingText(text = stringResource(Res.string.account_email), icon = Icons.Outlined.Email)
                Text(account.email ?: stringResource(Res.string.account_email_unavailable), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(Res.string.account_email_managed_by_google), style = MaterialTheme.typography.bodySmall)
                if (state.session.needsConnection) {
                    Text(stringResource(Res.string.account_offline_session), style = MaterialTheme.typography.bodyMedium)
                }
                if (!state.deletionPending) {
                    Text(stringResource(Res.string.inventory_account_local_notice), style = MaterialTheme.typography.bodyMedium)
                    val upload = state.uploads
                    SpoolHeadingText(text = stringResource(Res.string.inventory_upload_title), icon = Icons.Outlined.CloudUpload)
                    Text(
                        stringResource(when (upload.mode) {
                            InventoryUploadMode.Checking -> Res.string.inventory_upload_checking
                            InventoryUploadMode.Uploading -> Res.string.inventory_upload_active
                            InventoryUploadMode.Waiting -> Res.string.inventory_upload_waiting
                            InventoryUploadMode.SignInRequired -> Res.string.inventory_upload_session
                            InventoryUploadMode.NeedsAttention -> Res.string.inventory_upload_attention
                            InventoryUploadMode.Idle -> if (upload.queue.pending > 0) Res.string.inventory_upload_pending else Res.string.inventory_upload_complete
                        }),
                        style = MaterialTheme.typography.bodyMedium,
                        minLines = 2,
                    )
                    Text(stringResource(Res.string.inventory_upload_count, upload.queue.pending), style = MaterialTheme.typography.bodyMedium)
                    if (upload.queue.conflicts > 0) {
                        Text(stringResource(Res.string.inventory_upload_conflicts, upload.queue.conflicts), style = MaterialTheme.typography.bodyMedium)
                        SpoolButton(text = stringResource(Res.string.conflict_review_button), enabled = !state.busy && !state.deletionPending, onClick = reviewConflicts)
                    }
                    Text(stringResource(Res.string.inventory_upload_restore_notice), style = MaterialTheme.typography.bodySmall)
                    // Keep action positions stable as uploads start and finish.
                    SpoolButton(
                        text = stringResource(Res.string.inventory_upload_retry),
                        icon = Icons.Outlined.Refresh,
                        enabled = !state.busy && !state.deletionPending && upload.mode != InventoryUploadMode.Uploading && upload.mode != InventoryUploadMode.Checking,
                        onClick = viewModel::retryUploads,
                    )
                    SpoolButton(text = stringResource(Res.string.recovery_title), enabled = !state.busy && !state.deletionPending, onClick = openRecovery)
                }
                if (!state.deletionPending && state.guestSpoolCount > 0) {
                    Text(stringResource(Res.string.inventory_guest_available, state.guestSpoolCount), style = MaterialTheme.typography.bodyMedium)
                    SpoolButton(
                        text = stringResource(Res.string.inventory_import_button),
                        icon = Icons.Outlined.MoveToInbox,
                        enabled = !state.busy && !state.deletionPending,
                        onClick = viewModel::requestGuestImport,
                    )
                }
                if (state.confirmImport) {
                    AlertDialog(
                        onDismissRequest = viewModel::dismissGuestImport,
                        shape = MaterialTheme.shapes.medium,
                        title = { Text(stringResource(Res.string.inventory_import_title)) },
                        text = { Text(stringResource(Res.string.inventory_import_confirmation, state.guestSpoolCount, account.email ?: account.name ?: stringResource(Res.string.inventory_this_account))) },
                        confirmButton = { TextButton(onClick = viewModel::confirmGuestImport) { Text(stringResource(Res.string.inventory_import_button)) } },
                        dismissButton = { TextButton(onClick = viewModel::dismissGuestImport) { Text(stringResource(Res.string.inventory_keep_guest)) } },
                    )
                }
                if (state.deletionPending) {
                    Text(stringResource(Res.string.account_delete_pending), style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(enabled = !state.busy && !state.session.initializing, onClick = viewModel::requestAccountDeletion) {
                    Text(stringResource(if (state.deletionPending) Res.string.account_delete_retry else Res.string.account_delete),
                        color = MaterialTheme.colorScheme.error)
                }
                if (state.confirmDelete) {
                    BasicAlertDialog(onDismissRequest = viewModel::dismissAccountDeletion) {
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Column(Modifier.verticalScroll(rememberScrollState()).padding(Dimens.PaddingLarge),
                                verticalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium)) {
                                Text(stringResource(Res.string.account_delete_title), style = MaterialTheme.typography.headlineSmall)
                                Text(account.email ?: account.name ?: stringResource(Res.string.inventory_this_account),
                                    style = MaterialTheme.typography.bodyLarge)
                                Text(stringResource(Res.string.account_delete_confirmation), style = MaterialTheme.typography.bodyMedium)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = viewModel::dismissAccountDeletion) { Text(stringResource(Res.string.btn_cancel)) }
                                    TextButton(onClick = { viewModel.deleteAccount(google::clearCredentialState) }) {
                                        Text(stringResource(Res.string.account_delete), color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
                SpoolButton(
                    text = stringResource(Res.string.account_sign_out),
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    enabled = !state.busy,
                    onClick = {
                        if (state.uploads.queue.pending > 0) viewModel.requestSignOutConfirmation()
                        else viewModel.signOut(google::clearCredentialState)
                    },
                )
                if (state.confirmSignOut) {
                    AlertDialog(
                        onDismissRequest = viewModel::dismissSignOutConfirmation,
                        shape = MaterialTheme.shapes.medium,
                        title = { Text(stringResource(Res.string.account_sign_out)) },
                        text = { Text(stringResource(Res.string.inventory_upload_sign_out_pending)) },
                        confirmButton = {
                            TextButton(onClick = {
                                viewModel.dismissSignOutConfirmation()
                                viewModel.signOut(google::clearCredentialState)
                            }) { Text(stringResource(Res.string.account_sign_out)) }
                        },
                        dismissButton = {
                            TextButton(onClick = viewModel::dismissSignOutConfirmation) { Text(stringResource(Res.string.btn_cancel)) }
                        },
                    )
                }
            } else if (!google.supported) {
                Text(stringResource(Res.string.account_ios_unavailable), style = MaterialTheme.typography.bodyMedium)
            } else {
                SpoolButton(
                    text = stringResource(Res.string.account_sign_in),
                    iconPainter = painterResource(Res.drawable.google_g),
                    buttonContainerColor = MaterialTheme.colorScheme.surface,
                    buttonContentColor = MaterialTheme.colorScheme.onSurface,
                    hasBorder = true,
                    enabled = !state.busy && !state.session.initializing && viewModel.googleWebClientId.isNotBlank(),
                    onClick = { viewModel.signIn(google::requestCredentials) },
                )
                if (viewModel.googleWebClientId.isBlank()) {
                    Text(stringResource(Res.string.account_sign_in_unavailable), style = MaterialTheme.typography.bodyMedium)
                }
            }
            state.error?.takeUnless { it == AccountError.Profile || it == AccountError.NameInvalid }?.let { error ->
                Text(
                    stringResource(when (error) {
                        AccountError.Delete -> Res.string.account_delete_error
                        AccountError.SignIn -> Res.string.account_sign_in_error
                        AccountError.Session -> Res.string.account_session_error
                        AccountError.SignOut -> Res.string.account_sign_out_error
                        AccountError.Import -> Res.string.inventory_import_error
                        AccountError.Profile -> Res.string.account_name_save_error
                        AccountError.NameInvalid -> Res.string.account_name_save_error
                    }),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
