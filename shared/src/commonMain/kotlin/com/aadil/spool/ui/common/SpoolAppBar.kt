package com.aadil.spool.ui.common


import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.aadil.spool.ui.components.SpoolIconActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aadil.spool.ui.theme.Dimens


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpoolAppBar(
    modifier: Modifier = Modifier,
    title: String,
    canNavigateBack: Boolean,
    navigateUp: () -> Unit,
    isDashboardScreen: Boolean = false,
//    onFilterClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    dropDownMenu: @Composable () -> Unit = {},
    isSettingsMenuExpanded: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {

    val rotateAngle by animateFloatAsState(
        targetValue =  if (isSettingsMenuExpanded) 180f else 0f,
        animationSpec = tween (
            durationMillis = 400,
            easing = FastOutSlowInEasing
        ),
        label = "setting_gear_rotation"
    )

    // Align bar content with the body's 16dp edge; M3 bakes in only 4dp.
    val edgeInset = Dimens.PaddingMedium - 4.dp

    TopAppBar(
        title = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        // Dashboard keeps its original tighter bar inset; other screens
                        // align with the body's 16dp edge (M3 bakes in 4dp).
                        start = if (isDashboardScreen) 0.dp else edgeInset,
                        end = if (isDashboardScreen) Dimens.PaddingSmall else edgeInset,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (isDashboardScreen) {
                    Box{
                        SpoolIconActionButton(
                            icon = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            onClick = onSettingsClick,
                            iconModifier = Modifier.rotate(rotateAngle),
                        )
                        dropDownMenu()
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = MaterialTheme.colorScheme.onBackground
        ),
        navigationIcon = {
            if (canNavigateBack) {
                Box(modifier = Modifier.padding(start = edgeInset)) {
                    SpoolIconActionButton(
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Go Back",
                        onClick = navigateUp,
                    )
                }
            }
        },
        actions = {
            Row(
                modifier = Modifier.padding(end = edgeInset),
                verticalAlignment = Alignment.CenterVertically,
            ) { actions() }
        },
        modifier = modifier
    )
}
