package com.aadil.spool.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.aadil.spool.BuildConfig
import com.aadil.spool.R
import com.aadil.spool.ui.common.SpoolAppBar
import com.aadil.spool.ui.components.SpoolButton
import com.aadil.spool.ui.theme.Dimens
import com.aadil.spool.ui.theme.SpoolTheme
import com.mikepenz.aboutlibraries.entity.License

@Composable
fun AboutScreen(
    navigateUp: () -> Unit,
    onOpenSourceLicenseClick: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            SpoolAppBar(
                title = stringResource(R.string.about_screen_label),
                canNavigateBack = true,
                navigateUp = navigateUp
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(paddingValues)
                .padding(
                    horizontal = Dimens.PaddingLarge,
                    vertical = Dimens.PaddingMedium
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.PaddingLarge)
        ) {
            // App Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.PaddingTiny)
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    letterSpacing = Dimens.LetterSpacing
                )
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Mission Statement Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = RoundedCornerShape(Dimens.CornerRadius)
            ) {
                Column(
                    modifier = Modifier.padding(Dimens.PaddingMedium),
                    verticalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(Dimens.IconSmall)
                        )
                        Text(
                            text = stringResource(R.string.our_mission_header_label),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = stringResource(R.string.our_mission_slogan),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // Developer Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = RoundedCornerShape(Dimens.CornerRadius)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = Dimens.PaddingSmall)
                ) {
                    // Developer Header
                    Row(
                        modifier = Modifier.padding(
                            horizontal = Dimens.PaddingMedium,
                            vertical = Dimens.PaddingSmall
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(Dimens.IconMedium)
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.created_by_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.creator_label),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = Dimens.PaddingMedium),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    // Social Links
                    DeveloperLinkItem(
                        label = stringResource(R.string.social_x),
                        icon = Icons.Outlined.Link,
                        onClick = { uriHandler.openUri("https://x.com/_aadil_114") }
                    )
                    DeveloperLinkItem(
                        label = stringResource(R.string.social_linkedin),
                        icon = Icons.Outlined.Link,
                        onClick = { uriHandler.openUri("https://www.linkedin.com/in/aadilreyazwani/") }
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Rate & Review Button
            SpoolButton(
                text = stringResource(R.string.rating_button_label),
                icon = Icons.Default.Star,
                onClick = { uriHandler.openUri("https://play.google.com/store/apps/details?id=${context.packageName}") }
            )

            // Bottom Links
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Dimens.PaddingMedium),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onOpenSourceLicenseClick) {
                    Text(
                        text = stringResource(R.string.open_source_licenses_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = stringResource(R.string.dot_separator_label),
                    modifier = Modifier.padding(horizontal = Dimens.PaddingTiny),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                TextButton(onClick = { uriHandler.openUri("https://sites.google.com/view/spool-privacy/home") }) {
                    Text(
                        text = stringResource(R.string.privacy_policy_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun DeveloperLinkItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = Dimens.PaddingMedium,
                vertical = Dimens.PaddingSmall
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Dimens.IconSmall)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFF8F5)
@Composable
fun AboutScreenPreview() {
    SpoolTheme {
        AboutScreen(navigateUp = {}, onOpenSourceLicenseClick = {})
    }
}