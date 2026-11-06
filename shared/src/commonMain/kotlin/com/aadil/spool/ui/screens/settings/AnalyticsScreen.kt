package com.aadil.spool.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aadil.spool.feature.settings.AnalyticsViewModel
import com.aadil.spool.ui.common.SpoolAppBar
import com.aadil.spool.ui.theme.Dimens
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import spool.shared.generated.resources.*

@Composable
fun AnalyticsScreen(navigateUp: () -> Unit, viewModel: AnalyticsViewModel = koinViewModel()) {
    val consent by viewModel.consent.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val usageTitle = stringResource(Res.string.analytics_usage_title)
    Scaffold(topBar = {
        SpoolAppBar(title = stringResource(Res.string.analytics_privacy_title), canNavigateBack = true, navigateUp = navigateUp)
    }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(Dimens.PaddingMedium),
            verticalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium),
        ) {
            SettingsSection {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall)) {
                    Text(usageTitle, modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium)
                    Switch(checked = consent.choice == true, onCheckedChange = viewModel::choose,
                        modifier = Modifier.semantics { contentDescription = usageTitle },
                        enabled = consent.loaded && consent.available && !saving)
                }
                Text(stringResource(Res.string.analytics_description), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (consent.loaded && !consent.available) Text(stringResource(Res.string.analytics_unavailable),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(Res.string.analytics_optional), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (error) {
                Text(stringResource(Res.string.analytics_save_error), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = viewModel::retryChoice, enabled = !saving) {
                    Text(stringResource(Res.string.analytics_retry))
                }
            }
            TextButton(onClick = { uriHandler.openUri("https://sites.google.com/view/spool-privacy/home") }) {
                Text(stringResource(Res.string.privacy_policy_label))
            }
        }
    }
}

@Composable
fun AnalyticsConsentDialog(viewModel: AnalyticsViewModel) {
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = { viewModel.choose(false) },
        shape = MaterialTheme.shapes.medium,
        title = { Text(stringResource(Res.string.analytics_consent_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall)) {
                Text(stringResource(Res.string.analytics_description))
                Text(stringResource(Res.string.analytics_optional))
                if (error) Text(stringResource(Res.string.analytics_save_error), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.choose(true) }, enabled = !saving) {
                Text(stringResource(Res.string.analytics_allow))
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.choose(false) }, enabled = !saving) {
                Text(stringResource(Res.string.analytics_decline))
            }
        },
    )
}
