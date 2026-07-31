package com.mj.yata.ui.screen.lock

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.R
import com.mj.yata.ui.theme.LocalHapticsEnabled
import com.mj.yata.ui.theme.LocalReduceMotion
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.YataEase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The smallest PIN the setup screen allows, and so the fewest dots ever drawn. */
private const val MIN_PIN_LENGTH = 4

/**
 * The screen shown when the app is locked.
 *
 * Biometrics are the primary path and the PIN is the fallback, which is the order the platform's
 * own lock screen uses and the order these two actually get used in. The prompt is therefore
 * raised on arrival — but only once per lock, not on every recomposition: re-raising it after the
 * user dismissed it to type their PIN would fight them for the screen.
 *
 * The keypad is always visible rather than hidden behind a "use PIN instead" button. There is
 * nothing else this screen does, so making the fallback a second tap only slowed down the case
 * where biometrics have already failed.
 *
 * Every animation here is decorative, so all of them defer to Reduce Motion: the entrance is
 * skipped outright rather than shortened, since a shortened flourish is still a flourish.
 */
@Composable
fun LockScreen(
    onUnlockClick: () -> Unit,
    pinAvailable: Boolean = false,
    biometricAvailable: Boolean = true,
    pinLength: Int = 0,
    lockedUntilMillis: Long = 0L,
    onVerifyPin: suspend (String) -> Boolean = { false },
    onPinFailed: suspend () -> Unit = {},
    onPinUnlocked: () -> Unit = {}
) {
    // Keyed to the lock rather than to composition: `Unit` would re-fire on configuration change,
    // and the prompt would reappear over a half-typed PIN after a rotation.
    var promptedForThisLock by remember { mutableStateOf(false) }
    LaunchedEffect(biometricAvailable) {
        if (biometricAvailable && !promptedForThisLock) {
            promptedForThisLock = true
            onUnlockClick()
        }
    }

    val reduceMotion = LocalReduceMotion.current
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val entrance by animateFloatAsState(
        targetValue = if (entered || reduceMotion) 1f else 0f,
        animationSpec = tween(durationMillis = YataDur.nav, easing = YataEase.emphDecel),
        label = "lockEntrance"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .graphicsLayer {
                    alpha = entrance
                    // A short rise into place, not a slide across the screen.
                    translationY = (1f - entrance) * 32.dp.toPx()
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Weighted spacers rather than a centred column: the header sits in the upper third
            // and the keypad low enough to reach with a thumb, which is where a lock screen wants
            // them. Centring everything left a dense block adrift in the middle of the screen.
            Spacer(modifier = Modifier.weight(0.9f))

            LockHeader()

            Spacer(modifier = Modifier.weight(0.7f))

            if (pinAvailable) {
                PinPad(
                    pinLength = pinLength,
                    lockedUntilMillis = lockedUntilMillis,
                    biometricAvailable = biometricAvailable,
                    onVerifyPin = onVerifyPin,
                    onPinFailed = onPinFailed,
                    onCorrect = onPinUnlocked,
                    onUseBiometric = onUnlockClick
                )
            } else {
                // No PIN configured, so biometrics are the only way in and the button is the whole
                // interface. Without this the screen would be a dead end whenever the prompt was
                // dismissed.
                TextButton(onClick = onUnlockClick) {
                    Text(stringResource(R.string.lock_unlock))
                }
            }

            Spacer(modifier = Modifier.weight(0.5f))
        }
    }
}

@Composable
private fun LockHeader() {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(34.dp)
        )
    }
    Text(
        text = stringResource(R.string.lock_title),
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 24.dp)
    )
}

