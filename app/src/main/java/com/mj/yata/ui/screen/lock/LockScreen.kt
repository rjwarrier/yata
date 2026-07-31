package com.mj.yata.ui.screen.lock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.R
import com.mj.yata.ui.theme.LocalHapticsEnabled
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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            LockHeader()

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
                Spacer(modifier = Modifier.height(24.dp))
                TextButton(onClick = onUnlockClick) {
                    Text(stringResource(R.string.lock_unlock))
                }
            }
        }
    }
}

@Composable
private fun LockHeader() {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp)
        )
    }
    Text(
        text = stringResource(R.string.lock_title),
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 20.dp)
    )
}

@Composable
private fun PinPad(
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
        if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            shake.snapTo(0f)
            shake.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 320
                    (-14f) at 60
                    14f at 120
                    (-9f) at 180
                    9f at 240
                    0f at 320
                }
            )
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
                ok -> onCorrect()
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

    Spacer(modifier = Modifier.height(28.dp))

    PinDots(
        entered = digits.length,
        total = if (knownLength) pinLength else maxOf(MIN_PIN_LENGTH, digits.length),
        isError = wrong,
        modifier = Modifier.graphicsLayer { translationX = shake.value }
    )

    Text(
        text = when {
            lockedOut -> stringResource(R.string.lock_too_many_attempts, secondsRemaining)
            wrong -> stringResource(R.string.lock_incorrect_pin)
            else -> stringResource(R.string.lock_enter_pin)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = if (lockedOut || wrong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)
    )

    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9")
    )
    rows.forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            row.forEach { digit ->
                PinKey(enabled = !lockedOut, onClick = { press(digit) }) {
                    Text(
                        text = digit,
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }

    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
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
            Spacer(modifier = Modifier.size(72.dp))
        }
        PinKey(enabled = !lockedOut, onClick = { press("0") }) {
            Text(
                text = "0",
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp),
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
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
    ) {
        repeat(total.coerceAtLeast(MIN_PIN_LENGTH)) { index ->
            val filled = index < entered
            val color = when {
                isError -> MaterialTheme.colorScheme.error
                filled -> MaterialTheme.colorScheme.primary
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(color, CircleShape)
                    .border(
                        width = if (filled || isError) 0.dp else 1.5.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    )
            )
        }
    }
}

/** One 72dp circular key — a real target, unlike the bare IconButtons this replaced. */
@Composable
private fun PinKey(
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClick = onClick
            )
            .widthIn(min = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
