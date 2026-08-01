package com.aadil.spool.ui.screens.details

import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Bathtub
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeviceThermostat
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.aadil.spool.R
import com.aadil.spool.feature.details.PrintObjectUiState
import com.aadil.spool.data.entity.Filament
import com.aadil.spool.ui.common.GhostCard
import com.aadil.spool.ui.common.SpoolAppBar
import com.aadil.spool.ui.components.DeleteConfirmationAlertDialog
import com.aadil.spool.ui.components.InputAlertDialog
import com.aadil.spool.ui.components.SpoolButton
import com.aadil.spool.ui.components.SpoolHeadingText
import com.aadil.spool.ui.components.SpoolHorizontalDivider
import com.aadil.spool.ui.components.SpoolProgressBar
import com.aadil.spool.ui.components.SpoolTag
import com.aadil.spool.ui.theme.BrandOrange
import com.aadil.spool.ui.theme.Dimens
import com.aadil.spool.utils.formatToInternationalStandard
import com.aadil.spool.utils.toParseLocalizedDouble
import java.util.Locale.getDefault

@Composable
fun SpoolDetailsScreen(
    spoolDetails: Filament,
    navigateUp: () -> Unit,
    onUpdateClick: (Int) -> Unit,
    onConfirmDelete: (Filament) -> Unit,
    onPrintWeightValueChange: (String) -> Unit,
    onPrintTitleValueChange: (String) -> Unit,
    onPrintWeightClick: (Int, String) -> Unit,
    uiState: PrintObjectUiState,
    onCheckedChange: (Boolean) -> Unit,
    isPrintErrorState: String?,
    onPrintHistoryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            SpoolAppBar(
                title = stringResource(R.string.spool_details),
                navigateUp = navigateUp,
                canNavigateBack = true
            )
        }
    ) { paddingValues ->

        DetailsScreen(
            totalWeight = spoolDetails.totalWeight,
            currentWeight = spoolDetails.currentWeight,
            colorHex = spoolDetails.colorHex,
            brandName = spoolDetails.brand,
            materialType = spoolDetails.material,
            colorName = spoolDetails.colorName,
            nozzleTemp = spoolDetails.tempNozzle,
            bedTemp = spoolDetails.tempBed,
            note = spoolDetails.note,
            onEditClick = { onUpdateClick(spoolDetails.id) },
            onConfirmDelete = { onConfirmDelete(spoolDetails) },
            uiState = uiState,
            onPrintWeightValueChange = onPrintWeightValueChange,
            onPrintTitleValueChange = onPrintTitleValueChange,
            onPrintWeightClick = {
                onPrintWeightClick(spoolDetails.id, uiState.gramsUsed)
            },
            onCheckedChange = onCheckedChange,
            isPrintErrorState = isPrintErrorState,
            onPrintHistoryClick = onPrintHistoryClick,
            modifier = Modifier.padding(paddingValues = paddingValues)
        )
    }
}