@Composable
private fun ColumnScope.PinPad(
    pinLength: Int,
    lockedUntilMillis: Long,
    biometricAvailable: Boolean,
    onVerifyPin: suspend (String) -> Boolean,
    onPinFailed: suspend () -> Unit,
    onCorrect: () -> Unit,
    onUseBiometric: () -> Unit
) {
    var digits by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var secondsRemaining by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val hapticsEnabled = LocalHapticsEnabled.current
    val context = LocalContext.current
    val reduceMotion = LocalReduceMotion.current
    val shake = remember { Animatable(0f, Float.VectorConverter) }

    // Ticks the cooldown down once a second so the message counts rather than sitting still.
    LaunchedEffect(lockedUntilMillis) {
        while (true) {
            val remaining = ((lockedUntilMillis - System.currentTimeMillis()) / 1000L).toInt()
            secondsRemaining = remaining.coerceAtLeast(0)
            if (remaining <= 0) break
            delay(1000)
        }
    }
    val lockedOut = secondsRemaining > 0

    // Unknown length means a PIN set before the length was recorded; those verify on every keypress
    // from the minimum up, which is the only way to submit without a confirm key. The attempt is
    // silent — a wrong result at four digits when the real PIN is six isn't a failure to report.
    val knownLength = pinLength >= MIN_PIN_LENGTH

    fun fail() {
        wrong = true
        digits = ""
        // A double pulse, deliberately unlike the single tick of a keypress: the whole job of
        // this one is to be distinguishable without looking at the screen.
        if (hapticsEnabled) rejectHaptic(context, haptics)
        if (!reduceMotion) {
            scope.launch {
                shake.snapTo(0f)
                shake.animateTo(
                    targetValue = 0f,
                    animationSpec = keyframes {
                        durationMillis = 340
                        (-14f) at 60
                        14f at 130
                        (-9f) at 200
                        9f at 270
                        0f at 340
                    }
                )
            }
        }
        scope.launch { onPinFailed() }
    }

    fun attempt(candidate: String, silentOnFailure: Boolean) {
        if (checking) return
        checking = true
        scope.launch {
            val ok = onVerifyPin(candidate)
            checking = false
            when {
                ok -> {
                    if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCorrect()
                }
                silentOnFailure -> {}
                else -> fail()
            }
        }
    }

    fun press(digit: String) {
        if (lockedOut || checking) return
        wrong = false
        if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val next = digits + digit
        // Cap at the known length, or at a generous ceiling when it isn't known.
        val ceiling = if (knownLength) pinLength else 12
        if (next.length > ceiling) return
        digits = next
        when {
            knownLength && next.length == pinLength -> attempt(next, silentOnFailure = false)
            !knownLength && next.length >= MIN_PIN_LENGTH -> attempt(next, silentOnFailure = true)
        }
    }

    PinDots(
        entered = digits.length,
        total = if (knownLength) pinLength else maxOf(MIN_PIN_LENGTH, digits.length),
        isError = wrong,
        modifier = Modifier.graphicsLayer { translationX = shake.value }
    )

    // Crossfaded so the message changes without the layout jumping, and so the countdown ticking
    // down doesn't flicker the whole line each second.
    AnimatedContent(
        targetState = when {
            lockedOut -> stringResource(R.string.lock_too_many_attempts, secondsRemaining)
            wrong -> stringResource(R.string.lock_incorrect_pin)
            else -> stringResource(R.string.lock_enter_pin)
        },
        transitionSpec = {
            fadeIn(tween(YataDur.fade)) togetherWith fadeOut(tween(YataDur.fade))
        },
        label = "lockStatus",
        modifier = Modifier.padding(top = 20.dp, bottom = 32.dp)
    ) { message ->
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (lockedOut || wrong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }

    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9")
    )
    rows.forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            row.forEach { digit ->
                PinKey(enabled = !lockedOut, onClick = { press(digit) }) {
                    Text(
                        text = digit,
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 27.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
    }

    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        // Bottom-left is the biometric retry, where the platform's own keypad puts it. It holds
        // the space even when unavailable so the 0 key stays centred.
        if (biometricAvailable) {
            PinKey(enabled = !lockedOut, onClick = onUseBiometric) {
                Icon(
                    imageVector = Icons.Filled.Fingerprint,
                    contentDescription = stringResource(R.string.lock_use_biometric_instead),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            Spacer(modifier = Modifier.size(76.dp))
        }
        PinKey(enabled = !lockedOut, onClick = { press("0") }) {
            Text(
                text = "0",
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 27.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        PinKey(
            enabled = !lockedOut && digits.isNotEmpty(),
            onClick = {
                wrong = false
                digits = digits.dropLast(1)
                if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Backspace,
                contentDescription = stringResource(R.string.lock_backspace),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PinDots(
    entered: Int,
    total: Int,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        modifier = modifier
    ) {
        repeat(total.coerceAtLeast(MIN_PIN_LENGTH)) { index ->
            val filled = index < entered
            // Each dot swells as it fills, so a keypress registers in the corner of the eye
            // without having to look straight at the row.
            val scale by animateFloatAsState(
                targetValue = if (filled) 1f else 0.72f,
                animationSpec = tween(durationMillis = YataDur.micro, easing = YataEase.spring),
                label = "pinDotScale"
            )
            val color by animateColorAsState(
                targetValue = when {
                    isError -> MaterialTheme.colorScheme.error
                    filled -> MaterialTheme.colorScheme.primary
                    else -> Color.Transparent
                },
                animationSpec = tween(durationMillis = YataDur.micro),
                label = "pinDotColor"
            )
            val borderColor by animateColorAsState(
                targetValue = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                animationSpec = tween(durationMillis = YataDur.micro),
                label = "pinDotBorder"
            )
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(color, CircleShape)
                    .border(
                        width = if (filled || isError) 0.dp else 1.5.dp,
                        color = borderColor,
                        shape = CircleShape
                    )
            )
        }
    }
}

/** One 76dp circular key — a real target, unlike the bare IconButtons this replaced. */
@Composable
private fun PinKey(
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // The ripple alone reads as a smudge on a dark key; the dip is what makes the press feel like
    // a button going down.
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = YataDur.micro, easing = YataEase.emphDecel),
        label = "pinKeyScale"
    )
    Box(
        modifier = Modifier
            .size(76.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                if (enabled) MaterialTheme.colorScheme.surfaceContainerHigh
                else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f)
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * The "no" buzz. Compose's own haptic vocabulary has nothing that reads as rejection — LongPress
 * is the same thing a successful long-press gives — so this goes to the vibrator for a two-beat
 * pattern that can't be confused with the keypress tick.
 */
private fun rejectHaptic(context: android.content.Context, fallback: androidx.compose.ui.hapticfeedback.HapticFeedback) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
    }
    if (vibrator == null || !vibrator.hasVibrator()) {
        fallback.performHapticFeedback(HapticFeedbackType.LongPress)
        return
    }
    runCatching {
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 28, 90, 28), -1))
    }.onFailure {
        fallback.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}
