package com.kashmir.spool.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.kashmir.spool.ui.theme.Dimens

@Composable
fun GhostCard(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier) {

    val stroke = Stroke(
        width = 4f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
    )
    val color = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    style = stroke,
                    color = color,
                    cornerRadius = CornerRadius(Dimens.CornerRadius.toPx())
                )
            }
            .padding(Dimens.PaddingMedium),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(Dimens.PaddingMedium),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "Ghost Card Icon",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Dimens.IconSize)
            )
            Spacer(modifier = Modifier.height(Dimens.gapHeight))
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                fontSize = Dimens.TextInGhostCard,
                textAlign = TextAlign.Center,
                lineHeight = TextUnit(value = 1f, type = TextUnitType.Em)
            )
        }
    }

}

@Preview
@Composable
private fun GhostCardPreview() {
    GhostCard(
        text = "Please Add Something",
        icon = Icons.Default.EventNote
    )
}