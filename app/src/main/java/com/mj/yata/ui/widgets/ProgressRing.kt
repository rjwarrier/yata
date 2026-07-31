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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

    // The wave is purely decorative — a smooth arc reads identically at rest — so it's the one
    // thing here Reduce Motion should stop outright rather than shorten, per LocalReduceMotion's
    // own contract. It's also invisible at the small sizes this composable is called at in list
    // rows (People/Tags/Projects pass 32-46dp): below ~40dp the amplitude is sub-pixel, so those
    // callers were paying for an infinite per-frame transition, a fresh Path allocation and up to
    // 180 sin/cos calls every frame for an effect nobody could see. Skipping it there removes
    // nearly all of this composable's per-row animation cost.
    val reduceMotion = com.mj.yata.ui.theme.LocalReduceMotion.current
    val showWave = !reduceMotion && size >= 40.dp
    val wavePhase = if (showWave) {
        val infiniteTransition = rememberInfiniteTransition(label = "progressRingWave")
        val phase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wavePhase"
        )
        phase
    } else 0f

    // Reused rather than allocated fresh in the draw scope every frame — DrawScope runs on every
    // frame the wave animates, and Path() was one of the allocations happening there ~60 times/sec.
    val path = remember { Path() }

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

                if (!showWave) {
                    // Reduce Motion, or a ring too small for the wave to read: a plain smooth arc,
                    // the M3 non-wavy-indicator convention, with no per-frame path building.
                    drawArc(
                        color = activeColor,
                        startAngle = -90f,
                        sweepAngle = sweepDegrees,
                        useCenter = false,
                        style = stroke
                    )
                } else {
                    val circumference = 2 * PI.toFloat() * radius
                    val wavelengthPx = 14.dp.toPx()
                    val waveCount = max(3, (circumference / wavelengthPx).roundToInt())
                    val amplitude = strokePx * 0.55f

                    path.reset()
                    // 4° steps rather than 2° halves the per-frame trig calls; at the ring sizes
                    // this draws at (40dp+) the difference is imperceptible.
                    val angleStepDeg = 4f
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
        }

        val label = centerLabel ?: if (showLabel && size >= 36.dp) {
            "${(progress.coerceIn(0f, 1f) * 100).toInt()}"
        } else {
            null
        }
        if (label != null) {
            val baseFontSize = when {
                size >= 70.dp -> 15.sp
                size >= 50.dp -> 12.sp
                else -> 10.sp
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    // A three-character label ("99+", or "100" at full progress) is half again as
                    // wide as the two-character case these sizes were picked for, and the ring it
                    // sits in can be as small as 32dp. Step it down rather than let it draw over
                    // the stroke — more so because the app's own UI-scale and text-scale settings
                    // multiply on top of the system font scale.
                    fontSize = if (label.length >= 3) baseFontSize * 0.8f else baseFontSize
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}
