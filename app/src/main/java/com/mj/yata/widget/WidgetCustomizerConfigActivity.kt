package com.mj.yata.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import com.mj.yata.domain.model.Project
import com.mj.yata.domain.model.Tag
import com.mj.yata.domain.model.YataList
import com.mj.yata.domain.repository.YataRepository
import com.mj.yata.ui.theme.ALL_ACCENT_KEYS
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.theme.YataTheme
import com.mj.yata.ui.widgets.SegmentedControl
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private enum class ConfigCategory { LIST, PROJECT, TAG }

@AndroidEntryPoint
class WidgetCustomizerConfigActivity : ComponentActivity() {

    @Inject lateinit var repository: YataRepository

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var providerClass: String? = null

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

        val appWidgetManager = AppWidgetManager.getInstance(this)
        providerClass = when (appWidgetId) {
            in appWidgetManager.getAppWidgetIds(android.content.ComponentName(this, SingleListWidgetReceiver::class.java)) -> "SingleListWidgetReceiver"
            in appWidgetManager.getAppWidgetIds(android.content.ComponentName(this, QuickAddWidgetReceiver::class.java)) -> "QuickAddWidgetReceiver"
            in appWidgetManager.getAppWidgetIds(android.content.ComponentName(this, YataAppWidgetReceiver::class.java)) -> "YataAppWidgetReceiver"
            in appWidgetManager.getAppWidgetIds(android.content.ComponentName(this, UpcomingWidgetReceiver::class.java)) -> "UpcomingWidgetReceiver"
            in appWidgetManager.getAppWidgetIds(android.content.ComponentName(this, ProgressStatsWidgetReceiver::class.java)) -> "ProgressStatsWidgetReceiver"
            else -> {
                val appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
                appWidgetInfo?.provider?.className
            }
        }
        Log.d("WidgetConfig", "Configuring widget $appWidgetId of type $providerClass")

