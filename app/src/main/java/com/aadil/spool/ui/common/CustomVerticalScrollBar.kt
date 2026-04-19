package com.aadil.spool.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isEmpty
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



fun Modifier.lazyVerticalScrollbar(
    state: LazyListState, // Takes LazyListState instead of ScrollState
    width: Dp = Dimens.PaddingTiny,
    color: Color = Color.Gray.copy(alpha = 0.3f),
    paddingLeft: Boolean = false,
    radius: Dp = Dimens.CornerRadius,
    verticalPadding: Dp = Dimens.CustomScrollbarTopBottomPadding
): Modifier = drawWithContent {
    drawContent()

    val info = state.layoutInfo
    val totalItemsCount = info.totalItemsCount
    if (totalItemsCount == 0) return@drawWithContent

    val visibleItems = info.visibleItemsInfo
    if (visibleItems.isEmpty()) return@drawWithContent

    // Rough estimation logic for LazyList
    val viewPortHeight = size.height
    val firstItem = visibleItems.first()
    val lastItem = visibleItems.last()

    // Estimate total height based on average of visible items
    val estimatedTotalHeight = (viewPortHeight / visibleItems.size) * totalItemsCount
    if (estimatedTotalHeight <= viewPortHeight) return@drawWithContent

    // Simple scrollbar calculation
    val scrollbarHeight = (viewPortHeight / totalItemsCount) * visibleItems.size
    val scrollbarOffset = (state.firstVisibleItemIndex.toFloat() / totalItemsCount) * viewPortHeight

    drawRoundRect(
        color = color,
        topLeft = Offset(x = size.width - width.toPx(), y = scrollbarOffset),
        size = Size(width = width.toPx(), height = scrollbarHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx())
    )
}