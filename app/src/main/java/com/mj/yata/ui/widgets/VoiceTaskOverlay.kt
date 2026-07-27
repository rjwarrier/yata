package com.mj.yata.ui.widgets

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mj.yata.R
import com.mj.yata.data.voice.OnDeviceVoiceRecognizer
import com.mj.yata.data.voice.VoiceState
import com.mj.yata.util.NaturalLanguageParser
import com.mj.yata.util.ParsedQuickAdd
import kotlin.math.PI
import kotlin.math.sin

/**
 * 100% On-device Local Voice Task Creation sheet featuring real-time audio waveform animation,
 * live transcript parsing into NaturalLanguageParser entity chips, and instant one-tap creation.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VoiceTaskOverlay(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onTaskRecognized: (ParsedQuickAdd) -> Unit,
    modifier: Modifier = Modifier,
    voiceLanguage: String = "default"
) {
    if (!isOpen) return

    val context = LocalContext.current
    val voiceRecognizer = remember(context) { OnDeviceVoiceRecognizer(context) }
    val voiceState by voiceRecognizer.state.collectAsStateWithLifecycle()
    val rmsDb by voiceRecognizer.rmsDb.collectAsStateWithLifecycle()

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            voiceRecognizer.startListening(language = voiceLanguage)
        }
    }

    LaunchedEffect(isOpen) {
        if (isOpen) {
            if (hasMicPermission) {
                voiceRecognizer.startListening(language = voiceLanguage)
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceRecognizer.stopListening()
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = {
            voiceRecognizer.stopListening()
            onDismiss()
        },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val infinitePulse = rememberInfiniteTransition(label = "headerPulse")
                    val pulseScale by infinitePulse.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.7f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulseScale"
                    )
                    val pulseAlpha by infinitePulse.animateFloat(
                        initialValue = 0.8f,
                        targetValue = 0.15f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulseAlpha"
                    )

                    Box(contentAlignment = Alignment.Center) {
                        if (voiceState is VoiceState.Listening || voiceState is VoiceState.Speaking) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha))
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    if (voiceState is VoiceState.Listening || voiceState is VoiceState.Speaking)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.outline
                                )
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ON-DEVICE VOICE INPUT",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        voiceRecognizer.stopListening()
                        onDismiss()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Audio Waveform Animation Canvas
            AudioWaveformCanvas(
                amplitude = rmsDb,
                isListening = voiceState is VoiceState.Listening || voiceState is VoiceState.Speaking,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

    var manualText by remember { mutableStateOf("") }
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull() ?: ""
            if (spoken.isNotBlank()) {
                manualText = spoken
            }
        }
    }

    val launchSystemDictation = {
        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak your task")
        }
        try {
            speechLauncher.launch(intent)
        } catch (_: Exception) {}
    }

    if (!hasMicPermission) {
        Text(
            text = "Microphone permission required for voice task creation.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp)
        )
    } else when (val state = voiceState) {
        is VoiceState.Listening -> {
            Text(
                text = "Listening... Speak your task naturally\n(e.g., 'Buy groceries tomorrow at 5pm')",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        is VoiceState.Error -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.TextButton(onClick = { launchSystemDictation() }) {
                    Text(stringResource(R.string.voice_task_overlay_tap_to_use_system_voice_dialog))
                }
            }
        }

        else -> {}
    }

    val currentText = remember(voiceState, manualText) {
        if (manualText.isNotBlank()) {
            manualText
        } else {
            when (val state = voiceState) {
                is VoiceState.Speaking -> state.partialText
                is VoiceState.FinalResult -> state.text
                else -> ""
            }
        }
    }

    val parsedInfo = remember(currentText) {
        if (currentText.isNotBlank()) NaturalLanguageParser.parse(currentText) else null
    }

    if (currentText.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = currentText,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (parsedInfo != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                parsedInfo.due?.let { dueDate ->
                                    VoiceChip(
                                        label = "Due: ${dueDate}${parsedInfo.time?.let { " $it" } ?: ""}",
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                parsedInfo.priority?.let { priority ->
                                    VoiceChip(
                                        label = "Priority: ${priority.uppercase()}",
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                                if (parsedInfo.flag) {
                                    VoiceChip(
                                        label = "Flagged",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                parsedInfo.projectName?.let { proj ->
                                    VoiceChip(
                                        label = "Project: $proj",
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                parsedInfo.listName?.let { list ->
                                    VoiceChip(
                                        label = "List: $list",
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                parsedInfo.tagNames.forEach { tag ->
                                    VoiceChip(
                                        label = "#$tag",
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                parsedInfo.assigneeNames.forEach { assignee ->
                                    VoiceChip(
                                        label = "@$assignee",
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { launchSystemDictation() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.voice_task_overlay_speak_again),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                val canCreate = parsedInfo != null && parsedInfo.title.isNotBlank()
                val buttonBg = if (canCreate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                val buttonContentColor = if (canCreate) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

                PressableScaleBox(
                    onClick = {
                        if (parsedInfo != null && parsedInfo.title.isNotBlank()) {
                            voiceRecognizer.stopListening()
                            onTaskRecognized(parsedInfo)
                            onDismiss()
                        }
                    },
                    enabled = canCreate,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = buttonBg,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = buttonContentColor
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Create Task",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = buttonContentColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun VoiceChip(label: String, color: Color) {
    AnimatedVisibility(
        visible = true,
        enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) + fadeIn(),
        exit = scaleOut() + fadeOut()
    ) {
        Surface(
            color = color.copy(alpha = 0.15f),
            contentColor = color,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun AudioWaveformCanvas(
    amplitude: Float,
    isListening: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedAmplitude by animateFloatAsState(
        targetValue = if (isListening) (0.22f + amplitude * 0.78f).coerceIn(0.08f, 1.0f) else 0.04f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "animatedAmplitude"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.tertiary
    val auraColor = MaterialTheme.colorScheme.secondary

    val infiniteTransition = rememberInfiniteTransition(label = "waveformWave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val maxAmp = (height / 2f - 6.dp.toPx()) * animatedAmplitude

        val path1 = Path()
        val path2 = Path()
        val path3 = Path()

        var x = 0f
        val step = 3f
        var first = true

        while (x <= width) {
            val normX = x / width
            val envelope = sin(normX * PI.toFloat()) // Smooth fade at edges
            val y1 = centerY + (sin(normX * 4 * PI.toFloat() + phase) * maxAmp * envelope)
            val y2 = centerY + (sin(normX * 3 * PI.toFloat() - phase * 0.8f) * maxAmp * 0.75f * envelope)
            val y3 = centerY + (sin(normX * 5 * PI.toFloat() + phase * 1.3f) * maxAmp * 0.45f * envelope)

            if (first) {
                path1.moveTo(x, y1)
                path2.moveTo(x, y2)
                path3.moveTo(x, y3)
                first = false
            } else {
                path1.lineTo(x, y1)
                path2.lineTo(x, y2)
                path3.lineTo(x, y3)
            }
            x += step
        }

        // Layer 3: Soft ambient glow wave
        drawPath(
            path = path3,
            color = auraColor.copy(alpha = 0.25f),
            style = Stroke(width = 4.dp.toPx())
        )

        // Layer 2: Tertiary accent wave
        drawPath(
            path = path2,
            color = secondaryColor.copy(alpha = 0.5f),
            style = Stroke(width = 2.5.dp.toPx())
        )

        // Layer 1: Primary wave
        drawPath(
            path = path1,
            color = primaryColor,
            style = Stroke(width = 3.5.dp.toPx())
        )
    }
}
