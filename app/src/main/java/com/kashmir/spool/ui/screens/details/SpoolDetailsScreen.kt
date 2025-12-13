package com.kashmir.spool.ui.screens.details

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Bathtub
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeviceThermostat
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.kashmir.spool.R
import com.kashmir.spool.data.entity.Filament
import com.kashmir.spool.ui.common.GhostCard
import com.kashmir.spool.ui.common.SpoolAppBar
import com.kashmir.spool.ui.components.DeleteConfirmationAlertDialog
import com.kashmir.spool.ui.components.SpoolButton
import com.kashmir.spool.ui.components.SpoolOutlinedTextField
import com.kashmir.spool.ui.theme.Dimens
import java.util.Locale.getDefault

@Composable
fun SpoolDetailsScreen(
    spoolDetails: Filament,
    navigateUp: () -> Unit,
    onUpdateClick: (Int) -> Unit,
    onConfirmDelete: (Filament) -> Unit,
    printWeight: String,
    onPrintWeightValueChange: (String) -> Unit,
    onPrintWeightClick: (Int, String) -> Unit,
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
            totalWeight = spoolDetails.totalWeight.toString(),
            currentWeight = spoolDetails.currentWeight.toString(),
            colorHex = spoolDetails.colorHex,
            brandName = spoolDetails.brand,
            materialType = spoolDetails.material,
            colorName = spoolDetails.colorName,
            nozzleTemp = spoolDetails.tempNozzle.toString(),
            bedTemp = spoolDetails.tempBed.toString(),
            note = spoolDetails.note,
            onEditClick = { onUpdateClick(spoolDetails.id) },
            onConfirmDelete = { onConfirmDelete(spoolDetails) },
            printWeight = printWeight,
            onPrintWeightValueChange = onPrintWeightValueChange,
            onPrintWeightClick = { onPrintWeightClick(spoolDetails.id, printWeight) },
            modifier = Modifier.padding(paddingValues = paddingValues)
        )
    }
}

@Composable
fun DetailsScreen(
    totalWeight: String,
    currentWeight: String,
    colorHex: Long,
    brandName: String,
    materialType: String,
    colorName: String,
    nozzleTemp: String,
    bedTemp: String,
    note: String,
    onEditClick: () -> Unit,
    onConfirmDelete: () -> Unit,
    printWeight: String,
    onPrintWeightValueChange: (String) -> Unit,
    onPrintWeightClick: () -> Unit,
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
            colorName = colorName
        )

        // Temperature Card
        if (nozzleTemp.toInt() > 0 || bedTemp.toInt() > 0) {
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
                printWeight = printWeight,
                onPrintWeightValueChange = onPrintWeightValueChange,
                onPrintWeightClick = onPrintWeightClick
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
                    buttonContainerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    buttonContentColor = MaterialTheme.colorScheme.onSurface,
                    enabled = true,
                    hasBorder = false,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(Dimens.PaddingSmall))
                SpoolButton(
                    text = stringResource(R.string.btn_delete),
                    icon = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.btn_delete),
                    onClick = { showDialog = true },
                    buttonContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    buttonContentColor = MaterialTheme.colorScheme.error,
                    enabled = true,
                    hasBorder = false,
                    modifier = Modifier.weight(1f)
                )
            }
        }

//        Column(
//            modifier = Modifier.padding(Dimens.PaddingMedium)
//        ) {
//            Button(
//                onClick = onEditClick,
//                shape = RoundedCornerShape(Dimens.BorderRadius),
//                modifier = Modifier.fillMaxWidth()
//            ) {
//                Text(
//                    text = stringResource(R.string.btn_update),
//                    style = MaterialTheme.typography.labelLarge,
//                    fontWeight = FontWeight.Bold
//                )
//            }
//            Spacer(modifier = Modifier.height(Dimens.PaddingTiny))
//            OutlinedButton(
//                onClick = { showDialog = true },
//                shape = RoundedCornerShape(Dimens.BorderRadius),
//                border = BorderStroke(
//                    width = Dimens.BorderThickness,
//                    color = MaterialTheme.colorScheme.primary
//                ),
//                modifier = Modifier.fillMaxWidth()
//            ) {
//                Text(
//                    text = stringResource(R.string.btn_delete),
//                    style = MaterialTheme.typography.labelLarge,
//                    fontWeight = FontWeight.Bold,
//                    color = MaterialTheme.colorScheme.onPrimaryContainer
//                )
//            }
//        }

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

@Preview
@Composable
fun DetailsScreenPreview() {
    DetailsScreen(
        totalWeight = "1000",
        currentWeight = "230",
        colorHex = 0xFF4CAF50,
        brandName = "Prusament",
        materialType = "PLA",
        colorName = "Galaxy Green",
        nozzleTemp = "216",
        bedTemp = "60",
        note = "This is my note",
        onEditClick = {},
        onConfirmDelete = {},
        onPrintWeightValueChange = {},
        printWeight = "",
        onPrintWeightClick = {}
    )
}

