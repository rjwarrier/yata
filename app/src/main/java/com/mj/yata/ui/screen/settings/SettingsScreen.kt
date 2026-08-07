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
import androidx.compose.material.icons.automirrored.filled.HelpOutline
// Section-heading icons.
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.R
import com.mj.yata.data.backup.BackupDiff
import com.mj.yata.domain.model.AppFont
import com.mj.yata.domain.model.BackgroundTint
import com.mj.yata.domain.model.ColorIntensity
import com.mj.yata.domain.model.DateAliasDefinition
import com.mj.yata.domain.model.DateAliasTarget
import com.mj.yata.domain.model.DateFormat
import com.mj.yata.domain.model.DefaultDueDate
import com.mj.yata.domain.model.FabPosition
import com.mj.yata.domain.model.MotionMode
import com.mj.yata.domain.model.SavedThemePreset
import com.mj.yata.domain.model.StartupTab
import com.mj.yata.domain.model.SwipeAction
import com.mj.yata.domain.model.TaskRowDensity
import com.mj.yata.domain.model.TimeFormat
import com.mj.yata.domain.model.ThemeMode
import com.mj.yata.domain.model.YataList
import com.mj.yata.domain.sync.RestorePoint
import kotlin.math.roundToInt
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
import com.mj.yata.ui.widgets.showError
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.toArgb
import com.mj.yata.util.ProfilePhotoUtils
import com.mj.yata.util.selfHostedSyncLockFailure
import com.mj.yata.util.syncLockClearPrompt
import com.mj.yata.util.TaskScheduleUtils
import com.mj.yata.util.localized
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mj.yata.BuildConfig
import com.mj.yata.ui.theme.BodoniModaFamily

private data class SettingsSearchTarget(
    val key: String,
    val title: String,
    val summary: String,
    val keywords: String,
    val destination: SettingsDestination?,
    val icon: ImageVector
)

private data class SettingsHubDestination(
    val title: String,
    val summary: String,
    val destination: SettingsDestination,
    val icon: ImageVector
)

enum class SettingsDestination(val routeSegment: String, private val aliases: Set<String> = emptySet()) {
    APPEARANCE_DISPLAY("appearance_display", setOf("appearance", "display")),
    NAVIGATION_FEATURES("navigation_features", setOf("navigation", "features", "manage")),
    SOUND_FEEDBACK("sound_feedback"),
    TASK_DEFAULTS("task_defaults"),
    NOTIFICATIONS("notifications"),
    PRIVACY_SECURITY("privacy_security"),
    DATA_MANAGEMENT("data_management", setOf("backup_data")),
    BACKUP_SYNC("backup_sync", setOf("remote_backup", "local_backup", "cloud_backup")),
    HELP_ABOUT("help_about");

    companion object {
        fun fromRouteSegment(routeSegment: String?): SettingsDestination? =
            entries.firstOrNull { it.routeSegment == routeSegment || routeSegment in it.aliases }
    }
}