@Composable
fun DetailsScreen(
    totalWeight: Double,
    currentWeight: Double,
    colorHex: Long,
    brandName: String,
    materialType: String,
    colorName: String,
    nozzleTemp: Int,
    bedTemp: Int,
    note: String,
    onEditClick: () -> Unit,
    onConfirmDelete: () -> Unit,
    uiState: PrintObjectUiState,
    onPrintWeightValueChange: (String) -> Unit,
    onPrintTitleValueChange: (String) -> Unit,
    onPrintWeightClick: (String) -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    isPrintErrorState: String?,
    onPrintHistoryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var showDialog by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        MainDetailsCard(
            totalWeight = totalWeight,
            currentWeight = currentWeight,
            colorHex = colorHex,
            brandName = brandName,
            materialType = materialType,
            colorName = colorName,
            onPrintHistoryClick = onPrintHistoryClick
        )

        // Temperature Card
        if (nozzleTemp > 0 || bedTemp > 0) {
            TemperatureDetailsCard(
                nozzleTemp = nozzleTemp,
                bedTemp = bedTemp
            )
        } else {
            GhostCard(
                text = stringResource(R.string.missing_settings),
                icon = Icons.Outlined.DeviceThermostat,
                modifier = Modifier.padding(Dimens.PaddingMedium)
            )
        }

        // Notes Card
        if (note.isNotBlank()) {
            NotesCard(
                note = note
            )
        } else {
            GhostCard(
                text = stringResource(R.string.missing_notes),
                icon = Icons.AutoMirrored.Outlined.StickyNote2,
                modifier = Modifier.padding(Dimens.PaddingMedium)
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = Dimens.PaddingMedium))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.PaddingMedium)
        ) {
            PrintCard(
                uiState = uiState,
                onPrintWeightValueChange = onPrintWeightValueChange,
                onPrintTitleValueChange = onPrintTitleValueChange,
                onPrintClick = onPrintWeightClick,
                onCheckedChange = onCheckedChange,
                isPrintErrorState = isPrintErrorState
            )
            Spacer(modifier = Modifier.height(Dimens.PaddingMedium))
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                SpoolButton(
                    text = stringResource(R.string.btn_update),
                    icon = Icons.Outlined.Update,
                    contentDescription = stringResource(R.string.btn_update),
                    onClick = onEditClick,
                    buttonContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    buttonContentColor = MaterialTheme.colorScheme.onSurface,
                    enabled = true,
                    hasBorder = false,
                    buttonDefaultElevation = 0.dp,
                    buttonPressedElevation = 0.dp,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(Dimens.PaddingSmall))
                SpoolButton(
                    text = stringResource(R.string.btn_delete),
                    icon = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.btn_delete),
                    onClick = { showDialog = true },
                    buttonContainerColor = MaterialTheme.colorScheme.errorContainer,
                    buttonContentColor = MaterialTheme.colorScheme.onErrorContainer,
                    enabled = true,
                    hasBorder = false,
                    modifier = Modifier.weight(1f)
                )
            }
            if (showDialog) {
                DeleteConfirmationAlertDialog(
                    onConfirmDelete = {
                        onConfirmDelete()
                        showDialog = false
                    },
                    onDismiss = {
                        showDialog = false
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DetailsScreenPreview() {
    DetailsScreen(
        totalWeight = 1000.0,
        currentWeight = 23.0,
        colorHex = 0xFF4CAF50,
        brandName = "Prusament",
        materialType = "PLA",
        colorName = "Galaxy Green",
        nozzleTemp = 216,
        bedTemp = 60,
        note = "This is my note",
        onEditClick = {},
        onConfirmDelete = {},
        onPrintWeightValueChange = {},
        onPrintTitleValueChange = {},
        uiState = PrintObjectUiState(),
        onPrintWeightClick = {},
        onCheckedChange = {},
        isPrintErrorState = "",
        onPrintHistoryClick = {}
    )
}

@SuppressLint("DefaultLocale")
@Composable
fun MainDetailsCard(
    totalWeight: Double,
    currentWeight: Double,
    colorHex: Long,
    brandName: String,
    materialType: String,
    colorName: String,
    onPrintHistoryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
//    val remainingPercentage = String.format(
//        "%.0f",
//        currentWeight.toDouble().div(totalWeight.toDouble()) * 100
//    )

//    val percentage = remainingPercentage.toDouble()
    val percentage = (currentWeight / totalWeight) * 100
    Card(
        elevation = CardDefaults.cardElevation(Dimens.CardElevation),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier
            .padding(Dimens.PaddingMedium)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.PaddingMedium)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .padding(Dimens.PaddingMedium),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.Start
                ) {

                    SpoolTag(
                        text = materialType.uppercase()
                    )
                    Text(
                        text = brandName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (colorName.isNotBlank()) {
                        Text(
                            text = colorName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }

                }
                Box(
                    modifier = Modifier
                        .padding(Dimens.PaddingTiny)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Box(
                        modifier = Modifier
                            .size(Dimens.ColorDotSizeExtra)
                            .clip(shape = RoundedCornerShape(Dimens.CornerRadius))
                            .background(color = Color(colorHex).copy(alpha = 0.7f))
                            .border(
                                width = Dimens.BorderThickness,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(Dimens.CornerRadius)
                            )
                    )
                }

            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Dimens.PaddingTiny),
            ) {
                Text(
                    text = "Remaining Weight".uppercase(getDefault()),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    letterSpacing = 0.05.em,
                    textAlign = TextAlign.Center,

                    modifier = Modifier.fillMaxWidth()
                )
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = currentWeight.formatToInternationalStandard(),
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = if (percentage < 20) MaterialTheme.colorScheme.error else BrandOrange,
                        )
                        Text(
                            text = "g",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        )
                    }
                }

            }
            Column(
                modifier = Modifier.padding(vertical = Dimens.PaddingMedium)
            ) {
                // This warns the users of low stock for a particular filament
                if (percentage < 20) {
                    SpoolTag(
                        text = if (percentage > 0) stringResource(R.string.low_stock) else stringResource(
                            R.string.empty_spool
                        ),
                        textColor = MaterialTheme.colorScheme.error,
                        surfaceColor = MaterialTheme.colorScheme.errorContainer,
                        isIconicTag = true
                    )
                }
                Spacer(modifier = Modifier.height(Dimens.HeightOrWidth))
                // Progress Bar
                SpoolProgressBar(
                    percentage = percentage,
                    currentWeight = currentWeight,
                    totalWeight = totalWeight
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = "Capacity: ${totalWeight.formatToInternationalStandard()}g   ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.Start,

                    modifier = Modifier.weight(1f)
                )
                VerticalDivider(
                    modifier = Modifier.height(20.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Text(
//                    text = "Remaining: ${percentage}%",
                    text = "Remaining: ${percentage.formatToInternationalStandard()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }

            SpoolHorizontalDivider(
                modifier = Modifier.padding(top = Dimens.PaddingMedium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border = BorderStroke(
                        width = Dimens.BorderThickness,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    onClick = onPrintHistoryClick,
                    content = {
                        Row(
                            modifier = Modifier.padding(Dimens.PaddingSmall),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingTiny)
                        ) {
                            Text(
                                text = stringResource(R.string.print_history),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )

                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(Dimens.IconTiny)
                            )
                        }
                    }
                )
            }

        }
    }
}

