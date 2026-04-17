package com.aadil.spool.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aadil.spool.ui.theme.Dimens

fun Modifier.verticalScrollbar(
    scrollState: ScrollState,
    width: Dp = Dimens.PaddingTiny,
    color: Color = Color.Gray.copy(alpha = 0.3f),
    paddingLeft: Boolean = false,
    radius: Dp = Dimens.CornerRadius,
    verticalPadding: Dp = Dimens.CustomScrollbarTopBottomPadding

): Modifier = drawWithContent {
    drawContent()

    val viewPortHeight = size.height
    val totalContentHeight = scrollState.maxValue + viewPortHeight

    // Don't draw fi content fits on screen
    if (totalContentHeight <= viewPortHeight) return@drawWithContent

    // Calculate how much height to fit within the padded area
    val vPaddingPx = verticalPadding.toPx()
    val availableHeight = viewPortHeight - (vPaddingPx * 2)

    val scrollbarHeight = (viewPortHeight / totalContentHeight) * availableHeight
    val scrollbarOffset = vPaddingPx + (scrollState.value / totalContentHeight) * availableHeight

    val xOffset = if (paddingLeft) 4.dp.toPx() else size.width - width.toPx() - 4.dp.toPx()

    drawRoundRect(
        color = color,
        topLeft = Offset(x = xOffset, y = scrollbarOffset),
        size = Size(width = width.toPx(), height = scrollbarHeight),
        cornerRadius = CornerRadius(x = radius.toPx(), y = radius.toPx())
    )
}