@Composable
fun MainDetailsCard(
    totalWeight: String,
    currentWeight: String,
    colorHex: Long,
    brandName: String,
    materialType: String,
    colorName: String,
    modifier: Modifier = Modifier
) {
    Card(
        elevation = CardDefaults.cardElevation(Dimens.CardElevation),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
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
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .padding(Dimens.PaddingTiny)
                ) {
                    Box(
                        modifier = Modifier
                            .size(Dimens.ColorDotSize)
                            .clip(shape = RoundedCornerShape(Dimens.CornerRadius))
                            .background(color = Color(colorHex))
                            .border(
                                width = Dimens.BorderThickness,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(Dimens.CornerRadius)
                            )
                    )
                }


                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimens.PaddingMedium),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.Start
                ) {


                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = materialType.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            modifier = Modifier
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = brandName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (colorName.isNotBlank()) {
                        Text(
                            text = colorName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Dimens.PaddingMedium),
            ) {
                Text(
                    text = "Remaining Weight".uppercase(getDefault()),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
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
                        val remainingPercentage = String.format(
                            "%.0f",
                            currentWeight.toDouble().div(totalWeight.toDouble()) * 100
                        )
                        Text(
                            text = String.format("%.0f", currentWeight.toDouble()),
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = " g",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        )
                    }
                }

            }
            Column(
                modifier = Modifier.padding(vertical = Dimens.PaddingMedium)
            ) {
                // Progress Bar
                LinearProgressIndicator(
                    progress = { currentWeight.toFloat() / totalWeight.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .size(Dimens.ProgressBarHeight * 2)
                        .clip(CircleShape),
//            color = Color(colorHex),
                    color = MaterialTheme.colorScheme.primary,  //Have to change the color
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    strokeCap = StrokeCap.Butt,
                    gapSize = 1.5.dp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val remainingPercentage = String.format(
                    "%.0f",
                    currentWeight.toDouble().div(totalWeight.toDouble()) * 100
                )
                Text(
                    text = "Capacity: ${totalWeight}g   ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.Start,

                    modifier = Modifier.weight(1f)
                )
                VerticalDivider(modifier = Modifier.height(20.dp))
                Text(
                    text = "Remaining: ${remainingPercentage}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.End,

                    modifier = Modifier.weight(1f)
                )
            }

        }
    }
}

@Composable
fun PrintCard(
    printWeight: String,
    onPrintWeightValueChange: (String) -> Unit,
    onPrintWeightClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPrintField by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {

        if (showPrintField) {

            SpoolOutlinedTextField(
                value = printWeight,
                onValueChange = onPrintWeightValueChange,
                label = stringResource(R.string.label_deduct_weight),
                isError = false,
                leadingIcon = Icons.Outlined.MonitorWeight,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
            SpoolButton(
                text = stringResource(R.string.btn_log_print),
                icon = Icons.Outlined.Print,
                contentDescription = "Print Button",
                onClick = {
                    showPrintField = false
                    onPrintWeightClick()
                },
                buttonContainerColor = MaterialTheme.colorScheme.primary,
                buttonContentColor = MaterialTheme.colorScheme.onPrimary,
                enabled = true,
                hasBorder = true
            )
        } else {
            SpoolButton(
                text = stringResource(R.string.btn_log_print),
                icon = Icons.Outlined.Print,
                contentDescription = "Print Button",
                onClick = {
                    showPrintField = true
                },
                buttonContainerColor = MaterialTheme.colorScheme.primary,
                buttonContentColor = MaterialTheme.colorScheme.onPrimary,
                enabled = true,
                hasBorder = true
            )
        }
    }
}


@Preview
@Composable
fun MainDetailsCardPreview() {
    MainDetailsCard(
        totalWeight = "1000",
        currentWeight = "120",
        colorHex = 0xFF000000,
        brandName = "MatterHackers",
        materialType = "PETG",
        colorName = "Galaxy Must Green",
        modifier = Modifier
    )
}


@Composable
fun TemperatureDetailsCard(
    nozzleTemp: String,
    bedTemp: String,
    modifier: Modifier = Modifier
) {
    Card(
        elevation = CardDefaults.cardElevation(Dimens.CardElevation / 2),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Thermostat,
                    tint = MaterialTheme.colorScheme.primary,
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.IconSize)
                )
                Spacer(modifier = Modifier.width(Dimens.gapHeight))
                Text(
                    text = "Temperature",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
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
                        .background(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
                        .border(
                            width = 0.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
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
                            Icon(
                                imageVector = Icons.Outlined.Bathtub,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.width(Dimens.gapHeight))
                            Text(
                                text = "Nozzle".uppercase(getDefault()),
                                color = MaterialTheme.colorScheme.outline,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Spacer(modifier = Modifier.height(Dimens.gapHeight))
                        Text(
                            text = "${nozzleTemp}°C",
                            color = MaterialTheme.colorScheme.onBackground,
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
                        .background(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
                        .border(
                            width = 0.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
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
                            Icon(
                                imageVector = Icons.Outlined.Apps,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.width(Dimens.gapHeight))
                            Text(
                                text = "Bed".uppercase(getDefault()),
                                color = MaterialTheme.colorScheme.outline,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Spacer(modifier = Modifier.height(Dimens.gapHeight))
                        Text(
                            text = "${bedTemp}°C",
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun TemperatureDetailsCardPreview() {
    TemperatureDetailsCard(
        nozzleTemp = "280",
        bedTemp = "23"
    )
}


@Composable
fun NotesCard(
    note: String,
    modifier: Modifier = Modifier
) {
    Card(
        elevation = CardDefaults.cardElevation(Dimens.CardElevation / 2),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
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
            Text(
                text = "Notes",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Text(
                text = note,
                fontStyle = FontStyle.Italic,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .fillMaxWidth()
            )
        }
    }
}

@Preview
@Composable
private fun NotesCardPreview() {
    NotesCard(
        note = "This is my note..."
    )
}


