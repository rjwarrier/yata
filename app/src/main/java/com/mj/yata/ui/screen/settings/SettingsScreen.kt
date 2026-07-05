package com.mj.yata.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.domain.model.AppFont
import com.mj.yata.domain.model.ThemeMode
import com.mj.yata.domain.model.YataList
import com.mj.yata.ui.screen.main.MainViewModel
import com.mj.yata.ui.widgets.SegmentedControl
import com.mj.yata.ui.widgets.YataTimePickerLauncher
import com.mj.yata.util.TaskScheduleUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onExportRequested: () -> Unit,
    onImportRequested: () -> Unit,
    onNavigateToTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val appFont by viewModel.appFont.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val userEmail by viewModel.userEmail.collectAsState()
    val defaultListId by viewModel.defaultListId.collectAsState()
    val startOfWeekSunday by viewModel.startOfWeekSunday.collectAsState()
    val defaultReminderHour by viewModel.defaultReminderHour.collectAsState()
    val defaultReminderMinute by viewModel.defaultReminderMinute.collectAsState()
    val uiScale by viewModel.uiScale.collectAsState()
    val dynamicColorEnabled by viewModel.dynamicColorEnabled.collectAsState()
    val peopleFeatureEnabled by viewModel.peopleFeatureEnabled.collectAsState()
    val tagsFeatureEnabled by viewModel.tagsFeatureEnabled.collectAsState()
    val projectsFeatureEnabled by viewModel.projectsFeatureEnabled.collectAsState()
    val lists by viewModel.lists.collectAsState()

    var showDefaultListMenu by remember { mutableStateOf(false) }
    var showReminderTimePicker by remember { mutableStateOf(false) }

    var editingName by remember { mutableStateOf(false) }
    var tempName by remember { mutableStateOf(userName) }

    var editingEmail by remember { mutableStateOf(false) }
    var tempEmail by remember { mutableStateOf(userEmail) }

    val todayBadgeCount by viewModel.todayRemainingCount.collectAsState()

    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var isDeletingAll by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            com.mj.yata.ui.screen.main.CustomBottomNav(
                selectedTab = -1,
                todayBadgeCount = todayBadgeCount,
                peopleEnabled = peopleFeatureEnabled,
                tagsEnabled = tagsFeatureEnabled,
                projectsEnabled = projectsFeatureEnabled,
                onTabSelected = onNavigateToTab
            )
        },
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Profile Section
            Text(
                text = "PROFILE",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    com.mj.yata.ui.widgets.PersonAvatar(
                        initials = userName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase(),
                        accentKey = "accentC",
                        size = 48.dp
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (editingName) {
                            OutlinedTextField(
                                value = tempName,
                                onValueChange = { tempName = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                trailingIcon = {
                                    IconButton(onClick = {
                                        viewModel.setUserName(tempName)
                                        editingName = false
                                    }) {
                                        Icon(Icons.Default.Check, contentDescription = "Save name")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = userName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.clickable {
                                    tempName = userName
                                    editingName = true
                                }
                            )
                        }

                        if (editingEmail) {
                            OutlinedTextField(
                                value = tempEmail,
                                onValueChange = { tempEmail = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall,
                                trailingIcon = {
                                    IconButton(onClick = {
                                        viewModel.setUserEmail(tempEmail)
                                        editingEmail = false
                                    }) {
                                        Icon(Icons.Default.Check, contentDescription = "Save email")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = userEmail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable {
                                    tempEmail = userEmail
                                    editingEmail = true
                                }
                            )
                        }
                    }
                }
            }

            // 2. Preferences Section
            Text(
                text = "PREFERENCES",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Theme SegmentedControl
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Theme mode",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        SegmentedControl(
                            items = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK),
                            selectedItem = themeMode,
                            onItemSelected = { viewModel.setThemeMode(it) },
                            labelProvider = { it.name }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Font SegmentedControl
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Font",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        SegmentedControl(
                            items = listOf(AppFont.INTER, AppFont.JETBRAINS_MONO),
                            selectedItem = appFont,
                            onItemSelected = { viewModel.setAppFont(it) },
                            labelProvider = { if (it == AppFont.INTER) "Inter" else "JetBrains Mono" }
                        )
                    }

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Material You colors",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    text = "Theme the app from your wallpaper's colors instead of the fixed accent theme.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = dynamicColorEnabled,
                                onCheckedChange = { viewModel.setDynamicColorEnabled(it) }
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Start of week
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Start week on Sunday",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = "App agendas will start on Sunday instead of Monday.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = startOfWeekSunday,
                            onCheckedChange = { viewModel.setStartOfWeekSunday(it) }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Default list for new tasks
                    Box {
                        SettingsRow(
                            label = "Default list for new tasks",
                            value = lists.find { it.id == defaultListId }?.name ?: "None",
                            onClick = { showDefaultListMenu = true }
                        )
                        DropdownMenu(expanded = showDefaultListMenu, onDismissRequest = { showDefaultListMenu = false }) {
                            lists.forEach { list ->
                                DropdownMenuItem(
                                    text = { Text(list.name) },
                                    onClick = {
                                        viewModel.setDefaultListId(list.id)
                                        showDefaultListMenu = false
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Default reminder time
                    SettingsRow(
                        label = "Default reminder time",
                        value = TaskScheduleUtils.formatTime(defaultReminderHour, defaultReminderMinute),
                        onClick = { showReminderTimePicker = true }
                    )
                }
            }

            // Features Section — hides the entire tab/pickers/chips for a feature, but never
            // touches stored data, so re-enabling shows everything exactly as it was.
            Text(
                text = "FEATURES",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FeatureToggleRow(
                        title = "Projects",
                        checked = projectsFeatureEnabled,
                        onCheckedChange = { viewModel.setProjectsFeatureEnabled(it) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    FeatureToggleRow(
                        title = "People",
                        checked = peopleFeatureEnabled,
                        onCheckedChange = { viewModel.setPeopleFeatureEnabled(it) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    FeatureToggleRow(
                        title = "Tags",
                        checked = tagsFeatureEnabled,
                        onCheckedChange = { viewModel.setTagsFeatureEnabled(it) }
                    )
                }
            }

            // Manage Section — tap-through to the People/Tags/Projects tabs, per handoff's Settings "Manage" rows.
            if (projectsFeatureEnabled || peopleFeatureEnabled || tagsFeatureEnabled) {
                Text(
                    text = "MANAGE",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        val manageRows = buildList {
                            if (projectsFeatureEnabled) add(Triple("Projects", Icons.Default.ChevronRight, 1))
                            if (peopleFeatureEnabled) add(Triple("People", Icons.Default.ChevronRight, 2))
                            if (tagsFeatureEnabled) add(Triple("Tags", Icons.Default.ChevronRight, 3))
                        }
                        manageRows.forEachIndexed { index, (title, _, tabIndex) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToTab(tabIndex) }
                                    .padding(vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                )
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                            if (index != manageRows.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            }
                        }
                    }
                }
            }

            // 3. Display Section
            Text(
                text = "DISPLAY",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "UI size",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Scales text and elements across the whole app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    var sliderPosition by remember(uiScale) { mutableFloatStateOf(uiScale) }
                    val presets = listOf("Small" to 0.85f, "Normal" to 1.0f, "Large" to 1.3f)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aa",
                            fontSize = (28 * sliderPosition).sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Slider(
                        value = sliderPosition,
                        onValueChange = { sliderPosition = it },
                        onValueChangeFinished = { viewModel.setUiScale(sliderPosition) },
                        valueRange = 0.85f..1.3f
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        presets.forEach { (label, value) ->
                            TextButton(onClick = {
                                sliderPosition = value
                                viewModel.setUiScale(value)
                            }) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 4. Backup/Data Section
            Text(
                text = "BACKUP & DATA",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onExportRequested() }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "Export data",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Backup YATA to File",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Exports all tasks, folders, and settings to a JSON file.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onImportRequested() }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Import data",
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Restore YATA from File",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Restore tasks and configuration from a previous backup file.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isDeletingAll) { showDeleteAllDialog = true }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = "Delete all data",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Delete All Data",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Backs up to Downloads automatically, then erases everything on this device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isDeletingAll) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Delete all data?") },
            text = {
                Text(
                    "This backs up everything to your Downloads folder first, then permanently " +
                        "erases all tasks, projects, people, and tags from this device. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteAllDialog = false
                    isDeletingAll = true
                    viewModel.backupThenDeleteAllData { filename ->
                        isDeletingAll = false
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (filename != null) "Backed up to Downloads/$filename, then deleted all data."
                                else "Backup failed — nothing was deleted."
                            )
                        }
                    }
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) { Text("Cancel") }
            }
        )
    }

    YataTimePickerLauncher(
        show = showReminderTimePicker,
        initialTime = TaskScheduleUtils.formatTime(defaultReminderHour, defaultReminderMinute),
        onDismiss = { showReminderTimePicker = false },
        onConfirm = { formatted ->
            val parsed = TaskScheduleUtils.parseTime(formatted)
            if (parsed != null) {
                viewModel.setDefaultReminderTime(parsed.hour, parsed.minute)
            }
            showReminderTimePicker = false
        }
    )
}

@Composable
private fun FeatureToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
            )
            Text(
                text = "Your data is kept even when hidden.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingsRow(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Edit",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}
