package com.aadil.spool.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.aadil.spool.ui.components.SpoolButton
import com.aadil.spool.ui.components.SpoolHeadingText
import com.aadil.spool.ui.theme.Dimens
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import spool.shared.generated.resources.*

@Composable
fun AccountScreen(navigateUp: () -> Unit) {
    val viewModel: AccountViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val google = rememberGoogleSignIn(viewModel.googleWebClientId)
    Scaffold(topBar = {
        SpoolAppBar(title = stringResource(Res.string.account_title), canNavigateBack = true, navigateUp = navigateUp)
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
                SpoolHeadingText(text = stringResource(Res.string.account_email), icon = Icons.Outlined.Email)
                Text(account.email ?: stringResource(Res.string.account_email_unavailable), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(Res.string.account_email_managed_by_google), style = MaterialTheme.typography.bodySmall)
                if (state.session.needsConnection) {
                    Text(stringResource(Res.string.account_offline_session), style = MaterialTheme.typography.bodyMedium)
                }
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
                }
                Text(stringResource(Res.string.inventory_upload_restore_notice), style = MaterialTheme.typography.bodySmall)
                // Keep action positions stable as uploads start and finish.
                SpoolButton(
                    text = stringResource(Res.string.inventory_upload_retry),
                    icon = Icons.Outlined.Refresh,
                    enabled = upload.queue.ready > 0 && !state.busy && upload.mode != InventoryUploadMode.Uploading,
                    onClick = viewModel::retryUploads,
                )
                if (state.guestSpoolCount > 0) {
                    Text(stringResource(Res.string.inventory_guest_available, state.guestSpoolCount), style = MaterialTheme.typography.bodyMedium)
                    SpoolButton(
                        text = stringResource(Res.string.inventory_import_button),
                        icon = Icons.Outlined.MoveToInbox,
                        enabled = !state.busy,
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
            state.error?.let { error ->
                Text(
                    stringResource(when (error) {
                        AccountError.SignIn -> Res.string.account_sign_in_error
                        AccountError.Session -> Res.string.account_session_error
                        AccountError.SignOut -> Res.string.account_sign_out_error
                        AccountError.Import -> Res.string.inventory_import_error
                    }),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
