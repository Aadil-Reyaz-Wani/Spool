package com.aadil.spool.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CompassCalibration
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aadil.spool.R
import com.aadil.spool.ui.theme.Dimens


@Composable
fun DeleteConfirmationAlertDialog(
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        modifier = modifier
            .fillMaxWidth()
            .background(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium),
        shape = MaterialTheme.shapes.medium,
        onDismissRequest = {
            onDismiss()
        },
        icon = {
            Icon(
                Icons.Outlined.DeleteSweep,
                null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(Dimens.IconLarge)
            )

        },
        title = {
                Text(
                    text = "Confirm Deletion",
                    color = MaterialTheme.colorScheme.onSurface
                )
        },
        text = {
            Box(
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Are you sure you want to delete this item? This action cannot be undone.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()

                )
            }

        },
        confirmButton = {
            FilledTonalButton(
                onClick = onConfirmDelete,
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Delete",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}


@Composable
fun InputAlertDialog(
    printWeight: String,
    onValueChange: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        modifier = modifier
            .background(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium),
        shape = MaterialTheme.shapes.medium,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Scale,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Dimens.IconLarge)
            )

        },
        title = {
            Text(
                text = "Deduct Weight",
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column (
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ){
                SpoolOutlinedTextField(
                    value = printWeight,
                    onValueChange = onValueChange,
                    label = stringResource(R.string.label_deduct_weight),
                    placeholder = stringResource(R.string.hint_total_wight),
                    leadingIcon = Icons.Outlined.CompassCalibration,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    onConfirm(printWeight)
                    onDismissRequest()
                },
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Print,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Deduct",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                }
            ) {
                Text(
                    text = "Cancel",
                    color = MaterialTheme.colorScheme.onBackground,


                )
            }
        }
    )
}