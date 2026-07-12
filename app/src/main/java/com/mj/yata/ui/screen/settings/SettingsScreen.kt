package com.mj.yata.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.data.cloud.CloudBackupError
import com.mj.yata.data.cloud.isCloudBackupStale
import com.mj.yata.domain.model.AppFont
import com.mj.yata.domain.model.FabPosition
import com.mj.yata.domain.model.TaskRowDensity
import com.mj.yata.domain.model.ThemeMode
import com.mj.yata.domain.model.YataList
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.YataEase
import com.mj.yata.notification.NotificationPermissionUtils
import com.mj.yata.ui.screen.main.MainViewModel
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.widgets.CircularImageCropper
import com.mj.yata.ui.widgets.SegmentedControl
import com.mj.yata.ui.widgets.YataTimePickerLauncher
import com.mj.yata.util.ProfilePhotoUtils
import com.mj.yata.util.TaskScheduleUtils
import kotlinx.coroutines.launch

/** One-line-each feature reference shown in the Help & About card — kept short on purpose;
 * the full walkthrough with more detail lives in the "Show Welcome Tour" replay above. */
private val helpFeatures = listOf(
    "Today & Upcoming" to "What's due now, a week/month calendar, and a Next 10 Days list.",
    "Quick Add" to "Type naturally — \"call Priya tomorrow 3pm high priority\".",
    "Projects & Lists" to "Group related tasks and star favorites for quick drawer access.",
    "People" to "Assign work and see who's overdue, at a glance.",
    "Tags" to "Flexible labels that cut across projects and lists.",
    "Analytics" to "Completion streaks, on-time rate, and workload breakdowns.",
    "Home Widgets & Wear OS" to "Agenda, quick add, and progress widgets; today's count on your watch.",
    "Reminders" to "Per-task alerts that still fire after a reboot.",
    "Backup & Export" to "Automatic Google Drive backup, plus JSON/.ics/Markdown export.",
    "Trash" to "Deleted tasks are recoverable for 30 days before they're gone for good."
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onExportRequested: () -> Unit,
    onImportRequested: () -> Unit,
    onExportIcsRequested: () -> Unit,
    onCloudSignInRequested: () -> Unit,
    onNavigateToTab: (Int) -> Unit,
    onNavigateToTrash: () -> Unit,
    onNavigateToWelcome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val appFont by viewModel.appFont.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val userEmail by viewModel.userEmail.collectAsState()
    val userPhotoUri by viewModel.userPhotoUri.collectAsState()
    val defaultListId by viewModel.defaultListId.collectAsState()
    val startOfWeekSunday by viewModel.startOfWeekSunday.collectAsState()
    val defaultReminderHour by viewModel.defaultReminderHour.collectAsState()
    val defaultReminderMinute by viewModel.defaultReminderMinute.collectAsState()
    val themeScheduleStartHour by viewModel.themeScheduleStartHour.collectAsState()
    val themeScheduleStartMinute by viewModel.themeScheduleStartMinute.collectAsState()
    val themeScheduleEndHour by viewModel.themeScheduleEndHour.collectAsState()
    val themeScheduleEndMinute by viewModel.themeScheduleEndMinute.collectAsState()
    val reduceMotionEnabled by viewModel.reduceMotionEnabled.collectAsState()
    val textScale by viewModel.textScale.collectAsState()
    val taskRowDensity by viewModel.taskRowDensity.collectAsState()
    val hapticsEnabled by viewModel.hapticsEnabled.collectAsState()
    val todayTabEnabled by viewModel.todayTabEnabled.collectAsState()
    val upcomingTabEnabled by viewModel.upcomingTabEnabled.collectAsState()
    val fabPosition by viewModel.fabPosition.collectAsState()
    val uiScale by viewModel.uiScale.collectAsState()
    val dynamicColorEnabled by viewModel.dynamicColorEnabled.collectAsState()
    val peopleFeatureEnabled by viewModel.peopleFeatureEnabled.collectAsState()
    val tagsFeatureEnabled by viewModel.tagsFeatureEnabled.collectAsState()
    val projectsFeatureEnabled by viewModel.projectsFeatureEnabled.collectAsState()
    val lists by viewModel.lists.collectAsState()
    val cloudBackupEnabled by viewModel.cloudBackupEnabled.collectAsState()
    val cloudBackupAccountEmail by viewModel.cloudBackupAccountEmail.collectAsState()
    val cloudBackupLastAt by viewModel.cloudBackupLastAt.collectAsState()
    val cloudBackupWifiOnly by viewModel.cloudBackupWifiOnly.collectAsState()
    val cloudBackupIntervalMinutes by viewModel.cloudBackupIntervalMinutes.collectAsState()
    val cloudBackupArchiveMonths by viewModel.cloudBackupArchiveMonths.collectAsState()

    var showDefaultListMenu by remember { mutableStateOf(false) }
    var showReminderTimePicker by remember { mutableStateOf(false) }
    var showThemeScheduleStartPicker by remember { mutableStateOf(false) }
    var showThemeScheduleEndPicker by remember { mutableStateOf(false) }

    var editingName by remember { mutableStateOf(false) }
    var tempName by remember { mutableStateOf(userName) }

    var editingEmail by remember { mutableStateOf(false) }
    var tempEmail by remember { mutableStateOf(userEmail) }

    val todayBadgeCount by viewModel.todayRemainingCount.collectAsState()

    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var isDeletingAll by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var isCloudBackingUp by remember { mutableStateOf(false) }
    var showCloudRestoreSheet by remember { mutableStateOf(false) }
    var isLoadingCloudBackups by remember { mutableStateOf(false) }
    var cloudBackupList by remember { mutableStateOf<List<com.mj.yata.data.cloud.CloudBackupEntry>>(emptyList()) }
    var isRestoringCloudBackup by remember { mutableStateOf(false) }
    var showFrequencyDialog by remember { mutableStateOf(false) }
    var freqNumberText by remember { mutableStateOf("1") }
    var freqUnit by remember { mutableStateOf("Days") }
    var showArchiveMonthsDialog by remember { mutableStateOf(false) }
    var showBackupDiffDialog by remember { mutableStateOf(false) }
    var isLoadingBackupDiff by remember { mutableStateOf(false) }
    var backupDiffResult by remember { mutableStateOf<com.mj.yata.data.cloud.CloudBackupDiff?>(null) }
    var backupDiffError by remember { mutableStateOf<String?>(null) }
    var backupDiffIsReauth by remember { mutableStateOf(false) }
    var staleBannerDismissed by remember { mutableStateOf(false) }
    var pendingRestoreEntry by remember { mutableStateOf<com.mj.yata.data.cloud.CloudBackupEntry?>(null) }

    val context = LocalContext.current
    var pickedPhotoBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val photoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pickedPhotoBitmap = ProfilePhotoUtils.decodeSampledBitmap(context, uri)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            com.mj.yata.ui.screen.main.CustomBottomNav(
                selectedTab = -1,
                todayBadgeCount = todayBadgeCount,
                peopleEnabled = peopleFeatureEnabled,
                tagsEnabled = tagsFeatureEnabled,
                projectsEnabled = projectsFeatureEnabled,
                todayEnabled = todayTabEnabled,
                upcomingEnabled = upcomingTabEnabled,
                onTabSelected = onNavigateToTab
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        style = androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.ExtraBold,
                            fontSynthesis = androidx.compose.ui.text.font.FontSynthesis.All
                        )
                    )
                },
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
                    Box(
                        modifier = Modifier.clickable {
                            photoPickerLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }
                    ) {
                        com.mj.yata.ui.widgets.PersonAvatar(
                            initials = userName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase(),
                            accentKey = "accentC",
                            size = 48.dp,
                            photoUri = userPhotoUri
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .border(1.5.dp, MaterialTheme.colorScheme.surfaceContainerLow, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Change profile photo",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
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
                            items = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SCHEDULED),
                            selectedItem = themeMode,
                            onItemSelected = { viewModel.setThemeMode(it) },
                            labelProvider = { if (it == ThemeMode.SCHEDULED) "Scheduled" else it.name }
                        )
                        if (themeMode == ThemeMode.SCHEDULED) {
                            SettingsRow(
                                label = "Dark from",
                                value = TaskScheduleUtils.formatTime(themeScheduleStartHour, themeScheduleStartMinute),
                                onClick = { showThemeScheduleStartPicker = true }
                            )
                            SettingsRow(
                                label = "Light from",
                                value = TaskScheduleUtils.formatTime(themeScheduleEndHour, themeScheduleEndMinute),
                                onClick = { showThemeScheduleEndPicker = true }
                            )
                        }
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

            // Notifications Section — Android (especially Samsung/One UI) silently downgrades
            // reminders to a fuzzy ~1hr-late delivery window, or kills them outright in Doze,
            // unless these two OS-level permissions are granted. Neither is requestable at
            // runtime like POST_NOTIFICATIONS — the user has to grant them from system settings.
            Text(
                text = "NOTIFICATIONS",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    var exactAlarmsAllowed by remember { mutableStateOf(NotificationPermissionUtils.canScheduleExactAlarms(context)) }
                    var batteryUnrestricted by remember { mutableStateOf(NotificationPermissionUtils.isIgnoringBatteryOptimizations(context)) }

                    // Re-check when coming back from system settings (the app doesn't get a
                    // callback for these — only a lifecycle resume).
                    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner) {
                        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                                exactAlarmsAllowed = NotificationPermissionUtils.canScheduleExactAlarms(context)
                                batteryUnrestricted = NotificationPermissionUtils.isIgnoringBatteryOptimizations(context)
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    NotificationPermissionRow(
                        title = "Exact alarm timing",
                        granted = exactAlarmsAllowed,
                        grantedSubtitle = "Reminders fire at the exact time.",
                        deniedSubtitle = "Reminders may arrive up to an hour late — tap to fix.",
                        onClick = { NotificationPermissionUtils.openExactAlarmSettings(context) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    NotificationPermissionRow(
                        title = "Background delivery",
                        granted = batteryUnrestricted,
                        grantedSubtitle = "Battery optimization won't block reminders.",
                        deniedSubtitle = "Battery optimization may delay or drop reminders — tap to fix.",
                        onClick = { NotificationPermissionUtils.requestIgnoreBatteryOptimizations(context) }
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
                    // Guard against hiding every tab and stranding the user with an empty
                    // bottom nav — the last remaining visible tab can't be switched off.
                    val visibleTabCount = listOf(
                        todayTabEnabled, upcomingTabEnabled,
                        projectsFeatureEnabled, peopleFeatureEnabled, tagsFeatureEnabled
                    ).count { it }

                    FeatureToggleRow(
                        title = "Today tab",
                        checked = todayTabEnabled,
                        onCheckedChange = { viewModel.setTodayTabEnabled(it) },
                        enabled = !todayTabEnabled || visibleTabCount > 1
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    FeatureToggleRow(
                        title = "Upcoming tab",
                        checked = upcomingTabEnabled,
                        onCheckedChange = { viewModel.setUpcomingTabEnabled(it) },
                        enabled = !upcomingTabEnabled || visibleTabCount > 1
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    FeatureToggleRow(
                        title = "Projects",
                        checked = projectsFeatureEnabled,
                        onCheckedChange = { viewModel.setProjectsFeatureEnabled(it) },
                        enabled = !projectsFeatureEnabled || visibleTabCount > 1
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    FeatureToggleRow(
                        title = "People",
                        checked = peopleFeatureEnabled,
                        onCheckedChange = { viewModel.setPeopleFeatureEnabled(it) },
                        enabled = !peopleFeatureEnabled || visibleTabCount > 1
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    FeatureToggleRow(
                        title = "Tags",
                        checked = tagsFeatureEnabled,
                        onCheckedChange = { viewModel.setTagsFeatureEnabled(it) },
                        enabled = !tagsFeatureEnabled || visibleTabCount > 1
                    )
                }
            }

            // Manage Section — tap-through to the People/Tags/Projects tabs, per handoff's Settings "Manage" rows.
            // Purely a visibility toggle (see Features section above), so each row/divider fades
            // and collapses in and out in step with its feature flag rather than popping instantly.
            AnimatedVisibility(
                visible = projectsFeatureEnabled || peopleFeatureEnabled || tagsFeatureEnabled,
                enter = fadeIn(tween(YataDur.fade, easing = YataEase.emphDecel)) +
                    expandVertically(tween(YataDur.sheet, easing = YataEase.emphasized)),
                exit = fadeOut(tween(YataDur.fade)) +
                    shrinkVertically(tween(YataDur.sheet, easing = YataEase.emphasized))
            ) {
                Column {
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
                            AnimatedManageRow(
                                visible = projectsFeatureEnabled,
                                title = "Projects",
                                onClick = { onNavigateToTab(1) }
                            )
                            AnimatedDivider(visible = projectsFeatureEnabled && (peopleFeatureEnabled || tagsFeatureEnabled))
                            AnimatedManageRow(
                                visible = peopleFeatureEnabled,
                                title = "People",
                                onClick = { onNavigateToTab(2) }
                            )
                            AnimatedDivider(visible = peopleFeatureEnabled && tagsFeatureEnabled)
                            AnimatedManageRow(
                                visible = tagsFeatureEnabled,
                                title = "Tags",
                                onClick = { onNavigateToTab(3) }
                            )
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
                        valueRange = 0.85f..1.3f,
                        steps = 8 // 10 stops total (min + 8 + max), 0.05 apart
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

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Text(
                        text = "Text size",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Scales text only, independent of overall UI size.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    var textSliderPosition by remember(textScale) { mutableFloatStateOf(textScale) }
                    val textPresets = listOf("Small" to 0.85f, "Normal" to 1.0f, "Large" to 1.3f)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aa",
                            fontSize = (28 * textSliderPosition).sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Slider(
                        value = textSliderPosition,
                        onValueChange = { textSliderPosition = it },
                        onValueChangeFinished = { viewModel.setTextScale(textSliderPosition) },
                        valueRange = 0.85f..1.3f,
                        steps = 8
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        textPresets.forEach { (label, value) ->
                            TextButton(onClick = {
                                textSliderPosition = value
                                viewModel.setTextScale(value)
                            }) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Reduce motion",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = "Shortens navigation, sheet, and fade animations.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = reduceMotionEnabled,
                            onCheckedChange = { viewModel.setReduceMotionEnabled(it) }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Task row density",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        SegmentedControl(
                            items = listOf(TaskRowDensity.COMPACT, TaskRowDensity.COMFORTABLE, TaskRowDensity.SPACIOUS),
                            selectedItem = taskRowDensity,
                            onItemSelected = { viewModel.setTaskRowDensity(it) },
                            labelProvider = {
                                when (it) {
                                    TaskRowDensity.COMPACT -> "Compact"
                                    TaskRowDensity.COMFORTABLE -> "Comfortable"
                                    TaskRowDensity.SPACIOUS -> "Spacious"
                                }
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Quick-add button position",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        SegmentedControl(
                            items = listOf(FabPosition.LEFT, FabPosition.RIGHT, FabPosition.HIDDEN),
                            selectedItem = fabPosition,
                            onItemSelected = { viewModel.setFabPosition(it) },
                            labelProvider = {
                                when (it) {
                                    FabPosition.LEFT -> "Left"
                                    FabPosition.RIGHT -> "Right"
                                    FabPosition.HIDDEN -> "Hidden"
                                }
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Haptic feedback",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = "Vibrate on checkbox, swipe, and drag actions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = hapticsEnabled,
                            onCheckedChange = { viewModel.setHapticsEnabled(it) }
                        )
                    }
                }
            }

            // 4. Cloud Backup Section
            Text(
                text = "CLOUD BACKUP",
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
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cloud Backup",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (cloudBackupAccountEmail != null) {
                                    "Signed in as $cloudBackupAccountEmail"
                                } else {
                                    "Automatically backs up to your Google Drive."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (cloudBackupAccountEmail != null) {
                            Switch(
                                checked = cloudBackupEnabled,
                                onCheckedChange = { viewModel.setCloudBackupEnabled(it) }
                            )
                        } else {
                            TextButton(onClick = onCloudSignInRequested) {
                                Text("Sign in")
                            }
                        }
                    }

                    if (cloudBackupEnabled && !staleBannerDismissed && isCloudBackupStale(cloudBackupLastAt)) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (cloudBackupLastAt == null) {
                                        "Cloud backup hasn't run yet."
                                    } else {
                                        "Cloud backup hasn't run in over 7 days."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .clickable { staleBannerDismissed = true }
                                )
                            }
                        }
                    }

                    if (cloudBackupAccountEmail != null) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = cloudBackupEnabled && !isCloudBackingUp) {
                                    isCloudBackingUp = true
                                    viewModel.cloudBackupNow { result ->
                                        isCloudBackingUp = false
                                        scope.launch {
                                            val reauth = isReauthRecoverable(result.exceptionOrNull())
                                            val outcome = snackbarHostState.showSnackbar(
                                                message = if (result.isSuccess) "Backed up to Google Drive" else "Backup failed — ${result.exceptionOrNull()?.message ?: "try again later"}",
                                                actionLabel = if (reauth) "Reauthorize" else null
                                            )
                                            if (outcome == SnackbarResult.ActionPerformed) onCloudSignInRequested()
                                        }
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Back up now",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                    color = if (cloudBackupEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = "Last backed up: " + formatRelativeBackupTime(cloudBackupLastAt) +
                                        (formatAbsoluteBackupTime(cloudBackupLastAt)?.let { " · $it" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isCloudBackingUp) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isLoadingBackupDiff) {
                                    showBackupDiffDialog = true
                                    isLoadingBackupDiff = true
                                    backupDiffResult = null
                                    backupDiffError = null
                                    backupDiffIsReauth = false
                                    viewModel.compareWithLastBackup { result ->
                                        isLoadingBackupDiff = false
                                        result.fold(
                                            onSuccess = { backupDiffResult = it },
                                            onFailure = {
                                                backupDiffError = it.message ?: "Couldn't compare with backup"
                                                backupDiffIsReauth = isReauthRecoverable(it)
                                            }
                                        )
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CompareArrows,
                                contentDescription = "Compare with backup",
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Compare with Backup",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Wi-Fi only",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    text = "Skip backups on mobile data.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = cloudBackupWifiOnly,
                                onCheckedChange = { viewModel.setCloudBackupWifiOnly(it) }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val (value, unit) = minutesToIntervalDisplay(cloudBackupIntervalMinutes)
                                    freqNumberText = value.toString()
                                    freqUnit = unit
                                    showFrequencyDialog = true
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Backup frequency",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    text = formatBackupInterval(cloudBackupIntervalMinutes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showArchiveMonthsDialog = true }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Archive old completed tasks",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    text = formatArchiveMonths(cloudBackupArchiveMonths),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showCloudRestoreSheet = true
                                    isLoadingCloudBackups = true
                                    viewModel.listCloudBackups { result ->
                                        isLoadingCloudBackups = false
                                        cloudBackupList = result.getOrDefault(emptyList())
                                        val exc = result.exceptionOrNull()
                                        if (exc != null) {
                                            scope.launch {
                                                val outcome = snackbarHostState.showSnackbar(
                                                    message = "Couldn't reach Google Drive",
                                                    actionLabel = if (isReauthRecoverable(exc)) "Reauthorize" else null
                                                )
                                                if (outcome == SnackbarResult.ActionPerformed) onCloudSignInRequested()
                                            }
                                        }
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = "Restore from cloud",
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Restore from Cloud Backup",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.cloudSignOut()
                                    scope.launch { snackbarHostState.showSnackbar("Signed out of cloud backup") }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Sign out",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // 5. Backup/Data Section
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
                                text = "Exports all tasks, lists, and settings to a JSON file.",
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
                            .clickable { onExportIcsRequested() }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "Export to calendar",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Export to Calendar",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Saves a .ics file of due tasks for any calendar app.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToWelcome() }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.TaskAlt,
                            contentDescription = "Show welcome tour",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Show Welcome Tour",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Replay the intro explaining YATA's terms and features.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToTrash() }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Trash",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Trash",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Deleted tasks are kept here for 30 days.",
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

            // 6. Help & About Section — a concise feature reference, with the app-identity
            // "about" card (previously its own top-level section) now living at the bottom of it.
            Text(
                text = "HELP & ABOUT",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    helpFeatures.forEach { (title, description) ->
                        HelpFeatureRow(title = title, description = description)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(com.mj.yata.R.drawable.rj_logo_mark),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer),
                            modifier = Modifier.size(width = 44.dp, height = 29.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "yata",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = com.mj.yata.ui.theme.BodoniModaFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "yet another todo app",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "v${com.mj.yata.BuildConfig.VERSION_NAME} build ${com.mj.yata.BuildConfig.VERSION_CODE}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "From the Labs of RJ",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Made in 🇮🇳",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

    if (showBackupDiffDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDiffDialog = false },
            title = { Text("Compare with Backup") },
            text = {
                when {
                    isLoadingBackupDiff -> {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    backupDiffError != null -> {
                        Text(backupDiffError!!, color = MaterialTheme.colorScheme.error)
                    }
                    backupDiffResult != null -> {
                        val diff = backupDiffResult!!
                        Column(
                            modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (diff.pendingDiff == 0 && diff.doneDiff == 0) {
                                Text(
                                    "Up to date — no changes since last backup.",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            } else {
                                Text(
                                    "${signedCount(diff.pendingDiff)} Pending Tasks, ${signedCount(diff.doneDiff)} Done Tasks",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            Text(
                                "Last backup: " + formatBackupTimestamp(diff.backupCreatedTime),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Now: ${diff.currentPending} pending, ${diff.currentDone} done",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Backup: ${diff.backupPending} pending, ${diff.backupDone} done",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            BackupDiffTaskSection("New since backup", diff.addedTitles, diff.addedCount)
                            BackupDiffTaskSection("Missing from current data", diff.removedTitles, diff.removedCount)
                            BackupDiffTaskSection("Changed since backup", diff.changedTitles, diff.changedCount)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBackupDiffDialog = false }) { Text("Close") }
            },
            dismissButton = if (backupDiffIsReauth) {
                {
                    TextButton(onClick = {
                        showBackupDiffDialog = false
                        onCloudSignInRequested()
                    }) {
                        Text("Reauthorize")
                    }
                }
            } else null
        )
    }

    if (showFrequencyDialog) {
        AlertDialog(
            onDismissRequest = { showFrequencyDialog = false },
            title = { Text("Backup frequency") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = freqNumberText,
                        onValueChange = { new -> if (new.length <= 4 && new.all { it.isDigit() }) freqNumberText = new },
                        label = { Text("Every") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    SegmentedControl(
                        items = listOf("Minutes", "Hours", "Days"),
                        selectedItem = freqUnit,
                        onItemSelected = { freqUnit = it }
                    )
                    Text(
                        text = "Minimum 15 minutes — Android won't run background backups more often than that.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val value = (freqNumberText.toLongOrNull() ?: 1L).coerceAtLeast(1L)
                    viewModel.setCloudBackupIntervalMinutes(intervalDisplayToMinutes(value, freqUnit))
                    showFrequencyDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFrequencyDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showArchiveMonthsDialog) {
        AlertDialog(
            onDismissRequest = { showArchiveMonthsDialog = false },
            title = { Text("Archive old completed tasks") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Completed tasks older than this move out of the main cloud backup into a separate archive file, so the backup that uploads every time doesn't keep growing. Nothing is deleted from the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    listOf(0 to "Never", 3 to "3 months", 6 to "6 months", 12 to "12 months").forEach { (months, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.setCloudBackupArchiveMonths(months)
                                    showArchiveMonthsDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = cloudBackupArchiveMonths == months,
                                onClick = {
                                    viewModel.setCloudBackupArchiveMonths(months)
                                    showArchiveMonthsDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showArchiveMonthsDialog = false }) { Text("Done") }
            }
        )
    }

    if (showCloudRestoreSheet) {
        AlertDialog(
            onDismissRequest = { if (!isRestoringCloudBackup) showCloudRestoreSheet = false },
            title = { Text("Restore from Cloud Backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    when {
                        isLoadingCloudBackups -> {
                            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        cloudBackupList.isEmpty() -> {
                            Text(
                                "No cloud backups found yet.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        else -> {
                            cloudBackupList.forEach { entry ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !isRestoringCloudBackup) { pendingRestoreEntry = entry }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = formatBackupTimestamp(entry.createdTime),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        formatBackupSize(entry.sizeBytes)?.let { sizeLabel ->
                                            Text(
                                                text = sizeLabel,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                            if (isRestoringCloudBackup) {
                                Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCloudRestoreSheet = false }, enabled = !isRestoringCloudBackup) {
                    Text("Close")
                }
            }
        )
    }

    pendingRestoreEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingRestoreEntry = null },
            title = { Text("Restore this backup?") },
            text = {
                Text(
                    "This merges the backup from ${formatBackupTimestamp(entry.createdTime)} into your " +
                        "current data — tasks, lists, tags, and people from it are added or updated. " +
                        "Nothing currently on this device is deleted."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = entry
                    pendingRestoreEntry = null
                    isRestoringCloudBackup = true
                    viewModel.restoreCloudBackup(target.id) { result ->
                        isRestoringCloudBackup = false
                        showCloudRestoreSheet = false
                        scope.launch {
                            val reauth = isReauthRecoverable(result.exceptionOrNull())
                            val outcome = snackbarHostState.showSnackbar(
                                message = if (result.isSuccess) "Restored from cloud backup" else "Restore failed — ${result.exceptionOrNull()?.message ?: "try again later"}",
                                actionLabel = if (reauth) "Reauthorize" else null
                            )
                            if (outcome == SnackbarResult.ActionPerformed) onCloudSignInRequested()
                        }
                    }
                }) {
                    Text("Restore", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreEntry = null }) { Text("Cancel") }
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

    YataTimePickerLauncher(
        show = showThemeScheduleStartPicker,
        initialTime = TaskScheduleUtils.formatTime(themeScheduleStartHour, themeScheduleStartMinute),
        onDismiss = { showThemeScheduleStartPicker = false },
        onConfirm = { formatted ->
            val parsed = TaskScheduleUtils.parseTime(formatted)
            if (parsed != null) {
                viewModel.setThemeSchedule(parsed.hour, parsed.minute, themeScheduleEndHour, themeScheduleEndMinute)
            }
            showThemeScheduleStartPicker = false
        }
    )

    YataTimePickerLauncher(
        show = showThemeScheduleEndPicker,
        initialTime = TaskScheduleUtils.formatTime(themeScheduleEndHour, themeScheduleEndMinute),
        onDismiss = { showThemeScheduleEndPicker = false },
        onConfirm = { formatted ->
            val parsed = TaskScheduleUtils.parseTime(formatted)
            if (parsed != null) {
                viewModel.setThemeSchedule(themeScheduleStartHour, themeScheduleStartMinute, parsed.hour, parsed.minute)
            }
            showThemeScheduleEndPicker = false
        }
    )

    pickedPhotoBitmap?.let { bitmap ->
        CircularImageCropper(
            source = bitmap,
            onConfirm = { cropped ->
                val savedUri = ProfilePhotoUtils.saveCircularProfilePhoto(context, cropped)
                viewModel.setUserPhotoUri(savedUri.toString())
                pickedPhotoBitmap = null
            },
            onCancel = { pickedPhotoBitmap = null }
        )
    }
}

private fun formatRelativeBackupTime(epochMillis: Long?): String {
    if (epochMillis == null) return "never"
    val diffMs = System.currentTimeMillis() - epochMillis
    val minutes = diffMs / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 24 * 60 -> "${minutes / 60}h ago"
        else -> "${minutes / (24 * 60)}d ago"
    }
}

private fun formatBackupSize(bytes: Long?): String? {
    if (bytes == null) return null
    return if (bytes < 1024) "$bytes B" else "${"%.1f".format(bytes / 1024.0)} KB"
}

private fun formatAbsoluteBackupTime(epochMillis: Long?): String? {
    if (epochMillis == null) return null
    return java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a")
        .withZone(java.time.ZoneId.systemDefault())
        .format(java.time.Instant.ofEpochMilli(epochMillis))
}

private fun formatBackupTimestamp(isoCreatedTime: String): String {
    return try {
        val instant = java.time.Instant.parse(isoCreatedTime)
        java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a")
            .withZone(java.time.ZoneId.systemDefault())
            .format(instant)
    } catch (e: Exception) {
        isoCreatedTime
    }
}

/** Picks the largest whole unit the stored minutes divide evenly into, so e.g. 1440 shows back
 * as "1 / Days" instead of "1440 / Minutes" when the frequency dialog is reopened. */
private fun minutesToIntervalDisplay(minutes: Long): Pair<Long, String> = when {
    minutes >= 24 * 60 && minutes % (24 * 60) == 0L -> (minutes / (24 * 60)) to "Days"
    minutes >= 60 && minutes % 60 == 0L -> (minutes / 60) to "Hours"
    else -> minutes to "Minutes"
}

private fun intervalDisplayToMinutes(value: Long, unit: String): Long = when (unit) {
    "Days" -> value * 24 * 60
    "Hours" -> value * 60
    else -> value
}

private fun signedCount(n: Int): String = if (n > 0) "+$n" else "$n"

/** Both cases are fixed the same way (re-run the sign-in flow), so every "Reauthorize" action
 * button in this screen checks this instead of just NeedsReauth — NotSignedIn shows up when
 * Play Services' cached account silently disappears out from under a still-"enabled" local flag. */
private fun isReauthRecoverable(t: Throwable?): Boolean =
    t is CloudBackupError.NeedsReauth || t is CloudBackupError.NotSignedIn

/** Renders nothing when [totalCount] is 0 — most comparisons won't have all three categories,
 * and an empty "Changed since backup" header with no rows under it reads as broken, not "none." */
@Composable
private fun BackupDiffTaskSection(label: String, titles: List<String>, totalCount: Int) {
    if (totalCount == 0) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "$label ($totalCount)",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        titles.forEach { title ->
            Text(
                text = "• $title",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        if (totalCount > titles.size) {
            Text(
                text = "+${totalCount - titles.size} more",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

private fun formatBackupInterval(minutes: Long): String {
    val (value, unit) = minutesToIntervalDisplay(minutes)
    val label = if (value == 1L) unit.dropLast(1).lowercase() else unit.lowercase()
    return "Every $value $label"
}

private fun formatArchiveMonths(months: Int): String =
    if (months <= 0) "Never — backups always include everything" else "Older than $months months"

@Composable
private fun FeatureToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
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
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun AnimatedManageRow(visible: Boolean, title: String, onClick: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(YataDur.fade, easing = YataEase.emphDecel)) +
            expandVertically(tween(YataDur.sheet, easing = YataEase.emphasized)),
        exit = fadeOut(tween(YataDur.fade)) +
            shrinkVertically(tween(YataDur.sheet, easing = YataEase.emphasized))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
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
    }
}

@Composable
private fun HelpFeatureRow(title: String, description: String) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(title) }
            append("  —  ")
            append(description)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun AnimatedDivider(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(YataDur.fade)),
        exit = fadeOut(tween(YataDur.fade))
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
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

/** A permission status row — the trailing chip is always tappable (both directions go through
 * [onClick], which just opens the relevant system settings screen) and its label/color flips
 * between "Granted" and "Grant" to match current state. */
@Composable
private fun NotificationPermissionRow(
    title: String,
    granted: Boolean,
    grantedSubtitle: String,
    deniedSubtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
            )
            Text(
                text = if (granted) grantedSubtitle else deniedSubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (granted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        val chipColor = if (granted) LocalYataAccents.current.accentE else MaterialTheme.colorScheme.error
        AssistChip(
            onClick = onClick,
            label = { Text(if (granted) "Granted" else "Grant") },
            leadingIcon = {
                Icon(
                    imageVector = if (granted) Icons.Default.Check else Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = chipColor,
                    modifier = Modifier.size(16.dp)
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                labelColor = chipColor,
                leadingIconContentColor = chipColor
            )
        )
    }
}
