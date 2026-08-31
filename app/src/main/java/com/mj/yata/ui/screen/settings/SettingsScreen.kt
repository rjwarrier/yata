package com.mj.yata.ui.screen.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.IosShare
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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.R
import com.mj.yata.data.backup.BackupDiff
import com.mj.yata.domain.model.AppFont
import com.mj.yata.domain.model.postponementWarningThresholdFor
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
import com.mj.yata.domain.model.SubtaskCompletionAction
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
import com.mj.yata.ui.screen.lock.hasAppLockUnlockPath
import com.mj.yata.ui.screen.main.MainViewModel
import com.mj.yata.ui.theme.LocalYataAccents
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mj.yata.ui.widgets.YataDropdownMenu
import com.mj.yata.ui.widgets.YataDropdownMenuItem
import com.mj.yata.ui.widgets.CircularImageCropper
import com.mj.yata.ui.widgets.CustomColorPickerDialog
import com.mj.yata.ui.widgets.PresetAvatarChoice
import com.mj.yata.ui.widgets.SegmentedControl
import com.mj.yata.ui.widgets.YataCompactFieldShape
import com.mj.yata.ui.widgets.yataFieldColors
import com.mj.yata.ui.widgets.YataTimePickerLauncher
import com.mj.yata.ui.widgets.showSuccess
import com.mj.yata.ui.widgets.showError
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.toArgb
import com.mj.yata.util.ProfilePhotoUtils
import com.mj.yata.util.EstimateUtils
import com.mj.yata.util.emptyLocalDataConfirmationRequired
import com.mj.yata.util.initialSyncConfirmationRequired
import com.mj.yata.util.selfHostedSyncLockFailure
import com.mj.yata.util.syncLockClearPrompt
import com.mj.yata.util.TaskScheduleUtils
import com.mj.yata.util.localized
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mj.yata.BuildConfig
import com.mj.yata.BuildInfo
import com.mj.yata.ui.theme.BodoniModaFamily
import com.mj.yata.ui.util.AdaptiveContentBox
import com.mj.yata.ui.util.rememberAdaptiveLayoutInfo

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
    onNavigateToShareApp: () -> Unit,
    onNavigateToRemoteSync: () -> Unit,
    onNavigateToHolidayCalendar: () -> Unit,
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
    val defaultProjectId = uiState.defaultProjectId
    val defaultTagIds = uiState.defaultTagIds
    val defaultEstimateMinutes = uiState.defaultEstimateMinutes
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
    val quietHoursEnabled by viewModel.quietHoursEnabled.collectAsStateWithLifecycle()
    val quietHoursStartHour by viewModel.quietHoursStartHour.collectAsStateWithLifecycle()
    val quietHoursStartMinute by viewModel.quietHoursStartMinute.collectAsStateWithLifecycle()
    val quietHoursEndHour by viewModel.quietHoursEndHour.collectAsStateWithLifecycle()
    val quietHoursEndMinute by viewModel.quietHoursEndMinute.collectAsStateWithLifecycle()
    val undoWindowSeconds by viewModel.undoWindowSeconds.collectAsStateWithLifecycle()
    val snoozeTonightHour by viewModel.snoozeTonightHour.collectAsStateWithLifecycle()
    val snoozeTonightMinute by viewModel.snoozeTonightMinute.collectAsStateWithLifecycle()
    val snoozeTomorrowHour by viewModel.snoozeTomorrowHour.collectAsStateWithLifecycle()
    val snoozeTomorrowMinute by viewModel.snoozeTomorrowMinute.collectAsStateWithLifecycle()
    val defaultDueDate by viewModel.defaultDueDate.collectAsStateWithLifecycle()
    val defaultPriority by viewModel.defaultPriority.collectAsStateWithLifecycle()
    val postponementWarningThreshold by viewModel.postponementWarningThreshold.collectAsStateWithLifecycle()
    val subtaskCompletionAction by viewModel.subtaskCompletionAction.collectAsStateWithLifecycle()
    val autoAssignToMe by viewModel.autoAssignToMe.collectAsStateWithLifecycle()
    val todayShowUpcomingWhenEmpty by viewModel.todayShowUpcomingWhenEmpty.collectAsStateWithLifecycle()
    val dueCountdownEnabled by viewModel.dueCountdownEnabled.collectAsStateWithLifecycle()
    val peopleFeatureEnabled = uiState.peopleFeatureEnabled
    val tagsFeatureEnabled = uiState.tagsFeatureEnabled
    val projectsFeatureEnabled = uiState.projectsFeatureEnabled
    val lists = uiState.lists
    val activeProjects = uiState.activeProjects
    val tags = uiState.tags
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
    val githubTokenExpiresAt = uiState.githubTokenExpiresAt
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
    var showDefaultProjectMenu by remember { mutableStateOf(false) }
    var showDefaultTagsMenu by remember { mutableStateOf(false) }
    var showDefaultEstimateDialog by remember { mutableStateOf(false) }
    var defaultEstimateText by rememberSaveable { mutableStateOf("") }
    val defaultListMenuScrollState = rememberScrollState()
    val defaultProjectMenuScrollState = rememberScrollState()
    val defaultTagsMenuScrollState = rememberScrollState()
    val voiceLanguageMenuScrollState = rememberScrollState()
    val dateAliasTargetMenuScrollState = rememberScrollState()
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
    var showAgendaTimePicker by remember { mutableStateOf(false) }
    var showReminderTimePicker by remember { mutableStateOf(false) }
    var showQuietHoursStartPicker by remember { mutableStateOf(false) }
    var showQuietHoursEndPicker by remember { mutableStateOf(false) }
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

    var showSftpRestoreDialog by remember { mutableStateOf(false) }
    var showClearSyncLockDialog by remember { mutableStateOf(false) }
    var clearSyncLockDialogMessage by remember { mutableStateOf<String?>(null) }
    var initialSyncMergeMessage by remember { mutableStateOf<String?>(null) }
    var emptyLocalSyncMessage by remember { mutableStateOf<String?>(null) }
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
        SettingsHubDestination(stringResource(R.string.settings_section_task_defaults), stringResource(R.string.settings_search_defaults_summary), SettingsDestination.TASK_DEFAULTS, Icons.Default.TaskAlt),
        SettingsHubDestination(stringResource(R.string.settings_section_navigation_features), stringResource(R.string.settings_search_navigation_features_summary), SettingsDestination.NAVIGATION_FEATURES, Icons.Default.Navigation),
        SettingsHubDestination(stringResource(R.string.settings_section_sound_feedback), stringResource(R.string.settings_search_feedback_summary), SettingsDestination.SOUND_FEEDBACK, Icons.AutoMirrored.Filled.VolumeUp),
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
        SettingsSearchTarget("sound_feedback", stringResource(R.string.settings_section_sound_feedback), stringResource(R.string.settings_search_feedback_summary), "sound haptic voice language speech recognition", SettingsDestination.SOUND_FEEDBACK, Icons.AutoMirrored.Filled.VolumeUp),
        SettingsSearchTarget("task_defaults", stringResource(R.string.settings_section_task_defaults), stringResource(R.string.settings_search_defaults_summary), "due priority list reminder week assign assignee me subtask complete completion auto ask undo window swipe confetti postponement postpone warning threshold", SettingsDestination.TASK_DEFAULTS, Icons.Default.TaskAlt),
        SettingsSearchTarget("date_aliases", "Date aliases", "Custom quick-add words for due dates", "quick add natural language date aliases keywords today tomorrow", SettingsDestination.TASK_DEFAULTS, Icons.Default.CalendarMonth),
        SettingsSearchTarget("notifications", stringResource(R.string.settings_section_notifications), stringResource(R.string.settings_search_notifications_summary), "alarm battery agenda overdue snooze delivery", SettingsDestination.NOTIFICATIONS, Icons.Default.Notifications),
        SettingsSearchTarget("privacy_security", stringResource(R.string.settings_section_privacy), stringResource(R.string.settings_search_privacy_summary), "privacy lock pin timeout security", SettingsDestination.PRIVACY_SECURITY, Icons.Default.Lock),
        SettingsSearchTarget("data_management", stringResource(R.string.settings_section_data_management), stringResource(R.string.settings_search_data_summary), "export import csv calendar trash archive delete data", SettingsDestination.DATA_MANAGEMENT, Icons.Default.Storage),
        SettingsSearchTarget("remote_backup", stringResource(R.string.settings_section_cloud_backup), stringResource(R.string.settings_search_cloud_summary), "self hosted server sync backup sftp ftp restore frequency manual backup to file", SettingsDestination.BACKUP_SYNC, Icons.Default.CloudSync),
        SettingsSearchTarget("local_backup", stringResource(R.string.settings_section_local_backup), stringResource(R.string.settings_search_local_summary), "local backup restore", SettingsDestination.BACKUP_SYNC, Icons.Default.Save),
        SettingsSearchTarget("help_about", stringResource(R.string.settings_section_help_about), stringResource(R.string.settings_search_help_summary), "help about version guide crash logs welcome tour onboarding", SettingsDestination.HELP_ABOUT, Icons.AutoMirrored.Filled.HelpOutline)
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
    val profileAvatarPresets = ProfilePhotoUtils.PROFILE_AVATAR_PRESETS
    val currentSettingsTitle =
        settingsDestination?.let { destination ->
            settingsHubDestinations.firstOrNull { it.destination == destination }?.title
        } ?: stringResource(R.string.settings_settings)
    val isSettingsRoot = settingsDestination == null
    val useWideSettings = rememberAdaptiveLayoutInfo().isWide
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
                com.mj.yata.ui.screen.main.AdaptiveBottomNav(
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
                        YataDropdownMenu(expanded = showSettingsMenu, onDismissRequest = { showSettingsMenu = false }) {
                            YataDropdownMenuItem(
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
        AdaptiveContentBox(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            LazyColumn(
                state = settingsListState,
                modifier = Modifier
                    .fillMaxSize()
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
            if (useWideSettings) {
                items(settingsHubDestinations.chunked(2), key = { row -> row.joinToString { it.destination.routeSegment } }) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        row.forEach { target ->
                            SettingsDestinationCard(
                                icon = target.icon,
                                title = target.title,
                                summary = target.summary,
                                onClick = { onNavigateToSettingsDestination(target.destination) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                items(settingsHubDestinations, key = { it.destination.routeSegment }) { target ->
                    SettingsDestinationCard(
                        icon = target.icon,
                        title = target.title,
                        summary = target.summary,
                        onClick = { onNavigateToSettingsDestination(target.destination) }
                    )
                }
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
                                    text = stringResource(R.string.settings_theme_presets),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    text = stringResource(R.string.settings_theme_presets_summary),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = {
                                themePresetName = ""
                                showThemePresetDialog = true
                            }) {
                                Text(stringResource(R.string.action_save))
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
                                                    contentDescription = stringResource(R.string.cd_remove_preset, preset.name),
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
                    var sliderPosition by remember(uiScale) { mutableFloatStateOf(uiScale) }
                    val presets = listOf("Small" to 0.85f, "Normal" to 1.0f, "Large" to 1.3f)
                    CompactScaleSliderSetting(
                        title = stringResource(R.string.settings_ui_size),
                        description = stringResource(R.string.settings_ui_size_desc),
                        value = sliderPosition,
                        onValueChange = { sliderPosition = it },
                        onValueChangeFinished = { viewModel.setUiScale(sliderPosition) },
                        valueRange = 0.85f..1.3f,
                        steps = 8,
                        presets = presets,
                        onPresetSelected = { value ->
                            sliderPosition = value
                            viewModel.setUiScale(value)
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    var textSliderPosition by remember(textScale) { mutableFloatStateOf(textScale) }
                    val textPresets = listOf("Small" to 0.85f, "Normal" to 1.0f, "Large" to 1.3f)
                    CompactScaleSliderSetting(
                        title = stringResource(R.string.settings_text_size),
                        description = stringResource(R.string.settings_text_size_desc),
                        value = textSliderPosition,
                        onValueChange = { textSliderPosition = it },
                        onValueChangeFinished = { viewModel.setTextScale(textSliderPosition) },
                        valueRange = 0.85f..1.3f,
                        steps = 8,
                        presets = textPresets,
                        onPresetSelected = { value ->
                            textSliderPosition = value
                            viewModel.setTextScale(value)
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.settings_motion_mode),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = stringResource(R.string.settings_motion_mode_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val motionFullLabel = stringResource(R.string.motion_mode_full)
                        val motionReducedLabel = stringResource(R.string.motion_mode_reduced)
                        val motionOffLabel = stringResource(R.string.motion_mode_off)
                        SegmentedControl(
                            items = listOf(MotionMode.FULL, MotionMode.REDUCED, MotionMode.OFF),
                            selectedItem = motionMode,
                            onItemSelected = { viewModel.setMotionMode(it) },
                            labelProvider = {
                                when (it) {
                                    MotionMode.FULL -> motionFullLabel
                                    MotionMode.REDUCED -> motionReducedLabel
                                    MotionMode.OFF -> motionOffLabel
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
                        val densityCompactLabel = stringResource(R.string.task_row_density_compact)
                        val densityComfortableLabel = stringResource(R.string.task_row_density_comfortable)
                        val densitySpaciousLabel = stringResource(R.string.task_row_density_spacious)
                        SegmentedControl(
                            items = listOf(TaskRowDensity.COMPACT, TaskRowDensity.COMFORTABLE, TaskRowDensity.SPACIOUS),
                            selectedItem = taskRowDensity,
                            onItemSelected = { viewModel.setTaskRowDensity(it) },
                            labelProvider = {
                                when (it) {
                                    TaskRowDensity.COMPACT -> densityCompactLabel
                                    TaskRowDensity.COMFORTABLE -> densityComfortableLabel
                                    TaskRowDensity.SPACIOUS -> densitySpaciousLabel
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
                        YataDropdownMenu(expanded = showTimeFormatMenu, onDismissRequest = { showTimeFormatMenu = false }) {
                            TimeFormat.entries.forEach { format ->
                                YataDropdownMenuItem(
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
                        YataDropdownMenu(expanded = showDateFormatMenu, onDismissRequest = { showDateFormatMenu = false }) {
                            DateFormat.entries.forEach { format ->
                                YataDropdownMenuItem(
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
                    YataDropdownMenu(expanded = showStartupTabMenu, onDismissRequest = { showStartupTabMenu = false }) {
                        StartupTab.entries.forEach { tab ->
                            YataDropdownMenuItem(
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
            }
        }
        item {
            // Tasker is an automation/integration toggle, not something that changes the nav's
            // shape — kept in its own card rather than mixed into the nav-shape card above.
            SettingsSectionCard {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_tasker_integration),
                    subtitle = stringResource(R.string.settings_tasker_integration_summary),
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
                    SettingsPickerSurface(
                        label = stringResource(R.string.settings_voice_input_language),
                        value = voiceLanguages.find { it.first == voiceLanguage }?.second ?: systemDefaultVoiceLabel,
                        onClick = { showVoiceLanguageMenu = true }
                    )
                    YataDropdownMenu(
                        expanded = showVoiceLanguageMenu,
                        onDismissRequest = { showVoiceLanguageMenu = false },
                        modifier = Modifier.widthIn(min = 220.dp)
                    ) {
                        SettingsScrollableDropdownContent(scrollState = voiceLanguageMenuScrollState) {
                            voiceLanguages.forEach { (code, label) ->
                                YataDropdownMenuItem(
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

                SettingsToggleRow(
                    title = stringResource(R.string.settings_due_countdown),
                    subtitle = stringResource(R.string.settings_due_countdown_desc),
                    checked = dueCountdownEnabled,
                    onCheckedChange = { viewModel.setDueCountdownEnabled(it) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                SettingsToggleRow(
                    title = stringResource(R.string.settings_start_week_sunday),
                    subtitle = stringResource(R.string.settings_start_week_sunday_desc),
                    checked = startOfWeekSunday,
                    onCheckedChange = { viewModel.setStartOfWeekSunday(it) }
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
                        YataDropdownMenu(expanded = showSwipeRightMenu, onDismissRequest = { showSwipeRightMenu = false }) {
                            SwipeAction.entries.forEach { action ->
                                YataDropdownMenuItem(
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
                        YataDropdownMenu(expanded = showSwipeLeftMenu, onDismissRequest = { showSwipeLeftMenu = false }) {
                            SwipeAction.entries.forEach { action ->
                                YataDropdownMenuItem(
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
                        text = stringResource(R.string.settings_subtask_completion_action),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = stringResource(R.string.settings_subtask_completion_action_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val askLabel = stringResource(R.string.settings_subtask_completion_ask)
                    val autoLabel = stringResource(R.string.settings_subtask_completion_auto)
                    val nothingLabel = stringResource(R.string.settings_subtask_completion_nothing)
                    SegmentedControl(
                        items = listOf(
                            SubtaskCompletionAction.ASK,
                            SubtaskCompletionAction.AUTO_COMPLETE,
                            SubtaskCompletionAction.NOTHING
                        ),
                        selectedItem = subtaskCompletionAction,
                        onItemSelected = { viewModel.setSubtaskCompletionAction(it) },
                        labelProvider = {
                            when (it) {
                                SubtaskCompletionAction.ASK -> askLabel
                                SubtaskCompletionAction.AUTO_COMPLETE -> autoLabel
                                SubtaskCompletionAction.NOTHING -> nothingLabel
                            }
                        }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                SettingsSubsectionHeader(text = stringResource(R.string.settings_defaults_group))

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

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_default_estimate),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = stringResource(R.string.settings_default_estimate_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val estimateNoneLabel = stringResource(R.string.task_estimate_none)
                    val estimateCustomLabel = defaultEstimateMinutes
                        ?.takeIf { it !in setOf(15, 30, 60) }
                        ?.let(EstimateUtils::format)
                        ?: stringResource(R.string.settings_custom)
                    val selectedEstimateItem = when (defaultEstimateMinutes) {
                        null -> null
                        15, 30, 60 -> defaultEstimateMinutes
                        else -> -1
                    }
                    SegmentedControl(
                        items = listOf<Int?>(null, 15, 30, 60, -1),
                        selectedItem = selectedEstimateItem,
                        onItemSelected = { minutes ->
                            when (minutes) {
                                null -> viewModel.setDefaultEstimateMinutes(null)
                                -1 -> {
                                    defaultEstimateText = defaultEstimateMinutes?.toString() ?: "45"
                                    showDefaultEstimateDialog = true
                                }
                                else -> viewModel.setDefaultEstimateMinutes(minutes)
                            }
                        },
                        labelProvider = { minutes ->
                            when (minutes) {
                                null -> estimateNoneLabel
                                -1 -> estimateCustomLabel
                                else -> EstimateUtils.format(minutes)
                            }
                        }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Box {
                    SettingsPickerSurface(
                        label = stringResource(R.string.settings_default_list),
                        value = lists.find { it.id == defaultListId }?.name ?: stringResource(R.string.settings_none),
                        onClick = { showDefaultListMenu = true }
                    )
                    YataDropdownMenu(
                        expanded = showDefaultListMenu,
                        onDismissRequest = { showDefaultListMenu = false },
                        modifier = Modifier.widthIn(min = 220.dp)
                    ) {
                        SettingsScrollableDropdownContent(scrollState = defaultListMenuScrollState) {
                            YataDropdownMenuItem(
                                text = { Text(stringResource(R.string.settings_none)) },
                                onClick = {
                                    viewModel.setDefaultListId("")
                                    showDefaultListMenu = false
                                }
                            )
                            lists.forEach { list ->
                                YataDropdownMenuItem(
                                    text = { Text(list.name) },
                                    onClick = {
                                        viewModel.setDefaultListId(list.id)
                                        showDefaultListMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                if (projectsFeatureEnabled) {
                    Box {
                        SettingsPickerSurface(
                            label = stringResource(R.string.settings_default_project),
                            value = activeProjects.find { it.id == defaultProjectId }?.name ?: stringResource(R.string.settings_none),
                            onClick = { showDefaultProjectMenu = true }
                        )
                        YataDropdownMenu(
                            expanded = showDefaultProjectMenu,
                            onDismissRequest = { showDefaultProjectMenu = false },
                            modifier = Modifier.widthIn(min = 220.dp)
                        ) {
                            SettingsScrollableDropdownContent(scrollState = defaultProjectMenuScrollState) {
                                YataDropdownMenuItem(
                                    text = { Text(stringResource(R.string.settings_none)) },
                                    onClick = {
                                        viewModel.setDefaultProjectId("")
                                        showDefaultProjectMenu = false
                                    }
                                )
                                activeProjects.forEach { project ->
                                    YataDropdownMenuItem(
                                        text = { Text(project.name) },
                                        onClick = {
                                            viewModel.setDefaultProjectId(project.id)
                                            showDefaultProjectMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }

                if (tagsFeatureEnabled) {
                    val selectedDefaultTags = tags.filter { it.id in defaultTagIds }
                    val defaultTagsValue = when {
                        tags.isEmpty() -> stringResource(R.string.settings_default_tags_empty)
                        selectedDefaultTags.isEmpty() -> stringResource(R.string.settings_none)
                        selectedDefaultTags.size <= 2 -> selectedDefaultTags.joinToString(", ") { it.name }
                        else -> stringResource(
                            R.string.settings_default_tags_value_many,
                            selectedDefaultTags.take(2).joinToString(", ") { it.name },
                            selectedDefaultTags.size - 2
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box {
                            SettingsPickerSurface(
                                label = stringResource(R.string.settings_default_tags),
                                value = defaultTagsValue,
                                onClick = {
                                    if (tags.isNotEmpty()) {
                                        showDefaultTagsMenu = true
                                    }
                                }
                            )
                            YataDropdownMenu(
                                expanded = showDefaultTagsMenu,
                                onDismissRequest = { showDefaultTagsMenu = false },
                                modifier = Modifier.widthIn(min = 240.dp)
                            ) {
                                SettingsScrollableDropdownContent(scrollState = defaultTagsMenuScrollState) {
                                    YataDropdownMenuItem(
                                        text = { Text(stringResource(R.string.settings_default_tags_clear)) },
                                        onClick = {
                                            viewModel.setDefaultTagIds(emptySet())
                                        }
                                    )
                                    tags.forEach { tag ->
                                        val selected = tag.id in defaultTagIds
                                        YataDropdownMenuItem(
                                            text = { Text(tag.name) },
                                            leadingIcon = {
                                                Checkbox(
                                                    checked = selected,
                                                    onCheckedChange = null
                                                )
                                            },
                                            onClick = {
                                                val updated = if (selected) {
                                                    defaultTagIds - tag.id
                                                } else {
                                                    defaultTagIds + tag.id
                                                }
                                                viewModel.setDefaultTagIds(updated)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            text = stringResource(R.string.settings_default_tags_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }

                SettingsRow(
                    label = stringResource(R.string.settings_default_reminder_time),
                    value = TaskScheduleUtils.displayTime(defaultReminderHour, defaultReminderMinute),
                    onClick = { showReminderTimePicker = true }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                run {
                    val postponementOptions = (1..10).toList()
                    val postponementLabels = postponementOptions.map { count ->
                        pluralStringResource(R.plurals.settings_postponement_threshold_value, count, count)
                    }
                    var sliderThreshold by remember(postponementWarningThreshold) {
                        mutableIntStateOf(postponementWarningThreshold)
                    }
                    val medThreshold = postponementWarningThresholdFor("med", sliderThreshold)
                    val highThreshold = postponementWarningThresholdFor("high", sliderThreshold)
                    StopSliderSetting(
                        title = stringResource(R.string.settings_postponement_warning_threshold),
                        description = stringResource(
                            R.string.settings_postponement_warning_threshold_desc,
                            medThreshold,
                            highThreshold
                        ),
                        stopLabels = postponementLabels,
                        selectedIndex = postponementOptions.indexOf(sliderThreshold).coerceAtLeast(0),
                        onSelect = {
                            val selected = postponementOptions[it]
                            sliderThreshold = selected
                            viewModel.setPostponementWarningThreshold(selected)
                        }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToHolidayCalendar() }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_holidays),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = stringResource(R.string.settings_holidays_desc),
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

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.settings_date_aliases),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = stringResource(R.string.settings_date_aliases_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = newDateAlias,
                            onValueChange = { newDateAlias = it },
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp),
                            singleLine = true,
                            label = { Text(stringResource(R.string.settings_date_alias_word_label)) },
                            shape = YataCompactFieldShape,
                            colors = yataFieldColors()
                        )
                        Box(
                            modifier = Modifier.height(64.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .height(64.dp)
                                    .clickable { showDateAliasTargetMenu = true },
                                shape = YataCompactFieldShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                                )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(horizontal = 18.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = selectedDateAliasTarget.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            YataDropdownMenu(
                                expanded = showDateAliasTargetMenu,
                                onDismissRequest = { showDateAliasTargetMenu = false },
                                modifier = Modifier.widthIn(min = 168.dp)
                            ) {
                                SettingsScrollableDropdownContent(scrollState = dateAliasTargetMenuScrollState) {
                                    DateAliasTarget.entries.forEach { target ->
                                        YataDropdownMenuItem(
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
                    }
                    Button(
                        onClick = {
                            viewModel.addDateAlias(newDateAlias, selectedDateAliasTarget)
                            newDateAlias = ""
                        },
                        enabled = newDateAlias.isNotBlank()
                    ) {
                        Text(stringResource(R.string.settings_add_alias))
                    }
                    val aliases = dateAliasDefinitions.mapNotNull(DateAliasDefinition::decode).sortedBy { it.alias }
                    if (aliases.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(aliases, key = { it.encode() }) { alias ->
                                AssistChip(
                                    onClick = {},
                                    label = { Text(stringResource(R.string.settings_date_alias_mapping, alias.alias, alias.target.label)) },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { viewModel.removeDateAlias(alias.encode()) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = stringResource(R.string.cd_remove_alias, alias.alias),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                )
                            }
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
                    val lifecycleOwner = LocalLifecycleOwner.current
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

                    SettingsToggleRow(
                        title = stringResource(R.string.settings_quiet_hours),
                        subtitle = stringResource(R.string.settings_quiet_hours_desc),
                        checked = quietHoursEnabled,
                        onCheckedChange = { viewModel.setQuietHoursEnabled(it) }
                    )

                    AnimatedVisibility(visible = quietHoursEnabled) {
                        Column {
                            SettingsRow(
                                label = stringResource(R.string.settings_quiet_hours_start),
                                value = TaskScheduleUtils.displayTime(quietHoursStartHour, quietHoursStartMinute),
                                onClick = { showQuietHoursStartPicker = true }
                            )
                            SettingsRow(
                                label = stringResource(R.string.settings_quiet_hours_end),
                                value = TaskScheduleUtils.displayTime(quietHoursEndHour, quietHoursEndMinute),
                                onClick = { showQuietHoursEndPicker = true }
                            )
                        }
                    }

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
                                title = stringResource(R.string.tab_projects),
                                onClick = { onNavigateToTab(1) }
                            )
                            AnimatedDivider(visible = projectsFeatureEnabled && (peopleFeatureEnabled || tagsFeatureEnabled))
                            AnimatedManageRow(
                                visible = peopleFeatureEnabled,
                                title = stringResource(R.string.tab_people),
                                onClick = { onNavigateToTab(2) }
                            )
                            AnimatedDivider(visible = peopleFeatureEnabled && tagsFeatureEnabled)
                            AnimatedManageRow(
                                visible = tagsFeatureEnabled,
                                title = stringResource(R.string.tab_tags),
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
            val platformCredentialAvailable = remember {
                val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                } else {
                    androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
                }
                androidx.biometric.BiometricManager.from(context).canAuthenticate(
                    authenticators
                ) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
            }
            val appLockHasUnlockPath = hasAppLockUnlockPath(platformCredentialAvailable, appLockPinSet)
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
                                text = when {
                                    platformCredentialAvailable ->
                                        "Require biometric or device unlock to open YATA."
                                    appLockPinSet ->
                                        "Require your YATA PIN to open YATA."
                                    else ->
                                        "No screen lock set up on this device."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = appLockEnabled && appLockHasUnlockPath,
                            enabled = appLockHasUnlockPath,
                            onCheckedChange = { viewModel.setAppLockEnabled(it) }
                        )
                    }

                    if (appLockEnabled && appLockHasUnlockPath) {
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
            // Import & Export — bringing data in from, or sending it out to, another format/app.
            // Manual whole-app backup/restore lives in Backup & Sync next to the automatic kind.
            SettingsSectionCard {
                SettingsSubsectionHeader(text = stringResource(R.string.settings_import_export_group))

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
            }
        }
        item {
            // Task Lifecycle — where completed/deleted tasks go and how long they stay there.
            SettingsSectionCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
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

                run {
                    val trashRetentionOptions = listOf(1, 3, 7, 14, 30, 60, 90, 180, 0)
                    val trashRetentionLabels = trashRetentionOptions.map { days ->
                        if (days <= 0) {
                            stringResource(R.string.settings_trash_forever)
                        } else {
                            pluralStringResource(R.plurals.settings_trash_days_value, days, days)
                        }
                    }
                    StopSliderSetting(
                        title = stringResource(R.string.settings_trash_retention),
                        stopLabels = trashRetentionLabels,
                        selectedIndex = trashRetentionOptions.indexOf(trashRetentionDays).coerceAtLeast(0),
                        onSelect = { viewModel.setTrashRetentionDays(trashRetentionOptions[it]) }
                    )
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

                run {
                    // Off by default — silently shelving a user's completed tasks without
                    // them asking would look like data loss.
                    val autoArchiveOptions = listOf(0, 1, 3, 7, 14, 30, 60, 90)
                    val autoArchiveLabels = autoArchiveOptions.map { days ->
                        if (days <= 0) {
                            stringResource(R.string.settings_auto_archive_off)
                        } else {
                            pluralStringResource(R.plurals.settings_auto_archive_value, days, days)
                        }
                    }
                    StopSliderSetting(
                        title = stringResource(R.string.settings_auto_archive),
                        stopLabels = autoArchiveLabels,
                        selectedIndex = autoArchiveOptions.indexOf(autoArchiveDays).coerceAtLeast(0),
                        onSelect = { viewModel.setAutoArchiveDays(autoArchiveOptions[it]) }
                    )
                }
            }
        }
        item {
            // Danger Zone — irreversible. Kept as its own card so it isn't scanned past as just
            // another row in the middle of routine import/export/lifecycle settings.
            SettingsSectionCard {
                SettingsSubsectionHeader(text = stringResource(R.string.settings_danger_zone_group))

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

                        run {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToRemoteSync() }
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

                            // Compare/restore/clear-lock are gated on the toggle, not just on being
                            // configured -- when it's off, cloud sync is meant to be fully paused,
                            // not just "no longer automatic." Configure server above stays reachable
                            // either way so turning it on doesn't require re-entering credentials.
                            if (sftpBackupEnabled) {
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
                                    val initialJoinFailure = results.initialSyncConfirmationRequired()
                                    val emptyLocalFailure = results.emptyLocalDataConfirmationRequired()
                                    if (syncLockFailure != null) {
                                        clearSyncLockDialogMessage = syncLockClearPrompt(context, syncLockFailure)
                                        showClearSyncLockDialog = true
                                    } else if (initialJoinFailure != null) {
                                        initialSyncMergeMessage = initialJoinFailure.message
                                    } else if (emptyLocalFailure != null) {
                                        emptyLocalSyncMessage = emptyLocalFailure.message
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
        item {
            // Manual, one-off backup/restore to a file the user picks — distinct from the
            // automatic Local Backup card above and the Remote Backup card further up.
            SettingsSectionCard {
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
        if (settingsDestination == SettingsDestination.HELP_ABOUT) {
        item {
            SettingsSectionCard {
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
            }
        }
        }
        if (settingsDestination == SettingsDestination.HELP_ABOUT) {
        item {
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
        item {
            AboutEntrance(delayMillis = 0) {
                GitHubAndShareRow(onNavigateToShareApp = onNavigateToShareApp, modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            AboutEntrance(delayMillis = 60) {
                OtherAppsCard(modifier = Modifier.fillMaxWidth())
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
            title = { Text(stringResource(R.string.settings_edit_profile)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextField(
                        value = profileDraftName,
                        onValueChange = { profileDraftName = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.settings_profile_name_label)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        shape = YataCompactFieldShape,
                        colors = yataFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextField(
                        value = profileDraftEmail,
                        onValueChange = { profileDraftEmail = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.settings_profile_email_label)) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { saveProfile() }),
                        shape = YataCompactFieldShape,
                        colors = yataFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(R.string.settings_profile_avatar_label),
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
                    Text(stringResource(R.string.action_save))
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
            title = { Text(stringResource(R.string.settings_save_theme_preset)) },
            text = {
                TextField(
                    value = themePresetName,
                    onValueChange = { themePresetName = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.settings_theme_preset_name_label)) },
                    shape = YataCompactFieldShape,
                    colors = yataFieldColors(),
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
                    Text(stringResource(R.string.action_save))
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
                AssistChip(
                    onClick = { showDeleteAllDialog = false },
                    label = { Text(stringResource(R.string.action_cancel)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                    border = null
                )
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

    if (initialSyncMergeMessage != null) {
        AlertDialog(
            onDismissRequest = { initialSyncMergeMessage = null },
            title = { Text(stringResource(R.string.settings_initial_sync_merge_title)) },
            text = { Text(initialSyncMergeMessage ?: stringResource(R.string.settings_initial_sync_merge_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        initialSyncMergeMessage = null
                        isBackingUp = true
                        viewModel.backupAllNow(allowInitialJoinMerge = true) { results ->
                            isBackingUp = false
                            scope.launch {
                                reportBackupResults(results, snackbarHostState, context)
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.settings_initial_sync_merge_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { initialSyncMergeMessage = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (emptyLocalSyncMessage != null) {
        AlertDialog(
            onDismissRequest = { emptyLocalSyncMessage = null },
            title = { Text(stringResource(R.string.settings_empty_local_sync_title)) },
            text = { Text(emptyLocalSyncMessage ?: stringResource(R.string.settings_empty_local_sync_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        emptyLocalSyncMessage = null
                        viewModel.restoreLatestRemoteSnapshot { result ->
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (result.isSuccess) {
                                        context.getString(R.string.settings_remote_restore_success)
                                    } else {
                                        context.getString(
                                            R.string.settings_remote_restore_failed,
                                            result.exceptionOrNull()?.message ?: ""
                                        )
                                    }
                                )
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.settings_empty_local_sync_restore_action))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { emptyLocalSyncMessage = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    TextButton(
                        onClick = {
                            emptyLocalSyncMessage = null
                            isBackingUp = true
                            viewModel.backupAllNow(allowEmptyLocalOverwrite = true) { results ->
                                isBackingUp = false
                                scope.launch {
                                    reportBackupResults(results, snackbarHostState, context)
                                }
                            }
                        }
                    ) {
                        Text(
                            stringResource(R.string.settings_empty_local_sync_overwrite_action),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
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

    if (showDefaultEstimateDialog) {
        val estimateMinutes = defaultEstimateText.toIntOrNull()
        val estimateValid = estimateMinutes != null && estimateMinutes in 1..1440
        AlertDialog(
            onDismissRequest = { showDefaultEstimateDialog = false },
            title = { Text(stringResource(R.string.settings_default_estimate_custom_title)) },
            text = {
                OutlinedTextField(
                    value = defaultEstimateText,
                    onValueChange = { value ->
                        defaultEstimateText = value.filter { it.isDigit() }.take(4)
                    },
                    label = { Text(stringResource(R.string.settings_default_estimate_custom_label)) },
                    singleLine = true,
                    isError = defaultEstimateText.isNotBlank() && !estimateValid,
                    supportingText = {
                        if (defaultEstimateText.isNotBlank() && !estimateValid) {
                            Text(stringResource(R.string.settings_default_estimate_custom_error))
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (estimateValid) {
                                viewModel.setDefaultEstimateMinutes(estimateMinutes)
                                showDefaultEstimateDialog = false
                            }
                        }
                    )
                )
            },
            confirmButton = {
                TextButton(
                    enabled = estimateValid,
                    onClick = {
                        if (estimateMinutes != null) {
                            viewModel.setDefaultEstimateMinutes(estimateMinutes)
                            showDefaultEstimateDialog = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_done))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDefaultEstimateDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
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
        show = showQuietHoursStartPicker,
        initialTime = TaskScheduleUtils.formatTime(quietHoursStartHour, quietHoursStartMinute),
        onDismiss = { showQuietHoursStartPicker = false },
        onConfirm = { formatted ->
            TaskScheduleUtils.parseTime(formatted)?.let { parsed ->
                viewModel.setQuietHoursStart(parsed.hour, parsed.minute)
            }
            showQuietHoursStartPicker = false
        }
    )

    YataTimePickerLauncher(
        show = showQuietHoursEndPicker,
        initialTime = TaskScheduleUtils.formatTime(quietHoursEndHour, quietHoursEndMinute),
        onDismiss = { showQuietHoursEndPicker = false },
        onConfirm = { formatted ->
            TaskScheduleUtils.parseTime(formatted)?.let { parsed ->
                viewModel.setQuietHoursEnd(parsed.hour, parsed.minute)
            }
            showQuietHoursEndPicker = false
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
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = BodoniModaFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.settings_about_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = CircleShape
            ) {
                Text(
                    text = stringResource(
                        R.string.settings_about_version,
                        BuildConfig.VERSION_NAME,
                        "${BuildConfig.VERSION_CODE}.${BuildInfo.BUILD_DATE}"
                    ),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_about_credit),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.settings_about_made_in),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class OtherApp(
    val name: String,
    val tagline: String,
    val playStoreUrl: String,
    val icon: ImageVector
)

private val otherApps = listOf(
    OtherApp("yaja", "Journaling app", "https://play.google.com/store/apps/details?id=com.mj.yaja", Icons.Default.Book),
    OtherApp("Assetrack", "Track your assets", "https://play.google.com/store/apps/details?id=com.mj.assetrack", Icons.Default.Inventory2),
    OtherApp("Ultra", "Smart reminders", "https://play.google.com/store/apps/details?id=com.ultra.reminders", Icons.Default.Alarm)
)

/** Fades and rises the About screen's new link cards into place on first composition, staggered
 * by [delayMillis] so the two rows settle one after another rather than popping in together. */
@Composable
private fun AboutEntrance(delayMillis: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(YataDur.nav, easing = YataEase.emphDecel)) +
            slideInVertically(tween(YataDur.nav, easing = YataEase.emphDecel)) { it / 4 }
    ) {
        content()
    }
}

@Composable
private fun OtherAppsCard(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val accents = LocalYataAccents.current
    val tileAccents = listOf(accents.accentA, accents.accentB, accents.accentC)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.settings_about_other_apps),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                otherApps.forEachIndexed { index, app ->
                    val tint = tileAccents[index % tileAccents.size]
                    Surface(
                        color = tint.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, tint.copy(alpha = 0.3f)),
                        onClick = { uriHandler.openUri(app.playStoreUrl) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(tint.copy(alpha = 0.22f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = app.icon,
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = app.name,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Text(
                                text = app.tagline,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val YATA_GITHUB_URL = "https://github.com/rjwarrier/yata"
private const val YATA_WEBSITE_URL = "https://ranjithj.in/yata/"

@Composable
private fun GitHubAndShareRow(onNavigateToShareApp: () -> Unit, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val shareTitle = stringResource(R.string.settings_about_share)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { uriHandler.openUri(YATA_GITHUB_URL) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.settings_about_github),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            OutlinedButton(
                onClick = onNavigateToShareApp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Icon(
                    imageVector = Icons.Default.IosShare,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = shareTitle,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        FilledTonalButton(
            onClick = { uriHandler.openUri(YATA_WEBSITE_URL) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.settings_about_website),
                style = MaterialTheme.typography.labelLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                TextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) { newPin = it; error = null } },
                    label = { Text(stringResource(R.string.settings_new_pin_4_8_digits)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    shape = YataCompactFieldShape,
                    colors = yataFieldColors()
                )
                TextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) { confirmPin = it; error = null } },
                    label = { Text(stringResource(R.string.action_confirm_pin)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    shape = YataCompactFieldShape,
                    colors = yataFieldColors()
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
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

@Composable
private fun CompactScaleSliderSetting(
    title: String,
    description: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    presets: List<Pair<String, Float>>,
    onPresetSelected: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Text(
                    text = "${(value * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { (label, presetValue) ->
                FilterChip(
                    selected = value in (presetValue - 0.01f)..(presetValue + 0.01f),
                    onClick = { onPresetSelected(presetValue) },
                    label = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SettingsSubsectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 2.dp)
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

@Composable
private fun SettingsPickerSurface(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = YataCompactFieldShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(R.string.settings_edit),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsScrollableDropdownContent(
    scrollState: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val density = LocalDensity.current
    val viewportHeight = 288.dp
    val scrollbarTrackHeight = viewportHeight - 16.dp
    Box(modifier = modifier.heightIn(max = viewportHeight)) {
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(end = if (scrollState.maxValue > 0) 10.dp else 0.dp),
            content = content
        )
        if (scrollState.maxValue > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .height(scrollbarTrackHeight)
                    .width(8.dp)
                    .padding(horizontal = 2.dp)
            ) {
                val trackHeightPx = with(density) { scrollbarTrackHeight.toPx() }
                val minThumbPx = with(density) { 36.dp.toPx() }
                val thumbHeightPx = (
                    trackHeightPx * trackHeightPx /
                        (trackHeightPx + scrollState.maxValue)
                    ).coerceIn(minThumbPx, trackHeightPx)
                val thumbOffsetPx = (
                    (trackHeightPx - thumbHeightPx) *
                        scrollState.value /
                        scrollState.maxValue
                    ).coerceAtLeast(0f)
                Box(
                    modifier = Modifier
                        .offset(y = with(density) { thumbOffsetPx.toDp() })
                        .width(4.dp)
                        .height(with(density) { thumbHeightPx.toDp() })
                        .clip(RoundedCornerShape(percent = 50))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.72f))
                )
            }
        }
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
    description: String = "",
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
        if (description.isNotBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
