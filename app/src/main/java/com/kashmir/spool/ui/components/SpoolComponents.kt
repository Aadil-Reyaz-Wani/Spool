package com.kashmir.spool.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kashmir.spool.ui.theme.Dimens


// Solid Button
@Composable
fun SpoolButton(
    text: String,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    buttonContainerColor: Color,
    buttonContentColor: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth(),
        enabled = enabled,
        shape = MaterialTheme.shapes.extraLarge, // 50.dp pill shape
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonContainerColor, // Spool Yellow
            contentColor = buttonContentColor, // Black Text
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp,
            pressedElevation = 2.dp
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription
        )
        Spacer(modifier = Modifier.width(Dimens.gapHeight))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Preview
@Composable
private fun ButtonPev() {
    SpoolButton(
        text = "Button",
        icon = Icons.Outlined.Print,
        onClick = {},
        buttonContainerColor = Color.Yellow,
        buttonContentColor = Color.Black,
        enabled = true,
        contentDescription = "",
        modifier = Modifier.padding(Dimens.PaddingMedium)
    )
}

// Outlined Button
@Composable
fun SpoolOutlinedButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {

}