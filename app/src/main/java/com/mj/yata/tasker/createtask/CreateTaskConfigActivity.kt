package com.mj.yata.tasker.createtask

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelperNoOutput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.mj.yata.R
import com.mj.yata.ui.theme.YataTheme
import com.mj.yata.ui.widgets.YataCompactFieldShape
import com.mj.yata.ui.widgets.YataFieldShape
import com.mj.yata.ui.widgets.yataFieldColors

class CreateTaskConfigHelper(config: TaskerPluginConfig<CreateTaskInput>) :
    TaskerPluginConfigHelperNoOutput<CreateTaskInput, CreateTaskRunner>(config) {
    override val runnerClass = CreateTaskRunner::class.java
    override val inputClass = CreateTaskInput::class.java

    override fun addToStringBlurb(input: TaskerInput<CreateTaskInput>, blurbBuilder: StringBuilder) {
        val title = input.regular.title?.takeIf { it.isNotBlank() } ?: "(untitled)"
        blurbBuilder.append("Create task: $title")
    }
}

class CreateTaskConfigActivity : ComponentActivity(), TaskerPluginConfig<CreateTaskInput> {

    override val context get() = applicationContext
    private val taskerHelper by lazy { CreateTaskConfigHelper(this) }

    // Lifted to Activity-level state (rather than local vals inside the composable) so
    // `inputForTasker` — read from `finishForTasker()`, called via a Compose click handler —
    // can see the latest values.
    private var title by mutableStateOf<String?>(null)
    private var project by mutableStateOf<String?>(null)
    private var list by mutableStateOf<String?>(null)
    private var tags by mutableStateOf<String?>(null)
    private var assignees by mutableStateOf<String?>(null)
    private var due by mutableStateOf<String?>(null)
    private var time by mutableStateOf<String?>(null)
    private var reminder by mutableStateOf<String?>(null)
    private var priority by mutableStateOf<String?>(null)
    private var section by mutableStateOf<String?>(null)
    private var repeat by mutableStateOf<String?>(null)
    private var notes by mutableStateOf<String?>(null)

    override fun assignFromInput(input: TaskerInput<CreateTaskInput>) {
        val regular = input.regular
        title = regular.title
        project = regular.project
        list = regular.list
        tags = regular.tags
        assignees = regular.assignees
        due = regular.due
        time = regular.time
        reminder = regular.reminder
        priority = regular.priority
        section = regular.section
        repeat = regular.repeat
        notes = regular.notes
    }

    override val inputForTasker: TaskerInput<CreateTaskInput>
        get() = TaskerInput(
            CreateTaskInput(title, project, list, tags, assignees, due, time, reminder, priority, section, repeat, notes)
        )

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        taskerHelper.onCreate()

        setContent {
            YataTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.create_task_config_create_task), fontWeight = FontWeight.Bold) },
                            actions = {
                                IconButton(onClick = { taskerHelper.finishForTasker() }) {
                                    Icon(Icons.Default.Check, contentDescription = stringResource(R.string.action_save))
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.tasker_create_task_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LabeledField("Title *", title) { title = it }
                        LabeledField("Project (name)", project) { project = it }
                        LabeledField("List (name)", list) { list = it }
                        LabeledField("Tags (comma-separated)", tags) { tags = it }
                        LabeledField("Assign to (comma-separated names)", assignees) { assignees = it }
                        LabeledField("Due (e.g. 2026-07-10, tomorrow, next monday)", due) { due = it }
                        LabeledField("Time (e.g. 3:00 PM, 15:00, evening)", time) { time = it }
                        LabeledField("Reminder (e.g. 15 min before)", reminder) { reminder = it }
                        LabeledField("Priority (none, low, med, high)", priority) { priority = it }
                        LabeledField("Section (Morning or Afternoon)", section) { section = it }
                        LabeledField("Repeat (e.g. daily, every monday, every 2 weeks)", repeat) { repeat = it }
                        LabeledField("Notes", notes, singleLine = false) { notes = it }
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledField(label: String, value: String?, singleLine: Boolean = true, onValueChange: (String) -> Unit) {
    TextField(
        value = value ?: "",
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        shape = if (singleLine) YataCompactFieldShape else YataFieldShape,
        colors = yataFieldColors(),
        modifier = Modifier.fillMaxWidth()
    )
}