@Composable
private fun SettingsSearchField(
    query: String,
    onQueryChange: (String) -> Unit
) {
    val searchLabel = stringResource(R.string.settings_search_label)
    val focusManager = LocalFocusManager.current
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics { contentDescription = searchLabel },
        singleLine = true,
        shape = CircleShape,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_clear_search))
                }
            }
        },
        placeholder = { Text(stringResource(R.string.settings_search_placeholder)) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        colors = com.mj.yata.ui.widgets.yataFieldColors()
    )
}

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
    onNavigateToTab: (Int) -> Unit,
    onNavigateToTrash: () -> Unit,
    onNavigateToArchive: () -> Unit,
    onNavigateToWelcome: () -> Unit,
    onNavigateToHelpAbout: () -> Unit,
    onNavigateToCrashLog: () -> Unit,
    settingsDestination: SettingsDestination? = null,
    onNavigateToSettingsDestination: (SettingsDestination) -> Unit = {},
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
    val reduceMotionEnabled = uiState.reduceMotionEnabled
    val motionMode = uiState.motionMode
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
    val colorIntensity by viewModel.colorIntensity.collectAsStateWithLifecycle()
    val backgroundTint by viewModel.backgroundTint.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val taskCardBackground by viewModel.taskCardBackground.collectAsStateWithLifecycle()
    val trashRetentionDays by viewModel.trashRetentionDays.collectAsStateWithLifecycle()
    val autoArchiveDays by viewModel.autoArchiveDays.collectAsStateWithLifecycle()
    val demoModeEnabled by viewModel.demoModeEnabled.collectAsStateWithLifecycle()
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
    val todayShowUpcomingWhenEmpty by viewModel.todayShowUpcomingWhenEmpty.collectAsStateWithLifecycle()
    val peopleFeatureEnabled = uiState.peopleFeatureEnabled
    val tagsFeatureEnabled = uiState.tagsFeatureEnabled
    val projectsFeatureEnabled = uiState.projectsFeatureEnabled
    val lists = uiState.lists
    val backupIntervalMinutes = uiState.backupIntervalMinutes
    val localBackupEnabled = uiState.localBackupEnabled
    val localBackupLastAt = uiState.localBackupLastAt
    val sftpBackupEnabled = uiState.sftpBackupEnabled
    val sftpHost = uiState.sftpHost
    val sftpPort = uiState.sftpPort
    val sftpUsername = uiState.sftpUsername
    val sftpAuthMethod = uiState.sftpAuthMethod
    val sftpRemoteDir = uiState.sftpRemoteDir
    val sftpIntervalMinutes = uiState.sftpIntervalMinutes
    val sftpLastBackupAt = uiState.sftpLastBackupAt
    val sftpHostKeyFingerprint = uiState.sftpHostKeyFingerprint
    val remoteBackupProtocol = uiState.remoteBackupProtocol
    val ftpUseTls = uiState.ftpUseTls
    val sftpKeepCount = uiState.sftpKeepCount
    val isFtpProtocol = remoteBackupProtocol == com.mj.yata.domain.model.RemoteBackupProtocol.FTP
    val isGitHubProtocol = remoteBackupProtocol == com.mj.yata.domain.model.RemoteBackupProtocol.GITHUB
    val githubOwner = uiState.githubOwner
    val githubRepo = uiState.githubRepo
    val githubBranch = uiState.githubBranch
    val githubApiBase = uiState.githubApiBase
    val remoteConfigured = if (isGitHubProtocol) {
        githubOwner.isNotBlank() && githubRepo.isNotBlank()
    } else {
        sftpHost.isNotBlank()
    }
    val dateAliasDefinitions = uiState.dateAliasDefinitions
    val savedThemePresetDefinitions = uiState.savedThemePresetDefinitions
    val taskerIntegrationEnabled = uiState.taskerIntegrationEnabled

    val voiceLanguage by viewModel.voiceRecognitionLanguage.collectAsStateWithLifecycle()
    var showVoiceLanguageMenu by remember { mutableStateOf(false) }
    var showDefaultListMenu by remember { mutableStateOf(false) }
    var showStartupTabMenu by remember { mutableStateOf(false) }
    var showSwipeRightMenu by remember { mutableStateOf(false) }
    var showSwipeLeftMenu by remember { mutableStateOf(false) }
    var showTimeFormatMenu by remember { mutableStateOf(false) }
    var showDateFormatMenu by remember { mutableStateOf(false) }
    val startupTab by viewModel.startupTab.collectAsStateWithLifecycle()
    val swipeRightAction by viewModel.swipeRightAction.collectAsStateWithLifecycle()
    val swipeLeftAction by viewModel.swipeLeftAction.collectAsStateWithLifecycle()
    val confettiEnabled by viewModel.confettiEnabled.collectAsStateWithLifecycle()
    val timeFormat by viewModel.timeFormat.collectAsStateWithLifecycle()
    val dateFormat by viewModel.dateFormat.collectAsStateWithLifecycle()
    var showTrashRetentionMenu by remember { mutableStateOf(false) }
    var showAutoArchiveMenu by remember { mutableStateOf(false) }
    var showAgendaTimePicker by remember { mutableStateOf(false) }
    var showReminderTimePicker by remember { mutableStateOf(false) }
    var showSnoozeTonightPicker by remember { mutableStateOf(false) }
    var showSnoozeTomorrowPicker by remember { mutableStateOf(false) }
    var newDateAlias by rememberSaveable { mutableStateOf("") }
    var selectedDateAliasTarget by rememberSaveable { mutableStateOf(DateAliasTarget.TOMORROW) }
    var showDateAliasTargetMenu by remember { mutableStateOf(false) }
    var showThemePresetDialog by remember { mutableStateOf(false) }
    var themePresetName by rememberSaveable { mutableStateOf("") }

    var showProfileDialog by remember { mutableStateOf(false) }
    var profileDraftName by rememberSaveable { mutableStateOf("") }
    var profileDraftEmail by rememberSaveable { mutableStateOf("") }

    val todayBadgeCount = uiState.todayRemainingCount

    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showRestoreLocalDialog by remember { mutableStateOf(false) }
    var isDeletingAll by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    var showSftpConfigDialog by remember { mutableStateOf(false) }
    var showSftpRestoreDialog by remember { mutableStateOf(false) }
    var showClearSyncLockDialog by remember { mutableStateOf(false) }
    var clearSyncLockDialogMessage by remember { mutableStateOf<String?>(null) }
    var showGitHubPatHelpDialog by remember { mutableStateOf(false) }
    var demoModeFeedback by remember { mutableStateOf<Int?>(null) }
    var isLoadingSftpBackups by remember { mutableStateOf(false) }
    var sftpBackupList by remember { mutableStateOf<List<RestorePoint>>(emptyList()) }
    var isRestoringSftpBackup by remember { mutableStateOf(false) }
    var isClearingSyncLock by remember { mutableStateOf(false) }
    var pendingSftpRestorePoint by remember { mutableStateOf<RestorePoint?>(null) }
    var sftpBackupSummary by remember { mutableStateOf<com.mj.yata.domain.model.BackupSummary?>(null) }
    var isInspectingSftpBackup by remember { mutableStateOf(false) }
    var sftpInspectError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val settingsListState = rememberLazyListState()
    LaunchedEffect(demoModeFeedback) {
        if (demoModeFeedback != null) {
            kotlinx.coroutines.delay(3_000)
            demoModeFeedback = null
        }
    }
    var settingsSearchQuery by rememberSaveable { mutableStateOf("") }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showResetSettingsDialog by remember { mutableStateOf(false) }

    val settingsHubDestinations = listOf(
        SettingsHubDestination(stringResource(R.string.settings_section_appearance_display), stringResource(R.string.settings_search_appearance_display_summary), SettingsDestination.APPEARANCE_DISPLAY, Icons.Default.Palette),
        SettingsHubDestination(stringResource(R.string.settings_section_navigation_features), stringResource(R.string.settings_search_navigation_features_summary), SettingsDestination.NAVIGATION_FEATURES, Icons.Default.Navigation),
        SettingsHubDestination(stringResource(R.string.settings_section_sound_feedback), stringResource(R.string.settings_search_feedback_summary), SettingsDestination.SOUND_FEEDBACK, Icons.Default.VolumeUp),
        SettingsHubDestination(stringResource(R.string.settings_section_task_defaults), stringResource(R.string.settings_search_defaults_summary), SettingsDestination.TASK_DEFAULTS, Icons.Default.TaskAlt),
        SettingsHubDestination(stringResource(R.string.settings_section_notifications), stringResource(R.string.settings_search_notifications_summary), SettingsDestination.NOTIFICATIONS, Icons.Default.Notifications),
        SettingsHubDestination(stringResource(R.string.settings_section_privacy), stringResource(R.string.settings_search_privacy_summary), SettingsDestination.PRIVACY_SECURITY, Icons.Default.Lock),
        SettingsHubDestination(stringResource(R.string.settings_section_data_management), stringResource(R.string.settings_search_data_summary), SettingsDestination.DATA_MANAGEMENT, Icons.Default.Storage),
        SettingsHubDestination(stringResource(R.string.settings_section_backup_sync), stringResource(R.string.settings_search_backup_sync_summary), SettingsDestination.BACKUP_SYNC, Icons.Default.CloudSync),
        SettingsHubDestination(stringResource(R.string.settings_section_help_about), stringResource(R.string.settings_search_help_summary), SettingsDestination.HELP_ABOUT, Icons.AutoMirrored.Filled.HelpOutline)
    )
    val settingsSearchTargets = listOf(
        SettingsSearchTarget("profile", stringResource(R.string.settings_section_profile), stringResource(R.string.settings_search_profile_summary), "name email photo account", null, Icons.Default.Person),
        SettingsSearchTarget("language", stringResource(R.string.settings_app_language), stringResource(R.string.settings_app_language_desc), "language locale translation system default app", null, Icons.Default.Language),
        SettingsSearchTarget("appearance", stringResource(R.string.settings_section_appearance), stringResource(R.string.settings_search_appearance_summary), "theme dark light amoled color font motion intensity tint saturation vivid muted background", SettingsDestination.APPEARANCE_DISPLAY, Icons.Default.Palette),
        SettingsSearchTarget("display", stringResource(R.string.settings_section_display), stringResource(R.string.settings_search_display_summary), "scale text density compact spacious card cards row", SettingsDestination.APPEARANCE_DISPLAY, Icons.Default.Tune),
        SettingsSearchTarget("motion_mode", "Motion mode", "Full, reduced, or off", "animation reduce motion off accessibility", SettingsDestination.APPEARANCE_DISPLAY, Icons.Default.Tune),
        SettingsSearchTarget("theme_presets", "Theme presets", "Save and reapply personal themes", "theme preset saved color font material you", SettingsDestination.APPEARANCE_DISPLAY, Icons.Default.Palette),
        SettingsSearchTarget("navigation", stringResource(R.string.settings_section_navigation), stringResource(R.string.settings_search_navigation_summary), "bottom navigation labels fab quick add", SettingsDestination.NAVIGATION_FEATURES, Icons.Default.Navigation),
        SettingsSearchTarget("features", stringResource(R.string.settings_section_features), stringResource(R.string.settings_search_features_summary), "today upcoming projects people tags", SettingsDestination.NAVIGATION_FEATURES, Icons.Default.Extension),
        SettingsSearchTarget("manage", stringResource(R.string.settings_section_manage), stringResource(R.string.settings_search_manage_summary), "manage projects people tags", SettingsDestination.NAVIGATION_FEATURES, Icons.Default.Build),
        SettingsSearchTarget("tasker", "Tasker", "Automation access for creating tasks", "tasker automation plugin create task", SettingsDestination.NAVIGATION_FEATURES, Icons.Default.Extension),
        SettingsSearchTarget("sound_feedback", stringResource(R.string.settings_section_sound_feedback), stringResource(R.string.settings_search_feedback_summary), "sound haptic swipe undo", SettingsDestination.SOUND_FEEDBACK, Icons.Default.VolumeUp),
        SettingsSearchTarget("task_defaults", stringResource(R.string.settings_section_task_defaults), stringResource(R.string.settings_search_defaults_summary), "due priority list reminder week voice assign assignee me", SettingsDestination.TASK_DEFAULTS, Icons.Default.TaskAlt),
        SettingsSearchTarget("date_aliases", "Date aliases", "Custom quick-add words for due dates", "quick add natural language date aliases keywords today tomorrow", SettingsDestination.TASK_DEFAULTS, Icons.Default.CalendarMonth),
        SettingsSearchTarget("notifications", stringResource(R.string.settings_section_notifications), stringResource(R.string.settings_search_notifications_summary), "alarm battery agenda overdue snooze delivery", SettingsDestination.NOTIFICATIONS, Icons.Default.Notifications),
        SettingsSearchTarget("privacy_security", stringResource(R.string.settings_section_privacy), stringResource(R.string.settings_search_privacy_summary), "privacy lock pin timeout security", SettingsDestination.PRIVACY_SECURITY, Icons.Default.Lock),
        SettingsSearchTarget("data_management", stringResource(R.string.settings_section_data_management), stringResource(R.string.settings_search_data_summary), "export import csv calendar trash archive delete data", SettingsDestination.DATA_MANAGEMENT, Icons.Default.Storage),
        SettingsSearchTarget("remote_backup", stringResource(R.string.settings_section_cloud_backup), stringResource(R.string.settings_search_cloud_summary), "self hosted server sync backup sftp ftp restore frequency", SettingsDestination.BACKUP_SYNC, Icons.Default.CloudSync),
        SettingsSearchTarget("local_backup", stringResource(R.string.settings_section_local_backup), stringResource(R.string.settings_search_local_summary), "local backup restore", SettingsDestination.BACKUP_SYNC, Icons.Default.Save),
        SettingsSearchTarget("help_about", stringResource(R.string.settings_section_help_about), stringResource(R.string.settings_search_help_summary), "help about version guide crash logs", SettingsDestination.HELP_ABOUT, Icons.AutoMirrored.Filled.HelpOutline)
    )
    val normalizedSettingsQuery = settingsSearchQuery.trim().lowercase()
    val filteredSettingsTargets = remember(normalizedSettingsQuery, settingsSearchTargets) {
        if (normalizedSettingsQuery.isBlank()) emptyList() else settingsSearchTargets.filter {
            val haystack = "${it.title} ${it.summary} ${it.keywords}".lowercase()
            normalizedSettingsQuery.split(Regex("\\s+")).all(haystack::contains)
        }
    }

    var isBackingUp by remember { mutableStateOf(false) }
    var showFrequencyDialog by remember { mutableStateOf(false) }
    var showBackupDiffDialog by remember { mutableStateOf(false) }
    var isLoadingBackupDiff by remember { mutableStateOf(false) }
    var backupDiffResult by remember { mutableStateOf<BackupDiff?>(null) }
    var backupDiffError by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val profileAvatarPresets = listOf(
        ProfilePhotoUtils.PresetAvatar.LOOP,
        ProfilePhotoUtils.PresetAvatar.PERSON,
        ProfilePhotoUtils.PresetAvatar.SMILE,
        ProfilePhotoUtils.PresetAvatar.GLASSES,
        ProfilePhotoUtils.PresetAvatar.FRIENDS,
        ProfilePhotoUtils.PresetAvatar.TEAM,
        ProfilePhotoUtils.PresetAvatar.FAMILY,
        ProfilePhotoUtils.PresetAvatar.HELPER,
        ProfilePhotoUtils.PresetAvatar.THINKER,
        ProfilePhotoUtils.PresetAvatar.CHILD,
        ProfilePhotoUtils.PresetAvatar.GUIDE,
        ProfilePhotoUtils.PresetAvatar.CREATOR,
        ProfilePhotoUtils.PresetAvatar.LISTENER,
        ProfilePhotoUtils.PresetAvatar.LEADER,
        ProfilePhotoUtils.PresetAvatar.FOCUS,
        ProfilePhotoUtils.PresetAvatar.STAR,
        ProfilePhotoUtils.PresetAvatar.HEART,
        ProfilePhotoUtils.PresetAvatar.ROCKET,
        ProfilePhotoUtils.PresetAvatar.WORK,
        ProfilePhotoUtils.PresetAvatar.LEAF,
        ProfilePhotoUtils.PresetAvatar.SPARK,
        ProfilePhotoUtils.PresetAvatar.HOME,
        ProfilePhotoUtils.PresetAvatar.STUDY,
        ProfilePhotoUtils.PresetAvatar.TRAVEL,
        ProfilePhotoUtils.PresetAvatar.FITNESS,
        ProfilePhotoUtils.PresetAvatar.FOOD,
        ProfilePhotoUtils.PresetAvatar.BOOK,
        ProfilePhotoUtils.PresetAvatar.MUSIC,
        ProfilePhotoUtils.PresetAvatar.CODE,
        ProfilePhotoUtils.PresetAvatar.ART,
        ProfilePhotoUtils.PresetAvatar.CAMERA,
        ProfilePhotoUtils.PresetAvatar.IDEA,
        ProfilePhotoUtils.PresetAvatar.SHIELD,
        ProfilePhotoUtils.PresetAvatar.CLOUD,
        ProfilePhotoUtils.PresetAvatar.CHECK,
        ProfilePhotoUtils.PresetAvatar.COFFEE,
        ProfilePhotoUtils.PresetAvatar.CALENDAR,
        ProfilePhotoUtils.PresetAvatar.WAVE,
        ProfilePhotoUtils.PresetAvatar.ORBIT,
        ProfilePhotoUtils.PresetAvatar.BLOOM
    )
    val currentSettingsTitle =
        settingsDestination?.let { destination ->
            settingsHubDestinations.firstOrNull { it.destination == destination }?.title
        } ?: stringResource(R.string.settings_settings)
    val isSettingsRoot = settingsDestination == null
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
            if (isSettingsRoot) {
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
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(currentSettingsTitle,
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
                    if (isSettingsRoot) {
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
            if (isSettingsRoot) {
                item(key = "settings_search") {
                    SettingsSearchField(
                        query = settingsSearchQuery,
                        onQueryChange = { settingsSearchQuery = it }
                    )
                }
                item(key = "profile") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier.clickable {
                                        if (userPhotoUri.isNullOrBlank()) {
                                            photoPickerLauncher.launch(
                                                androidx.activity.result.PickVisualMediaRequest(
                                                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                                )
                                            )
                                        } else {
                                            scope.launch {
                                                val currentBitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                    try {
                                                        ProfilePhotoUtils.decodeSampledBitmap(
                                                            context,
                                                            android.net.Uri.parse(userPhotoUri),
                                                            maxDimension = 1600
                                                        )
                                                    } catch (e: Exception) {
                                                        null
                                                    }
                                                }
                                                if (currentBitmap != null) {
                                                    pickedPhotoBitmap = currentBitmap
                                                } else {
                                                    photoPickerLauncher.launch(
                                                        androidx.activity.result.PickVisualMediaRequest(
                                                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                ) {
                                    com.mj.yata.ui.widgets.PersonAvatar(
                                        initials = com.mj.yata.util.initialsFor(userName),
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
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            profileDraftName = userName
                                            profileDraftEmail = userEmail
                                            showProfileDialog = true
                                        },
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = userName.ifBlank { stringResource(R.string.profile_add_name) },
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (userName.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = userEmail.ifBlank { stringResource(R.string.profile_add_email) },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        profileDraftName = userName
                                        profileDraftEmail = userEmail
                                        showProfileDialog = true
                                    }
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.settings_edit))
                                }
                            }
                        }
                    }
                }
            item(key = "language") {
                LanguageSettingsSection(
                    selectedLanguage = appLanguage,
                    onLanguageSelected = viewModel::setAppLanguage
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
                                            target.destination?.let(onNavigateToSettingsDestination)
                                                ?: scope.launch {
                                                    settingsListState.animateScrollToItem(
                                                        if (target.key == "language") 1 else 0
                                                    )
                                                }
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
            items(settingsHubDestinations, key = { it.destination.routeSegment }) { target ->
                SettingsDestinationCard(
                    icon = target.icon,
                    title = target.title,
                    summary = target.summary,
                    onClick = { onNavigateToSettingsDestination(target.destination) }
                )
            }
            }
        if (settingsDestination == SettingsDestination.APPEARANCE_DISPLAY) {
        item {
            // 2. Preferences Section
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

                    // Both apply on top of whatever scheme is in play — Material You, a custom
                    // seed, or the built-in palette — so they stay useful regardless of the
                    // toggles above them.
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Theme presets",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    text = "Save this color, font, and theme combination.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = {
                                themePresetName = ""
                                showThemePresetDialog = true
                            }) {
                                Text("Save")
                            }
                        }
                        val savedPresets = savedThemePresetDefinitions.mapNotNull(SavedThemePreset::decode).sortedBy { it.name }
                        if (savedPresets.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(savedPresets, key = { it.encode() }) { preset ->
                                    AssistChip(
                                        onClick = { viewModel.applyThemePreset(preset.encode()) },
                                        label = { Text(preset.name) },
                                        trailingIcon = {
                                            IconButton(
                                                onClick = { viewModel.removeThemePreset(preset.encode()) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Remove ${preset.name}",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    StopSliderSetting(
                        title = stringResource(R.string.settings_color_intensity),
                        description = stringResource(R.string.settings_color_intensity_desc),
                        stopLabels = colorIntensityLabels(),
                        selectedIndex = colorIntensity.ordinal,
                        onSelect = { viewModel.setColorIntensity(ColorIntensity.entries[it]) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    StopSliderSetting(
                        title = stringResource(R.string.settings_background_tint),
                        description = stringResource(R.string.settings_background_tint_desc),
                        stopLabels = backgroundTintLabels(),
                        selectedIndex = backgroundTint.ordinal,
                        onSelect = { viewModel.setBackgroundTint(BackgroundTint.entries[it]) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

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
        }

        if (settingsDestination == SettingsDestination.APPEARANCE_DISPLAY) {
        item {
            // 3. Display Section
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

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Motion mode",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = "Control app animations without changing other visual settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SegmentedControl(
                            items = listOf(MotionMode.FULL, MotionMode.REDUCED, MotionMode.OFF),
                            selectedItem = motionMode,
                            onItemSelected = { viewModel.setMotionMode(it) },
                            labelProvider = {
                                when (it) {
                                    MotionMode.FULL -> "Full"
                                    MotionMode.REDUCED -> "Reduced"
                                    MotionMode.OFF -> "Off"
                                }
                            }
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

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Sits with density rather than under Appearance: both are about the shape of
                    // a task list rather than the app's colours, and they interact — a card at
                    // Compact is a very different thing from a card at Spacious.
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_task_cards),
                        subtitle = stringResource(R.string.settings_task_cards_desc),
                        checked = taskCardBackground,
                        onCheckedChange = { viewModel.setTaskCardBackground(it) }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // How dates and times are written. Both default to following the device, which
                    // is what the app should have done from the start — the old hardcoded patterns
                    // showed a 24-hour-clock user "5:00 PM" and wrote "Jul 4" in locales that say
                    // "4 Jul". These change display only; stored values are untouched.
                    val timeFormatLabels = mapOf(
                        TimeFormat.SYSTEM to stringResource(R.string.settings_time_format_system),
                        TimeFormat.TWELVE_HOUR to stringResource(R.string.settings_time_format_12h),
                        TimeFormat.TWENTY_FOUR_HOUR to stringResource(R.string.settings_time_format_24h)
                    )
                    Box {
                        SettingsRow(
                            label = stringResource(R.string.settings_time_format),
                            value = timeFormatLabels[timeFormat].orEmpty(),
                            onClick = { showTimeFormatMenu = true }
                        )
                        DropdownMenu(expanded = showTimeFormatMenu, onDismissRequest = { showTimeFormatMenu = false }) {
                            TimeFormat.entries.forEach { format ->
                                DropdownMenuItem(
                                    text = { Text(timeFormatLabels[format].orEmpty()) },
                                    onClick = {
                                        viewModel.setTimeFormat(format)
                                        showTimeFormatMenu = false
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    val dateFormatLabels = mapOf(
                        DateFormat.SYSTEM to stringResource(R.string.settings_date_format_system),
                        DateFormat.DAY_FIRST to stringResource(R.string.settings_date_format_day_first),
                        DateFormat.MONTH_FIRST to stringResource(R.string.settings_date_format_month_first),
                        DateFormat.ISO to stringResource(R.string.settings_date_format_iso)
                    )
                    Box {
                        SettingsRow(
                            label = stringResource(R.string.settings_date_format),
                            value = dateFormatLabels[dateFormat].orEmpty(),
                            onClick = { showDateFormatMenu = true }
                        )
                        DropdownMenu(expanded = showDateFormatMenu, onDismissRequest = { showDateFormatMenu = false }) {
                            DateFormat.entries.forEach { format ->
                                DropdownMenuItem(
                                    text = { Text(dateFormatLabels[format].orEmpty()) },
                                    onClick = {
                                        viewModel.setDateFormat(format)
                                        showDateFormatMenu = false
                                    }
                                )
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.settings_date_format_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        }
        if (settingsDestination == SettingsDestination.NAVIGATION_FEATURES) {
        item {
            // Navigation — everything that changes the bottom nav's shape or contents. Split out
            // of the old PREFERENCES catch-all, which mixed these with theming and task defaults.
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
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                val startupLastUsed = stringResource(R.string.settings_startup_last_used)
                val startupLabels = mapOf(
                    StartupTab.LAST_USED to startupLastUsed,
                    StartupTab.TODAY to stringResource(R.string.tab_today),
                    StartupTab.PROJECTS to stringResource(R.string.tab_projects),
                    StartupTab.PEOPLE to stringResource(R.string.tab_people),
                    StartupTab.TAGS to stringResource(R.string.tab_tags),
                    StartupTab.UPCOMING to stringResource(R.string.tab_upcoming)
                )
                Box {
                    SettingsRow(
                        label = stringResource(R.string.settings_startup_tab),
                        value = startupLabels[startupTab] ?: startupLastUsed,
                        onClick = { showStartupTabMenu = true }
                    )
                    DropdownMenu(expanded = showStartupTabMenu, onDismissRequest = { showStartupTabMenu = false }) {
                        StartupTab.entries.forEach { tab ->
                            DropdownMenuItem(
                                text = { Text(startupLabels[tab] ?: tab.name) },
                                onClick = {
                                    viewModel.setStartupTab(tab)
                                    showStartupTabMenu = false
                                }
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.settings_startup_tab_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingsToggleRow(
                    title = "Tasker integration",
                    subtitle = "Allow Tasker profiles to create tasks through the Yata plugin.",
                    checked = taskerIntegrationEnabled,
                    onCheckedChange = { viewModel.setTaskerIntegrationEnabled(it) }
                )
            }
        }
        }

        if (settingsDestination == SettingsDestination.SOUND_FEEDBACK) {
        item {
            // Sound & feedback — the app's response to an action, as opposed to its layout.
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

                // Only worth showing when swiping is on at all — otherwise these two configure
                // something that can't happen.
                if (taskSwipeActionsEnabled) {
                    val swipeActionLabels = mapOf(
                        SwipeAction.NONE to stringResource(R.string.settings_swipe_action_none),
                        SwipeAction.COMPLETE to stringResource(R.string.settings_swipe_action_complete),
                        SwipeAction.DELETE to stringResource(R.string.settings_swipe_action_delete),
                        SwipeAction.SNOOZE_TOMORROW to stringResource(R.string.settings_swipe_action_snooze),
                        SwipeAction.EDIT_TITLE to stringResource(R.string.settings_swipe_action_edit)
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Box {
                        SettingsRow(
                            label = stringResource(R.string.settings_swipe_right_action),
                            value = swipeActionLabels[swipeRightAction].orEmpty(),
                            onClick = { showSwipeRightMenu = true }
                        )
                        DropdownMenu(expanded = showSwipeRightMenu, onDismissRequest = { showSwipeRightMenu = false }) {
                            SwipeAction.entries.forEach { action ->
                                DropdownMenuItem(
                                    text = { Text(swipeActionLabels[action].orEmpty()) },
                                    onClick = {
                                        viewModel.setSwipeRightAction(action)
                                        showSwipeRightMenu = false
                                    }
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Box {
                        SettingsRow(
                            label = stringResource(R.string.settings_swipe_left_action),
                            value = swipeActionLabels[swipeLeftAction].orEmpty(),
                            onClick = { showSwipeLeftMenu = true }
                        )
                        DropdownMenu(expanded = showSwipeLeftMenu, onDismissRequest = { showSwipeLeftMenu = false }) {
                            SwipeAction.entries.forEach { action ->
                                DropdownMenuItem(
                                    text = { Text(swipeActionLabels[action].orEmpty()) },
                                    onClick = {
                                        viewModel.setSwipeLeftAction(action)
                                        showSwipeLeftMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingsToggleRow(
                    title = stringResource(R.string.settings_confetti),
                    subtitle = stringResource(R.string.settings_confetti_desc),
                    checked = confettiEnabled,
                    onCheckedChange = { viewModel.setConfettiEnabled(it) }
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
        }

        if (settingsDestination == SettingsDestination.TASK_DEFAULTS) {
        item {
            // Task defaults — what a newly created task inherits, plus the calendar/voice
            // conventions the app assumes. Previously buried at the end of PREFERENCES.
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

                SettingsToggleRow(
                    title = stringResource(R.string.settings_today_show_upcoming_when_empty),
                    subtitle = stringResource(R.string.settings_today_show_upcoming_when_empty_summary),
                    checked = todayShowUpcomingWhenEmpty,
                    onCheckedChange = { viewModel.setTodayShowUpcomingWhenEmpty(it) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

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

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Date aliases",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = "Teach quick add your own date words.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newDateAlias,
                            onValueChange = { newDateAlias = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Word") }
                        )
                        Box {
                            AssistChip(
                                onClick = { showDateAliasTargetMenu = true },
                                label = { Text(selectedDateAliasTarget.label) }
                            )
                            DropdownMenu(
                                expanded = showDateAliasTargetMenu,
                                onDismissRequest = { showDateAliasTargetMenu = false }
                            ) {
                                DateAliasTarget.entries.forEach { target ->
                                    DropdownMenuItem(
                                        text = { Text(target.label) },
                                        onClick = {
                                            selectedDateAliasTarget = target
                                            showDateAliasTargetMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Button(
                        onClick = {
                            viewModel.addDateAlias(newDateAlias, selectedDateAliasTarget)
                            newDateAlias = ""
                        },
                        enabled = newDateAlias.isNotBlank()
                    ) {
                        Text("Add alias")
                    }
                    val aliases = dateAliasDefinitions.mapNotNull(DateAliasDefinition::decode).sortedBy { it.alias }
                    if (aliases.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(aliases, key = { it.encode() }) { alias ->
                                AssistChip(
                                    onClick = {},
                                    label = { Text("${alias.alias} -> ${alias.target.label}") },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { viewModel.removeDateAlias(alias.encode()) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove ${alias.alias}",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
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
                    value = TaskScheduleUtils.displayTime(defaultReminderHour, defaultReminderMinute),
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
        }
        if (settingsDestination == SettingsDestination.NOTIFICATIONS) {
        item {
            // Notifications Section — Android (especially Samsung/One UI) silently downgrades
            // reminders to a fuzzy ~1hr-late delivery window, or kills them outright in Doze,
            // unless these two OS-level permissions are granted. Neither is requestable at
            // runtime like POST_NOTIFICATIONS — the user has to grant them from system settings.
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
                            value = TaskScheduleUtils.displayTime(dailyAgendaHour, dailyAgendaMinute),
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

                    // Sound and vibration are per-channel and belong to Android, not to us —
                    // reimplementing a tone picker here would only fight the system UI, which
                    // already does it per notification type. This just points at it.
                    SettingsRow(
                        label = stringResource(R.string.settings_system_notifications),
                        value = "",
                        onClick = { NotificationPermissionUtils.openNotificationSettings(context) }
                    )
                    Text(
                        text = stringResource(R.string.settings_system_notifications_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        value = TaskScheduleUtils.displayTime(snoozeTonightHour, snoozeTonightMinute),
                        onClick = { showSnoozeTonightPicker = true }
                    )
                    SettingsRow(
                        label = stringResource(R.string.settings_snooze_tomorrow),
                        value = TaskScheduleUtils.displayTime(snoozeTomorrowHour, snoozeTomorrowMinute),
                        onClick = { showSnoozeTomorrowPicker = true }
                    )
                }
            }
        }
        }
        if (settingsDestination == SettingsDestination.NAVIGATION_FEATURES) {
        item {
            // Features Section — hides the entire tab/pickers/chips for a feature, but never
            // touches stored data, so re-enabling shows everything exactly as it was.
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
        }
        if (settingsDestination == SettingsDestination.NAVIGATION_FEATURES) {
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
        }
        if (settingsDestination == SettingsDestination.PRIVACY_SECURITY) {
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
        }
        if (settingsDestination == SettingsDestination.DATA_MANAGEMENT) {
        item {
            // 5. Backup/Data Section
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
        }
        if (settingsDestination == SettingsDestination.BACKUP_SYNC) {
        item {
            // 4. Remote Backup Section: off-device destinations live together so the user can
            // reason about "where else is my data copied?" without jumping between cards.
            SettingsSectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
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
                            text = stringResource(R.string.settings_cloud_backup_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Dns,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.settings_sftp_backup),
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = if (!remoteConfigured) {
                                            stringResource(R.string.settings_sftp_backup_summary)
                                        } else if (isGitHubProtocol) {
                                            "GitHub: $githubOwner/$githubRepo${githubBranch.takeIf { it.isNotBlank() }?.let { " @ $it" } ?: ""}"
                                        } else {
                                            val protocolLabel = when {
                                                isFtpProtocol && ftpUseTls -> "FTPS"
                                                isFtpProtocol -> "FTP"
                                                else -> "SFTP"
                                            }
                                            "$sftpUsername@$sftpHost:$sftpPort ($protocolLabel)"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = sftpBackupEnabled,
                                onCheckedChange = { viewModel.setSftpBackupEnabled(it) }
                            )
                        }

                        if (sftpBackupEnabled) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showSftpConfigDialog = true }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.settings_sftp_configure_server),
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = if (!remoteConfigured) {
                                            stringResource(R.string.settings_sftp_not_configured)
                                        } else if (isGitHubProtocol) {
                                            "GitHub: $githubOwner/$githubRepo${githubBranch.takeIf { it.isNotBlank() }?.let { " @ $it" } ?: ""}"
                                        } else {
                                            val protocolLabel = when {
                                                isFtpProtocol && ftpUseTls -> "FTPS"
                                                isFtpProtocol -> "FTP"
                                                else -> "SFTP"
                                            }
                                            "$sftpUsername@$sftpHost:$sftpPort ($protocolLabel)"
                                        },
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
                                    .clickable(
                                        enabled = remoteConfigured && !isLoadingBackupDiff
                                    ) {
                                        showBackupDiffDialog = true
                                        isLoadingBackupDiff = true
                                        backupDiffResult = null
                                        backupDiffError = null
                                        viewModel.compareWithLastSelfHostedBackup { result ->
                                            isLoadingBackupDiff = false
                                            result.fold(
                                                onSuccess = { backupDiffResult = it },
                                                onFailure = {
                                                    backupDiffError = it.message ?: "Couldn't compare with backup"
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
                                    tint = if (remoteConfigured) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    }
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.settings_compare_with_backup),
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                        color = if (remoteConfigured) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                        }
                                    )
                                    if (!remoteConfigured) {
                                        Text(
                                            text = stringResource(R.string.settings_sftp_not_configured),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            if (remoteConfigured) {
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Text(
                                        text = stringResource(R.string.settings_last_sftp_backup, formatRelativeBackupTime(sftpLastBackupAt)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    // Host-key trust is an SFTP concept only -- FTPS validates
                                    // certificates through the platform trust store instead, with
                                    // no separate pin/confirm step to report status on here.
                                    if (!isFtpProtocol && !isGitHubProtocol) {
                                        Text(
                                            text = stringResource(
                                                if (sftpHostKeyFingerprint != null) {
                                                    R.string.settings_sftp_server_trusted
                                                } else {
                                                    R.string.settings_sftp_server_not_yet_trusted
                                                }
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (sftpHostKeyFingerprint != null) {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            } else {
                                                MaterialTheme.colorScheme.error
                                            }
                                        )
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showSftpRestoreDialog = true
                                            isLoadingSftpBackups = true
                                            viewModel.listRemoteRestorePoints { result ->
                                                isLoadingSftpBackups = false
                                                sftpBackupList = result.getOrDefault(emptyList())
                                                result.exceptionOrNull()?.let { error ->
                                                    scope.launch {
                                                        snackbarHostState.showError(error.message ?: context.getString(R.string.export_failed))
                                                    }
                                                }
                                            }
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_restore_sftp_backup),
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }

                                if (!isGitHubProtocol) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = !isClearingSyncLock) {
                                                clearSyncLockDialogMessage = null
                                                showClearSyncLockDialog = true
                                            }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteForever,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(R.string.settings_clear_sync_lock),
                                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                                color = MaterialTheme.colorScheme.error
                                            )
                                            Text(
                                                text = stringResource(R.string.settings_clear_sync_lock_summary),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (isClearingSyncLock) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (sftpBackupEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showFrequencyDialog = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_backup_frequency),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = syncFrequencyLabel(backupIntervalMinutes),
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

                    if (!isGitHubProtocol) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            val remoteKeepCount = sftpKeepCount
                            var keepCountPosition by remember(remoteKeepCount) { mutableFloatStateOf(remoteKeepCount.toFloat()) }
                            Text(
                                text = stringResource(R.string.settings_backups_to_keep),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = stringResource(R.string.settings_remote_backups_to_keep_summary, keepCountPosition.toInt()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = keepCountPosition,
                                onValueChange = { keepCountPosition = it },
                                onValueChangeFinished = { viewModel.setRemoteBackupKeepCount(keepCountPosition.toInt()) },
                                valueRange = 2f..15f,
                                steps = 12 // 14 stops total (min + 12 + max), 1 apart
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isBackingUp) {
                                isBackingUp = true
                                viewModel.backupAllNow { results ->
                                    isBackingUp = false
                                    val syncLockFailure = results.selfHostedSyncLockFailure()
                                    if (syncLockFailure != null) {
                                        clearSyncLockDialogMessage = syncLockClearPrompt(context, syncLockFailure)
                                        showClearSyncLockDialog = true
                                    } else {
                                        scope.launch {
                                            reportBackupResults(results, snackbarHostState, context)
                                        }
                                    }
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_sync_and_backup_now),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                            )
                        }
                        if (isBackingUp) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
        }
        if (settingsDestination == SettingsDestination.BACKUP_SYNC) {
        item {
            // Local Backup Section — encrypted, on-device, no account needed.
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
        }
        if (settingsDestination == SettingsDestination.HELP_ABOUT) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ABOUT",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                AboutYataCard(
                    demoModeEnabled = demoModeEnabled,
                    demoModeFeedback = demoModeFeedback,
                    onToggleDemoMode = {
                        viewModel.toggleDemoMode()
                        demoModeFeedback = if (demoModeEnabled) {
                            R.string.help_demo_mode_off
                        } else {
                            R.string.help_demo_mode_on
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        }
        if (settingsDestination == SettingsDestination.HELP_ABOUT) {
        item {
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
        }
        if (settingsDestination == SettingsDestination.HELP_ABOUT) {
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
}

    if (showProfileDialog) {
        val saveProfile = {
            viewModel.setUserName(profileDraftName.trim())
            viewModel.setUserEmail(profileDraftEmail.trim())
            showProfileDialog = false
        }
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = { Text("Edit profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = profileDraftName,
                        onValueChange = { profileDraftName = it },
                        singleLine = true,
                        label = { Text("Name") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = profileDraftEmail,
                        onValueChange = { profileDraftEmail = it },
                        singleLine = true,
                        label = { Text("Email") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { saveProfile() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Avatar",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(profileAvatarPresets, key = { it.name }) { preset ->
                            PresetAvatarChoice(
                                preset = preset,
                                label = preset.label,
                                context = context,
                                onClick = {
                                    pickedPhotoBitmap = ProfilePhotoUtils.presetAvatarBitmap(context, preset)
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = saveProfile) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showProfileDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showThemePresetDialog) {
        AlertDialog(
            onDismissRequest = { showThemePresetDialog = false },
            title = { Text("Save theme preset") },
            text = {
                OutlinedTextField(
                    value = themePresetName,
                    onValueChange = { themePresetName = it },
                    singleLine = true,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveCurrentThemePreset(
                            name = themePresetName,
                            themeMode = themeMode,
                            seedColorArgb = customThemeSeedColorArgb,
                            colorIntensity = colorIntensity,
                            backgroundTint = backgroundTint,
                            appFont = appFont,
                            dynamicColorEnabled = dynamicColorEnabled
                        )
                        showThemePresetDialog = false
                    },
                    enabled = themePresetName.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showThemePresetDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
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

    if (showClearSyncLockDialog) {
        AlertDialog(
            onDismissRequest = { if (!isClearingSyncLock) showClearSyncLockDialog = false },
            title = { Text(stringResource(R.string.settings_clear_sync_lock_title)) },
            text = { Text(clearSyncLockDialogMessage ?: stringResource(R.string.settings_clear_sync_lock_confirm)) },
            confirmButton = {
                TextButton(
                    enabled = !isClearingSyncLock,
                    onClick = {
                        isClearingSyncLock = true
                        viewModel.clearSelfHostedSyncLock { result ->
                            isClearingSyncLock = false
                            showClearSyncLockDialog = false
                            clearSyncLockDialogMessage = null
                            scope.launch {
                                result.fold(
                                    onSuccess = {
                                        snackbarHostState.showSuccess(context.getString(R.string.settings_clear_sync_lock_success))
                                    },
                                    onFailure = { error ->
                                        snackbarHostState.showError(
                                            error.message ?: context.getString(R.string.settings_clear_sync_lock_failed)
                                        )
                                    }
                                )
                            }
                        }
                    }
                ) {
                    if (isClearingSyncLock) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            text = stringResource(R.string.settings_clear_sync_lock_action),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showClearSyncLockDialog = false
                        clearSyncLockDialogMessage = null
                    },
                    enabled = !isClearingSyncLock
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showSftpConfigDialog) {
        var draftProtocol by remember { mutableStateOf(remoteBackupProtocol) }
        var draftHost by remember { mutableStateOf(sftpHost) }
        var draftPort by remember { mutableStateOf(sftpPort.toString()) }
        var draftUsername by remember { mutableStateOf(sftpUsername) }
        var draftRemoteDir by remember { mutableStateOf(sftpRemoteDir) }
        var draftAuthMethod by remember { mutableStateOf(sftpAuthMethod) }
        var draftPassword by remember { mutableStateOf("") }
        var draftPrivateKey by remember { mutableStateOf("") }
        var draftPassphrase by remember { mutableStateOf("") }
        var draftFtpUseTls by remember { mutableStateOf(ftpUseTls) }
        var draftGitHubRepo by remember { mutableStateOf(listOf(githubOwner, githubRepo).filter { it.isNotBlank() }.joinToString("/")) }
        var draftGitHubBranch by remember { mutableStateOf(githubBranch.ifBlank { "main" }) }
        var draftGitHubApiBase by remember { mutableStateOf(githubApiBase) }
        // Never pre-filled with the stored value — the passphrase is write-only from the UI's
        // point of view, same as the password fields. Blank therefore means "leave as-is".
        var draftBackupPassphrase by remember { mutableStateOf("") }
        val passwordAlreadySet = remember { viewModel.hasRemoteBackupPassword() }
        val githubTokenAlreadySet = remember { viewModel.hasGitHubToken() }
        val keyPassphraseAlreadySet = remember { viewModel.hasSftpKeyPassphrase() }
        val backupPassphraseAlreadySet = remember { viewModel.hasRemoteBackupPassphrase() }
        val savedSecretPlaceholder = "••••••••"
        var draftGitHubToken by remember {
            mutableStateOf(if (githubTokenAlreadySet) savedSecretPlaceholder else "")
        }
        var isTestingConnection by remember { mutableStateOf(false) }
        // null = untested this session, true/false = last test's outcome. A successful SFTP test
        // with no fingerprint pinned yet, or a failed one where the failure is a host-key
        // mismatch, both surface a trust prompt via pendingTrustFingerprint instead of a plain
        // result line. FTP/FTPS has no equivalent -- pendingTrustFingerprint stays null there and
        // every test outcome goes straight to testResultMessage.
        var testResultOk by remember { mutableStateOf<Boolean?>(null) }
        var testResultMessage by remember { mutableStateOf<String?>(null) }
        var pendingTrustFingerprint by remember { mutableStateOf<String?>(null) }
        var isHostKeyMismatch by remember { mutableStateOf(false) }
        val draftIsFtp = draftProtocol == com.mj.yata.domain.model.RemoteBackupProtocol.FTP
        val draftIsGitHub = draftProtocol == com.mj.yata.domain.model.RemoteBackupProtocol.GITHUB
        fun enteredGitHubToken(): String =
            draftGitHubToken.takeUnless { githubTokenAlreadySet && it == savedSecretPlaceholder }.orEmpty()

        fun parseGitHubRepoDraft(): Pair<String, String>? {
            val parts = draftGitHubRepo.trim().split("/", limit = 2)
            return if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                parts[0] to parts[1]
            } else {
                null
            }
        }

        fun saveServerConfiguration(onSaved: () -> Unit = {}) {
            if (draftIsGitHub) {
                enteredGitHubToken().takeIf { it.isNotBlank() }?.let(viewModel::setGitHubToken)
                val repoParts = parseGitHubRepoDraft()
                if (repoParts != null) {
                    viewModel.saveGitHubConfiguration(
                        owner = repoParts.first,
                        repo = repoParts.second,
                        branch = draftGitHubBranch,
                        apiBase = draftGitHubApiBase,
                        onSaved = onSaved
                    )
                } else {
                    testResultOk = false
                    testResultMessage = "Enter the repo as owner/name"
                    isTestingConnection = false
                }
                return
            }
            if (draftIsFtp) {
                if (draftPassword.isNotBlank()) viewModel.setSftpPassword(draftPassword)
                // Blank means "keep whatever is stored" rather than "remove encryption" — silently
                // dropping to unencrypted uploads because a field was left empty is not a default
                // anyone would want.
                if (draftBackupPassphrase.isNotBlank()) {
                    viewModel.setRemoteBackupPassphrase(draftBackupPassphrase)
                }
            } else {
                viewModel.setSftpAuthMethod(draftAuthMethod)
                if (draftAuthMethod == "PRIVATE_KEY") {
                    if (draftPrivateKey.isNotBlank() || draftPassphrase.isNotBlank()) {
                        viewModel.setSftpPrivateKey(draftPrivateKey, draftPassphrase)
                    }
                } else {
                    if (draftPassword.isNotBlank()) viewModel.setSftpPassword(draftPassword)
                }
            }
            viewModel.saveRemoteBackupConfiguration(
                protocol = draftProtocol,
                useTls = draftFtpUseTls,
                host = draftHost,
                port = draftPort.toIntOrNull() ?: sftpPort,
                username = draftUsername,
                remoteDir = draftRemoteDir,
                authMethod = draftAuthMethod,
                onSaved = onSaved
            )
        }

        AlertDialog(
            onDismissRequest = { showSftpConfigDialog = false },
            title = { Text("Remote sync") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    RemoteConfigHeader(protocol = draftProtocol)
                    RemoteProviderPicker(
                        selectedProtocol = draftProtocol,
                        onProtocolSelected = { newProtocol ->
                            // Only nudge the port if it's still sitting at the *other* protocol's
                            // default -- a custom port the user already typed must survive a
                            // protocol switch.
                            if (newProtocol == com.mj.yata.domain.model.RemoteBackupProtocol.FTP && draftPort == "22") {
                                draftPort = "21"
                            } else if (newProtocol == com.mj.yata.domain.model.RemoteBackupProtocol.SFTP && draftPort == "21") {
                                draftPort = "22"
                            }
                            draftProtocol = newProtocol
                        }
                    )
                    if (draftIsGitHub) {
                        RemoteConfigGroup(
                            title = "Repository access",
                            summary = "Limit the token to this private sync repo.",
                            icon = Icons.Default.Code
                        ) {
                            OutlinedTextField(
                                value = draftGitHubToken,
                                onValueChange = { draftGitHubToken = it },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Token")
                                        IconButton(
                                            onClick = { showGitHubPatHelpDialog = true },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = "How to create a GitHub token",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                },
                                placeholder = {
                                    if (githubTokenAlreadySet) Text(savedSecretPlaceholder)
                                },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = draftGitHubRepo,
                                onValueChange = { draftGitHubRepo = it },
                                label = { Text("Repo") },
                                placeholder = { Text("owner/repo") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = draftGitHubBranch,
                                onValueChange = { draftGitHubBranch = it },
                                label = { Text("Branch") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = draftGitHubApiBase,
                                onValueChange = { draftGitHubApiBase = it },
                                label = { Text("API base URL") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Fine-grained token: Contents read/write. Stored encrypted on this device.",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    } else {
                        RemoteConfigGroup(
                            title = "Server location",
                            summary = if (draftIsFtp) "Point YATA at an FTP or FTPS folder." else "Point YATA at an SSH/SFTP folder.",
                            icon = if (draftIsFtp) Icons.Default.Dns else Icons.Default.Storage
                        ) {
                            OutlinedTextField(
                                value = draftHost,
                                onValueChange = { draftHost = it },
                                label = { Text(stringResource(R.string.settings_sftp_host)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = draftPort,
                                    onValueChange = { new -> if (new.length <= 5 && new.all { it.isDigit() }) draftPort = new },
                                    label = { Text(stringResource(R.string.settings_sftp_port)) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(0.38f)
                                )
                                OutlinedTextField(
                                    value = draftUsername,
                                    onValueChange = { draftUsername = it },
                                    label = { Text(stringResource(R.string.settings_sftp_username)) },
                                    singleLine = true,
                                    modifier = Modifier.weight(0.62f)
                                )
                            }
                            OutlinedTextField(
                                value = draftRemoteDir,
                                onValueChange = { draftRemoteDir = it },
                                label = { Text(stringResource(R.string.settings_sftp_remote_dir)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    if (draftIsFtp) {
                        RemoteConfigGroup(
                            title = "Credentials",
                            summary = "Saved secrets are kept encrypted on this device.",
                            icon = Icons.Default.Lock
                        ) {
                        OutlinedTextField(
                            value = draftPassword,
                            onValueChange = { draftPassword = it },
                            label = { Text(stringResource(R.string.settings_sftp_password)) },
                            placeholder = {
                                if (passwordAlreadySet) Text(savedSecretPlaceholder)
                            },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.settings_ftp_use_tls),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(checked = draftFtpUseTls, onCheckedChange = { draftFtpUseTls = it })
                        }
                        if (!draftFtpUseTls) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_ftp_plain_warning),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                        OutlinedTextField(
                            value = draftBackupPassphrase,
                            onValueChange = { draftBackupPassphrase = it },
                            label = { Text(stringResource(R.string.settings_backup_passphrase)) },
                            placeholder = {
                                if (backupPassphraseAlreadySet) Text(savedSecretPlaceholder)
                            },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = if (backupPassphraseAlreadySet) {
                                stringResource(R.string.settings_backup_passphrase_set)
                            } else {
                                stringResource(R.string.settings_backup_passphrase_hint)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        }
                    } else if (!draftIsGitHub) {
                        RemoteConfigGroup(
                            title = "Credentials",
                            summary = "Use a password or private key for this SFTP server.",
                            icon = Icons.Default.Lock
                        ) {
                        val authPasswordLabel = stringResource(R.string.settings_sftp_auth_password)
                        val authKeyLabel = stringResource(R.string.settings_sftp_auth_key)
                        SegmentedControl(
                            items = listOf("PASSWORD", "PRIVATE_KEY"),
                            selectedItem = draftAuthMethod,
                            onItemSelected = { draftAuthMethod = it },
                            labelProvider = { if (it == "PASSWORD") authPasswordLabel else authKeyLabel }
                        )
                        if (draftAuthMethod == "PRIVATE_KEY") {
                            OutlinedTextField(
                                value = draftPrivateKey,
                                onValueChange = { draftPrivateKey = it },
                                label = { Text(stringResource(R.string.settings_sftp_private_key)) },
                                placeholder = { Text(stringResource(R.string.settings_sftp_private_key_placeholder), style = MaterialTheme.typography.bodySmall) },
                                minLines = 3,
                                maxLines = 6,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = draftPassphrase,
                                onValueChange = { draftPassphrase = it },
                                label = { Text(stringResource(R.string.settings_sftp_passphrase)) },
                                placeholder = {
                                    if (keyPassphraseAlreadySet) Text(savedSecretPlaceholder)
                                },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            OutlinedTextField(
                                value = draftPassword,
                                onValueChange = { draftPassword = it },
                                label = { Text(stringResource(R.string.settings_sftp_password)) },
                                placeholder = {
                                    if (passwordAlreadySet) Text(savedSecretPlaceholder)
                                },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        }
                    }

                    FilledTonalButton(
                        onClick = {
                            testResultOk = null
                            testResultMessage = null
                            pendingTrustFingerprint = null
                            isHostKeyMismatch = false
                            isTestingConnection = true
                            if (draftIsGitHub) {
                                viewModel.connectGitHubConfiguration(
                                    repoText = draftGitHubRepo,
                                    token = enteredGitHubToken(),
                                    apiBase = draftGitHubApiBase
                                ) { result ->
                                    isTestingConnection = false
                                    testResultOk = result.isSuccess
                                    testResultMessage = if (result.isSuccess) {
                                        "GitHub connected"
                                    } else {
                                        result.exceptionOrNull()?.message ?: context.getString(R.string.export_failed)
                                    }
                                }
                            } else {
                                saveServerConfiguration {
                                    if (draftIsFtp) {
                                        viewModel.testFtpConnection { result ->
                                            isTestingConnection = false
                                            testResultOk = result.isSuccess
                                            testResultMessage = if (result.isSuccess) {
                                                context.getString(R.string.settings_sftp_connection_ok)
                                            } else {
                                                result.exceptionOrNull()?.message ?: context.getString(R.string.export_failed)
                                            }
                                        }
                                    } else {
                                        viewModel.testSftpConnection { result ->
                                            isTestingConnection = false
                                            testResultOk = result.success
                                            val firstObservedKey = sftpHostKeyFingerprint == null &&
                                                result.fingerprint != null &&
                                                result.fingerprint.isNotBlank()
                                            if (firstObservedKey) {
                                                // The transport intentionally stopped before authentication.
                                                // Confirming below pins the key, then runs the real auth test.
                                                pendingTrustFingerprint = result.fingerprint
                                            } else if (result.success) {
                                                testResultMessage = context.getString(R.string.settings_sftp_connection_ok)
                                            } else {
                                                val mismatch = sftpHostKeyFingerprint != null &&
                                                    result.fingerprint != null &&
                                                    result.fingerprint != sftpHostKeyFingerprint
                                                if (mismatch) {
                                                    isHostKeyMismatch = true
                                                    pendingTrustFingerprint = result.fingerprint
                                                } else {
                                                    testResultMessage = result.error?.message
                                                        ?: context.getString(R.string.export_failed)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !isTestingConnection,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isTestingConnection) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = if (draftIsGitHub) Icons.Default.Code else Icons.Default.CloudSync,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (draftIsGitHub) {
                                if (isTestingConnection) "Connecting..." else "Connect GitHub"
                            } else if (isTestingConnection) {
                                stringResource(R.string.settings_sftp_testing_connection)
                            } else {
                                stringResource(R.string.settings_sftp_test_connection)
                            }
                        )
                    }

                    pendingTrustFingerprint?.let { fingerprint ->
                        Surface(
                            color = if (isHostKeyMismatch) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = stringResource(
                                        if (isHostKeyMismatch) R.string.settings_sftp_host_key_changed else R.string.settings_sftp_trust_prompt,
                                        fingerprint
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isHostKeyMismatch) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = {
                                        pendingTrustFingerprint = null
                                        isHostKeyMismatch = false
                                        testResultMessage = null
                                        isTestingConnection = true
                                        viewModel.pinAndTestSftpConnection(fingerprint) { result ->
                                            isTestingConnection = false
                                            testResultOk = result.success
                                            if (result.success) {
                                                testResultMessage = context.getString(R.string.settings_sftp_connection_ok)
                                            } else {
                                                val changedAgain = result.fingerprint != null &&
                                                    result.fingerprint != fingerprint
                                                if (changedAgain) {
                                                    isHostKeyMismatch = true
                                                    pendingTrustFingerprint = result.fingerprint
                                                } else {
                                                    testResultMessage = result.error?.message
                                                        ?: context.getString(R.string.export_failed)
                                                }
                                            }
                                        }
                                    },
                                    colors = if (isHostKeyMismatch) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
                                ) {
                                    Text(
                                        stringResource(
                                            if (isHostKeyMismatch) R.string.settings_sftp_trust_new_key else R.string.settings_sftp_trust_and_save
                                        )
                                    )
                                }
                            }
                        }
                    }

                    testResultMessage?.let { message ->
                        Surface(
                            color = if (testResultOk == true) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                            contentColor = if (testResultOk == true) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (testResultOk == true) Icons.Default.CheckCircle else Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (draftIsGitHub && parseGitHubRepoDraft() == null) {
                        testResultOk = false
                        testResultMessage = "Enter the repo as owner/name"
                    } else {
                        saveServerConfiguration()
                        showSftpConfigDialog = false
                    }
                }) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSftpConfigDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showGitHubPatHelpDialog) {
        AlertDialog(
            onDismissRequest = { showGitHubPatHelpDialog = false },
            title = { Text("Create a GitHub token") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Create a fine-grained personal access token for the sync repo.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text("1. Open GitHub > Settings > Developer settings > Personal access tokens > Fine-grained tokens.")
                    Text("2. Tap Generate new token and name it YATA sync.")
                    Text("3. Under Repository access, select only your YATA sync repo.")
                    Text("4. Under Repository permissions, set Contents to Read and write.")
                    Text("5. Generate the token, copy it, then paste it here.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showGitHubPatHelpDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    if (showSftpRestoreDialog) {
        AlertDialog(
            onDismissRequest = { if (!isRestoringSftpBackup) showSftpRestoreDialog = false },
            title = { Text(if (isGitHubProtocol) "Restore from GitHub" else stringResource(R.string.settings_sftp_restore_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    when {
                        isLoadingSftpBackups -> {
                            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        sftpBackupList.isEmpty() -> {
                            Text(
                                stringResource(R.string.settings_sftp_no_backups_found),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        else -> {
                            sftpBackupList.forEach { restorePoint ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !isRestoringSftpBackup) { pendingSftpRestorePoint = restorePoint }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(restorePoint.label, style = MaterialTheme.typography.bodyMedium)
                                        restorePoint.createdAt?.let {
                                            Text(
                                                formatBackupTimestamp(it.toString()),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                            if (isRestoringSftpBackup) {
                                Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSftpRestoreDialog = false }, enabled = !isRestoringSftpBackup) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    pendingSftpRestorePoint?.let { restorePoint ->
        // Read the backup before offering to restore it. Restore overwrites live data, and the
        // filename alone can't tell a full backup from one taken while the database was nearly
        // empty — the counts are what make this a checkable decision.
        LaunchedEffect(restorePoint.id) {
            isInspectingSftpBackup = true
            sftpBackupSummary = null
            sftpInspectError = null
            viewModel.inspectRemoteSnapshot(restorePoint.id) { result ->
                isInspectingSftpBackup = false
                result
                    .onSuccess { sftpBackupSummary = it }
                    .onFailure { sftpInspectError = it.message ?: context.getString(R.string.export_failed) }
            }
        }
        AlertDialog(
            onDismissRequest = { pendingSftpRestorePoint = null },
            title = { Text(stringResource(R.string.settings_sftp_restore_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(restorePoint.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    when {
                        isInspectingSftpBackup -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    stringResource(R.string.settings_backup_summary_loading),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        sftpBackupSummary != null -> {
                            val summary = sftpBackupSummary!!
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        stringResource(
                                            R.string.settings_backup_summary_device,
                                            summary.createdByDevice
                                                ?: stringResource(R.string.settings_backup_summary_device_unknown)
                                        ),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        stringResource(R.string.settings_backup_summary_tasks, summary.totalTasks),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        stringResource(R.string.settings_backup_summary_open, summary.openTasks),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        stringResource(R.string.settings_backup_summary_projects, summary.totalProjects),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                        sftpInspectError != null -> {
                            Text(
                                stringResource(R.string.settings_backup_summary_failed, sftpInspectError!!),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Text(stringResource(R.string.settings_sftp_restore_confirm_body))
                }
            },
            confirmButton = {
                TextButton(
                    // A backup that couldn't be read is one that couldn't be restored either —
                    // better to block here than to fail halfway through overwriting live data.
                    enabled = !isInspectingSftpBackup && sftpInspectError == null,
                    onClick = {
                    pendingSftpRestorePoint = null
                    isRestoringSftpBackup = true
                    viewModel.restoreRemoteSnapshot(restorePoint.id) { result ->
                        isRestoringSftpBackup = false
                        showSftpRestoreDialog = false
                        scope.launch {
                            if (result.isSuccess) {
                                snackbarHostState.showSuccess(context.getString(R.string.settings_sftp_connection_ok))
                            } else {
                                snackbarHostState.showError(result.exceptionOrNull()?.message ?: context.getString(R.string.export_failed))
                            }
                        }
                    }
                }) {
                    Text(stringResource(R.string.cd_trash_restore), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSftpRestorePoint = null }) { Text(stringResource(R.string.action_cancel)) }
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
            }
        )
    }

    if (showFrequencyDialog) {
        AlertDialog(
            onDismissRequest = { showFrequencyDialog = false },
            title = { Text(stringResource(R.string.settings_backup_frequency)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.settings_backup_frequency_explainer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    SYNC_FREQUENCY_MINUTES.forEach { minutes ->
                        val label = syncFrequencyLabel(minutes)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.setBackupIntervalMinutes(minutes)
                                    showFrequencyDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // RadioButton over a plain conditional Icon — its selected/unselected
                            // dot is a built-in animated transition (same as the Archive Months
                            // dialog right below), instead of the checkmark just popping in/out.
                            RadioButton(
                                selected = backupIntervalMinutes == minutes,
                                onClick = {
                                    viewModel.setBackupIntervalMinutes(minutes)
                                    showFrequencyDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFrequencyDialog = false }) { Text(stringResource(R.string.action_cancel)) }
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

    // Recognition happens once at import; the transparent source itself stays unchanged so the
    // avatar renderer can apply whatever Material palette is current later.
    pickedPhotoBitmap?.let { bitmap ->
        CircularImageCropper(
            source = bitmap,
            onConfirm = { cropped ->
                scope.launch {
                    val savedUri = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        // Mark white-on-transparent artwork for live Material tinting at render.
                        val isMaterialGlyph = ProfilePhotoUtils.looksLikeTransparentGlyph(cropped)
                        ProfilePhotoUtils.saveCircularProfilePhoto(
                            context,
                            cropped,
                            isMaterialGlyph = isMaterialGlyph
                        )
                    }
                    viewModel.setUserPhotoUri(savedUri.toString())
                    pickedPhotoBitmap = null
                }
            },
            onCancel = { pickedPhotoBitmap = null },
            onSelectNewImage = {
                pickedPhotoBitmap = null
                photoPickerLauncher.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            }
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

/** WorkManager's own floor for periodic work (see UserPreferences.setBackupIntervalMinutes'
 * coerceAtLeast) — there's no shorter periodic schedule to fall back to, so it doubles as the
 * "right after any change" option's stored value. The actual near-immediate upload is the
 * always-on short debounce in BackupOperations.scheduleDebouncedBackup, which this dialog
 * doesn't control either way. */
private const val SYNC_AFTER_CHANGE_MINUTES = 15L

/** The four choices in the "Backup frequency" dialog, in display order. Only the values live
 * here — labels are resolved through [syncFrequencyLabel] so they can come from strings.xml. */
private val SYNC_FREQUENCY_MINUTES: List<Long> = listOf(SYNC_AFTER_CHANGE_MINUTES, 30L, 60L, 120L)

/** Falls back to the generic interval formatter for a value saved by an older build, which could
 * be any number of minutes/hours/days rather than one of the four presets. */
@Composable
private fun syncFrequencyLabel(minutes: Long): String = when {
    minutes == SYNC_AFTER_CHANGE_MINUTES -> stringResource(R.string.settings_sync_after_change)
    minutes in SYNC_FREQUENCY_MINUTES -> stringResource(R.string.settings_sync_every_minutes, minutes.toInt())
    else -> formatBackupInterval(minutes)
}

private fun signedCount(n: Int): String = if (n > 0) "+$n" else "$n"

/** Both cases are fixed the same way (re-run the sign-in flow), so every "Reauthorize" action
 * button in this screen checks this instead of just NeedsReauth — NotSignedIn shows up when
 * Play Services' cached account silently disappears out from under a still-"enabled" local flag. */
/** Shows a multi-destination backup run as one snackbar — message built by
 * [com.mj.yata.util.backupResultMessage] so every "back up now" entry point words it the same. */
private suspend fun reportBackupResults(
    results: List<com.mj.yata.domain.model.BackupRunResult>,
    snackbarHostState: SnackbarHostState,
    context: android.content.Context
) {
    val message = com.mj.yata.util.backupResultMessage(results, context)
    if (message.isError) snackbarHostState.showError(message.text) else snackbarHostState.showSuccess(message.text)
}

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

@Composable
private fun AboutYataCard(
    demoModeEnabled: Boolean,
    demoModeFeedback: Int?,
    onToggleDemoMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
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
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(onClick = onToggleDemoMode),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.rj_logo_mark),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer),
                    modifier = Modifier.size(width = 44.dp, height = 29.dp)
                )
            }
            if (demoModeEnabled) {
                Text(
                    text = stringResource(R.string.help_demo_mode_active),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            demoModeFeedback?.let { messageRes ->
                Text(
                    text = stringResource(messageRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "yata",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = BodoniModaFamily,
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
                text = "v${BuildConfig.VERSION_NAME}  ·  Build ${BuildConfig.VERSION_CODE}.${BuildConfig.BUILD_DATE}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(8.dp))
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

@Composable
private fun RemoteConfigHeader(
    protocol: com.mj.yata.domain.model.RemoteBackupProtocol,
    modifier: Modifier = Modifier
) {
    val (title, body, icon) = when (protocol) {
        com.mj.yata.domain.model.RemoteBackupProtocol.GITHUB -> Triple(
            "GitHub sync",
            "Sync through a private repository with commit history as restore points.",
            Icons.Default.Code
        )
        com.mj.yata.domain.model.RemoteBackupProtocol.FTP -> Triple(
            "FTP / FTPS sync",
            "Use your own server folder with rotated backup files.",
            Icons.Default.Dns
        )
        com.mj.yata.domain.model.RemoteBackupProtocol.SFTP -> Triple(
            "SFTP sync",
            "Use SSH-backed storage with host-key trust and rotated backups.",
            Icons.Default.Storage
        )
    }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.64f),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(body, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun RemoteProviderPicker(
    selectedProtocol: com.mj.yata.domain.model.RemoteBackupProtocol,
    onProtocolSelected: (com.mj.yata.domain.model.RemoteBackupProtocol) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Provider",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        RemoteProviderOption(
            selected = selectedProtocol == com.mj.yata.domain.model.RemoteBackupProtocol.GITHUB,
            icon = Icons.Default.Code,
            title = "GitHub",
            summary = "Private repo, PAT, commit history",
            onClick = { onProtocolSelected(com.mj.yata.domain.model.RemoteBackupProtocol.GITHUB) }
        )
        RemoteProviderOption(
            selected = selectedProtocol == com.mj.yata.domain.model.RemoteBackupProtocol.SFTP,
            icon = Icons.Default.Storage,
            title = "SFTP",
            summary = "SSH server with host-key trust",
            onClick = { onProtocolSelected(com.mj.yata.domain.model.RemoteBackupProtocol.SFTP) }
        )
        RemoteProviderOption(
            selected = selectedProtocol == com.mj.yata.domain.model.RemoteBackupProtocol.FTP,
            icon = Icons.Default.Dns,
            title = "FTP / FTPS",
            summary = "Server folder with optional TLS",
            onClick = { onProtocolSelected(com.mj.yata.domain.model.RemoteBackupProtocol.FTP) }
        )
    }
}

@Composable
private fun RemoteProviderOption(
    selected: Boolean,
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = content.copy(alpha = 0.12f),
                shape = CircleShape,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                Text(summary, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.78f))
            }
            if (selected) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun RemoteConfigGroup(
    title: String,
    summary: String? = null,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    summary?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            content()
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

@Composable
private fun SettingsDestinationCard(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = summary,
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

/** The primary-colored caps label above each settings group. */
@Composable
private fun SettingsSectionHeader(text: String, icon: ImageVector? = null) {
    // The old heading was labelSmall — the same size as the caption under a toggle — which left a
    // long scroll with no landmarks to scan by. Title-sized text in a tinted pill gives each
    // section an anchor the eye can find without reading, and the icon carries the section's
    // subject so it's recognisable before the word is.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null, // the heading text beside it already says this
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSynthesis = androidx.compose.ui.text.font.FontSynthesis.All,
                letterSpacing = 0.6.sp
            ),
            color = MaterialTheme.colorScheme.primary
        )
    }
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
private fun colorIntensityLabels(): List<String> = listOf(
    stringResource(R.string.settings_intensity_minimal),
    stringResource(R.string.settings_intensity_muted),
    stringResource(R.string.settings_intensity_soft),
    stringResource(R.string.settings_intensity_normal),
    stringResource(R.string.settings_intensity_bright),
    stringResource(R.string.settings_intensity_vivid),
    stringResource(R.string.settings_intensity_bold),
    stringResource(R.string.settings_intensity_pop),
    stringResource(R.string.settings_intensity_electric)
)

@Composable
private fun backgroundTintLabels(): List<String> = listOf(
    stringResource(R.string.settings_tint_clean),
    stringResource(R.string.settings_tint_pale),
    stringResource(R.string.settings_tint_soft),
    stringResource(R.string.settings_tint_mild),
    stringResource(R.string.settings_tint_medium),
    stringResource(R.string.settings_tint_rich),
    stringResource(R.string.settings_tint_full),
    stringResource(R.string.settings_tint_deep),
    stringResource(R.string.settings_tint_bold),
    stringResource(R.string.settings_tint_max)
)

/** Above this many stops, only the first and last are labelled under the slider. */
private const val MAX_INLINE_STOP_LABELS = 5

/**
 * A slider that snaps to a fixed set of named stops, with the current stop's name shown beside the
 * title and the stops labelled underneath — all of them when there are few enough to fit, the two
 * ends only when there are not.
 *
 * Unlike the UI-size and text-size sliders above, this commits on every change rather than on
 * `onValueChangeFinished`: those two rescale the entire UI (including this screen) on each frame,
 * so they defer the write until the finger lifts. These only recolour, which is exactly the
 * feedback someone dragging a colour slider is looking for.
 */
@Composable
private fun StopSliderSetting(
    title: String,
    description: String,
    stopLabels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val lastStop = (stopLabels.size - 1).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stopLabels.getOrElse(selectedIndex) { "" },
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = selectedIndex.toFloat(),
            onValueChange = { onSelect(it.roundToInt().coerceIn(0, lastStop)) },
            valueRange = 0f..lastStop.toFloat(),
            steps = (stopLabels.size - 2).coerceAtLeast(0)
        )
        // Past a handful of stops the labels stop fitting across a phone and start colliding, so
        // only the two ends are drawn. Nothing is lost: the current stop is named beside the
        // title, which is the only one whose name is actually being read.
        val labelledStops = if (stopLabels.size <= MAX_INLINE_STOP_LABELS) {
            stopLabels.indices.toList()
        } else {
            listOf(0, stopLabels.lastIndex)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            labelledStops.forEach { index ->
                Text(
                    text = stopLabels[index],
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == selectedIndex) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

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
private fun PresetAvatarChoice(
    preset: ProfilePhotoUtils.PresetAvatar,
    label: String,
    context: android.content.Context,
    onClick: () -> Unit
) {
    val imageBitmap = remember(context, preset) {
        ProfilePhotoUtils.presetAvatarBitmap(context, preset).asImageBitmap()
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(54.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = label,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer),
                    modifier = Modifier.size(34.dp)
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
