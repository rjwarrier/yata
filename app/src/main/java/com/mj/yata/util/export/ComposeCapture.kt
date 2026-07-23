package com.mj.yata.util.export

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Renders [content] off-screen at a fixed pixel width and rasterizes it to a [Bitmap].
 *
 * There's no on-screen Compose region to snapshot for an export card, so a hidden
 * [ComposeView] is attached to the current Activity's content root (needed so it inherits a
 * real lifecycle/composition context), given a few frames to let Compose actually run layout,
 * then drawn onto a Canvas manually — [View.draw] rasterizes regardless of [View.INVISIBLE],
 * so nothing flashes on screen.
 */
suspend fun captureComposableToBitmap(
    context: Context,
    widthPx: Int,
    content: @Composable () -> Unit
): Bitmap {
    val activity = context.findActivity()
        ?: throw IllegalStateException("captureComposableToBitmap requires an Activity context")
    val root = activity.findViewById<ViewGroup>(android.R.id.content)

    val composeView = ComposeView(activity).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        visibility = View.INVISIBLE
        setContent(content)
    }

    root.addView(composeView, FrameLayout.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT))
    try {
        // Let Compose's Recomposer actually run composition/layout for the new subtree —
        // a couple of frames is the standard margin used for this off-screen-capture trick.
        repeat(3) { withFrameNanos {} }

        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        composeView.layout(0, 0, composeView.measuredWidth, composeView.measuredHeight)

        val height = composeView.measuredHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        composeView.draw(canvas)
        return bitmap
    } finally {
        root.removeView(composeView)
    }
}
