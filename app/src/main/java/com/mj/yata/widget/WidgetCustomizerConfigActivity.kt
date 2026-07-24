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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Inbox
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
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.roundToInt

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
            in appWidgetManager.getAppWidgetIds(android.content.ComponentName(this, TeamOverdueWidgetReceiver::class.java)) -> "TeamOverdueWidgetReceiver"
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
            // Single-List and Quick Add widgets persist their preset destination under different
            // keys (see onConfigSaved below) — reading the wrong one here silently shows "no
            // destination selected" on reconfigure and wipes the saved preset on save.
            val initialSourceId: String
            val initialSourceType: String
            if (providerClass?.endsWith("QuickAddWidgetReceiver") == true) {
                initialSourceId = prefs[QUICK_ADD_TARGET_ID_KEY] ?: ""
                initialSourceType = prefs[QUICK_ADD_TARGET_TYPE_KEY] ?: "list"
            } else {
                initialSourceId = prefs[SINGLE_LIST_ID_KEY] ?: ""
                initialSourceType = prefs[SINGLE_SOURCE_TYPE_KEY] ?: "list"
            }
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
            // Resolving the id and writing prefs is the part that must succeed for the save to
            // count as successful at all — kept in its own try/catch, distinct from the refresh
            // below, so a failure here (and only here) reports "couldn't save" and leaves the
            // result RESULT_CANCELED (the default set in onCreate).
            val glanceId = try {
                GlanceAppWidgetManager(this@WidgetCustomizerConfigActivity).getGlanceIdBy(appWidgetId)
            } catch (e: Exception) {
                Log.e("WidgetConfig", "onConfigSaved: couldn't resolve glanceId for widget $appWidgetId", e)
                showSaveFailedToast()
                finish()
                return@launch
            }
            Log.d("WidgetConfig", "glanceId: $glanceId")

            try {
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
                Log.d("WidgetConfig", "prefs written, forcing update for providerClass=$providerClass")
            } catch (e: Exception) {
                Log.e("WidgetConfig", "onConfigSaved: prefs write failed for widget $appWidgetId (glanceId=$glanceId)", e)
                showSaveFailedToast()
                finish()
                return@launch
            }

            // Settings are durably saved at this point (confirmed above) — a failure past this
            // point only means the on-screen widget is stale until the next unrelated refresh,
            // NOT that the save itself failed, so it gets its own catch: logged for diagnosis,
            // but doesn't block setResult(RESULT_OK)/finish() or show a misleading "couldn't
            // save" toast for something that did in fact save.
            //
            // NonCancellable: this dispatch — and the redraw it triggers — must run to
            // completion even if the Activity is torn down mid-flight (back-button spam,
            // rotation, the system reclaiming it while WidgetRefresher's per-widget Mutex is
            // still queued behind another in-flight update). Without this, lifecycleScope's
            // cancellation on destroy could cut the work off partway through, which is exactly
            // "closed the configure screen but the widget never actually got the update."
            withContext(kotlinx.coroutines.NonCancellable) {
                try {
                    // Force widget update through the same WidgetRefresher path every task-change
                    // refresh already uses (WidgetUpdater), rather than a one-off `SomeWidget().update()`
                    // call — that direct call was found to render stale content on the home screen
                    // until a later unrelated save/refresh caught it up.
                    val updated = when {
                        providerClass?.endsWith("SingleListWidgetReceiver") == true -> { WidgetRefresher.refreshWidget(this@WidgetCustomizerConfigActivity, SingleListWidget::class.java); true }
                        providerClass?.endsWith("QuickAddWidgetReceiver") == true -> { WidgetRefresher.refreshWidget(this@WidgetCustomizerConfigActivity, QuickAddWidget::class.java); true }
                        providerClass?.endsWith("YataAppWidgetReceiver") == true -> { WidgetRefresher.refreshWidget(this@WidgetCustomizerConfigActivity, YataAppWidget::class.java); true }
                        providerClass?.endsWith("UpcomingWidgetReceiver") == true -> { WidgetRefresher.refreshWidget(this@WidgetCustomizerConfigActivity, UpcomingWidget::class.java); true }
                        providerClass?.endsWith("ProgressStatsWidgetReceiver") == true -> { WidgetRefresher.refreshWidget(this@WidgetCustomizerConfigActivity, ProgressStatsWidget::class.java); true }
                        providerClass?.endsWith("TeamOverdueWidgetReceiver") == true -> { WidgetRefresher.refreshWidget(this@WidgetCustomizerConfigActivity, TeamOverdueWidget::class.java); true }
                        else -> false
                    }
                    if (!updated) {
                        Log.w("WidgetConfig", "onConfigSaved: unrecognized providerClass='$providerClass' for widget $appWidgetId — settings saved but no .update() call matched, refreshing every widget type as a fallback")
                        WidgetRefresher.refreshAll(this@WidgetCustomizerConfigActivity)
                    }
                } catch (e: Exception) {
                    Log.e("WidgetConfig", "onConfigSaved: settings saved but widget refresh failed for widget $appWidgetId (providerClass=$providerClass)", e)
                }
            }

            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(Activity.RESULT_OK, resultValue)
            finish()
        }
    }

    private fun showSaveFailedToast() {
        android.widget.Toast.makeText(
            this,
            "Couldn't save widget settings — please try again",
            android.widget.Toast.LENGTH_LONG
        ).show()
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
    // Team Overdue has no per-entity accent to switch between M3/custom colors for (unlike the
    // other widgets' per-list/per-task tint) — the toggle would be a visible no-op, so it's
    // hidden here instead of silently doing nothing after Save.
    val supportsM3Colors = providerClass?.endsWith("TeamOverdueWidgetReceiver") != true

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
        ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Corner Radius Customizer
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Corner Radius", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${radius} dp",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = radius.toFloat(),
                        onValueChange = { radius = it.roundToInt() },
                        valueRange = 2f..30f,
                        // Even steps only (2, 4, 6, ... 30) — matches the old +/- 2 stepper's
                        // granularity, just as an M3 slider with visible tick marks instead.
                        steps = 13,
                        thumb = { ExpressiveSliderThumb() },
                        track = { state -> ExpressiveSliderTrack(state) }
                    )
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

            // M3 Colors Toggle — hidden for widgets that don't actually apply it (see supportsM3Colors)
            if (supportsM3Colors) {
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
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (isSingleList) "Choose Source to Pin" else "Choose Preset Destination (Optional)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        if (selectedSourceId != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onPrimaryContainer))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = selectedSourceName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = selectedSourceType.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                }
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
                            Box(
                                modifier = Modifier.fillMaxWidth().height(120.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No items available in this category.")
                            }
                        } else {
                            // Fixed (not weight()-based) height — this Card sits inside the
                            // outer screen's own scroll, so the list gets its own bounded,
                            // independently-scrollable region instead of competing for
                            // leftover space with every other card on the screen. That
                            // competition used to squeeze this down to near-zero height,
                            // making it look like most items were simply missing.
                            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
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
        } // end scrollable content Column

            // Save button — pinned outside the scrollable region so it's always reachable
            // without hunting for it at the bottom of a long scroll.
            Button(
                onClick = {
                    if (isSingleList && selectedSourceId == null) {
                        // Source is required for SingleListWidget
                        return@Button
                    }
                    onSave(radius, label, useM3, selectedSourceId, selectedSourceType, opacity, accentOverride)
                },
                enabled = !isSingleList || selectedSourceId != null,
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(50.dp)
            ) {
                Text("Save Changes", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** M3 Expressive-style slider thumb — a tall rounded bar instead of the classic round knob. */
@Composable
private fun ExpressiveSliderThumb() {
    Box(
        modifier = Modifier
            .width(6.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.primary)
    )
}

/** M3 Expressive-style slider track — a capsule track split into active/inactive segments with
 * a small gap at the thumb, plus step-tick dots, instead of the classic single continuous bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpressiveSliderTrack(state: SliderState) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.primaryContainer
    val range = state.valueRange.endInclusive - state.valueRange.start
    val fraction = if (range > 0f) ((state.value - state.valueRange.start) / range).coerceIn(0f, 1f) else 0f
    val tickCount = state.steps + 2 // stops including both endpoints

    Box(modifier = Modifier.fillMaxWidth().height(16.dp)) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(fraction.coerceAtLeast(0.0001f))
                    .height(16.dp)
                    .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 2.dp, bottomEnd = 2.dp))
                    .background(activeColor)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .weight((1f - fraction).coerceAtLeast(0.0001f))
                    .height(16.dp)
                    .clip(RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp, topEnd = 8.dp, bottomEnd = 8.dp))
                    .background(inactiveColor)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.Center).padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val onActiveTick = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
            val onInactiveTick = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
            repeat(tickCount) { i ->
                val tickFraction = if (tickCount > 1) i / (tickCount - 1).toFloat() else 0f
                // First/last ticks sit under the rounded track ends — skip them so they don't
                // look like they're floating just outside the capsule.
                if (i == 0 || i == tickCount - 1) {
                    Spacer(modifier = Modifier.size(4.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(if (tickFraction <= fraction) onActiveTick else onInactiveTick)
                    )
                }
            }
        }
    }
}