        val listsState = repository.getLists().stateIn(lifecycleScope, SharingStarted.Eagerly, emptyList())
        val projectsState = repository.getProjects().stateIn(lifecycleScope, SharingStarted.Eagerly, emptyList())
        val tagsState = repository.getTags().stateIn(lifecycleScope, SharingStarted.Eagerly, emptyList())

        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@WidgetCustomizerConfigActivity).getGlanceIdBy(appWidgetId)
            val prefs = androidx.glance.appwidget.state.getAppWidgetState(
                this@WidgetCustomizerConfigActivity,
                androidx.glance.state.PreferencesGlanceStateDefinition,
                glanceId
            )

            val initialRadius = prefs[WIDGET_CORNER_RADIUS_KEY] ?: 28
            val initialLabel = prefs[WIDGET_LABEL_KEY] ?: ""
            val initialUseM3 = prefs[WIDGET_USE_M3_COLORS_KEY] ?: false
            val initialSourceId = prefs[SINGLE_LIST_ID_KEY] ?: ""
            val initialSourceType = prefs[SINGLE_SOURCE_TYPE_KEY] ?: "list"
            val initialOpacity = prefs[WIDGET_OPACITY_KEY] ?: 1.0f
            val initialAccentOverride = prefs[WIDGET_ACCENT_OVERRIDE_KEY]

            setContent {
                YataTheme {
                    WidgetCustomizerScreen(
                        providerClass = providerClass,
                        lists = listsState,
                        projects = projectsState,
                        tags = tagsState,
                        initialRadius = initialRadius,
                        initialLabel = initialLabel,
                        initialUseM3 = initialUseM3,
                        initialSourceId = initialSourceId,
                        initialSourceType = initialSourceType,
                        initialOpacity = initialOpacity,
                        initialAccentOverride = initialAccentOverride,
                        onSave = { radius, label, useM3, sourceId, sourceType, opacity, accentOverride ->
                            onConfigSaved(radius, label, useM3, sourceId, sourceType, opacity, accentOverride)
                        }
                    )
                }
            }
        }
    }

    private fun onConfigSaved(
        radius: Int,
        label: String,
        useM3Colors: Boolean,
        sourceId: String?,
        sourceType: String?,
        opacity: Float,
        accentOverride: String?
    ) {
        Log.d("WidgetConfig", "onConfigSaved: radius=$radius, label=$label, useM3=$useM3Colors, sourceId=$sourceId, sourceType=$sourceType, opacity=$opacity, accentOverride=$accentOverride, providerClass=$providerClass")
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@WidgetCustomizerConfigActivity).getGlanceIdBy(appWidgetId)
            Log.d("WidgetConfig", "glanceId: $glanceId")
            updateAppWidgetState(this@WidgetCustomizerConfigActivity, glanceId) { prefs ->
                prefs[WIDGET_CORNER_RADIUS_KEY] = radius
                if (label.isNotBlank()) {
                    prefs[WIDGET_LABEL_KEY] = label
                } else {
                    prefs.remove(WIDGET_LABEL_KEY)
                }
                prefs[WIDGET_USE_M3_COLORS_KEY] = useM3Colors
                prefs[WIDGET_OPACITY_KEY] = opacity
                if (accentOverride != null) {
                    prefs[WIDGET_ACCENT_OVERRIDE_KEY] = accentOverride
                } else {
                    prefs.remove(WIDGET_ACCENT_OVERRIDE_KEY)
                }

                if (providerClass?.endsWith("SingleListWidgetReceiver") == true) {
                    if (sourceId != null && sourceType != null) {
                        prefs[SINGLE_LIST_ID_KEY] = sourceId
                        prefs[SINGLE_SOURCE_TYPE_KEY] = sourceType
                    }
                } else if (providerClass?.endsWith("QuickAddWidgetReceiver") == true) {
                    if (sourceId != null && sourceType != null) {
                        prefs[QUICK_ADD_TARGET_TYPE_KEY] = sourceType
                        prefs[QUICK_ADD_TARGET_ID_KEY] = sourceId
                    } else {
                        prefs.remove(QUICK_ADD_TARGET_TYPE_KEY)
                        prefs.remove(QUICK_ADD_TARGET_ID_KEY)
                    }
                }
            }

            // Force widget update
            when {
                providerClass?.endsWith("SingleListWidgetReceiver") == true -> SingleListWidget().update(this@WidgetCustomizerConfigActivity, glanceId)
                providerClass?.endsWith("QuickAddWidgetReceiver") == true -> QuickAddWidget().update(this@WidgetCustomizerConfigActivity, glanceId)
                providerClass?.endsWith("YataAppWidgetReceiver") == true -> YataAppWidget().update(this@WidgetCustomizerConfigActivity, glanceId)
                providerClass?.endsWith("UpcomingWidgetReceiver") == true -> UpcomingWidget().update(this@WidgetCustomizerConfigActivity, glanceId)
                providerClass?.endsWith("ProgressStatsWidgetReceiver") == true -> ProgressStatsWidget().update(this@WidgetCustomizerConfigActivity, glanceId)
            }

            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(Activity.RESULT_OK, resultValue)
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetCustomizerScreen(
    providerClass: String?,
    lists: StateFlow<List<YataList>>,
    projects: StateFlow<List<Project>>,
    tags: StateFlow<List<Tag>>,
    initialRadius: Int,
    initialLabel: String,
    initialUseM3: Boolean,
    initialSourceId: String,
    initialSourceType: String,
    initialOpacity: Float,
    initialAccentOverride: String?,
    onSave: (Int, String, Boolean, String?, String?, Float, String?) -> Unit
) {
    var radius by remember { mutableStateOf(initialRadius.coerceIn(2, 30)) }
    var label by remember { mutableStateOf(initialLabel) }
    var useM3 by remember { mutableStateOf(initialUseM3) }
    var opacity by remember { mutableStateOf(initialOpacity.coerceIn(0.3f, 1.0f)) }
    var accentOverride by remember { mutableStateOf(initialAccentOverride) }

    var selectedSourceId by remember { mutableStateOf(initialSourceId.takeIf { it.isNotBlank() }) }
    var selectedSourceType by remember { mutableStateOf(initialSourceType) }
    var selectedSourceName by remember { mutableStateOf("") }

    val allLists by lists.collectAsState()
    val allProjects by projects.collectAsState()
    val allTags by tags.collectAsState()

    val isSingleList = providerClass?.endsWith("SingleListWidgetReceiver") == true
    val isQuickAdd = providerClass?.endsWith("QuickAddWidgetReceiver") == true

    var sourceCategory by remember {
        mutableStateOf(
            when (initialSourceType) {
                "project" -> ConfigCategory.PROJECT
                "tag" -> ConfigCategory.TAG
                else -> ConfigCategory.LIST
            }
        )
    }

    LaunchedEffect(selectedSourceId, selectedSourceType, allLists, allProjects, allTags) {
        selectedSourceName = when (selectedSourceType) {
            "project" -> allProjects.find { it.id == selectedSourceId }?.name ?: ""
            "tag" -> allTags.find { it.id == selectedSourceId }?.name ?: ""
            else -> allLists.find { it.id == selectedSourceId }?.name ?: ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Widget Customizer", fontWeight = FontWeight.Bold) })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Corner Radius Customizer
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Corner Radius", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { if (radius > 2) radius -= 2 },
                            enabled = radius > 2
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Decrease")
                        }
                        Text("${radius} dp (px)", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        IconButton(
                            onClick = { if (radius < 30) radius += 2 },
                            enabled = radius < 30
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Increase")
                        }
                    }
                }
            }

            // Custom Label Customizer
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Custom Header Label", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        placeholder = { Text("e.g. Work, Today, Quick Add") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // M3 Colors Toggle
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Use Material 3 Colors", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Themes widget using standard system/M3 colors", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = useM3, onCheckedChange = { useM3 = it })
                }
            }

            // Opacity Customizer
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Opacity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${(opacity * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = opacity,
                        onValueChange = { opacity = it },
                        valueRange = 0.3f..1.0f
                    )
                }
            }

            // Accent Color Override
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Widget Accent Color", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Overrides the app's default accent for this widget only.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        item {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { accentOverride = null },
                                contentAlignment = Alignment.Center
                            ) {
                                if (accentOverride == null) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "App default",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        items(ALL_ACCENT_KEYS) { key ->
                            val color = LocalYataAccents.current.getAccent(key)
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (accentOverride == key) 3.dp else 0.dp,
                                        color = if (accentOverride == key) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { accentOverride = key },
                                contentAlignment = Alignment.Center
                            ) {
                                if (accentOverride == key) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Source Picker for Single List or Quick Add
            if (isSingleList || isQuickAdd) {
                Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                        Text(
                            text = if (isSingleList) "Choose Source to Pin" else "Choose Preset Destination (Optional)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (selectedSourceId != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Selected: $selectedSourceName (${selectedSourceType.uppercase()})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                if (isQuickAdd) {
                                    TextButton(onClick = {
                                        selectedSourceId = null
                                        selectedSourceType = "list"
                                    }) {
                                        Text("Clear Preset")
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        SegmentedControl(
                            items = if (isSingleList) listOf(ConfigCategory.LIST, ConfigCategory.PROJECT, ConfigCategory.TAG) else listOf(ConfigCategory.LIST, ConfigCategory.PROJECT),
                            selectedItem = sourceCategory,
                            onItemSelected = { sourceCategory = it },
                            labelProvider = {
                                when (it) {
                                    ConfigCategory.LIST -> "List"
                                    ConfigCategory.PROJECT -> "Project"
                                    ConfigCategory.TAG -> "Tag"
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val targetItems = when (sourceCategory) {
                            ConfigCategory.LIST -> allLists
                            ConfigCategory.PROJECT -> allProjects
                            ConfigCategory.TAG -> allTags
                        }

                        val nameExtractor: (Any) -> String = { item ->
                            when (item) {
                                is YataList -> item.name
                                is Project -> item.name
                                is Tag -> item.name
                                else -> ""
                            }
                        }

                        val colorExtractor: (Any) -> String = { item ->
                            when (item) {
                                is YataList -> item.color
                                is Project -> item.color
                                is Tag -> item.color
                                else -> ""
                            }
                        }

                        val idExtractor: (Any) -> String = { item ->
                            when (item) {
                                is YataList -> item.id
                                is Project -> item.id
                                is Tag -> item.id
                                else -> ""
                            }
                        }

                        if (targetItems.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No items available in this category.")
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                items(targetItems) { item ->
                                    val itemColor = LocalYataAccents.current.getAccent(colorExtractor(item))
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clickable {
                                                selectedSourceId = idExtractor(item)
                                                selectedSourceType = when (sourceCategory) {
                                                    ConfigCategory.LIST -> "list"
                                                    ConfigCategory.PROJECT -> "project"
                                                    ConfigCategory.TAG -> "tag"
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(itemColor))
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(nameExtractor(item), style = MaterialTheme.typography.bodyLarge)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Save button
            Button(
                onClick = {
                    if (isSingleList && selectedSourceId == null) {
                        // Source is required for SingleListWidget
                        return@Button
                    }
                    onSave(radius, label, useM3, selectedSourceId, selectedSourceType, opacity, accentOverride)
                },
                enabled = !isSingleList || selectedSourceId != null,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Save Changes", fontWeight = FontWeight.Bold)
            }
        }
    }
}
