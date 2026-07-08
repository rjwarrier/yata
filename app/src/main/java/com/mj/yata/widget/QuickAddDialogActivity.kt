package com.mj.yata.widget

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.mj.yata.MainActivity
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.repository.YataRepository
import com.mj.yata.ui.theme.YataTheme
import com.mj.yata.util.NaturalLanguageParser
import com.mj.yata.util.findSimilarTask
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/**
 * Lightweight overlay dialog for the Quick Add widget — creating a one-off task shouldn't
 * require opening the whole app. Reads the target list/project this widget instance (or its
 * per-list chip) points at and drops the new task straight into it.
 */
@AndroidEntryPoint
class QuickAddDialogActivity : ComponentActivity() {

    @Inject lateinit var repository: YataRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetType = intent?.getStringExtra("target_type")?.takeIf { it.isNotBlank() }
        val targetId = intent?.getStringExtra("target_id")?.takeIf { it.isNotBlank() }
        val targetName = intent?.getStringExtra("target_name")?.takeIf { it.isNotBlank() }

        // Shared text (share sheet, or a long-pressed text selection) seeds the title — first
        // line goes through the same NaturalLanguageParser used for in-app typing and Tasker's
        // external input (CreateTaskRunner), remaining lines become the task's notes.
        val sharedText = when (intent?.action) {
            Intent.ACTION_SEND -> intent?.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT -> intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }?.trim()?.takeIf { it.isNotBlank() }
        val sharedLines = sharedText?.lines()?.filter { it.isNotBlank() }
        val sharedFirstLine = sharedLines?.firstOrNull()
        val sharedNotes = sharedLines?.drop(1)?.joinToString("\n")?.takeIf { it.isNotBlank() }
        val parsedShared = sharedFirstLine?.let { NaturalLanguageParser.parse(it) }

        setContent {
            YataTheme {
                var pendingDuplicate by remember { mutableStateOf<Task?>(null) }
                var pendingTitle by remember { mutableStateOf("") }

                fun createTask(title: String) {
                    lifecycleScope.launch {
                        repository.upsertTask(
                            Task(
                                id = "t_" + UUID.randomUUID().toString(),
                                title = title,
                                listId = if (targetType == "list") targetId else null,
                                projectId = if (targetType == "project") targetId else null,
                                section = "Afternoon",
                                due = parsedShared?.due ?: LocalDate.now().toString(),
                                time = parsedShared?.time,
                                reminder = null,
                                priority = "none",
                                flag = false,
                                done = false,
                                assigneeIds = emptyList(),
                                tagIds = emptyList(),
                                recurrence = parsedShared?.recurrence,
                                subtasks = emptyList(),
                                notes = sharedNotes
                            )
                        )
                        WidgetRefresher.refreshAll(this@QuickAddDialogActivity)
                        finish()
                    }
                }

                QuickAddDialogContent(
                    targetName = targetName,
                    initialTitle = parsedShared?.title?.takeIf { it.isNotBlank() } ?: sharedFirstLine.orEmpty(),
                    onSubmit = { title ->
                        lifecycleScope.launch {
                            val duplicate = findSimilarTask(title, repository.getTasks().first())
                            if (duplicate != null) {
                                pendingTitle = title
                                pendingDuplicate = duplicate
                            } else {
                                createTask(title)
                            }
                        }
                    },
                    onDismiss = { finish() }
                )

                pendingDuplicate?.let { existing ->
                    AlertDialog(
                        onDismissRequest = { pendingDuplicate = null },
                        title = { Text("Similar task already exists") },
                        text = { Text("\"${existing.title}\" looks like a match for what you're creating.") },
                        confirmButton = {
                            TextButton(onClick = {
                                val title = pendingTitle
                                pendingDuplicate = null
                                createTask(title)
                            }) {
                                Text("Ignore and create")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                val existingId = existing.id
                                pendingDuplicate = null
                                startActivity(
                                    Intent(this@QuickAddDialogActivity, MainActivity::class.java).apply {
                                        putExtra("navigate_to", "task_detail")
                                        putExtra("task_id", existingId)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    }
                                )
                                finish()
                            }) {
                                Text("Go to existing task")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickAddDialogContent(
    targetName: String?,
    initialTitle: String = "",
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    val focusRequester = remember { FocusRequester() }
    val noRipple = remember { MutableInteractionSource() }
    val context = LocalContext.current

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
            if (!spoken.isNullOrBlank()) {
                title = if (title.isBlank()) spoken else title.trimEnd() + " " + spoken
            }
        }
    }
    val startVoiceInput = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your task")
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            speechLauncher.launch(intent)
        } else {
            android.widget.Toast.makeText(context, "Voice input isn't available on this device.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: IllegalStateException) {
            // Not yet attached — skip autofocus rather than crash.
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(indication = null, interactionSource = noRipple) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth()
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { /* swallow — don't dismiss */ },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Quick add", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                if (targetName != null) {
                    Text(
                        text = "Adding to $targetName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("What needs doing?") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { if (title.isNotBlank()) onSubmit(title.trim()) }
                    ),
                    trailingIcon = {
                        IconButton(onClick = startVoiceInput) {
                            Icon(Icons.Default.Mic, contentDescription = "Add task by voice")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { if (title.isNotBlank()) onSubmit(title.trim()) },
                        enabled = title.isNotBlank()
                    ) {
                        Text("Add")
                    }
                }
            }
        }
    }
}
