package com.mj.yata.widget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.repository.YataRepository
import com.mj.yata.ui.theme.YataTheme
import dagger.hilt.android.AndroidEntryPoint
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

        setContent {
            YataTheme {
                QuickAddDialogContent(
                    targetName = targetName,
                    onSubmit = { title ->
                        lifecycleScope.launch {
                            repository.upsertTask(
                                Task(
                                    id = "t_" + UUID.randomUUID().toString(),
                                    title = title,
                                    listId = if (targetType == "list") targetId else null,
                                    projectId = if (targetType == "project") targetId else null,
                                    section = "Afternoon",
                                    due = LocalDate.now().toString(),
                                    time = null,
                                    reminder = null,
                                    priority = "none",
                                    flag = false,
                                    done = false,
                                    assigneeIds = emptyList(),
                                    tagIds = emptyList(),
                                    recurrence = null,
                                    subtasks = emptyList(),
                                    notes = null
                                )
                            )
                            WidgetRefresher.refreshAll(this@QuickAddDialogActivity)
                            finish()
                        }
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }
}

@Composable
private fun QuickAddDialogContent(
    targetName: String?,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val noRipple = remember { MutableInteractionSource() }

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
