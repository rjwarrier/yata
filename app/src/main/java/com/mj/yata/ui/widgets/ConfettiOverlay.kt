package com.mj.yata.ui.widgets

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.isActive
import java.util.Random

private class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var color: Color,
    var size: Float,
    var angle: Float,
    var va: Float,
    var alpha: Float = 1.0f
)

@Composable
fun ConfettiOverlay(
    trigger: Int,
    modifier: Modifier = Modifier
) {
    if (trigger == 0) return

    val context = LocalContext.current
    val isReducedMotion = remember(context) {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        scale == 0f
    }

    if (isReducedMotion) return

    var particles by remember { mutableStateOf<List<Particle>>(emptyList()) }
    val random = remember { Random() }
    val colors = remember {
        listOf(
            Color(0xFFE8886B), // AccentA
            Color(0xFF9DAE55), // AccentB
            Color(0xFF8C7BE0), // AccentC
            Color(0xFFE0A93A), // AccentD
            Color(0xFF4FA97D), // AccentE
            Color(0xFFDB6FA0)  // AccentF
        )
    }

    LaunchedEffect(trigger) {
        if (trigger > 0) {
            // Spawn 26 particles centered at relative screen center
            val list = List(26) {
                val angleRad = random.nextFloat() * 2f * Math.PI.toFloat()
                val speed = 5f + random.nextFloat() * 12f
                Particle(
                    x = 0.5f,
                    y = 0.4f,
                    vx = Math.cos(angleRad.toDouble()).toFloat() * speed,
                    vy = Math.sin(angleRad.toDouble()).toFloat() * speed - 6f, // Shoot upwards
                    color = colors[random.nextInt(colors.size)],
                    size = 20f + random.nextFloat() * 20f,
                    angle = random.nextFloat() * 360f,
                    va = -15f + random.nextFloat() * 30f
                )
            }
            particles = list

            val startMs = System.currentTimeMillis()
            while (isActive && particles.any { it.alpha > 0f }) {
                awaitFrame()
                val elapsed = System.currentTimeMillis() - startMs
                val alpha = (1f - elapsed / 1000f).coerceIn(0f, 1f)
                
                particles = particles.map { p ->
                    p.x += p.vx * 0.0015f
                    p.y += p.vy * 0.0015f
                    p.vy += 0.35f // Gravity
                    p.vx *= 0.96f // Drag
                    p.vy *= 0.96f
                    p.angle += p.va
                    p.alpha = alpha
                    p
                }
            }
            particles = emptyList()
        }
    }

    if (particles.isNotEmpty()) {
        Canvas(modifier = modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            particles.forEach { p ->
                if (p.alpha > 0f) {
                    val px = p.x * width
                    val py = p.y * height

                    rotate(p.angle, pivot = Offset(px, py)) {
                        drawRect(
                            color = p.color.copy(alpha = p.alpha),
                            topLeft = Offset(px - p.size / 2, py - p.size / 2),
                            size = Size(p.size, p.size * 0.5f)
                        )
                    }
                }
            }
        }
    }
}
