package com.kashmir.spool.ui.components

import android.app.AlertDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


@Composable
fun DeleteConfirmationAlertDialog(
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = {
            onDismiss()
        },
        title = {
            Text(text = "Confirm Deletion")
        },
        text = {
            Text(text = "Are you sure you want to delete this item? This action cannot be undone.")
        },
        confirmButton = {
            TextButton(
                onClick = onConfirmDelete
            ) {
                Text(
                    text = "Delete",
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(
                    text = "Cancel",

                )
            }
        }
    )
}


@Composable
fun InputAlertDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var inputText by rememberSaveable { mutableStateOf("") } // State to hold the TextField's value

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = "Enter Deducted Weight")
        },
        text = {
            Column { // Use a Column to place the Text and TextField vertically
                Text("Please enter weight:")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it }, // Update state on user input
                    label = { Text("Deduct Weight (g)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(inputText) // Pass the input value to the confirmation logic
                    onDismissRequest() // Dismiss the dialog after action
                }
            ) {
                Text("Print")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismissRequest() // Dismiss the dialog on cancel
                }
            ) {
                Text("Cancel")
            }
        }
    )
}