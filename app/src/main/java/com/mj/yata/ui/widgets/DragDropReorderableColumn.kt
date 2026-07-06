package com.mj.yata.ui.widgets

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A [LazyColumn] that supports long-press drag-to-reorder of [items], plus a top/bottom edge
 * drop zone: holding a dragged item there for [edgeDwellMillis] fires [onDragToTopEdge] /
 * [onDragToBottomEdge] (used to surface a "move to another list/project" picker) mid-drag,
 * without waiting for the drag to end.
 *
 * The caller owns list state: [onMove] is invoked live as the dragged item crosses a
 * neighbor, so the caller should reorder its own list to keep [items] in sync during the
 * drag. [onDragEnd] fires once the drag finishes so the caller can persist the final order.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> DragDropReorderableColumn(
    items: List<T>,
    key: (T) -> Any,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    edgeZoneHeight: Dp = 56.dp,
    edgeDwellMillis: Long = 800L,
    onDragToTopEdge: ((T) -> Unit)? = null,
    onDragToBottomEdge: ((T) -> Unit)? = null,
    onDragStateChanged: (Boolean) -> Unit = {},
    itemContent: @Composable (T) -> Unit
) {
    val listState: LazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var edgeDwellJob by remember { mutableStateOf<Job?>(null) }
    var listHeightPx by remember { mutableStateOf(0f) }

    fun cancelEdgeDwell() {
        edgeDwellJob?.cancel()
        edgeDwellJob = null
    }

    fun checkEdgeDwell(pointerYInList: Float, draggedItem: T) {
        val edgeZonePx = with(density) { edgeZoneHeight.toPx() }
        val inTopZone = onDragToTopEdge != null && pointerYInList < edgeZonePx
        val inBottomZone = onDragToBottomEdge != null && pointerYInList > listHeightPx - edgeZonePx
        if (!inTopZone && !inBottomZone) {
            cancelEdgeDwell()
            return
        }
        if (edgeDwellJob != null) return
        edgeDwellJob = scope.launch {
            delay(edgeDwellMillis)
            draggingIndex = null
            dragOffsetY = 0f
            if (inTopZone) onDragToTopEdge?.invoke(draggedItem) else onDragToBottomEdge?.invoke(draggedItem)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.onGloballyPositioned { listHeightPx = it.size.height.toFloat() },
        contentPadding = contentPadding
    ) {
        itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
            val isDragging = draggingIndex == index
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (isDragging) dragOffsetY else 0f
                        shadowElevation = if (isDragging) 8f else 0f
                    }
                    .pointerInput(item, items.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingIndex = index
                                dragOffsetY = 0f
                                onDragStateChanged(true)
                            },
                            onDragEnd = {
                                cancelEdgeDwell()
                                draggingIndex = null
                                dragOffsetY = 0f
                                onDragStateChanged(false)
                                onDragEnd()
                            },
                            onDragCancel = {
                                cancelEdgeDwell()
                                draggingIndex = null
                                dragOffsetY = 0f
                                onDragStateChanged(false)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetY += dragAmount.y
                                val currentIndex = draggingIndex ?: return@detectDragGesturesAfterLongPress
                                val itemInfo = listState.layoutInfo.visibleItemsInfo.find { it.index == currentIndex }
                                    ?: return@detectDragGesturesAfterLongPress
                                val draggedCenter = itemInfo.offset + itemInfo.size / 2 + dragOffsetY
                                val others = listState.layoutInfo.visibleItemsInfo.filter { it.index != currentIndex }
                                // Fast path: the dragged center actually lands inside a neighbor's band.
                                // Fallback: on a fast/large drag delta, the center can jump clear over a
                                // neighbor's band in a single pointer event and miss the exact-containment
                                // check above — fall back to the nearest neighbor by center distance (bounded
                                // to one item-height away) so the swap isn't silently skipped for that frame.
                                val targetInfo = others.find { draggedCenter.toInt() in it.offset..(it.offset + it.size) }
                                    ?: others.minByOrNull { kotlin.math.abs((it.offset + it.size / 2) - draggedCenter) }
                                        ?.takeIf { kotlin.math.abs((it.offset + it.size / 2) - draggedCenter) < it.size }
                                if (targetInfo != null) {
                                    onMove(currentIndex, targetInfo.index)
                                    dragOffsetY += (itemInfo.offset - targetInfo.offset).toFloat()
                                    draggingIndex = targetInfo.index
                                }
                                checkEdgeDwell(draggedCenter, item)
                            }
                        )
                    }
            ) {
                itemContent(item)
            }
        }
    }
}
