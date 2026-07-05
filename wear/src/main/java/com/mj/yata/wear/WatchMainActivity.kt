package com.mj.yata.wear

import android.app.Activity
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.wear.compose.material.rememberScalingLazyListState
import androidx.wear.input.RemoteInputIntentHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val QUICK_ADD_INPUT_KEY = "quick_add_input"

class WatchMainActivity : ComponentActivity() {

    private val quickAddLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("YataWear", "quick add result: resultCode=${result.resultCode} (OK=${Activity.RESULT_OK}) hasData=${result.data != null}")
        val data = result.data
        if (data == null) {
            Log.d("YataWear", "quick add: no data intent, user likely cancelled")
            return@registerForActivityResult
        }
        val results = RemoteInput.getResultsFromIntent(data)
        Log.d("YataWear", "quick add: results bundle=$results keys=${results?.keySet()}")
        val text = results?.getCharSequence(QUICK_ADD_INPUT_KEY)?.toString()
        Log.d("YataWear", "quick add: extracted text='$text'")
        if (!text.isNullOrBlank()) {
            lifecycleScope.launch(Dispatchers.IO) {
                Log.d("YataWear", "quick add: sending to phone")
                WatchMessenger.sendQuickAdd(applicationContext, text)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WatchTaskRepository.load(applicationContext)

        setContent {
            MaterialTheme {
                TaskListScreen(
                    onToggle = { taskId ->
                        WatchTaskRepository.toggleLocally(applicationContext, taskId)
                        lifecycleScope.launch(Dispatchers.IO) { WatchMessenger.sendToggle(applicationContext, taskId) }
                    },
                    onQuickAdd = { launchQuickAdd() }
                )
            }
        }
    }

    private fun launchQuickAdd() {
        val remoteInputs = listOf(
            RemoteInput.Builder(QUICK_ADD_INPUT_KEY)
                .setLabel("Add a task")
                .build()
        )
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, remoteInputs)
        quickAddLauncher.launch(intent)
    }
}

@androidx.compose.runtime.Composable
private fun TaskListScreen(onToggle: (String) -> Unit, onQuickAdd: () -> Unit) {
    val tasks by WatchTaskRepository.tasks.collectAsState()
    val listState = rememberScalingLazyListState()

    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState
        ) {
            item {
                Chip(
                    onClick = onQuickAdd,
                    label = { Text("+ Add task") },
                    colors = ChipDefaults.primaryChipColors()
                )
            }
            if (tasks.isEmpty()) {
                item {
                    Text("Nothing due today")
                }
            } else {
                tasks.forEach { task ->
                    item {
                        ToggleChip(
                            checked = task.done,
                            onCheckedChange = { onToggle(task.id) },
                            label = { Text(task.title, maxLines = 2) },
                            secondaryLabel = task.time?.let { time -> { Text(time) } },
                            toggleControl = { androidx.wear.compose.material.Checkbox(checked = task.done) },
                            colors = ToggleChipDefaults.toggleChipColors()
                        )
                    }
                }
            }
        }
    }
}
