package com.mj.yata.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp

/** Widgets can't use Compose's Canvas/drawArc (Glance has no custom-drawing primitive), so the
 * progress ring is pre-rendered to a plain android.graphics Bitmap and shown via Image — the
 * standard workaround for charts/rings in Glance widgets. Flat arc, not the in-app wavy one
 * (that's an idle animation; a single static widget frame would just look like a frozen glitch). */
object WidgetRing {
    fun render(
        context: Context,
        sizeDp: Dp,
        strokeDp: Dp,
        progress: Float,
        activeColor: Color,
        trackColor: Color
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp.value * density).toInt().coerceAtLeast(1)
        val strokePx = strokeDp.value * density

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            strokeCap = Paint.Cap.ROUND
        }
        val inset = strokePx / 2f
        val rect = RectF(inset, inset, sizePx - inset, sizePx - inset)

        paint.color = trackColor.toArgb()
        canvas.drawOval(rect, paint)

        val sweep = progress.coerceIn(0f, 1f) * 360f
        if (sweep > 0f) {
            paint.color = activeColor.toArgb()
            canvas.drawArc(rect, -90f, sweep, false, paint)
        }
        return bitmap
    }
}
