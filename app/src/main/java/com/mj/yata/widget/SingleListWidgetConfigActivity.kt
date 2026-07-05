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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import com.mj.yata.domain.model.YataList
import com.mj.yata.domain.repository.YataRepository
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.theme.YataTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Shown automatically by the OS when the "Single list" widget is dragged onto the home screen —
 * lets the user pick which list this particular widget instance pins, before it's placed. */
@AndroidEntryPoint
class SingleListWidgetConfigActivity : ComponentActivity() {

    @Inject lateinit var repository: YataRepository

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If the user backs out without picking a list, the widget host should not place it.
        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val listsState = repository.getLists().stateIn(lifecycleScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

        setContent {
            YataTheme {
                ListPickerScreen(
                    lists = listsState,
                    onSelect = { list -> onListChosen(list.id) }
                )
            }
        }
    }

    private fun onListChosen(listId: String) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@SingleListWidgetConfigActivity).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this@SingleListWidgetConfigActivity, glanceId) { prefs ->
                prefs[SINGLE_LIST_ID_KEY] = listId
            }
            SingleListWidget().update(this@SingleListWidgetConfigActivity, glanceId)

            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(Activity.RESULT_OK, resultValue)
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListPickerScreen(lists: kotlinx.coroutines.flow.StateFlow<List<YataList>>, onSelect: (YataList) -> Unit) {
    val allLists by lists.collectAsState()
    val accents = LocalYataAccents.current

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Choose a list", fontWeight = FontWeight.Bold) })
        }
    ) { innerPadding ->
        if (allLists.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("No lists yet — create one in the app first.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(allLists, key = { it.id }) { list ->
                    val color = accents.getAccent(list.color)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(list) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(list.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}
