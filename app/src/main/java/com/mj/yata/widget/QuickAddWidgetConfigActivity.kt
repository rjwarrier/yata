package com.mj.yata.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import com.mj.yata.domain.model.Project
import com.mj.yata.domain.model.YataList
import com.mj.yata.domain.repository.YataRepository
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.theme.YataTheme
import com.mj.yata.ui.widgets.SegmentedControl
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Shown when the Quick Add widget is dragged onto the home screen — optionally presets a
 * project or list every task created via this widget instance drops straight into, instead of
 * asking every time. "No preset" skips this and adds to the Inbox (no list/project) as before. */
@AndroidEntryPoint
class QuickAddWidgetConfigActivity : ComponentActivity() {

    @Inject lateinit var repository: YataRepository

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val listsState = repository.getLists().stateIn(lifecycleScope, SharingStarted.Eagerly, emptyList())
        val projectsState = repository.getProjects().stateIn(lifecycleScope, SharingStarted.Eagerly, emptyList())

        setContent {
            YataTheme {
                QuickAddTargetPickerScreen(
                    lists = listsState,
                    projects = projectsState,
                    onSelectNone = { onTargetChosen(null, null) },
                    onSelectList = { list -> onTargetChosen("list", list.id) },
                    onSelectProject = { project -> onTargetChosen("project", project.id) }
                )
            }
        }
    }

    private fun onTargetChosen(targetType: String?, targetId: String?) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@QuickAddWidgetConfigActivity).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this@QuickAddWidgetConfigActivity, glanceId) { prefs ->
                if (targetType != null && targetId != null) {
                    prefs[QUICK_ADD_TARGET_TYPE_KEY] = targetType
                    prefs[QUICK_ADD_TARGET_ID_KEY] = targetId
                } else {
                    prefs.remove(QUICK_ADD_TARGET_TYPE_KEY)
                    prefs.remove(QUICK_ADD_TARGET_ID_KEY)
                }
            }
            QuickAddWidget().update(this@QuickAddWidgetConfigActivity, glanceId)

            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(Activity.RESULT_OK, resultValue)
            finish()
        }
    }
}

private enum class QuickAddCategory { LIST, PROJECT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddTargetPickerScreen(
    lists: StateFlow<List<YataList>>,
    projects: StateFlow<List<Project>>,
    onSelectNone: () -> Unit,
    onSelectList: (YataList) -> Unit,
    onSelectProject: (Project) -> Unit
) {
    var category by remember { mutableStateOf(QuickAddCategory.LIST) }
    val allLists by lists.collectAsState()
    val allProjects by projects.collectAsState()
    val accents = LocalYataAccents.current

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Preset a project/list (optional)", fontWeight = FontWeight.Bold) })
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clickable { onSelectNone() }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Inbox, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("No preset", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Tasks go to the Inbox, same as before",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            SegmentedControl(
                items = listOf(QuickAddCategory.LIST, QuickAddCategory.PROJECT),
                selectedItem = category,
                onItemSelected = { category = it },
                labelProvider = { if (it == QuickAddCategory.LIST) "List" else "Project" },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            when (category) {
                QuickAddCategory.LIST -> QuickAddTargetList(
                    items = allLists,
                    emptyMessage = "No lists yet — create one in the app first.",
                    name = { it.name },
                    colorKey = { it.color },
                    onSelect = onSelectList
                )
                QuickAddCategory.PROJECT -> QuickAddTargetList(
                    items = allProjects,
                    emptyMessage = "No projects yet — create one in the app first.",
                    name = { it.name },
                    colorKey = { it.color },
                    onSelect = onSelectProject
                )
            }
        }
    }
}

@Composable
private fun <T> QuickAddTargetList(
    items: List<T>,
    emptyMessage: String,
    name: (T) -> String,
    colorKey: (T) -> String,
    onSelect: (T) -> Unit
) {
    val accents = LocalYataAccents.current
    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(items) { item ->
                val color = accents.getAccent(colorKey(item))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(item) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(name(item), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
