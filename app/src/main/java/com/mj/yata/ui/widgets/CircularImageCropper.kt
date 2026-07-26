package com.mj.yata.ui.widgets

import com.mj.yata.R
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.max
import kotlin.math.min

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 5f
private val VIEWPORT_SIZE = 280.dp

/**
 * Full-screen pinch/pan cropper: [source] sits behind a fixed circular viewport. The user
 * drags to reposition and pinches to zoom; "Use photo" extracts exactly the square region
 * visible inside the circle, so the preview is WYSIWYG.
 */
@Composable
fun CircularImageCropper(
    source: Bitmap,
    onConfirm: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val density = LocalDensity.current
    val viewportPx = with(density) { VIEWPORT_SIZE.toPx() }

    // "Cover" fit: the image exactly fills the circle when zoom == 1.
    val baseScale = remember(source) {
        max(viewportPx / source.width, viewportPx / source.height)
    }

    fun clamp(candidate: Offset, currentZoom: Float): Offset {
        val effectiveScale = baseScale * currentZoom
        val displayedW = source.width * effectiveScale
        val displayedH = source.height * effectiveScale
        val maxX = max(0f, (displayedW - viewportPx) / 2f)
        val maxY = max(0f, (displayedH - viewportPx) / 2f)
        return Offset(candidate.x.coerceIn(-maxX, maxX), candidate.y.coerceIn(-maxY, maxY))
    }

    val displayWidthDp = with(density) { (source.width * baseScale).toDp() }
    val displayHeightDp = with(density) { (source.height * baseScale).toDp() }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            // Must be false, matching every other full-screen Dialog in the app. Left at the
            // default (true) on a targetSdk 35 build, the window is sized to the whole screen but
            // its content is still offset by the system bars, so the bottom is clipped by that
            // much — which silently swallowed the entire Cancel / Use photo row. Opting out makes
            // Compose's own insets the single source of truth, and safeDrawingPadding below then
            // does the insetting exactly once.
            decorFitsSystemWindows = false
        )
    ) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "Drag to reposition, pinch to zoom",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                Box(
                    modifier = Modifier
                        .size(VIEWPORT_SIZE)
                        .clip(CircleShape)
                        .background(Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = source.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(displayWidthDp, displayHeightDp)
                            .graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = offset.x
                                translationY = offset.y
                            }
                            .pointerInput(source) {
                                detectTransformGestures { _, pan, gestureZoom, _ ->
                                    val newZoom = (zoom * gestureZoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                                    zoom = newZoom
                                    offset = clamp(offset + pan, newZoom)
                                }
                            }
                    )
                }

                // Deliberately directly beneath the circle rather than pinned to the bottom of
                // the window. Bottom-anchored, these buttons kept getting clipped on API 35 —
                // the circle and hint always render, so keeping the actions in that same centred
                // block makes them visible regardless of how the dialog window resolves insets.
                Spacer(modifier = Modifier.height(28.dp))

                Row(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.action_cancel), color = Color.White)
                    }
                    Button(onClick = {
                        onConfirm(cropVisibleRegion(source, viewportPx, baseScale, zoom, offset))
                    }) {
                        Text(stringResource(R.string.circular_image_cropper_use_photo))
                    }
                }

                // Balances the leading weight spacer so hint + circle + actions stay centred as
                // one block. Without it the leading spacer alone would shove everything to the
                // bottom edge — straight back into the clipping this change exists to avoid.
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Extracts the square region of [source] currently visible inside the circular viewport. */
private fun cropVisibleRegion(
    source: Bitmap,
    viewportPx: Float,
    baseScale: Float,
    zoom: Float,
    offset: Offset
): Bitmap {
    val effectiveScale = baseScale * zoom
    val displayedW = source.width * effectiveScale
    val displayedH = source.height * effectiveScale

    val viewportLeftInDisplayed = (displayedW - viewportPx) / 2f - offset.x
    val viewportTopInDisplayed = (displayedH - viewportPx) / 2f - offset.y

    val cropSize = (viewportPx / effectiveScale).coerceAtMost(min(source.width, source.height).toFloat())
    val cropX = (viewportLeftInDisplayed / effectiveScale).coerceIn(0f, source.width - cropSize)
    val cropY = (viewportTopInDisplayed / effectiveScale).coerceIn(0f, source.height - cropSize)

    return Bitmap.createBitmap(
        source,
        cropX.toInt(),
        cropY.toInt(),
        cropSize.toInt().coerceAtLeast(1),
        cropSize.toInt().coerceAtLeast(1)
    )
}
