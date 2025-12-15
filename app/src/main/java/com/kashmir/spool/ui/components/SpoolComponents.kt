package com.kashmir.spool.ui.components

import android.graphics.PixelFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kashmir.spool.ui.theme.BrandOrange
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
    hasBorder: Boolean,
    buttonDefaultElevation: Dp = 4.dp,
    buttonPressedElevation: Dp = 2.dp,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.ButtonHeight),
        enabled = enabled,
        shape = MaterialTheme.shapes.extraLarge,
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonContainerColor,
            contentColor = buttonContentColor,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = buttonDefaultElevation,
            pressedElevation = buttonPressedElevation
        ),
        border = if (hasBorder) BorderStroke(
            Dimens.BorderThickness,
            color = MaterialTheme.colorScheme.primary
        ) else null
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
        hasBorder = false,
        modifier = Modifier.padding(Dimens.PaddingMedium)
    )
}

// Outlined Button
@Composable
fun SpoolOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isError: Boolean = false,
    leadingIcon: ImageVector,
    singleLine: Boolean = true,
    supportingText: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = Color.Gray) },
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        singleLine = singleLine,
        isError = isError,
        supportingText = {
            if (isError) {
                Text(
                    text = supportingText,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null
            )
        },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            focusedLabelColor = MaterialTheme.colorScheme.onBackground,
            unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            cursorColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)

        )
    )
}

@Preview
@Composable
private fun TextFieldPrev() {
    SpoolOutlinedTextField(
        value = "",
        onValueChange = {},
        label = "Label",
        modifier = Modifier,
        placeholder = "Placeholder",
        isError = false,
        leadingIcon = Icons.Outlined.TextFields,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        keyboardActions = KeyboardActions(onDone = {  })
    )
}

@Composable
fun SpoolHeadingText(
    text: String,
    icon: ImageVector,
    alpha: Float = 1f,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.gapHeight)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(Dimens.IconMedium)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = textColor,
            modifier = Modifier.fillMaxWidth()
        )
    }
}


@Composable
fun SpoolTag(
    text: String,
    modifier: Modifier = Modifier
) {

    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.border(
            width = Dimens.BorderThickness,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            shape = MaterialTheme.shapes.extraSmall
        ),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = null,
        content = {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = modifier.padding(horizontal = 6.dp, vertical = 2.dp)

            )
        }
    )
}


@Composable
fun SpoolProgressBar(
    percentage: Double,
    currentWeight: String,
    totalWeight: String,
    modifier: Modifier = Modifier
) {
    val progress = currentWeight.toFloat()/totalWeight.toFloat()
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .size(Dimens.ProgressBarHeight)
            .clip(CircleShape),
        color = if (percentage > 40) {
            BrandOrange
        } else if (percentage < 20) {
            BrandOrange
        } else {
            BrandOrange
        },
        trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
        strokeCap = StrokeCap.Butt,
        gapSize = 1.5.dp,
    )
}