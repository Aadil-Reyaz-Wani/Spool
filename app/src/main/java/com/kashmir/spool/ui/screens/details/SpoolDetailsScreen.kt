package com.kashmir.spool.ui.screens.details

import android.graphics.drawable.Icon
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.outlined.DeviceThermostat
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kashmir.spool.R
import com.kashmir.spool.data.entity.Filament
import com.kashmir.spool.ui.common.GhostCard
import com.kashmir.spool.ui.common.SpoolAppBar
import com.kashmir.spool.ui.common.WeightProgressBar
import com.kashmir.spool.ui.theme.Dimens

@Composable
fun SpoolDetailsScreen(
    spoolDetails: Filament,
    navigateUp: () -> Unit,
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
            colorHex = spoolDetails.colorHex,
            brandName = spoolDetails.brand,
            materialType = spoolDetails.material,
            colorName = spoolDetails.colorName,
            nozzleTemp = spoolDetails.tempNozzle,
            bedTemp = spoolDetails.tempBed,
            note = spoolDetails.note,
            modifier = Modifier.padding(paddingValues = paddingValues)
        )
    }
}

@Composable
fun DetailsScreen(
    totalWeight: String,
    colorHex: Long,
    brandName: String,
    materialType: String,
    colorName: String,
    nozzleTemp: Int,
    bedTemp: Int,
    note: String,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        MainDetailsCard(
            totalWeight = totalWeight,
            colorHex = colorHex,
            brandName = brandName,
            materialType = materialType,
            colorName = colorName
        )

        // Temperature Card
        if (nozzleTemp > 0 || bedTemp > 0) {
            TemperatureDetailsCard(
                nozzleTemp = nozzleTemp.toString(),
                bedTemp = bedTemp.toString()
            )
        }else {
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
        }else {
            GhostCard(
                text = stringResource(R.string.missing_notes),
                icon = Icons.AutoMirrored.Outlined.StickyNote2,
                modifier = Modifier.padding(Dimens.PaddingMedium)
            )
        }

        Column (
//            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(Dimens.PaddingMedium)
        ){
            Button(
                onClick = {},
                shape = RoundedCornerShape(Dimens.BorderRadius),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.btn_edit),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(Dimens.PaddingTiny))
            OutlinedButton(
                onClick = {},
                shape = RoundedCornerShape(Dimens.BorderRadius),
                border = BorderStroke(width = Dimens.BorderThickness, color = MaterialTheme.colorScheme.primary),
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
    }

}

@Preview
@Composable
fun DetailsScreenPreview() {
    DetailsScreen(
        totalWeight = "1000",
        colorHex = 0xFF4CAF50,
        brandName = "Prusament",
        materialType = "PLA",
        colorName = "Galaxy Green",
        nozzleTemp = 216,
        bedTemp = 60,
        note = "This is my note"
    )
}

@Composable
fun MainDetailsCard(
    totalWeight: String,
    colorHex: Long,
    brandName: String,
    materialType: String,
    colorName: String,
    modifier: Modifier = Modifier
) {
    Card(
        elevation = CardDefaults.cardElevation(Dimens.CardElevation),
//        border = BorderStroke(width = Dimens.BorderThickness, color = MaterialTheme.colorScheme.primary),
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
                    vertical = Dimens.PaddingSmall
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
                colorHex = colorHex,
            )
        }
    }
}

@Preview
@Composable
fun MainDetailsCardPreview() {
    MainDetailsCard(
        totalWeight = "1000",
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
//        border = BorderStroke(width = Dimens.BorderThickness, color = MaterialTheme.colorScheme.primaryContainer),
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
                    vertical = Dimens.PaddingSmall
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
//        border = BorderStroke(width = Dimens.BorderThickness, color = MaterialTheme.colorScheme.primaryContainer),
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


