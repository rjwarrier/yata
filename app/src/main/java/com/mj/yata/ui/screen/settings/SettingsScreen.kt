package com.mj.yata.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.R
import com.mj.yata.data.cloud.CloudBackupError
import com.mj.yata.data.cloud.isCloudBackupStale
import com.mj.yata.domain.model.AppFont
import com.mj.yata.domain.model.DefaultDueDate
import com.mj.yata.domain.model.FabPosition
import com.mj.yata.domain.model.TaskRowDensity
import com.mj.yata.domain.model.ThemeMode
import com.mj.yata.domain.model.YataList
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.YataEase
import com.mj.yata.ui.theme.THEME_PRESETS
import com.mj.yata.ui.theme.colorSchemeFromSeed
import com.mj.yata.notification.DailyAgendaWorker
import com.mj.yata.notification.OverdueEscalationWorker
import com.mj.yata.notification.NotificationPermissionUtils
import com.mj.yata.ui.screen.main.MainViewModel
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.widgets.CircularImageCropper
import com.mj.yata.ui.widgets.CustomColorPickerDialog
import com.mj.yata.ui.widgets.SegmentedControl
import com.mj.yata.ui.widgets.YataTimePickerLauncher
import com.mj.yata.ui.widgets.showSuccess
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.toArgb
import com.mj.yata.util.ProfilePhotoUtils
import com.mj.yata.util.TaskScheduleUtils
import com.mj.yata.util.localized
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private data class SettingsSearchTarget(
    val title: String,
    val summary: String,
    val keywords: String,
    val itemIndex: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onExportRequested: () -> Unit,
    onImportRequested: () -> Unit,
    onImportPlainTextRequested: () -> Unit,
    onExportCsvRequested: () -> Unit,
    onExportIcsRequested: () -> Unit,
    onCloudSignInRequested: () -> Unit,
    onNavigateToTab: (Int) -> Unit,
    onNavigateToTrash: () -> Unit,
    onNavigateToArchive: () -> Unit,
    onNavigateToWelcome: () -> Unit,
    onNavigateToHelpAbout: () -> Unit,
    onNavigateToCrashLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.settingsUiState.collectAsStateWithLifecycle()
    val customThemeSeedColorArgb by viewModel.customThemeSeedColor.collectAsStateWithLifecycle()
    val themeMode = uiState.themeMode
    val appFont = uiState.appFont
    val userName = uiState.userName
    val userEmail = uiState.userEmail
    val userPhotoUri = uiState.userPhotoUri
    val defaultListId = uiState.defaultListId
    val startOfWeekSunday = uiState.startOfWeekSunday
    val defaultReminderHour = uiState.defaultReminderHour
    val defaultReminderMinute = uiState.defaultReminderMinute
    val themeScheduleStartHour = uiState.themeScheduleStartHour
    val themeScheduleStartMinute = uiState.themeScheduleStartMinute
    val themeScheduleEndHour = uiState.themeScheduleEndHour
    val themeScheduleEndMinute = uiState.themeScheduleEndMinute
    val reduceMotionEnabled = uiState.reduceMotionEnabled
    val enhancedM3ThemingEnabled = uiState.enhancedM3ThemingEnabled
    val floatingBottomNavEnabled = uiState.floatingBottomNavEnabled
    val bottomNavLabelsEnabled = uiState.bottomNavLabelsEnabled
    val completionSoundEnabled = uiState.completionSoundEnabled
    val textScale = uiState.textScale
    val taskRowDensity = uiState.taskRowDensity
    val hapticsEnabled = uiState.hapticsEnabled
    val taskSwipeActionsEnabled = uiState.taskSwipeActionsEnabled
    val appLockEnabled = uiState.appLockEnabled
    val appLockPinSet = uiState.appLockPinSet
    val appLockTimeoutMinutes = uiState.appLockTimeoutMinutes
    val todayTabEnabled = uiState.todayTabEnabled
    val upcomingTabEnabled = uiState.upcomingTabEnabled
    val fabPosition = uiState.fabPosition
    val uiScale = uiState.uiScale
    val dynamicColorEnabled = uiState.dynamicColorEnabled
    val trashRetentionDays by viewModel.trashRetentionDays.collectAsStateWithLifecycle()
    val autoArchiveDays by viewModel.autoArchiveDays.collectAsStateWithLifecycle()
    val dailyAgendaEnabled by viewModel.dailyAgendaEnabled.collectAsStateWithLifecycle()
    val dailyAgendaHour by viewModel.dailyAgendaHour.collectAsStateWithLifecycle()
    val dailyAgendaMinute by viewModel.dailyAgendaMinute.collectAsStateWithLifecycle()
    val overdueNudgesEnabled by viewModel.overdueNudgesEnabled.collectAsStateWithLifecycle()
    val undoWindowSeconds by viewModel.undoWindowSeconds.collectAsStateWithLifecycle()
    val snoozeTonightHour by viewModel.snoozeTonightHour.collectAsStateWithLifecycle()
    val snoozeTonightMinute by viewModel.snoozeTonightMinute.collectAsStateWithLifecycle()
    val snoozeTomorrowHour by viewModel.snoozeTomorrowHour.collectAsStateWithLifecycle()
    val snoozeTomorrowMinute by viewModel.snoozeTomorrowMinute.collectAsStateWithLifecycle()
    val defaultDueDate by viewModel.defaultDueDate.collectAsStateWithLifecycle()
    val defaultPriority by viewModel.defaultPriority.collectAsStateWithLifecycle()
    val autoAssignToMe by viewModel.autoAssignToMe.collectAsStateWithLifecycle()
    val peopleFeatureEnabled = uiState.peopleFeatureEnabled
    val tagsFeatureEnabled = uiState.tagsFeatureEnabled
    val projectsFeatureEnabled = uiState.projectsFeatureEnabled
    val lists = uiState.lists
    val cloudBackupEnabled = uiState.cloudBackupEnabled
    val cloudBackupAccountEmail = uiState.cloudBackupAccountEmail
    val cloudBackupLastAt = uiState.cloudBackupLastAt
    val cloudBackupWifiOnly = uiState.cloudBackupWifiOnly
    val cloudBackupIntervalMinutes = uiState.cloudBackupIntervalMinutes
    val localBackupEnabled = uiState.localBackupEnabled
    val localBackupLastAt = uiState.localBackupLastAt
    val cloudBackupArchiveMonths = uiState.cloudBackupArchiveMonths

    val voiceLanguage by viewModel.voiceRecognitionLanguage.collectAsStateWithLifecycle()
    var showVoiceLanguageMenu by remember { mutableStateOf(false) }
    var showDefaultListMenu by remember { mutableStateOf(false) }
    var showTrashRetentionMenu by remember { mutableStateOf(false) }
    var showAutoArchiveMenu by remember { mutableStateOf(false) }
    var showAgendaTimePicker by remember { mutableStateOf(false) }
    var showReminderTimePicker by remember { mutableStateOf(false) }
    var showSnoozeTonightPicker by remember { mutableStateOf(false) }
    var showSnoozeTomorrowPicker by remember { mutableStateOf(false) }

    var editingName by remember { mutableStateOf(false) }
    var tempName by remember { mutableStateOf(userName) }

    var editingEmail by remember { mutableStateOf(false) }
    var tempEmail by remember { mutableStateOf(userEmail) }

    val todayBadgeCount = uiState.todayRemainingCount

    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showRestoreLocalDialog by remember { mutableStateOf(false) }
    var isDeletingAll by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val settingsListState = rememberLazyListState()
    var settingsSearchQuery by rememberSaveable { mutableStateOf("") }
    var pendingSettingsIndex by remember { mutableStateOf<Int?>(null) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showResetSettingsDialog by remember { mutableStateOf(false) }

    val settingsSearchTargets = listOf(
        SettingsSearchTarget(stringResource(R.string.settings_section_profile), stringResource(R.string.settings_search_profile_summary), "name email photo account", 1),
        SettingsSearchTarget(stringResource(R.string.settings_section_appearance), stringResource(R.string.settings_search_appearance_summary), "theme dark light amoled color font language motion", 2),
        SettingsSearchTarget(stringResource(R.string.settings_section_display), stringResource(R.string.settings_search_display_summary), "scale text density compact spacious", 3),
        SettingsSearchTarget(stringResource(R.string.settings_section_navigation), stringResource(R.string.settings_search_navigation_summary), "bottom navigation labels fab quick add", 4),
        SettingsSearchTarget(stringResource(R.string.settings_section_sound_feedback), stringResource(R.string.settings_search_feedback_summary), "sound haptic swipe undo", 5),
        SettingsSearchTarget(stringResource(R.string.settings_section_task_defaults), stringResource(R.string.settings_search_defaults_summary), "due priority list reminder week voice assign assignee me", 6),
        SettingsSearchTarget(stringResource(R.string.settings_section_notifications), stringResource(R.string.settings_search_notifications_summary), "alarm battery agenda overdue snooze delivery", 7),
        SettingsSearchTarget(stringResource(R.string.settings_section_features), stringResource(R.string.settings_search_features_summary), "today upcoming projects people tags", 8),
        SettingsSearchTarget(stringResource(R.string.settings_section_manage), stringResource(R.string.settings_search_manage_summary), "manage projects people tags", 9),
        SettingsSearchTarget(stringResource(R.string.settings_section_privacy), stringResource(R.string.settings_search_privacy_summary), "privacy lock pin timeout security", 10),
        SettingsSearchTarget(stringResource(R.string.settings_section_backup), stringResource(R.string.settings_search_data_summary), "export import csv calendar trash archive delete data", 11),
        SettingsSearchTarget(stringResource(R.string.settings_section_cloud_backup), stringResource(R.string.settings_search_cloud_summary), "cloud backup restore wifi frequency", 12),
        SettingsSearchTarget(stringResource(R.string.settings_section_local_backup), stringResource(R.string.settings_search_local_summary), "local backup restore", 13),
        SettingsSearchTarget(stringResource(R.string.settings_section_help_about), stringResource(R.string.settings_search_help_summary), "help about version guide", 14)
    )
    val normalizedSettingsQuery = settingsSearchQuery.trim().lowercase()
    val filteredSettingsTargets = remember(normalizedSettingsQuery, settingsSearchTargets) {
        if (normalizedSettingsQuery.isBlank()) emptyList() else settingsSearchTargets.filter {
            val haystack = "${it.title} ${it.summary} ${it.keywords}".lowercase()
            normalizedSettingsQuery.split(Regex("\\s+")).all(haystack::contains)
        }
    }

    LaunchedEffect(pendingSettingsIndex) {
        pendingSettingsIndex?.let { index ->
            settingsListState.animateScrollToItem(index)
            pendingSettingsIndex = null
        }
    }

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
            scope.launch {
                val bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        ProfilePhotoUtils.decodeSampledBitmap(context, uri)
                    } catch (e: Exception) {
                        null
                    }
                }
                pickedPhotoBitmap = bitmap
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) { data -> com.mj.yata.ui.widgets.YataSnackbar(data) } },
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
                    Text(stringResource(R.string.settings_settings),
                        style = androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.ExtraBold,
                            fontSynthesis = androidx.compose.ui.text.font.FontSynthesis.All
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showSettingsMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
                    }
                    DropdownMenu(expanded = showSettingsMenu, onDismissRequest = { showSettingsMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_reset_settings)) },
                            leadingIcon = { Icon(Icons.Default.RestartAlt, contentDescription = null) },
                            onClick = {
                                showSettingsMenu = false
                                showResetSettingsDialog = true
                            }
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = settingsListState,
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item(key = "settings_search") {
                // Styled to the M3 search-field spec rather than as a general text field: pill
                // shape, tonal surfaceContainerHigh container, no indicator line, and a
                // placeholder instead of a floating label — search fields don't take one, and the
                // label animating up over a magnifier icon was the least M3 thing on the screen.
                //
                // Deliberately not the M3 SearchBar/DockedSearchBar composable: those own an
                // expanding full-screen surface and render their own results, which fights this
                // screen — the results here are a card inline in the settings list, and the field
                // scrolls away with it. Same visual language, without hijacking the interaction.
                val searchLabel = stringResource(R.string.settings_search_label)
                val focusManager = LocalFocusManager.current
                TextField(
                    value = settingsSearchQuery,
                    onValueChange = { settingsSearchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .semantics { contentDescription = searchLabel },
                    singleLine = true,
                    shape = CircleShape,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        // Only present with text to clear, so the field isn't permanently carrying
                        // a control that would do nothing.
                        if (settingsSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { settingsSearchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_clear_search))
                            }
                        }
                    },
                    placeholder = { Text(stringResource(R.string.settings_search_placeholder)) },
                    // Filtering is live, so the IME action has nothing to submit — it just gets
                    // the keyboard out of the way of the results.
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    // Same tonal treatment as the notes/comment/subtask fields — one definition
                    // in YataInputField rather than a second copy of the colour list here.
                    colors = com.mj.yata.ui.widgets.yataFieldColors()
                )
            }
            if (settingsSearchQuery.isNotBlank()) {
                item(key = "settings_search_results") {
                    SettingsSectionCard {
                        if (filteredSettingsTargets.isEmpty()) {
                            Text(
                                stringResource(R.string.settings_search_no_results),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        } else {
                            filteredSettingsTargets.forEachIndexed { index, target ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            settingsSearchQuery = ""
                                            pendingSettingsIndex = target.itemIndex
                                        }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(target.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                        Text(target.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                                }
                                if (index != filteredSettingsTargets.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                }
                            }
                        }
                    }
                }
            }
            item {
            // 1. Profile Section
            Text(
                text = stringResource(R.string.settings_section_profile),
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
                                contentDescription = stringResource(R.string.settings_change_profile_photo),
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
                                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.settings_save_name))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = userName.ifBlank { stringResource(R.string.profile_add_name) },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (userName.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.onSurface,
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
                                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.settings_save_email))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = userEmail.ifBlank { stringResource(R.string.profile_add_email) },
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
        }
        item {
            // 2. Preferences Section
            Text(
                text = stringResource(R.string.settings_section_appearance),
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
                            text = stringResource(R.string.settings_theme_mode),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        val themeSystemLabel = stringResource(R.string.theme_mode_system)
                        val themeLightLabel = stringResource(R.string.theme_mode_light)
                        val themeDarkLabel = stringResource(R.string.theme_mode_dark)
                        val themeAmoledLabel = stringResource(R.string.theme_mode_amoled)
                        SegmentedControl(
                            items = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.AMOLED),
                            selectedItem = themeMode,
                            onItemSelected = { viewModel.setThemeMode(it) },
                            labelProvider = {
                                when (it) {
                                    ThemeMode.SYSTEM -> themeSystemLabel
                                    ThemeMode.LIGHT -> themeLightLabel
                                    ThemeMode.DARK -> themeDarkLabel
                                    ThemeMode.AMOLED -> themeAmoledLabel
                                }
                            }
                        )
                        if (themeMode == ThemeMode.AMOLED) {
                            Text(
                                text = stringResource(R.string.theme_mode_amoled_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                                    text = stringResource(R.string.settings_material_you_colors),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    text = stringResource(R.string.settings_material_you_colors_desc),
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

                    val dynamicColorActive = dynamicColorEnabled && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                    AnimatedVisibility(visible = !dynamicColorActive) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            ThemeColorPicker(
                                selectedSeedArgb = customThemeSeedColorArgb,
                                onSelect = { argb -> viewModel.setCustomThemeSeedColor(argb) }
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Per-app language picker, Android 13+ only — below that the system has no
                    // such screen and the intent would resolve to nothing. Deep-links into
                    // system settings rather than reimplementing a language list, so it always
                    // matches whichever locales the installed APK actually ships.
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        val languageContext = LocalContext.current
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    runCatching {
                                        languageContext.startActivity(
                                            android.content.Intent(
                                                android.provider.Settings.ACTION_APP_LOCALE_SETTINGS,
                                                android.net.Uri.fromParts("package", languageContext.packageName, null)
                                            )
                                        )
                                    }
                                }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_app_language),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    text = stringResource(R.string.settings_app_language_desc),
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
                    }

                    // Font sits after the color controls: theme mode, Material You and the seed
                    // picker are one continuous "what color is the app" decision, and the font
                    // choice was previously splitting that group in half.
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.settings_font),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        SegmentedControl(
                            items = listOf(AppFont.INTER, AppFont.JETBRAINS_MONO),
                            selectedItem = appFont,
                            onItemSelected = { viewModel.setAppFont(it) },
                            labelProvider = { if (it == AppFont.INTER) "Inter" else "JetBrains Mono" }
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
                                text = stringResource(R.string.settings_enhanced_theming),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = stringResource(R.string.settings_enhanced_theming_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = enhancedM3ThemingEnabled,
                            onCheckedChange = { viewModel.setEnhancedM3ThemingEnabled(it) }
                        )
                    }

                }
            }
        }

        item {
            // 3. Display Section
            Text(
                text = stringResource(R.string.settings_section_display),
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
                        text = stringResource(R.string.settings_ui_size),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = stringResource(R.string.settings_ui_size_desc),
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
                            text = stringResource(R.string.settings_font_sample),
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
                        text = stringResource(R.string.settings_text_size),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = stringResource(R.string.settings_text_size_desc),
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
                            text = stringResource(R.string.settings_font_sample),
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
                                text = stringResource(R.string.settings_reduce_motion),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = stringResource(R.string.settings_reduce_motion_desc),
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
                            text = stringResource(R.string.settings_task_row_density),
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
                        RowDensityPreview(taskRowDensity)
                    }

                }
            }
        }
        item {
            // Navigation — everything that changes the bottom nav's shape or contents. Split out
            // of the old PREFERENCES catch-all, which mixed these with theming and task defaults.
            SettingsSectionHeader(stringResource(R.string.settings_section_navigation))
            SettingsSectionCard {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_floating_bottom_panel),
                    subtitle = stringResource(R.string.settings_floating_bottom_panel_desc),
                    checked = floatingBottomNavEnabled,
                    onCheckedChange = { viewModel.setFloatingBottomNavEnabled(it) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingsToggleRow(
                    title = stringResource(R.string.settings_bottom_nav_labels),
                    subtitle = stringResource(R.string.settings_bottom_nav_labels_desc),
                    checked = bottomNavLabelsEnabled,
                    onCheckedChange = { viewModel.setBottomNavLabelsEnabled(it) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_quick_add_position),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                    val fabLeftLabel = stringResource(R.string.settings_fab_left)
                    val fabRightLabel = stringResource(R.string.settings_fab_right)
                    val fabHiddenLabel = stringResource(R.string.settings_fab_hidden)
                    SegmentedControl(
                        items = listOf(FabPosition.LEFT, FabPosition.RIGHT, FabPosition.HIDDEN),
                        selectedItem = fabPosition,
                        onItemSelected = { viewModel.setFabPosition(it) },
                        labelProvider = {
                            when (it) {
                                FabPosition.LEFT -> fabLeftLabel
                                FabPosition.RIGHT -> fabRightLabel
                                FabPosition.HIDDEN -> fabHiddenLabel
                            }
                        }
                    )
                }
            }
        }

        item {
            // Sound & feedback — the app's response to an action, as opposed to its layout.
            SettingsSectionHeader(stringResource(R.string.settings_section_sound_feedback))
            SettingsSectionCard {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_completion_sound),
                    subtitle = stringResource(R.string.settings_completion_sound_desc),
                    checked = completionSoundEnabled,
                    onCheckedChange = { viewModel.setCompletionSoundEnabled(it) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingsToggleRow(
                    title = stringResource(R.string.settings_haptic_feedback),
                    subtitle = stringResource(R.string.settings_haptic_feedback_desc),
                    checked = hapticsEnabled,
                    onCheckedChange = { viewModel.setHapticsEnabled(it) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingsToggleRow(
                    title = stringResource(R.string.settings_swipe_actions),
                    subtitle = stringResource(R.string.settings_swipe_actions_desc),
                    checked = taskSwipeActionsEnabled,
                    onCheckedChange = { viewModel.setTaskSwipeActionsEnabled(it) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_undo_window),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = stringResource(R.string.settings_undo_window_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Resolved outside the lambda: labelProvider is not a composable scope.
                    val undoShort = pluralStringResource(R.plurals.settings_undo_window_value, 4, 4)
                    val undoMedium = pluralStringResource(R.plurals.settings_undo_window_value, 8, 8)
                    val undoLong = pluralStringResource(R.plurals.settings_undo_window_value, 15, 15)
                    SegmentedControl(
                        items = listOf(4, 8, 15),
                        selectedItem = undoWindowSeconds,
                        onItemSelected = { viewModel.setUndoWindowSeconds(it) },
                        labelProvider = { secs ->
                            when (secs) {
                                4 -> undoShort
                                8 -> undoMedium
                                else -> undoLong
                            }
                        }
                    )
                }
            }
        }

        item {
            // Task defaults — what a newly created task inherits, plus the calendar/voice
            // conventions the app assumes. Previously buried at the end of PREFERENCES.
            SettingsSectionHeader(stringResource(R.string.settings_section_task_defaults))
            SettingsSectionCard {
                // Hidden when the People feature is off: with no people there is nobody to assign
                // to, so the row would toggle something with no observable effect.
                if (peopleFeatureEnabled) {
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_auto_assign),
                        subtitle = stringResource(R.string.settings_auto_assign_desc),
                        checked = autoAssignToMe,
                        onCheckedChange = { viewModel.setAutoAssignToMe(it) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_default_due_date),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = stringResource(R.string.settings_default_due_date_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val dueTodayLabel = stringResource(R.string.settings_due_today)
                    val dueTomorrowLabel = stringResource(R.string.settings_due_tomorrow)
                    val dueNoneLabel = stringResource(R.string.settings_due_none)
                    SegmentedControl(
                        items = listOf(DefaultDueDate.TODAY, DefaultDueDate.TOMORROW, DefaultDueDate.NONE),
                        selectedItem = defaultDueDate,
                        onItemSelected = { viewModel.setDefaultDueDate(it) },
                        labelProvider = {
                            when (it) {
                                DefaultDueDate.TODAY -> dueTodayLabel
                                DefaultDueDate.TOMORROW -> dueTomorrowLabel
                                DefaultDueDate.NONE -> dueNoneLabel
                            }
                        }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_default_priority),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                    val prioNone = stringResource(R.string.settings_priority_none)
                    val prioLow = stringResource(R.string.settings_priority_low)
                    val prioMed = stringResource(R.string.settings_priority_med)
                    val prioHigh = stringResource(R.string.settings_priority_high)
                    SegmentedControl(
                        items = listOf("none", "low", "med", "high"),
                        selectedItem = defaultPriority,
                        onItemSelected = { viewModel.setDefaultPriority(it) },
                        labelProvider = {
                            when (it) {
                                "low" -> prioLow
                                "med" -> prioMed
                                "high" -> prioHigh
                                else -> prioNone
                            }
                        }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Box {
                    SettingsRow(
                        label = stringResource(R.string.settings_default_list),
                        value = lists.find { it.id == defaultListId }?.name ?: stringResource(R.string.settings_none),
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

                SettingsRow(
                    label = stringResource(R.string.settings_default_reminder_time),
                    value = TaskScheduleUtils.formatTime(defaultReminderHour, defaultReminderMinute),
                    onClick = { showReminderTimePicker = true }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                SettingsToggleRow(
                    title = stringResource(R.string.settings_start_week_sunday),
                    subtitle = stringResource(R.string.settings_start_week_sunday_desc),
                    checked = startOfWeekSunday,
                    onCheckedChange = { viewModel.setStartOfWeekSunday(it) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                val systemDefaultVoiceLabel = stringResource(R.string.settings_voice_system_default)
                val voiceLanguages = remember(systemDefaultVoiceLabel) {
                    listOf(
                        "default" to systemDefaultVoiceLabel,
                        "en-US" to "English (US)",
                        "en-IN" to "English (India)",
                        "en-GB" to "English (UK)",
                        "es-ES" to "Spanish",
                        "fr-FR" to "French",
                        "de-DE" to "German",
                        "hi-IN" to "Hindi",
                        "ja-JP" to "Japanese",
                        "zh-CN" to "Chinese",
                        "pt-BR" to "Portuguese"
                    )
                }
                Box {
                    SettingsRow(
                        label = stringResource(R.string.settings_voice_input_language),
                        value = voiceLanguages.find { it.first == voiceLanguage }?.second ?: systemDefaultVoiceLabel,
                        onClick = { showVoiceLanguageMenu = true }
                    )
                    DropdownMenu(expanded = showVoiceLanguageMenu, onDismissRequest = { showVoiceLanguageMenu = false }) {
                        voiceLanguages.forEach { (code, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    viewModel.setVoiceRecognitionLanguage(code)
                                    showVoiceLanguageMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }
        item {
            // Notifications Section — Android (especially Samsung/One UI) silently downgrades
            // reminders to a fuzzy ~1hr-late delivery window, or kills them outright in Doze,
            // unless these two OS-level permissions are granted. Neither is requestable at
            // runtime like POST_NOTIFICATIONS — the user has to grant them from system settings.
            Text(
                text = stringResource(R.string.settings_section_notifications),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    var notificationsEnabled by remember { mutableStateOf(NotificationPermissionUtils.areNotificationsEnabled(context)) }
                    var exactAlarmsAllowed by remember { mutableStateOf(NotificationPermissionUtils.canScheduleExactAlarms(context)) }
                    var batteryUnrestricted by remember { mutableStateOf(NotificationPermissionUtils.isIgnoringBatteryOptimizations(context)) }

                    // Re-check when coming back from system settings (the app doesn't get a
                    // callback for these — only a lifecycle resume).
                    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner) {
                        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                                notificationsEnabled = NotificationPermissionUtils.areNotificationsEnabled(context)
                                exactAlarmsAllowed = NotificationPermissionUtils.canScheduleExactAlarms(context)
                                batteryUnrestricted = NotificationPermissionUtils.isIgnoringBatteryOptimizations(context)
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    Surface(
                        color = if (notificationsEnabled && exactAlarmsAllowed && batteryUnrestricted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = if (notificationsEnabled && exactAlarmsAllowed && batteryUnrestricted) stringResource(R.string.settings_reminder_health_ready) else stringResource(R.string.settings_reminder_health_attention),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(stringResource(R.string.settings_reminder_health_explanation), style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    NotificationPermissionRow(
                        title = stringResource(R.string.settings_notification_access),
                        granted = notificationsEnabled,
                        grantedSubtitle = stringResource(R.string.settings_notification_access_granted),
                        deniedSubtitle = stringResource(R.string.settings_notification_access_denied),
                        onClick = { NotificationPermissionUtils.openNotificationSettings(context) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    NotificationPermissionRow(
                        title = stringResource(R.string.settings_exact_alarm_timing),
                        granted = exactAlarmsAllowed,
                        grantedSubtitle = stringResource(R.string.settings_exact_alarm_granted),
                        deniedSubtitle = stringResource(R.string.settings_exact_alarm_denied),
                        onClick = { NotificationPermissionUtils.openExactAlarmSettings(context) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    NotificationPermissionRow(
                        title = stringResource(R.string.settings_background_delivery),
                        granted = batteryUnrestricted,
                        grantedSubtitle = stringResource(R.string.settings_background_delivery_granted),
                        deniedSubtitle = stringResource(R.string.settings_background_delivery_denied),
                        onClick = { NotificationPermissionUtils.requestIgnoreBatteryOptimizations(context) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // These two workers previously ran unconditionally with no way to silence
                    // them. Rescheduling happens here rather than only in YataApplication so a
                    // change applies now instead of at next launch.
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_daily_agenda),
                        subtitle = stringResource(R.string.settings_daily_agenda_desc),
                        checked = dailyAgendaEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.setDailyAgendaEnabled(enabled)
                            if (enabled) {
                                DailyAgendaWorker.schedule(context, dailyAgendaHour, dailyAgendaMinute)
                            } else {
                                DailyAgendaWorker.cancel(context)
                            }
                        }
                    )

                    AnimatedVisibility(visible = dailyAgendaEnabled) {
                        SettingsRow(
                            label = stringResource(R.string.settings_daily_agenda_time),
                            value = TaskScheduleUtils.formatTime(dailyAgendaHour, dailyAgendaMinute),
                            onClick = { showAgendaTimePicker = true }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    SettingsToggleRow(
                        title = stringResource(R.string.settings_overdue_nudges),
                        subtitle = stringResource(R.string.settings_overdue_nudges_desc),
                        checked = overdueNudgesEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.setOverdueNudgesEnabled(enabled)
                            if (enabled) {
                                OverdueEscalationWorker.schedule(context)
                            } else {
                                OverdueEscalationWorker.cancel(context)
                            }
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Text(stringResource(R.string.settings_quick_snooze_times), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.settings_quick_snooze_times_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SettingsRow(
                        label = stringResource(R.string.settings_snooze_tonight),
                        value = TaskScheduleUtils.formatTime(snoozeTonightHour, snoozeTonightMinute),
                        onClick = { showSnoozeTonightPicker = true }
                    )
                    SettingsRow(
                        label = stringResource(R.string.settings_snooze_tomorrow),
                        value = TaskScheduleUtils.formatTime(snoozeTomorrowHour, snoozeTomorrowMinute),
                        onClick = { showSnoozeTomorrowPicker = true }
                    )
                }
            }
        }
        item {
            // Features Section — hides the entire tab/pickers/chips for a feature, but never
            // touches stored data, so re-enabling shows everything exactly as it was.
            Text(
                text = stringResource(R.string.settings_section_features),
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
                        title = stringResource(R.string.settings_today_tab),
                        checked = todayTabEnabled,
                        onCheckedChange = { viewModel.setTodayTabEnabled(it) },
                        enabled = !todayTabEnabled || visibleTabCount > 1
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    FeatureToggleRow(
                        title = stringResource(R.string.settings_upcoming_tab),
                        checked = upcomingTabEnabled,
                        onCheckedChange = { viewModel.setUpcomingTabEnabled(it) },
                        enabled = !upcomingTabEnabled || visibleTabCount > 1
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    FeatureToggleRow(
                        title = stringResource(R.string.settings_projects),
                        checked = projectsFeatureEnabled,
                        onCheckedChange = { viewModel.setProjectsFeatureEnabled(it) },
                        enabled = !projectsFeatureEnabled || visibleTabCount > 1
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    FeatureToggleRow(
                        title = stringResource(R.string.settings_people),
                        checked = peopleFeatureEnabled,
                        onCheckedChange = { viewModel.setPeopleFeatureEnabled(it) },
                        enabled = !peopleFeatureEnabled || visibleTabCount > 1
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    FeatureToggleRow(
                        title = stringResource(R.string.settings_tags),
                        checked = tagsFeatureEnabled,
                        onCheckedChange = { viewModel.setTagsFeatureEnabled(it) },
                        enabled = !tagsFeatureEnabled || visibleTabCount > 1
                    )
                }
            }
        }
        item {
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
                        text = stringResource(R.string.settings_section_manage),
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
        }
        item {
            // Privacy & Security Section
            val context = LocalContext.current
            val biometricAvailable = remember {
                androidx.biometric.BiometricManager.from(context).canAuthenticate(
                    androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                ) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
            }
            var showPinDialog by remember { mutableStateOf(false) }

            Text(
                text = stringResource(R.string.settings_section_privacy),
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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_app_lock),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (biometricAvailable)
                                    "Require biometric or device unlock to open YATA."
                                else
                                    "No screen lock set up on this device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = appLockEnabled && biometricAvailable,
                            enabled = biometricAvailable,
                            onCheckedChange = { viewModel.setAppLockEnabled(it) }
                        )
                    }

                    if (appLockEnabled && biometricAvailable) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_pin_code),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    text = if (appLockPinSet)
                                        "Set — usable as a fallback for App Lock."
                                    else
                                        "Not set — biometric/device credential only.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { showPinDialog = true }) {
                                Text(if (appLockPinSet) "Change" else "Set PIN")
                            }
                            if (appLockPinSet) {
                                TextButton(onClick = { viewModel.setAppLockPin(null) }) {
                                    Text(stringResource(R.string.settings_remove), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.settings_auto_lock),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            SegmentedControl(
                                items = listOf(0, 1, 5, 15),
                                selectedItem = appLockTimeoutMinutes,
                                onItemSelected = { viewModel.setAppLockTimeoutMinutes(it) },
                                labelProvider = {
                                    when (it) {
                                        0 -> "Immediately"
                                        1 -> "1 min"
                                        5 -> "5 min"
                                        else -> "15 min"
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (showPinDialog) {
                PinSetupDialog(
                    onDismiss = { showPinDialog = false },
                    onConfirm = { pin ->
                        viewModel.setAppLockPin(pin)
                        showPinDialog = false
                    }
                )
            }
        }
        item {
            // 5. Backup/Data Section
            Text(
                text = stringResource(R.string.settings_section_backup),
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
                            contentDescription = stringResource(R.string.settings_export_data),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.settings_backup_to_file),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = stringResource(R.string.settings_backup_to_file_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_task_lifecycle),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = stringResource(R.string.settings_task_lifecycle_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                            contentDescription = stringResource(R.string.settings_import_data),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.settings_restore_from_file),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = stringResource(R.string.settings_restore_from_file_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onImportPlainTextRequested() }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = stringResource(R.string.settings_import_csv_or_text),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.settings_import_csv),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = stringResource(R.string.settings_import_csv_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onExportCsvRequested() }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = stringResource(R.string.settings_export_csv),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.settings_export_csv),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = stringResource(R.string.settings_export_csv_summary),
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
                            contentDescription = stringResource(R.string.settings_export_to_calendar),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.settings_export_calendar),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = stringResource(R.string.settings_export_calendar_summary),
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
                            contentDescription = stringResource(R.string.settings_show_welcome_tour),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.settings_show_welcome_tour),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = stringResource(R.string.settings_show_welcome_tour_summary),
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
                            contentDescription = stringResource(R.string.trash_title),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.trash_title),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                // Reflects the configured retention rather than claiming a fixed
                                // 30 days, which stopped being true once this became a setting.
                                text = if (trashRetentionDays <= 0) {
                                    stringResource(R.string.settings_trash_kept_forever)
                                } else {
                                    pluralStringResource(
                                        R.plurals.settings_trash_kept_days,
                                        trashRetentionDays,
                                        trashRetentionDays
                                    )
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Box {
                        val retentionOptions = listOf(7, 30, 90, 0)
                        SettingsRow(
                            label = stringResource(R.string.settings_trash_retention),
                            value = if (trashRetentionDays <= 0) {
                                stringResource(R.string.settings_trash_forever)
                            } else {
                                pluralStringResource(
                                    R.plurals.settings_trash_days_value,
                                    trashRetentionDays,
                                    trashRetentionDays
                                )
                            },
                            onClick = { showTrashRetentionMenu = true }
                        )
                        DropdownMenu(
                            expanded = showTrashRetentionMenu,
                            onDismissRequest = { showTrashRetentionMenu = false }
                        ) {
                            retentionOptions.forEach { days ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (days <= 0) {
                                                stringResource(R.string.settings_trash_forever)
                                            } else {
                                                pluralStringResource(R.plurals.settings_trash_days_value, days, days)
                                            }
                                        )
                                    },
                                    onClick = {
                                        viewModel.setTrashRetentionDays(days)
                                        showTrashRetentionMenu = false
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToArchive() }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Archive,
                            contentDescription = stringResource(R.string.archive_title),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.archive_title),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = stringResource(R.string.settings_archive_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Box {
                        // Off by default — silently shelving a user's completed tasks without
                        // them asking would look like data loss.
                        val autoArchiveOptions = listOf(0, 7, 30, 90)
                        SettingsRow(
                            label = stringResource(R.string.settings_auto_archive),
                            value = if (autoArchiveDays <= 0) {
                                stringResource(R.string.settings_auto_archive_off)
                            } else {
                                pluralStringResource(
                                    R.plurals.settings_auto_archive_value,
                                    autoArchiveDays,
                                    autoArchiveDays
                                )
                            },
                            onClick = { showAutoArchiveMenu = true }
                        )
                        DropdownMenu(
                            expanded = showAutoArchiveMenu,
                            onDismissRequest = { showAutoArchiveMenu = false }
                        ) {
                            autoArchiveOptions.forEach { days ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (days <= 0) {
                                                stringResource(R.string.settings_auto_archive_off)
                                            } else {
                                                pluralStringResource(R.plurals.settings_auto_archive_value, days, days)
                                            }
                                        )
                                    },
                                    onClick = {
                                        viewModel.setAutoArchiveDays(days)
                                        showAutoArchiveMenu = false
                                    }
                                )
                            }
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
                            contentDescription = stringResource(R.string.settings_delete_all_data),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_delete_all_data),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = stringResource(R.string.settings_delete_all_data_summary),
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
        item {
            // 4. Cloud Backup Section
            Text(
                text = stringResource(R.string.settings_section_cloud_backup),
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
                                text = stringResource(R.string.settings_cloud_backup),
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
                                Text(stringResource(R.string.settings_sign_in))
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
                                    contentDescription = stringResource(R.string.settings_dismiss),
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
                                    text = stringResource(R.string.settings_back_up_now),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                    color = if (cloudBackupEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = stringResource(
                                        R.string.settings_last_backed_up,
                                        formatRelativeBackupTime(cloudBackupLastAt) +
                                            (formatAbsoluteBackupTime(cloudBackupLastAt)?.let { " · $it" } ?: "")
                                    ),
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
                                imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                                contentDescription = stringResource(R.string.settings_compare_with_backup_2),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = stringResource(R.string.settings_compare_with_backup),
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
                                    text = stringResource(R.string.settings_wifi_only),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    text = stringResource(R.string.settings_wifi_only_summary),
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
                                    text = stringResource(R.string.settings_backup_frequency),
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
                                    text = stringResource(R.string.settings_archive_old_completed),
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
                                contentDescription = stringResource(R.string.settings_restore_from_cloud),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = stringResource(R.string.settings_restore_from_cloud),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.cloudSignOut()
                                    scope.launch { snackbarHostState.showSuccess(context.getString(R.string.settings_cloud_signed_out)) }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.settings_sign_out),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
        item {
            // Local Backup Section — encrypted, on-device, no account needed.
            Text(
                text = stringResource(R.string.settings_section_local_backup),
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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_local_backup),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = stringResource(R.string.settings_local_backup_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = localBackupEnabled,
                            onCheckedChange = { viewModel.setLocalBackupEnabled(it) }
                        )
                    }

                    if (localBackupEnabled) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.settings_last_local_backup, formatRelativeBackupTime(localBackupLastAt)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = {
                                viewModel.backupLocalNow()
                                scope.launch { snackbarHostState.showSuccess(context.getString(R.string.settings_local_backup_started)) }
                            }) {
                                Text(stringResource(R.string.settings_back_up_now))
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showRestoreLocalDialog = true }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.settings_restore_local_backup),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
        item {
            Text(
                text = stringResource(R.string.settings_section_help_about),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToHelpAbout() }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = stringResource(R.string.settings_help_and_about),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_help_about_title),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = stringResource(R.string.settings_help_about_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToCrashLog() }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_crash_logs),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = stringResource(R.string.settings_crash_logs_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

    if (showResetSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showResetSettingsDialog = false },
            title = { Text(stringResource(R.string.settings_reset_dialog_title)) },
            text = { Text(stringResource(R.string.settings_reset_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showResetSettingsDialog = false
                    viewModel.resetAppSettings()
                    DailyAgendaWorker.schedule(context, 7, 30)
                    OverdueEscalationWorker.schedule(context)
                    scope.launch { snackbarHostState.showSuccess(context.getString(R.string.settings_reset_success)) }
                }) {
                    Text(stringResource(R.string.settings_reset_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetSettingsDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text(stringResource(R.string.settings_delete_all_data_2)) },
            text = {
                Text(stringResource(R.string.settings_this_backs_up_everything_to_your_downloads) +
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
                    Text(stringResource(R.string.cd_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showRestoreLocalDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreLocalDialog = false },
            title = { Text(stringResource(R.string.settings_restore_from_local_backup)) },
            text = {
                Text(stringResource(R.string.settings_this_overwrites_your_current_lists_tasks_a) +
                        "on-device backup. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreLocalDialog = false
                    viewModel.restoreLocalBackup { success ->
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (success) "Restored from local backup" else "Restore failed — no local backup found"
                            )
                        }
                    }
                }) {
                    Text(stringResource(R.string.cd_trash_restore), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreLocalDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showBackupDiffDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDiffDialog = false },
            title = { Text(stringResource(R.string.settings_compare_with_backup)) },
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
                                Text(stringResource(R.string.settings_up_to_date_no_changes_since_last_backup),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            } else {
                                Text(
                                    stringResource(R.string.settings_backup_diff_summary, signedCount(diff.pendingDiff), signedCount(diff.doneDiff)),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            Text(stringResource(R.string.settings_last_backup_at, formatBackupTimestamp(diff.backupCreatedTime)),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                stringResource(R.string.settings_backup_now_counts, diff.currentPending, diff.currentDone),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                stringResource(R.string.settings_backup_backup_counts, diff.backupPending, diff.backupDone),
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
                TextButton(onClick = { showBackupDiffDialog = false }) { Text(stringResource(R.string.action_close)) }
            },
            dismissButton = if (backupDiffIsReauth) {
                {
                    TextButton(onClick = {
                        showBackupDiffDialog = false
                        onCloudSignInRequested()
                    }) {
                        Text(stringResource(R.string.settings_reauthorize))
                    }
                }
            } else null
        )
    }

    if (showFrequencyDialog) {
        AlertDialog(
            onDismissRequest = { showFrequencyDialog = false },
            title = { Text(stringResource(R.string.settings_backup_frequency)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = freqNumberText,
                        onValueChange = { new -> if (new.length <= 4 && new.all { it.isDigit() }) freqNumberText = new },
                        label = { Text(stringResource(R.string.settings_every)) },
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
                        text = stringResource(R.string.settings_backup_frequency_minimum),
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
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showFrequencyDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showArchiveMonthsDialog) {
        AlertDialog(
            onDismissRequest = { showArchiveMonthsDialog = false },
            title = { Text(stringResource(R.string.settings_archive_old_completed_tasks)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.settings_cloud_archive_explainer),
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
                TextButton(onClick = { showArchiveMonthsDialog = false }) { Text(stringResource(R.string.action_done)) }
            }
        )
    }

    if (showCloudRestoreSheet) {
        AlertDialog(
            onDismissRequest = { if (!isRestoringCloudBackup) showCloudRestoreSheet = false },
            title = { Text(stringResource(R.string.settings_restore_from_cloud_backup)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    when {
                        isLoadingCloudBackups -> {
                            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        cloudBackupList.isEmpty() -> {
                            Text(stringResource(R.string.settings_no_cloud_backups_found_yet),
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
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    pendingRestoreEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingRestoreEntry = null },
            title = { Text(stringResource(R.string.settings_restore_this_backup)) },
            text = {
                Text(
                    stringResource(R.string.settings_restore_merge_body, formatBackupTimestamp(entry.createdTime))
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
                    Text(stringResource(R.string.cd_trash_restore), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreEntry = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    YataTimePickerLauncher(
        show = showAgendaTimePicker,
        initialTime = TaskScheduleUtils.formatTime(dailyAgendaHour, dailyAgendaMinute),
        onDismiss = { showAgendaTimePicker = false },
        onConfirm = { formatted ->
            val parsed = TaskScheduleUtils.parseTime(formatted)
            if (parsed != null) {
                viewModel.setDailyAgendaTime(parsed.hour, parsed.minute)
                // Reschedule immediately; the worker uses UPDATE so this replaces the pending run.
                DailyAgendaWorker.schedule(context, parsed.hour, parsed.minute)
            }
            showAgendaTimePicker = false
        }
    )

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
        show = showSnoozeTonightPicker,
        initialTime = TaskScheduleUtils.formatTime(snoozeTonightHour, snoozeTonightMinute),
        onDismiss = { showSnoozeTonightPicker = false },
        onConfirm = { formatted ->
            TaskScheduleUtils.parseTime(formatted)?.let { parsed ->
                viewModel.setSnoozeTonightTime(parsed.hour, parsed.minute)
            }
            showSnoozeTonightPicker = false
        }
    )

    YataTimePickerLauncher(
        show = showSnoozeTomorrowPicker,
        initialTime = TaskScheduleUtils.formatTime(snoozeTomorrowHour, snoozeTomorrowMinute),
        onDismiss = { showSnoozeTomorrowPicker = false },
        onConfirm = { formatted ->
            TaskScheduleUtils.parseTime(formatted)?.let { parsed ->
                viewModel.setSnoozeTomorrowTime(parsed.hour, parsed.minute)
            }
            showSnoozeTomorrowPicker = false
        }
    )

    pickedPhotoBitmap?.let { bitmap ->
        CircularImageCropper(
            source = bitmap,
            onConfirm = { cropped ->
                scope.launch {
                    val savedUri = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        ProfilePhotoUtils.saveCircularProfilePhoto(context, cropped)
                    }
                    viewModel.setUserPhotoUri(savedUri.toString())
                    pickedPhotoBitmap = null
                }
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
    return java.time.Instant.ofEpochMilli(epochMillis).localized()
}

private fun formatBackupTimestamp(isoCreatedTime: String): String {
    return try {
        val instant = java.time.Instant.parse(isoCreatedTime)
        instant.localized()
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
            text = stringResource(R.string.settings_diff_category, label, totalCount),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        titles.forEach { title ->
            Text(
                text = stringResource(R.string.settings_diff_item, title),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        if (totalCount > titles.size) {
            Text(
                text = stringResource(R.string.settings_diff_more, totalCount - titles.size),
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
private fun PinSetupDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_set_pin)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) { newPin = it; error = null } },
                    label = { Text(stringResource(R.string.settings_new_pin_4_8_digits)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) { confirmPin = it; error = null } },
                    label = { Text(stringResource(R.string.action_confirm_pin)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    newPin.length < 4 -> error = "PIN must be at least 4 digits"
                    newPin != confirmPin -> error = "PINs don't match"
                    else -> onConfirm(newPin)
                }
            }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

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
                text = stringResource(R.string.settings_data_kept_when_hidden),
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
private fun AnimatedDivider(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(YataDur.fade)),
        exit = fadeOut(tween(YataDur.fade))
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
}

/** The primary-colored caps label above each settings group. */
@Composable
private fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary
    )
}

/** The rounded card every settings group sits in. Was copy-pasted per section. */
@Composable
private fun SettingsSectionCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

/** Title + explanatory subtitle on the left, Switch on the right — the shape most settings use. */
@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
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
                text = subtitle,
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
            contentDescription = stringResource(R.string.settings_edit),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

/** A permission status row — the trailing chip is always tappable (both directions go through
 * [onClick], which just opens the relevant system settings screen) and its label/color flips
 * between "Granted" and "Grant" to match current state. */
@Composable
private fun RowDensityPreview(density: TaskRowDensity) {
    val verticalPadding = when (density) {
        TaskRowDensity.COMPACT -> 6.dp
        TaskRowDensity.COMFORTABLE -> 11.dp
        TaskRowDensity.SPACIOUS -> 16.dp
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            listOf("Plan sprint review", "Send invoice reminder").forEachIndexed { index, title ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = verticalPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (index == 0) "Today" else "No due date",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

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

/** Row of seed-color swatches shown when Material You dynamic color is off — "Default" (the
 * app's fixed warm coral palette), 11 curated presets, and a "Custom" slot that opens a free-form
 * color picker. All of them (including presets) feed the same [colorSchemeFromSeed] generator, so
 * picking one is just choosing which seed color to theme from. */
@Composable
private fun ThemeColorPicker(selectedSeedArgb: Int?, onSelect: (Int?) -> Unit) {
    var showCustomPicker by remember { mutableStateOf(false) }
    val presetArgbs = remember { THEME_PRESETS.map { it.seed.toArgb() }.toSet() }
    val isCustomActive = selectedSeedArgb != null && selectedSeedArgb !in presetArgbs

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.settings_theme_color),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
        )
        Text(
            text = stringResource(R.string.settings_theme_color_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            item {
                ThemeSwatch(
                    color = com.mj.yata.ui.theme.LightColors.primary,
                    label = stringResource(R.string.settings_seed_default),
                    selected = selectedSeedArgb == null,
                    onClick = { onSelect(null) }
                )
            }
            items(THEME_PRESETS) { preset ->
                ThemeSwatch(
                    color = preset.seed,
                    label = preset.name,
                    selected = selectedSeedArgb == preset.seed.toArgb(),
                    onClick = { onSelect(preset.seed.toArgb()) }
                )
            }
            item {
                ThemeSwatch(
                    color = if (isCustomActive) Color(selectedSeedArgb!!) else MaterialTheme.colorScheme.surfaceContainerHighest,
                    label = stringResource(R.string.settings_seed_custom),
                    selected = isCustomActive,
                    showAddIcon = !isCustomActive,
                    onClick = { showCustomPicker = true }
                )
            }
        }
    }

    if (showCustomPicker) {
        CustomColorPickerDialog(
            initialColor = if (isCustomActive) Color(selectedSeedArgb!!) else com.mj.yata.ui.theme.LightColors.primary,
            onDismiss = { showCustomPicker = false },
            onConfirm = { color ->
                onSelect(color.toArgb())
                showCustomPicker = false
            }
        )
    }
}

@Composable
private fun ThemeSwatch(
    color: Color,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    showAddIcon: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (showAddIcon) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp).rotate(45f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = androidx.compose.ui.graphics.Color.White
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