@Composable
fun PrintCard(
    uiState: PrintObjectUiState,
    onPrintWeightValueChange: (String) -> Unit,
    onPrintTitleValueChange: (String) -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    onPrintClick: (String) -> Unit,
    isPrintErrorState: String?,
    modifier: Modifier = Modifier
) {
    var showPrintField by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {

        SpoolButton(
            text = stringResource(R.string.btn_log_print),
            icon = Icons.Outlined.Print,
            contentDescription = stringResource(R.string.btn_log_print),
            onClick = {
                showPrintField = true
            },
            buttonContainerColor = MaterialTheme.colorScheme.primary,
            buttonContentColor = MaterialTheme.colorScheme.onPrimary,
            enabled = true,
            hasBorder = false
        )

        if (showPrintField) {
            InputAlertDialog(
                uiState = uiState,
                onGramsUsedValueChange = onPrintWeightValueChange,
                onPrintTitleValueChange = onPrintTitleValueChange,
                onConfirm = { weight ->
                    onPrintClick(weight)
                },
                onDismissRequest = {
                    showPrintField = false
                },
                onCheckedChange = onCheckedChange,
                isPrintErrorState = isPrintErrorState,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}


@Composable
fun TemperatureDetailsCard(
    nozzleTemp: Int,
    bedTemp: Int,
    modifier: Modifier = Modifier
) {
    Card(
        elevation = CardDefaults.cardElevation(Dimens.CardElevation / 2),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier
            .padding(Dimens.PaddingMedium)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = Dimens.PaddingMedium,
                    vertical = Dimens.PaddingMedium
                )
        ) {

            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.HeightOrWidth),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SpoolHeadingText(
                    text = stringResource(R.string.heading_temperature),
                    icon = Icons.Outlined.Thermostat
                )
            }

            Row(
                modifier = Modifier
                    .padding(vertical = Dimens.PaddingMedium)
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .weight(1f)
                        .clip(shape = RoundedCornerShape(16.dp))
                        .background(color = MaterialTheme.colorScheme.surface)
                        .border(
                            width = Dimens.BorderThickness,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(16.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .padding(Dimens.PaddingMedium)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,

                            ) {

                            SpoolHeadingText(
                                text = stringResource(R.string.heading_nozzle).uppercase(),
                                icon = Icons.Outlined.Bathtub,
                                iconTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                textColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        }
                        Spacer(modifier = Modifier.height(Dimens.HeightOrWidth))
                        Text(
                            text = "${nozzleTemp}°C",
                            color = if (nozzleTemp <= 0) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(Dimens.PaddingMedium))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .weight(1f)
                        .clip(shape = RoundedCornerShape(16.dp))
                        .background(color = MaterialTheme.colorScheme.surface)
                        .border(
                            width = Dimens.BorderThickness,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(16.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .padding(Dimens.PaddingMedium)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,

                            ) {
                            SpoolHeadingText(
                                text = stringResource(R.string.heading_bed).uppercase(),
                                icon = Icons.Outlined.Apps,
                                iconTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                textColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        }
                        Spacer(modifier = Modifier.height(Dimens.HeightOrWidth))
                        Text(
                            text = "${bedTemp}°C",
                            color = if (bedTemp <= 0) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NotesCard(
    note: String,
    modifier: Modifier = Modifier
) {
    Card(
        elevation = CardDefaults.cardElevation(Dimens.CardElevation / 2),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier
            .padding(Dimens.PaddingMedium)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = Dimens.PaddingMedium,
                    vertical = Dimens.PaddingMedium
                )
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                SpoolHeadingText(
                    text = stringResource(R.string.heading_note),
                    icon = Icons.AutoMirrored.Outlined.Notes
                )
            }
            Spacer(modifier = Modifier.height(Dimens.HeightOrWidth))
            Text(
                text = note,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
            )
        }
    }
}


