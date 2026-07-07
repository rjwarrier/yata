package com.mj.yata.ui.widgets

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.zIndex

/**
 * A plain (non-lazy) [Column] that supports long-press drag-to-reorder of [items] — the
 * non-lazy sibling of [DragDropReorderableColumn], for small entity lists (Projects, People
 * within a group, Lists in the nav drawer) that need to nest inside another scrollable
 * container. A [DragDropReorderableColumn] can't do this: its inner `LazyColumn` requires a
 * bounded height even with `userScrollEnabled = false`, and throws when nested inside another
 * scrollable parent ("measured with an infinity maximum height constraints"). A plain [Column]
 * has no such restriction since it just wraps to its content height.
 *
 * Item positions are tracked via `onGloballyPositioned` (relative to this Column, its direct
 * parent) instead of `LazyListState.layoutInfo`, since there's no lazy layout info here.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> DragReorderColumn(
    items: List<T>,
    key: (T) -> Any,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
    onDragStateChanged: (Boolean) -> Unit = {},
    itemContent: @Composable (T) -> Unit
) {
    var draggingKey by remember { mutableStateOf<Any?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    // Each item's (top, height) in this Column's own coordinate space, refreshed on every layout.
    val itemBounds = remember { mutableStateMapOf<Any, Pair<Float, Float>>() }

    Column(modifier = modifier) {
        items.forEach { item ->
            val itemKey = key(item)
            val isDragging = draggingKey == itemKey
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (isDragging) dragOffsetY else 0f
                        shadowElevation = if (isDragging) 8f else 0f
                    }
                    .onGloballyPositioned { coords ->
                        itemBounds[itemKey] = coords.positionInParent().y to coords.size.height.toFloat()
                    }
                    .pointerInput(itemKey, items.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingKey = itemKey
                                dragOffsetY = 0f
                                onDragStateChanged(true)
                            },
                            onDragEnd = {
                                draggingKey = null
                                dragOffsetY = 0f
                                onDragStateChanged(false)
                                onDragEnd()
                            },
                            onDragCancel = {
                                draggingKey = null
                                dragOffsetY = 0f
                                onDragStateChanged(false)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetY += dragAmount.y
                                val currentKey = draggingKey ?: return@detectDragGesturesAfterLongPress
                                val (myTop, myHeight) = itemBounds[currentKey] ?: return@detectDragGesturesAfterLongPress
                                val draggedCenter = myTop + myHeight / 2 + dragOffsetY

                                val currentIndex = items.indexOfFirst { key(it) == currentKey }
                                val targetEntry = itemBounds.entries
                                    .filter { it.key != currentKey }
                                    .minByOrNull { (_, bounds) -> kotlin.math.abs((bounds.first + bounds.second / 2) - draggedCenter) }
                                    ?.takeIf { (_, bounds) -> kotlin.math.abs((bounds.first + bounds.second / 2) - draggedCenter) < bounds.second }

                                if (targetEntry != null && currentIndex >= 0) {
                                    val targetIndex = items.indexOfFirst { key(it) == targetEntry.key }
                                    if (targetIndex >= 0) {
                                        onMove(currentIndex, targetIndex)
                                        dragOffsetY += (myTop - targetEntry.value.first)
                                    }
                                }
                            }
                        )
                    }
            ) {
                itemContent(item)
            }
        }
    }
}
