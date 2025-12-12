package com.kashmir.spool.ui.screens.details

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.DeviceThermostat
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kashmir.spool.R
import com.kashmir.spool.data.entity.Filament
import com.kashmir.spool.ui.common.GhostCard
import com.kashmir.spool.ui.common.SpoolAppBar
import com.kashmir.spool.ui.common.WeightProgressBar
import com.kashmir.spool.ui.components.DeleteConfirmationAlertDialog
import com.kashmir.spool.ui.theme.Dimens

@Composable
fun SpoolDetailsScreen(
    spoolDetails: Filament,
    navigateUp: () -> Unit,
    onUpdateClick: (Int) -> Unit,
    onConfirmDelete: (Filament) -> Unit,
    isPrintMode: Boolean,
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
            isPrintMode = isPrintMode,
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
    isPrintMode: Boolean,
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

        PrintCard(
            isPrintMode = isPrintMode,
            printWeight = printWeight,
            onPrintWeightValueChange = onPrintWeightValueChange,
            onPrintWeightClick = onPrintWeightClick
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

        Column(
            modifier = Modifier.padding(Dimens.PaddingMedium)
        ) {
            Button(
                onClick = onEditClick,
                shape = RoundedCornerShape(Dimens.BorderRadius),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.btn_update),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(Dimens.PaddingTiny))
            OutlinedButton(
                onClick = { showDialog = true },
                shape = RoundedCornerShape(Dimens.BorderRadius),
                border = BorderStroke(
                    width = Dimens.BorderThickness,
                    color = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.btn_delete),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
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

@Preview
@Composable
fun DetailsScreenPreview() {
    DetailsScreen(
        totalWeight = "1000",
        currentWeight = "320",
        colorHex = 0xFF4CAF50,
        brandName = "Prusament",
        materialType = "PLA",
        colorName = "Galaxy Green",
        nozzleTemp = "216",
        bedTemp = "60",
        note = "This is my note",
        onEditClick = {},
        onConfirmDelete = {},
        isPrintMode = false,
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
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
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
            Row {
                Text(
                    text = stringResource(R.string.label_brand),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),


                    )
                Text(
                    text = brandName,
                    textAlign = TextAlign.End,
                    fontStyle = FontStyle.Italic,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(alignment = Alignment.CenterVertically)
                )
            }
            Spacer(modifier = Modifier.height(Dimens.gapHeight))
            Row {
                Text(
                    text = stringResource(R.string.label_material),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),


                    )
                Text(
                    text = materialType,
                    textAlign = TextAlign.End,
                    fontStyle = FontStyle.Italic,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(alignment = Alignment.CenterVertically)
                )
            }
            Spacer(modifier = Modifier.height(Dimens.gapHeight))
            if (colorName.isNotBlank()) {
                Row {
                    Text(
                        text = stringResource(R.string.label_color),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),


                        )
                    Text(
                        text = colorName,
                        textAlign = TextAlign.End,
                        fontStyle = FontStyle.Italic,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(alignment = Alignment.CenterVertically)
                    )
                }
            }
            Spacer(modifier = Modifier.height(Dimens.gapHeight))
            // Progress Bar
            WeightProgressBar(
                totalWeight = totalWeight,
                currentWeight = currentWeight,
                colorHex = colorHex,
            )
        }
    }
}

@Composable
fun PrintCard(
    isPrintMode: Boolean,
    printWeight: String,
    onPrintWeightValueChange: (String) -> Unit,
    onPrintWeightClick:() -> Unit,
    modifier: Modifier = Modifier
) {
    var showPrintField by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .padding(horizontal = Dimens.PaddingMedium)
            .fillMaxWidth()
    ) {

        if (showPrintField) {
            OutlinedTextField(
                value = printWeight,
                onValueChange = onPrintWeightValueChange,
                shape = MaterialTheme.shapes.large,
                label = {Text(text = stringResource(R.string.label_deduct_weight))},
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    showPrintField = false
                    onPrintWeightClick()

                },
                shape = RoundedCornerShape(Dimens.BorderRadius),
                modifier = modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Outlined.Print,
                    contentDescription = stringResource(R.string.btn_log_print)
                )
                Spacer(modifier = Modifier.width(Dimens.gapHeight))
                Text(
                    text = stringResource(R.string.btn_log_print),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Button(
                onClick = {
                    showPrintField = true

                },
                shape = RoundedCornerShape(Dimens.BorderRadius),
                modifier = modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Outlined.Print,
                    contentDescription = stringResource(R.string.btn_log_print)
                )
                Spacer(modifier = Modifier.width(Dimens.gapHeight))
                Text(
                    text = stringResource(R.string.btn_log_print),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}


@Preview
@Composable
fun MainDetailsCardPreview() {
    MainDetailsCard(
        totalWeight = "1000",
        currentWeight = "230",
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
        elevation = CardDefaults.cardElevation(Dimens.CardElevation),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
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
                text = "Temperatures",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Row {
                Icon(
                    imageVector = Icons.Outlined.Thermostat,
                    contentDescription = "Nozzle Temp Icon",
                    modifier = Modifier
                        .size(18.dp)
                        .align(alignment = Alignment.CenterVertically)
                )
                Spacer(modifier = Modifier.width(Dimens.PaddingTiny))
                Text(
                    text = stringResource(R.string.label_temp_nozzle),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),


                    )
                Text(
                    text = "${nozzleTemp}°C",
                    textAlign = TextAlign.End,
                    fontStyle = FontStyle.Italic,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(alignment = Alignment.CenterVertically)
                )
            }
            Spacer(modifier = Modifier.height(Dimens.gapHeight))
            Row {
                Icon(
                    imageVector = Icons.Outlined.Whatshot,
                    contentDescription = "Bed Temp Icon",
                    modifier = Modifier
                        .size(18.dp)
                        .align(alignment = Alignment.CenterVertically)
                )
                Spacer(modifier = Modifier.width(Dimens.PaddingTiny))
                Text(
                    text = stringResource(R.string.label_temp_bed),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),


                    )
                Text(
                    text = "${bedTemp}°C",
                    textAlign = TextAlign.End,
                    fontStyle = FontStyle.Italic,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(alignment = Alignment.CenterVertically)
                )
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
        elevation = CardDefaults.cardElevation(Dimens.CardElevation),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
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


