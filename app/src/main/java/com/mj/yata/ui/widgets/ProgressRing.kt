package com.mj.yata.ui.widgets

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * M3 Expressive-style ring: the active sweep is a gently squiggling wave (not a flat arc),
 * with the wave slowly crawling around the ring for a "lively" feel even at rest. The
 * background track stays a plain smooth circle, matching M3's wavy-indicator convention.
 */
@Composable
fun ProgressRing(
    progress: Float, // 0f to 1f
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    strokeWidth: Dp = 4.dp,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    showLabel: Boolean = true,
    /**
     * Replaces the percentage in the middle of the ring. Set it where the count matters more than
     * the completion figure — the People tab shows each person's open-task count here. Unlike the
     * percentage this is drawn at any ring size: a caller passing it has decided the text is the
     * point, and it is typically one or two characters where a percentage is two or three.
     */
    centerLabel: String? = null
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(
            durationMillis = 380,
            easing = { it * (2f - it) } // Decelerate curve
        )
    )

    val infiniteTransition = rememberInfiniteTransition(label = "progressRingWave")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            val stroke = Stroke(width = strokePx, cap = StrokeCap.Round)
            val radius = (this.size.minDimension - strokePx) / 2f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)

            // Background track — plain smooth ring.
            drawCircle(color = inactiveColor, radius = radius, style = stroke)

            if (animatedProgress > 0f) {
                val sweepDegrees = animatedProgress * 360f
                val circumference = 2 * PI.toFloat() * radius
                val wavelengthPx = 14.dp.toPx()
                val waveCount = max(3, (circumference / wavelengthPx).roundToInt())
                val amplitude = strokePx * 0.55f

                val path = Path()
                val angleStepDeg = 2f
                var angleDeg = 0f
                var first = true
                while (angleDeg <= sweepDegrees) {
                    val angleRad = Math.toRadians((-90.0 + angleDeg))
                    val wave = amplitude * sin(waveCount * Math.toRadians(angleDeg.toDouble()) + wavePhase)
                    val r = radius + wave
                    val x = center.x + (r * cos(angleRad)).toFloat()
                    val y = center.y + (r * sin(angleRad)).toFloat()
                    if (first) {
                        path.moveTo(x, y)
                        first = false
                    } else {
                        path.lineTo(x, y)
                    }
                    angleDeg += angleStepDeg
                }
                drawPath(path, color = activeColor, style = stroke)
            }
        }

        val label = centerLabel ?: if (showLabel && size >= 36.dp) {
            "${(progress.coerceIn(0f, 1f) * 100).toInt()}"
        } else {
            null
        }
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = when {
                        size >= 70.dp -> 15.sp
                        size >= 50.dp -> 12.sp
                        else -> 10.sp
                    }
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}
