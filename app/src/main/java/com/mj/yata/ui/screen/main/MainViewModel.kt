package com.mj.yata.ui.screen.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mj.yata.R
import com.mj.yata.data.backup.BackupDiff
import com.mj.yata.data.local.crash.CrashLogCluster
import com.mj.yata.data.local.crash.CrashLogEntry
import com.mj.yata.data.local.crash.CrashLogStore
import com.mj.yata.data.local.datastore.UserPreferences
import com.mj.yata.data.local.operationhistory.OperationHistoryEntry
import com.mj.yata.data.local.operationhistory.OperationHistoryStore
import com.mj.yata.data.github.GitHubNotFoundException
import com.mj.yata.data.github.GitHubPermissionException
import com.mj.yata.data.github.HttpGitHubApi
import com.mj.yata.data.sftp.RemoteBackupCredentialsStore
import com.mj.yata.data.sftp.SftpConnectionTestResult
import com.mj.yata.domain.model.*
import com.mj.yata.domain.repository.YataRepository
import com.mj.yata.domain.sync.RestorePoint
import com.mj.yata.domain.usecase.BackupOperations
import com.mj.yata.domain.usecase.TaskOperations
import com.mj.yata.util.AnalyticsPeriod
import com.mj.yata.util.AnalyticsUiState
import com.mj.yata.util.AnalyticsUtils
import com.mj.yata.util.AppLanguageController
import com.mj.yata.ui.error.AppErrorBus
import com.mj.yata.ui.sheets.NewTaskDraft
import com.mj.yata.util.NaturalLanguageParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: YataRepository,
    private val userPreferences: UserPreferences,
    private val taskOperations: TaskOperations,
    private val backupOperations: BackupOperations,
    private val errorBus: AppErrorBus,
    private val crashLogStore: CrashLogStore,
    private val operationHistoryStore: OperationHistoryStore,
    private val remoteBackupCredentialsStore: RemoteBackupCredentialsStore
) : ViewModel() {

    /**
     * Every write path below runs through this instead of `viewModelScope.launch` directly.
     * These coroutines all end in a Room call, and Room throws for reasons that are not bugs and
     * not preventable from here — a foreign-key violation from a row deleted on another thread,
     * a full disk, `SQLiteDiskIOException` on failing storage. Uncaught in a bare `launch`, each
     * of those reaches the thread's uncaught handler and kills the app, turning a failed save
     * into a crash on the most common actions in the app (add task, add person).
     *
     * Caught, the operation is simply lost and the user is told so, which is recoverable — they
     * can retry. CancellationException is rethrown so normal scope teardown still cancels
     * children rather than being reported as a failure.
     */
    private fun safeLaunch(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e("MainViewModel", "Operation failed", t)
                // Recorded as non-fatal. These used to take the app down; now that they don't,
                // the stack trace would otherwise exist only in logcat — i.e. be gone by the time
                // anyone noticed the save silently failed. Written on the IO dispatcher, unlike
                // the uncaught-handler path which has to be synchronous: safeLaunch runs on the
                // main thread and this is still a file write, rare error path or not.
                val failedOn = Thread.currentThread().name
                withContext(Dispatchers.IO) { crashLogStore.record(t, failedOn, fatal = false) }
                errorBus.emit(R.string.error_action_failed)
            }
        }
    }

    /**
     * Crash history for `Screen.CrashLog`. Backed by files rather than Room or DataStore, so there
     * is nothing to observe — the screen calls [refreshCrashLogs] when it opens and after each
     * mutation instead of this updating itself.
     */
    private val _crashLogs = MutableStateFlow<List<CrashLogEntry>>(emptyList())
    val crashLogs: StateFlow<List<CrashLogEntry>> = _crashLogs.asStateFlow()
    private val _crashClusters = MutableStateFlow<List<CrashLogCluster>>(emptyList())
    val crashClusters: StateFlow<List<CrashLogCluster>> = _crashClusters.asStateFlow()
    private val _operationHistory = MutableStateFlow<List<OperationHistoryEntry>>(emptyList())
    val operationHistory: StateFlow<List<OperationHistoryEntry>> = _operationHistory.asStateFlow()

    fun refreshCrashLogs() {
        safeLaunch {
            val entries = withContext(Dispatchers.IO) { crashLogStore.list() }
            val clusters = withContext(Dispatchers.IO) { crashLogStore.listClusters() }
            _crashLogs.value = entries
            _crashClusters.value = clusters
            refreshOperationHistory()
        }
    }

    fun refreshOperationHistory() {
        safeLaunch {
            val entries = withContext(Dispatchers.IO) { operationHistoryStore.list() }
            _operationHistory.value = entries
        }
    }

    fun clearOperationHistory() {
        safeLaunch {
            withContext(Dispatchers.IO) { operationHistoryStore.clear() }
            refreshOperationHistory()
        }
    }

    /** Full report text for one entry, read off the main thread. */
    suspend fun readCrashLog(id: String): String = withContext(Dispatchers.IO) { crashLogStore.read(id) }

    fun deleteCrashLog(id: String) {
        safeLaunch {
            withContext(Dispatchers.IO) { crashLogStore.delete(id) }
            refreshCrashLogs()
        }
    }

    fun clearCrashLogs() {
        safeLaunch {
            withContext(Dispatchers.IO) { crashLogStore.clear() }
            refreshCrashLogs()
        }
    }

    init {
        safeLaunch {
            repository.seedInitialDataIfNeeded()
            repository.purgeOldTrash()
            repository.autoArchiveOldCompleted()
            syncMePersonPhotoWithProfile()
            // A device with no local edit would otherwise wait for the periodic worker before it
            // sees another device's changes. Opening the app is the natural low-cost pull trigger.
            backupOperations.syncSelfHostedIfConfigured()
        }
    }

    /**
     * One-time reconciliation on startup: the "me" [Person] row (used for assignee avatars
     * everywhere — task rows, PersonDetailScreen, mentions) is seeded once with `photoUri = null`
     * and was never kept in sync with the profile photo saved in Settings, which lives separately
     * in DataStore. Anyone who set a profile picture before this fix has a "me" row that still
     * shows initials instead of it. [setUserPhotoUri] keeps the two in sync going forward; this
     * catches everyone who set theirs before that existed.
     */
    private suspend fun syncMePersonPhotoWithProfile() {
        val targetUri = userPreferences.userPhotoUriFlow.first()
        val me = repository.getPeople().first().find { it.isMe } ?: return
        if (me.photoUri != targetUri) {
            repository.upsertPerson(me.copy(photoUri = targetUri))
        }
    }

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appFont: AppFont = AppFont.INTER,
    val userName: String = "",
    val userEmail: String = "",
    val userPhotoUri: String? = null,
    val defaultListId: String = "",
    val startOfWeekSunday: Boolean = true,
    val defaultReminderHour: Int = 9,
    val defaultReminderMinute: Int = 0,
    val reduceMotionEnabled: Boolean = false,
    val motionMode: MotionMode = MotionMode.FULL,
    val enhancedM3ThemingEnabled: Boolean = false,
    val floatingBottomNavEnabled: Boolean = false,
    val bottomNavLabelsEnabled: Boolean = true,
    val textScale: Float = 1.0f,
    val taskRowDensity: TaskRowDensity = TaskRowDensity.COMFORTABLE,
    val hapticsEnabled: Boolean = true,
    val taskSwipeActionsEnabled: Boolean = true,
    val completionSoundEnabled: Boolean = true,
    val appLockEnabled: Boolean = false,
    val appLockPinSet: Boolean = false,
    val appLockTimeoutMinutes: Int = 0,
    val todayTabEnabled: Boolean = true,
    val upcomingTabEnabled: Boolean = true,
    val fabPosition: FabPosition = FabPosition.RIGHT,
    val uiScale: Float = 1.0f,
    val dynamicColorEnabled: Boolean = true,
    val peopleFeatureEnabled: Boolean = true,
    val tagsFeatureEnabled: Boolean = true,
    val projectsFeatureEnabled: Boolean = true,
    val lists: List<YataList> = emptyList(),
    val backupIntervalMinutes: Long = 1440L,
    val localBackupEnabled: Boolean = false,
    val localBackupLastAt: Long? = null,
    val sftpBackupEnabled: Boolean = false,
    val sftpHost: String = "",
    val sftpPort: Int = 22,
    val sftpUsername: String = "",
    val sftpAuthMethod: String = "PASSWORD",
    val sftpRemoteDir: String = "/yata-backups",
    val sftpIntervalMinutes: Long = 1440L,
    val sftpLastBackupAt: Long? = null,
    val sftpHostKeyFingerprint: String? = null,
    val remoteBackupProtocol: com.mj.yata.domain.model.RemoteBackupProtocol = com.mj.yata.domain.model.RemoteBackupProtocol.SFTP,
    val ftpUseTls: Boolean = true,
    val sftpKeepCount: Int = 5,
    val githubOwner: String = "",
    val githubRepo: String = "",
    val githubBranch: String = "",
    val githubApiBase: String = "https://api.github.com",
    val githubTokenExpiresAt: Long? = null,
    val githubLastHeadSha: String? = null,
    val dateAliasDefinitions: Set<String> = emptySet(),
    val savedThemePresetDefinitions: Set<String> = emptySet(),
    val taskerIntegrationEnabled: Boolean = true,
    val todayRemainingCount: Int = 0
)

data class MainScreenUiState(
    val tasks: List<Task> = emptyList(),
    val projects: List<Project> = emptyList(),
    val activeProjects: List<Project> = emptyList(),
    val lists: List<YataList> = emptyList(),
    val people: List<Person> = emptyList(),
    val activePeople: List<Person> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val tagGroups: List<TagGroup> = emptyList(),
    val personGroups: List<PersonGroup> = emptyList(),
    val userName: String = "",
    val userEmail: String = "",
    val userPhotoUri: String? = null,
    val startOfWeekSunday: Boolean = true,
    val peopleFeatureEnabled: Boolean = true,
    val tagsFeatureEnabled: Boolean = true,
    val projectsFeatureEnabled: Boolean = true,
    val taskRowDensity: TaskRowDensity = TaskRowDensity.COMFORTABLE,
    val todayTabEnabled: Boolean = true,
    val upcomingTabEnabled: Boolean = true,
    val fabPosition: FabPosition = FabPosition.RIGHT,
    val hideCompletedToday: Boolean = false,
    val todayRemainingCount: Int = 0
)

private data class SettingsProfileState(
    val themeMode: ThemeMode,
    val appFont: AppFont,
    val userName: String,
    val userEmail: String,
    val userPhotoUri: String?
)

private data class SettingsReminderState(
    val defaultListId: String,
    val startOfWeekSunday: Boolean,
    val defaultReminderHour: Int,
    val defaultReminderMinute: Int
)

// The theme-schedule times that used to pad these two out went with the SCHEDULED theme mode when
// it was replaced by AMOLED — they were still being read from DataStore and carried all the way
// into the settings state, where nothing had looked at them since. Dropping them collapses the
// nested combine that only existed to fit them within combine's five-flow limit.
private data class SettingsDisplayState(
    val reduceMotionEnabled: Boolean,
    val motionMode: MotionMode,
    val enhancedM3ThemingEnabled: Boolean,
    val floatingBottomNavEnabled: Boolean,
    val bottomNavLabelsEnabled: Boolean,
    val textScale: Float
)

private data class SettingsFeatureState(
    val taskRowDensity: TaskRowDensity,
    val hapticsEnabled: Boolean,
    val taskSwipeActionsEnabled: Boolean,
    val completionSoundEnabled: Boolean,
    val appLockEnabled: Boolean,
    val appLockPinSet: Boolean,
    val appLockTimeoutMinutes: Int,
    val todayTabEnabled: Boolean,
    val upcomingTabEnabled: Boolean,
    val fabPosition: FabPosition
)

private data class AppLockFlags(
    val hapticsEnabled: Boolean,
    val taskSwipeActionsEnabled: Boolean,
    val completionSoundEnabled: Boolean,
    val appLockEnabled: Boolean,
    val appLockPinSet: Boolean,
    val appLockTimeoutMinutes: Int
)

private data class SettingsVisualFeatureState(
    val uiScale: Float,
    val dynamicColorEnabled: Boolean,
    val peopleFeatureEnabled: Boolean,
    val tagsFeatureEnabled: Boolean,
    val projectsFeatureEnabled: Boolean
)

private data class SettingsBackupState(
    val lists: List<YataList>,
    val backupIntervalMinutes: Long,
    val localBackupEnabled: Boolean,
    val localBackupLastAt: Long?
)

// Split across two nested groups (rather than one) purely because there are 9 SFTP fields and
// combine's direct-lambda overload tops out at 5 — same reason SettingsBackupState/
// SettingsCloudScheduleState are split from each other.
private data class SftpConfigState(
    val sftpBackupEnabled: Boolean,
    val sftpHost: String,
    val sftpPort: Int,
    val sftpUsername: String,
    val sftpAuthMethod: String
)

private data class SftpStatusState(
    val sftpRemoteDir: String,
    val sftpIntervalMinutes: Long,
    val sftpLastBackupAt: Long?,
    val sftpHostKeyFingerprint: String?
)

private data class RemoteBackupProtocolState(
    val remoteBackupProtocol: com.mj.yata.domain.model.RemoteBackupProtocol,
    val ftpUseTls: Boolean,
    val sftpKeepCount: Int
)

private data class GitHubSettingsState(
    val owner: String,
    val repo: String,
    val branch: String,
    val apiBase: String,
    val tokenExpiresAt: Long?,
    val lastHeadSha: String?
)

private data class SftpSettingsState(
    val config: SftpConfigState,
    val status: SftpStatusState,
    val protocol: RemoteBackupProtocolState,
    val github: GitHubSettingsState
)

private data class SettingsPortState(
    val dateAliasDefinitions: Set<String>,
    val savedThemePresetDefinitions: Set<String>,
    val taskerIntegrationEnabled: Boolean
)

private data class SettingsCoreState(
    val profile: SettingsProfileState,
    val reminder: SettingsReminderState,
    val display: SettingsDisplayState,
    val feature: SettingsFeatureState,
    val visualFeature: SettingsVisualFeatureState
)

private data class MainDataState(
    val tasks: List<Task>,
    val projects: List<Project>,
    val activeProjects: List<Project>,
    val lists: List<YataList>,
    val people: List<Person>
)

private data class MainExtraDataState(
    val activePeople: List<Person>,
    val tags: List<Tag>,
    val tagGroups: List<TagGroup>,
    val personGroups: List<PersonGroup>
)

private data class MainProfileState(
    val userName: String,
    val userEmail: String,
    val userPhotoUri: String?,
    val startOfWeekSunday: Boolean
)

private data class MainFeatureState(
    val peopleFeatureEnabled: Boolean,
    val tagsFeatureEnabled: Boolean,
    val projectsFeatureEnabled: Boolean,
    val taskRowDensity: TaskRowDensity,
    val todayTabEnabled: Boolean
)

private data class MainNavigationState(
    val upcomingTabEnabled: Boolean,
    val fabPosition: FabPosition,
    val hideCompletedToday: Boolean,
    val todayRemainingCount: Int
)

    // Data streams
    val tasks: StateFlow<List<Task>> = repository.getTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val projects: StateFlow<List<Project>> = repository.getProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProjects: StateFlow<List<Project>> = repository.getActiveProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val archivedProjects: StateFlow<List<Project>> = repository.getArchivedProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lists: StateFlow<List<YataList>> = repository.getLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val people: StateFlow<List<Person>> = repository.getPeople()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activePeople: StateFlow<List<Person>> = repository.getActivePeople()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val archivedPeople: StateFlow<List<Person>> = repository.getArchivedPeople()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tags: StateFlow<List<Tag>> = repository.getTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tagGroups: StateFlow<List<TagGroup>> = repository.getTagGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val personGroups: StateFlow<List<PersonGroup>> = repository.getPersonGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Today's remaining (due, incomplete) task count — the badge shown on every bottom nav bar,
     * and consumed by [settingsUiState]/[mainScreenUiState] below instead of each recomputing it
     * independently (one of those inline copies had drifted and was missing the
     * hiddenFromMainTaskProjectIds() filter the others apply). Declared early (uses the raw
     * repository flows, not the [projects]/[lists] StateFlow properties, which are declared
     * later) so it's available to both of those combine chains.
     *
     * Uses the same Task.isActionableToday predicate (plus both container exclusions) as the
     * Today tab and the home-screen widgets — this one had drifted from *them* too, checking only
     * project exclusion and neither deferral nor waiting-on, so the badge could show a count the
     * Today screen it links to didn't actually list. */
    val todayRemainingCount: StateFlow<Int> = combine(
        tasks,
        projects,
        lists,
        people
    ) { list, projectList, listList, peopleList ->
        val todayStr = LocalDate.now().toString()
        val myId = peopleList.firstOrNull { it.isMe }?.id
        val excludedProjectIds = projectList.hiddenFromMainTaskProjectIds()
        val excludedListIds = listList.hiddenFromMainTaskListIds()
        list.count {
            it.isActionableToday(todayStr, System.currentTimeMillis(), myId) &&
                it.projectId !in excludedProjectIds && it.listId !in excludedListIds
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val voiceRecognitionLanguage: StateFlow<String> = userPreferences.voiceRecognitionLanguageFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "default")

    private val _appLanguage = MutableStateFlow(AppLanguageController.current())
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    fun setAppLanguage(language: AppLanguage) {
        AppLanguageController.apply(language)
        _appLanguage.value = language
    }

    fun setVoiceRecognitionLanguage(lang: String) {
        safeLaunch {
            userPreferences.setVoiceRecognitionLanguage(lang)
        }
    }

    fun getTaskById(taskId: String): Flow<Task?> = repository.getTaskById(taskId)

    fun getTasksForList(listId: String): Flow<List<Task>> = repository.getTasksForList(listId)

    fun getTasksForProject(projectId: String): Flow<List<Task>> = repository.getTasksForProject(projectId)

    fun getTasksForPerson(personId: String): Flow<List<Task>> = repository.getTasksForPerson(personId)

    fun searchTasks(query: String): Flow<List<Task>> = repository.searchTasks(query)

    private val settingsCoreFlow = combine(
        combine(
            userPreferences.themeModeFlow,
            userPreferences.appFontFlow,
            userPreferences.userNameFlow,
            userPreferences.userEmailFlow,
            userPreferences.userPhotoUriFlow
        ) { themeMode, appFont, userName, userEmail, userPhotoUri ->
            SettingsProfileState(themeMode, appFont, userName, userEmail, userPhotoUri)
        },
        combine(
            userPreferences.defaultListIdFlow,
            userPreferences.startOfWeekSundayFlow,
            userPreferences.defaultReminderHourFlow,
            userPreferences.defaultReminderMinuteFlow
        ) { defaultListId, startOfWeekSunday, defaultReminderHour, defaultReminderMinute ->
            SettingsReminderState(defaultListId, startOfWeekSunday, defaultReminderHour, defaultReminderMinute)
        },
        combine(
            combine(
                userPreferences.motionModeFlow,
                userPreferences.reduceMotionEnabledFlow
            ) { motionMode, reduceMotion -> motionMode to reduceMotion },
            userPreferences.enhancedM3ThemingEnabledFlow,
            userPreferences.floatingBottomNavEnabledFlow,
            userPreferences.bottomNavLabelsEnabledFlow,
            userPreferences.textScaleFlow
        ) { motion, enhancedM3, floatingNav, bottomNavLabels, textScale ->
            SettingsDisplayState(motion.second, motion.first, enhancedM3, floatingNav, bottomNavLabels, textScale)
        },
        combine(
            userPreferences.taskRowDensityFlow,
            combine(
                userPreferences.hapticsEnabledFlow,
                userPreferences.taskSwipeActionsEnabledFlow,
                userPreferences.completionSoundEnabledFlow,
                combine(
                    userPreferences.appLockEnabledFlow,
                    userPreferences.appLockPinSetFlow,
                    userPreferences.appLockTimeoutMinutesFlow
                ) { appLockEnabled, appLockPinSet, appLockTimeoutMinutes ->
                    Triple(appLockEnabled, appLockPinSet, appLockTimeoutMinutes)
                }
            ) { hapticsEnabled, taskSwipeActionsEnabled, completionSoundEnabled, appLockDetails ->
                AppLockFlags(
                    hapticsEnabled,
                    taskSwipeActionsEnabled,
                    completionSoundEnabled,
                    appLockDetails.first,
                    appLockDetails.second,
                    appLockDetails.third
                )
            },
            userPreferences.todayTabEnabledFlow,
            userPreferences.upcomingTabEnabledFlow,
            userPreferences.fabPositionFlow
        ) { taskRowDensity, appLockFlags, todayTabEnabled, upcomingTabEnabled, fabPosition ->
            SettingsFeatureState(
                taskRowDensity,
                appLockFlags.hapticsEnabled,
                appLockFlags.taskSwipeActionsEnabled,
                appLockFlags.completionSoundEnabled,
                appLockFlags.appLockEnabled,
                appLockFlags.appLockPinSet,
                appLockFlags.appLockTimeoutMinutes,
                todayTabEnabled,
                upcomingTabEnabled,
                fabPosition
            )
        },
        combine(
            userPreferences.uiScaleFlow,
            userPreferences.dynamicColorEnabledFlow,
            userPreferences.peopleFeatureEnabledFlow,
            userPreferences.tagsFeatureEnabledFlow,
            userPreferences.projectsFeatureEnabledFlow
        ) { uiScale, dynamicColorEnabled, peopleFeatureEnabled, tagsFeatureEnabled, projectsFeatureEnabled ->
            SettingsVisualFeatureState(uiScale, dynamicColorEnabled, peopleFeatureEnabled, tagsFeatureEnabled, projectsFeatureEnabled)
        }
    ) { profile, reminder, display, feature, visualFeature ->
        SettingsCoreState(profile, reminder, display, feature, visualFeature)
    }

    val settingsUiState: StateFlow<SettingsUiState> = combine(
        settingsCoreFlow,
        combine(
            lists,
            userPreferences.backupIntervalMinutesFlow,
            userPreferences.localBackupEnabledFlow,
            userPreferences.localBackupLastAtFlow
        ) { lists, backupIntervalMinutes, localBackupEnabled, localBackupLastAt ->
            SettingsBackupState(lists, backupIntervalMinutes, localBackupEnabled, localBackupLastAt)
        },
        combine(
            combine(
                userPreferences.sftpBackupEnabledFlow,
                userPreferences.sftpHostFlow,
                userPreferences.sftpPortFlow,
                userPreferences.sftpUsernameFlow,
                userPreferences.sftpAuthMethodFlow
            ) { enabled, host, port, username, authMethod -> SftpConfigState(enabled, host, port, username, authMethod) },
            combine(
                userPreferences.sftpRemoteDirFlow,
                userPreferences.sftpIntervalMinutesFlow,
                userPreferences.sftpLastBackupAtFlow,
                userPreferences.sftpHostKeyFingerprintFlow
            ) { remoteDir, intervalMinutes, lastBackupAt, hostKeyFingerprint -> SftpStatusState(remoteDir, intervalMinutes, lastBackupAt, hostKeyFingerprint) },
            combine(
                userPreferences.remoteBackupProtocolFlow,
                userPreferences.ftpUseTlsFlow,
                userPreferences.sftpKeepCountFlow
            ) { protocol, ftpUseTls, keepCount -> RemoteBackupProtocolState(protocol, ftpUseTls, keepCount) },
            combine(
                userPreferences.githubOwnerFlow,
                userPreferences.githubRepoFlow,
                userPreferences.githubBranchFlow,
                userPreferences.githubApiBaseFlow,
                combine(
                    userPreferences.githubTokenExpiresAtFlow,
                    userPreferences.githubLastHeadShaFlow
                ) { tokenExpiresAt, lastHeadSha -> tokenExpiresAt to lastHeadSha }
            ) { owner, repo, branch, apiBase, tokenState ->
                GitHubSettingsState(
                    owner = owner,
                    repo = repo,
                    branch = branch,
                    apiBase = apiBase,
                    tokenExpiresAt = tokenState.first,
                    lastHeadSha = tokenState.second
                )
            }
        ) { config, status, protocol, github -> SftpSettingsState(config, status, protocol, github) },
        combine(
            userPreferences.dateAliasDefinitionsFlow,
            userPreferences.savedThemePresetsFlow,
            userPreferences.taskerIntegrationEnabledFlow
        ) { dateAliases, savedPresets, taskerEnabled ->
            SettingsPortState(dateAliases, savedPresets, taskerEnabled)
        },
        todayRemainingCount
    ) { core, backup, sftp, ports, count ->
        SettingsUiState(
            themeMode = core.profile.themeMode,
            appFont = core.profile.appFont,
            userName = core.profile.userName,
            userEmail = core.profile.userEmail,
            userPhotoUri = core.profile.userPhotoUri,
            defaultListId = core.reminder.defaultListId,
            startOfWeekSunday = core.reminder.startOfWeekSunday,
            defaultReminderHour = core.reminder.defaultReminderHour,
            defaultReminderMinute = core.reminder.defaultReminderMinute,
            reduceMotionEnabled = core.display.reduceMotionEnabled,
            motionMode = core.display.motionMode,
            enhancedM3ThemingEnabled = core.display.enhancedM3ThemingEnabled,
            floatingBottomNavEnabled = core.display.floatingBottomNavEnabled,
            bottomNavLabelsEnabled = core.display.bottomNavLabelsEnabled,
            textScale = core.display.textScale,
            taskRowDensity = core.feature.taskRowDensity,
            hapticsEnabled = core.feature.hapticsEnabled,
            taskSwipeActionsEnabled = core.feature.taskSwipeActionsEnabled,
            completionSoundEnabled = core.feature.completionSoundEnabled,
            appLockEnabled = core.feature.appLockEnabled,
            appLockPinSet = core.feature.appLockPinSet,
            appLockTimeoutMinutes = core.feature.appLockTimeoutMinutes,
            todayTabEnabled = core.feature.todayTabEnabled,
            upcomingTabEnabled = core.feature.upcomingTabEnabled,
            fabPosition = core.feature.fabPosition,
            uiScale = core.visualFeature.uiScale,
            dynamicColorEnabled = core.visualFeature.dynamicColorEnabled,
            peopleFeatureEnabled = core.visualFeature.peopleFeatureEnabled,
            tagsFeatureEnabled = core.visualFeature.tagsFeatureEnabled,
            projectsFeatureEnabled = core.visualFeature.projectsFeatureEnabled,
            lists = backup.lists,
            backupIntervalMinutes = backup.backupIntervalMinutes,
            localBackupEnabled = backup.localBackupEnabled,
            localBackupLastAt = backup.localBackupLastAt,
            sftpBackupEnabled = sftp.config.sftpBackupEnabled,
            sftpHost = sftp.config.sftpHost,
            sftpPort = sftp.config.sftpPort,
            sftpUsername = sftp.config.sftpUsername,
            sftpAuthMethod = sftp.config.sftpAuthMethod,
            sftpRemoteDir = sftp.status.sftpRemoteDir,
            sftpIntervalMinutes = sftp.status.sftpIntervalMinutes,
            sftpLastBackupAt = sftp.status.sftpLastBackupAt,
            sftpHostKeyFingerprint = sftp.status.sftpHostKeyFingerprint,
            remoteBackupProtocol = sftp.protocol.remoteBackupProtocol,
            ftpUseTls = sftp.protocol.ftpUseTls,
            sftpKeepCount = sftp.protocol.sftpKeepCount,
            githubOwner = sftp.github.owner,
            githubRepo = sftp.github.repo,
            githubBranch = sftp.github.branch,
            githubApiBase = sftp.github.apiBase,
            githubTokenExpiresAt = sftp.github.tokenExpiresAt,
            githubLastHeadSha = sftp.github.lastHeadSha,
            dateAliasDefinitions = ports.dateAliasDefinitions,
            savedThemePresetDefinitions = ports.savedThemePresetDefinitions,
            taskerIntegrationEnabled = ports.taskerIntegrationEnabled,
            todayRemainingCount = count
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    val mainScreenUiState: StateFlow<MainScreenUiState> = combine(
        combine(
            tasks,
            projects,
            activeProjects,
            lists,
            people
        ) { tasks, projects, activeProjects, lists, people ->
            MainDataState(tasks, projects, activeProjects, lists, people)
        },
        combine(
            activePeople,
            tags,
            tagGroups,
            personGroups
        ) { activePeople, tags, tagGroups, personGroups ->
            MainExtraDataState(activePeople, tags, tagGroups, personGroups)
        },
        combine(
            userPreferences.userNameFlow,
            userPreferences.userEmailFlow,
            userPreferences.userPhotoUriFlow,
            userPreferences.startOfWeekSundayFlow
        ) { userName, userEmail, userPhotoUri, startOfWeekSunday ->
            MainProfileState(userName, userEmail, userPhotoUri, startOfWeekSunday)
        },
        combine(
            userPreferences.peopleFeatureEnabledFlow,
            userPreferences.tagsFeatureEnabledFlow,
            userPreferences.projectsFeatureEnabledFlow,
            userPreferences.taskRowDensityFlow,
            userPreferences.todayTabEnabledFlow
        ) { peopleFeatureEnabled, tagsFeatureEnabled, projectsFeatureEnabled, taskRowDensity, todayTabEnabled ->
            MainFeatureState(peopleFeatureEnabled, tagsFeatureEnabled, projectsFeatureEnabled, taskRowDensity, todayTabEnabled)
        },
        combine(
            userPreferences.upcomingTabEnabledFlow,
            userPreferences.fabPositionFlow,
            userPreferences.hideCompletedTodayFlow,
            todayRemainingCount
        ) { upcomingTabEnabled, fabPosition, hideCompletedToday, todayCount ->
            MainNavigationState(upcomingTabEnabled, fabPosition, hideCompletedToday, todayCount)
        }
    ) { data, extraData, profile, feature, navigation ->
        MainScreenUiState(
            tasks = data.tasks,
            projects = data.projects,
            activeProjects = data.activeProjects,
            lists = data.lists,
            people = data.people,
            activePeople = extraData.activePeople,
            tags = extraData.tags,
            tagGroups = extraData.tagGroups,
            personGroups = extraData.personGroups,
            userName = profile.userName,
            userEmail = profile.userEmail,
            userPhotoUri = profile.userPhotoUri,
            startOfWeekSunday = profile.startOfWeekSunday,
            peopleFeatureEnabled = feature.peopleFeatureEnabled,
            tagsFeatureEnabled = feature.tagsFeatureEnabled,
            projectsFeatureEnabled = feature.projectsFeatureEnabled,
            taskRowDensity = feature.taskRowDensity,
            todayTabEnabled = feature.todayTabEnabled,
            upcomingTabEnabled = navigation.upcomingTabEnabled,
            fabPosition = navigation.fabPosition,
            hideCompletedToday = navigation.hideCompletedToday,
            todayRemainingCount = navigation.todayRemainingCount
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainScreenUiState())

    private val analyticsPeriodFlow = MutableStateFlow(AnalyticsPeriod.WEEK)
    val analyticsPeriod: StateFlow<AnalyticsPeriod> = analyticsPeriodFlow.asStateFlow()

    fun setAnalyticsPeriod(period: AnalyticsPeriod) {
        analyticsPeriodFlow.value = period
    }

    /** Every Analytics-screen metric, computed off the UI thread in [AnalyticsUtils.computeUiState]
     * whenever the underlying data or the selected period changes — the screen only renders this. */
    val analyticsUiState: StateFlow<AnalyticsUiState> = combine(
        tasks, projects, people, tags, lists, analyticsPeriodFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        AnalyticsUtils.computeUiState(
            tasks = values[0] as List<Task>,
            projects = values[1] as List<Project>,
            people = values[2] as List<Person>,
            tags = values[3] as List<Tag>,
            lists = values[4] as List<YataList>,
            period = values[5] as AnalyticsPeriod
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AnalyticsUiState())

    // Preferences
    val themeMode: StateFlow<ThemeMode> = userPreferences.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val appFont: StateFlow<AppFont> = userPreferences.appFontFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppFont.INTER)

    // Standalone rather than folded into SettingsUiState: that state is assembled by a combine of
    // combines that is already at arity, and these are only read by the two sliders.
    val colorIntensity: StateFlow<com.mj.yata.domain.model.ColorIntensity> = userPreferences.colorIntensityFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.mj.yata.domain.model.ColorIntensity.NORMAL)

    val backgroundTint: StateFlow<com.mj.yata.domain.model.BackgroundTint> = userPreferences.backgroundTintFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.mj.yata.domain.model.BackgroundTint.SOFT)

    val taskCardBackground: StateFlow<Boolean> = userPreferences.taskCardBackgroundFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val userName: StateFlow<String> = userPreferences.userNameFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val userEmail: StateFlow<String> = userPreferences.userEmailFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val userPhotoUri: StateFlow<String?> = userPreferences.userPhotoUriFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val defaultListId: StateFlow<String> = userPreferences.defaultListIdFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val startOfWeekSunday: StateFlow<Boolean> = userPreferences.startOfWeekSundayFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val defaultReminderHour: StateFlow<Int> = userPreferences.defaultReminderHourFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 9)

    val defaultReminderMinute: StateFlow<Int> = userPreferences.defaultReminderMinuteFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val uiScale: StateFlow<Float> = userPreferences.uiScaleFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val dynamicColorEnabled: StateFlow<Boolean> = userPreferences.dynamicColorEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // New-task defaults, applied by NewTaskSheet when nothing more specific overrides them.
    val defaultDueDate: StateFlow<com.mj.yata.domain.model.DefaultDueDate> = userPreferences.defaultDueDateFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.mj.yata.domain.model.DefaultDueDate.TODAY)

    val defaultPriority: StateFlow<String> = userPreferences.defaultPriorityFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "none")

    val trashRetentionDays: StateFlow<Int> = userPreferences.trashRetentionDaysFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 30)

    val autoArchiveDays: StateFlow<Int> = userPreferences.autoArchiveDaysFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val dailyAgendaEnabled: StateFlow<Boolean> = userPreferences.dailyAgendaEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val dailyAgendaHour: StateFlow<Int> = userPreferences.dailyAgendaHourFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 7)

    val dailyAgendaMinute: StateFlow<Int> = userPreferences.dailyAgendaMinuteFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 30)

    val overdueNudgesEnabled: StateFlow<Boolean> = userPreferences.overdueNudgesEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val undoWindowSeconds: StateFlow<Int> = userPreferences.undoWindowSecondsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 4)

    val snoozeTonightHour: StateFlow<Int> = userPreferences.snoozeTonightHourFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 18)
    val snoozeTonightMinute: StateFlow<Int> = userPreferences.snoozeTonightMinuteFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val snoozeTomorrowHour: StateFlow<Int> = userPreferences.snoozeTomorrowHourFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 9)
    val snoozeTomorrowMinute: StateFlow<Int> = userPreferences.snoozeTomorrowMinuteFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val swipeRightAction: StateFlow<com.mj.yata.domain.model.SwipeAction> = userPreferences.swipeRightActionFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.mj.yata.domain.model.SwipeAction.COMPLETE)
    val swipeLeftAction: StateFlow<com.mj.yata.domain.model.SwipeAction> = userPreferences.swipeLeftActionFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.mj.yata.domain.model.SwipeAction.DELETE)
    val startupTab: StateFlow<com.mj.yata.domain.model.StartupTab> = userPreferences.startupTabFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.mj.yata.domain.model.StartupTab.LAST_USED)
    val confettiEnabled: StateFlow<Boolean> = userPreferences.confettiEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val todayShowUpcomingWhenEmpty: StateFlow<Boolean> = userPreferences.todayShowUpcomingWhenEmptyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val timeFormat: StateFlow<com.mj.yata.domain.model.TimeFormat> = userPreferences.timeFormatFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.mj.yata.domain.model.TimeFormat.SYSTEM)
    val dateFormat: StateFlow<com.mj.yata.domain.model.DateFormat> = userPreferences.dateFormatFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.mj.yata.domain.model.DateFormat.SYSTEM)

    fun setTodayShowUpcomingWhenEmpty(enabled: Boolean) {
        safeLaunch { userPreferences.setTodayShowUpcomingWhenEmpty(enabled) }
    }

    fun setSwipeRightAction(action: com.mj.yata.domain.model.SwipeAction) {
        safeLaunch { userPreferences.setSwipeRightAction(action) }
    }

    fun setSwipeLeftAction(action: com.mj.yata.domain.model.SwipeAction) {
        safeLaunch { userPreferences.setSwipeLeftAction(action) }
    }

    fun setStartupTab(tab: com.mj.yata.domain.model.StartupTab) {
        safeLaunch { userPreferences.setStartupTab(tab) }
    }

    fun setConfettiEnabled(enabled: Boolean) {
        safeLaunch { userPreferences.setConfettiEnabled(enabled) }
    }

    fun setTimeFormat(format: com.mj.yata.domain.model.TimeFormat) {
        safeLaunch { userPreferences.setTimeFormat(format) }
    }

    fun setDateFormat(format: com.mj.yata.domain.model.DateFormat) {
        safeLaunch { userPreferences.setDateFormat(format) }
    }

    fun setUndoWindowSeconds(seconds: Int) {
        safeLaunch { userPreferences.setUndoWindowSeconds(seconds) }
    }

    fun setSnoozeTonightTime(hour: Int, minute: Int) {
        safeLaunch { userPreferences.setSnoozeTonightTime(hour, minute) }
    }

    fun setSnoozeTomorrowTime(hour: Int, minute: Int) {
        safeLaunch { userPreferences.setSnoozeTomorrowTime(hour, minute) }
    }

    fun setDailyAgendaEnabled(enabled: Boolean) {
        safeLaunch { userPreferences.setDailyAgendaEnabled(enabled) }
    }

    fun setDailyAgendaTime(hour: Int, minute: Int) {
        safeLaunch { userPreferences.setDailyAgendaTime(hour, minute) }
    }

    fun setOverdueNudgesEnabled(enabled: Boolean) {
        safeLaunch { userPreferences.setOverdueNudgesEnabled(enabled) }
    }

    fun setAutoArchiveDays(days: Int) {
        safeLaunch { userPreferences.setAutoArchiveDays(days) }
    }

    fun setDefaultDueDate(mode: com.mj.yata.domain.model.DefaultDueDate) {
        safeLaunch { userPreferences.setDefaultDueDate(mode) }
    }

    fun setDefaultPriority(priority: String) {
        safeLaunch { userPreferences.setDefaultPriority(priority) }
    }

    fun setTrashRetentionDays(days: Int) {
        safeLaunch { userPreferences.setTrashRetentionDays(days) }
    }

    fun resetAppSettings() {
        safeLaunch { userPreferences.resetAppSettings() }
    }

    val customThemeSeedColor: StateFlow<Int?> = userPreferences.customThemeSeedColorFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val peopleFeatureEnabled: StateFlow<Boolean> = userPreferences.peopleFeatureEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val tagsFeatureEnabled: StateFlow<Boolean> = userPreferences.tagsFeatureEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val projectsFeatureEnabled: StateFlow<Boolean> = userPreferences.projectsFeatureEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val reduceMotionEnabled: StateFlow<Boolean> = userPreferences.reduceMotionEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val motionMode: StateFlow<MotionMode> = userPreferences.motionModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MotionMode.FULL)

    val dateAliasDefinitions: StateFlow<Set<String>> = userPreferences.dateAliasDefinitionsFlow
        .onEach { NaturalLanguageParser.configureDateAliases(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val savedThemePresetDefinitions: StateFlow<Set<String>> = userPreferences.savedThemePresetsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val taskerIntegrationEnabled: StateFlow<Boolean> = userPreferences.taskerIntegrationEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val enhancedM3ThemingEnabled: StateFlow<Boolean> = userPreferences.enhancedM3ThemingEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val floatingBottomNavEnabled: StateFlow<Boolean> = userPreferences.floatingBottomNavEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val bottomNavLabelsEnabled: StateFlow<Boolean> = userPreferences.bottomNavLabelsEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val demoModeEnabled: StateFlow<Boolean> = userPreferences.demoModeEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hideCompletedToday: StateFlow<Boolean> = userPreferences.hideCompletedTodayFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hideCompletedProject: StateFlow<Boolean> = userPreferences.hideCompletedProjectFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hideCompletedList: StateFlow<Boolean> = userPreferences.hideCompletedListFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hideCompletedPerson: StateFlow<Boolean> = userPreferences.hideCompletedPersonFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Per-screen sort mode, persisted like hideCompleted* above so it survives navigation.
    val sortModeToday: StateFlow<com.mj.yata.util.TaskSortMode> = userPreferences.sortModeTodayFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.mj.yata.util.TaskSortMode.MANUAL)

    val sortModeProject: StateFlow<com.mj.yata.util.TaskSortMode> = userPreferences.sortModeProjectFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.mj.yata.util.TaskSortMode.MANUAL)

    val sortModeList: StateFlow<com.mj.yata.util.TaskSortMode> = userPreferences.sortModeListFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.mj.yata.util.TaskSortMode.MANUAL)

    val sortModePerson: StateFlow<com.mj.yata.util.TaskSortMode> = userPreferences.sortModePersonFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.mj.yata.util.TaskSortMode.MANUAL)

    val sortModeTagDetail: StateFlow<com.mj.yata.util.TaskSortMode> = userPreferences.sortModeTagDetailFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.mj.yata.util.TaskSortMode.MANUAL)

    val sortModeTagsTab: StateFlow<com.mj.yata.util.EntitySortMode> = userPreferences.sortModeTagsTabFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.mj.yata.util.EntitySortMode.NAME_ASC)

    val sortModePeopleTab: StateFlow<com.mj.yata.util.EntitySortMode> = userPreferences.sortModePeopleTabFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.mj.yata.util.EntitySortMode.NAME_ASC)

    val textScale: StateFlow<Float> = userPreferences.textScaleFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val taskRowDensity: StateFlow<com.mj.yata.domain.model.TaskRowDensity> = userPreferences.taskRowDensityFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.mj.yata.domain.model.TaskRowDensity.COMFORTABLE)

    val taskSwipeActionsEnabled: StateFlow<Boolean> = userPreferences.taskSwipeActionsEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val completionSoundEnabled: StateFlow<Boolean> = userPreferences.completionSoundEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val hapticsEnabled: StateFlow<Boolean> = userPreferences.hapticsEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val todayTabEnabled: StateFlow<Boolean> = userPreferences.todayTabEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val upcomingTabEnabled: StateFlow<Boolean> = userPreferences.upcomingTabEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val fabPosition: StateFlow<com.mj.yata.domain.model.FabPosition> = userPreferences.fabPositionFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.mj.yata.domain.model.FabPosition.RIGHT)

    val autoAssignToMe: StateFlow<Boolean> = userPreferences.autoAssignToMeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setAutoAssignToMe(enabled: Boolean) {
        safeLaunch { userPreferences.setAutoAssignToMe(enabled) }
    }

    /**
     * True when *any* backup destination is switched on and usable — what the Today top bar's sync
     * button gates on. It used to gate on one remote provider alone, which hid the button entirely
     * from someone backing up only to their own server or only on-device: the one control for "back up right
     * now" was invisible precisely to the people who'd set a destination up for it.
     *
     * Self-hosted additionally requires a host, since the toggle can be on with the server dialog
     * never filled in — showing a sync button whose only destination is guaranteed to fail is
     * worse than not showing it.
     */
    private val remoteBackupConfiguredForTopBar: StateFlow<Boolean> = combine(
        userPreferences.remoteBackupProtocolFlow,
        userPreferences.sftpHostFlow,
        userPreferences.githubOwnerFlow,
        userPreferences.githubRepoFlow
    ) { protocol, host, githubOwner, githubRepo ->
        when (protocol) {
            com.mj.yata.domain.model.RemoteBackupProtocol.GITHUB ->
                githubOwner.isNotBlank() && githubRepo.isNotBlank()
            com.mj.yata.domain.model.RemoteBackupProtocol.FTP,
            com.mj.yata.domain.model.RemoteBackupProtocol.SFTP ->
                host.isNotBlank()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val anyBackupDestinationEnabled: StateFlow<Boolean> = combine(
        userPreferences.localBackupEnabledFlow,
        userPreferences.sftpBackupEnabledFlow,
        remoteBackupConfiguredForTopBar
    ) { local, selfHosted, remoteConfigured ->
        local || (selfHosted && remoteConfigured)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val syncInProgress: StateFlow<Boolean> = backupOperations.syncInProgress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val syncPendingOrInProgress: StateFlow<Boolean> = backupOperations.syncPendingOrInProgress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val lastSyncSucceeded: StateFlow<Boolean?> = backupOperations.lastSyncSucceeded
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val syncProgress: StateFlow<com.mj.yata.domain.model.SyncProgressState?> = backupOperations.syncProgress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val savedSmartFilterSets: StateFlow<Set<String>> = userPreferences.savedSmartFilterSetsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val recentTasks: StateFlow<List<Task>> = combine(
        userPreferences.recentTaskIdsFlow,
        tasks
    ) { ids, taskList ->
        val byId = taskList.associateBy { it.id }
        ids.mapNotNull { byId[it] }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val liveHomeTab = MutableStateFlow<Int?>(null)
    val lastHomeTab: StateFlow<Int> = combine(
        userPreferences.lastHomeTabFlow,
        liveHomeTab
    ) { persisted, live -> live ?: persisted }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Actions
    fun toggleTaskDone(id: String, onDoneCallback: () -> Unit) {
        safeLaunch {
            val task = tasks.value.find { it.id == id }
            val wasDone = task?.done ?: false
            repository.toggleTaskDone(id)
            if (!wasDone) {
                onDoneCallback() // Trigger confetti
            }
        }
    }

    fun skipTaskOccurrence(id: String) {
        safeLaunch {
            repository.skipTaskOccurrence(id)
        }
    }

    // Multi-task orchestration lives in TaskOperations (domain/usecase) — these wrappers only
    // supply the coroutine scope, so screens keep their existing entry points.
    fun bulkCompleteTasks(ids: List<String>) {
        safeLaunch { taskOperations.bulkComplete(ids) }
    }

    fun bulkDeleteTasks(ids: List<String>) {
        safeLaunch { taskOperations.bulkDelete(ids) }
    }

    fun restoreTasks(previousTasks: List<Task>) {
        if (previousTasks.isEmpty()) return
        safeLaunch {
            repository.upsertTasks(previousTasks, notify = true, resyncReminder = true)
        }
    }

    fun bulkAddTag(ids: List<String>, tagId: String) {
        safeLaunch { taskOperations.bulkAddTag(ids, tagId) }
    }

    fun bulkSetProject(ids: List<String>, projectId: String?) {
        safeLaunch { taskOperations.bulkSetProject(ids, projectId) }
    }

    fun bulkSetList(ids: List<String>, listId: String?) {
        safeLaunch { taskOperations.bulkSetList(ids, listId) }
    }

    fun duplicateTask(taskId: String, dueAdjustment: (LocalDate) -> LocalDate = { it }) {
        safeLaunch { taskOperations.duplicate(taskId, dueAdjustment) }
    }

    fun rolloverProjectTasks(projectId: String) {
        safeLaunch { taskOperations.rolloverProjectTasks(projectId) }
    }

    fun rolloverOverdueProjectTasks(projectId: String) {
        safeLaunch { taskOperations.rolloverOverdueProjectTasks(projectId) }
    }

    fun bulkDuplicateTasks(ids: List<String>) {
        safeLaunch { taskOperations.bulkDuplicate(ids) }
    }

    fun commitTaskOrder(orderedTasks: List<Task>) {
        safeLaunch { taskOperations.commitTaskOrder(orderedTasks) }
    }

    /** Persists a drag-and-drop reorder of the whole Projects tab (a single flat list). */
    fun commitProjectOrder(orderedProjects: List<Project>) {
        safeLaunch {
            orderedProjects.forEachIndexed { index, project ->
                if (project.sortOrder != index) {
                    repository.upsertProject(project.copy(sortOrder = index))
                }
            }
        }
    }

    /** Persists a drag-and-drop reorder of the nav drawer's Lists section (a single flat list). */
    fun commitListOrder(orderedLists: List<YataList>) {
        safeLaunch {
            orderedLists.forEachIndexed { index, list ->
                if (list.sortOrder != index) {
                    repository.upsertList(list.copy(sortOrder = index))
                }
            }
        }
    }

    fun moveTaskToList(taskId: String, targetListId: String?, targetProjectId: String? = null) {
        safeLaunch { taskOperations.moveTaskToList(taskId, targetListId, targetProjectId) }
    }

    fun bulkAssignPerson(ids: List<String>, personId: String) {
        safeLaunch { taskOperations.bulkAssignPerson(ids, personId) }
    }

    fun toggleTaskFlag(id: String) {
        safeLaunch {
            val task = tasks.value.find { it.id == id } ?: return@safeLaunch
            repository.setTaskFlag(id, !task.flag)
        }
    }

    fun cycleTaskPriority(id: String) {
        safeLaunch {
            val task = tasks.value.find { it.id == id } ?: return@safeLaunch
            val nextPriority = when (task.priority) {
                "none" -> "low"
                "low" -> "med"
                "med" -> "high"
                "high" -> "none"
                else -> "none"
            }
            repository.setTaskPriority(id, nextPriority)
        }
    }

    /** Creates the task a [NewTaskDraft] describes. The one place the sheet's collected fields are
     * mapped onto the domain model, so a new field on the draft lands here and nowhere else. */
    fun addTask(draft: NewTaskDraft) = addTask(
        title = draft.title,
        listId = draft.listId,
        priority = draft.priority,
        assigneeIds = draft.assigneeIds,
        tagIds = draft.tagIds,
        recurrence = draft.recurrence,
        notes = draft.notes,
        due = draft.due,
        startDate = draft.startDate,
        time = draft.time,
        reminder = draft.reminder,
        section = draft.section,
        projectId = draft.projectId,
        subtasks = draft.subtasks,
        flag = draft.flag
    )

    fun addTask(
        title: String,
        listId: String?,
        priority: String,
        assigneeIds: List<String>,
        tagIds: List<String>,
        recurrence: Recurrence?,
        notes: String? = null,
        due: String? = LocalDate.now().toString(),
        startDate: String? = null,
        time: String? = null,
        reminder: String? = null,
        section: String = "",
        projectId: String? = null,
        subtasks: List<Subtask> = emptyList(),
        flag: Boolean = false
    ) {
        safeLaunch {
            val newTask = Task(
                id = "t_" + UUID.randomUUID().toString(),
                title = title,
                listId = listId,
                projectId = projectId,
                section = section,
                due = due,
                startDate = startDate,
                time = time,
                reminder = reminder,
                priority = priority,
                flag = flag,
                done = false,
                assigneeIds = assigneeIds,
                tagIds = tagIds,
                recurrence = recurrence,
                subtasks = subtasks,
                notes = notes
            )
            repository.upsertTask(newTask)
        }
    }

    fun upsertTask(task: Task) {
        safeLaunch {
            repository.upsertTask(task)
        }
    }

    fun renameTask(id: String, title: String) {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return
        safeLaunch {
            val task = tasks.value.find { it.id == id } ?: return@safeLaunch
            val parsed = NaturalLanguageParser.parse(trimmed)
            val parsedTitle = parsed.title.ifBlank { trimmed }
            repository.upsertTask(
                task.copy(
                    title = parsedTitle,
                    due = parsed.due ?: task.due,
                    time = parsed.time ?: task.time,
                    reminder = parsed.reminder ?: task.reminder,
                    recurrence = parsed.recurrence ?: task.recurrence,
                    priority = parsed.priority ?: task.priority
                ),
                resyncReminder = parsed.due != null || parsed.time != null || parsed.reminder != null
            )
            userPreferences.recordRecentTask(id)
        }
    }

    /** Sets or clears the "waiting on" follow-up date on a delegated task — see
     * [com.mj.yata.domain.model.isWaitingOn] for what this does to Today. Pass null to clear. */
    fun setTaskFollowUp(id: String, followUpAt: Long?) {
        safeLaunch {
            val task = tasks.value.find { it.id == id } ?: return@safeLaunch
            repository.upsertTask(task.copy(followUpAt = followUpAt), resyncReminder = false)
        }
    }

    /** Sets or clears a task's planned effort in minutes. Pass null to clear back to unestimated. */
    fun setTaskEstimate(id: String, estimateMinutes: Int?) {
        safeLaunch {
            val task = tasks.value.find { it.id == id } ?: return@safeLaunch
            repository.upsertTask(task.copy(estimateMinutes = estimateMinutes), resyncReminder = false)
        }
    }

    fun setLastHomeTab(tab: Int) {
        val safeTab = tab.coerceIn(0, 4)
        liveHomeTab.value = safeTab
        safeLaunch {
            userPreferences.setLastHomeTab(safeTab)
        }
    }

    fun recordTaskViewed(id: String) {
        safeLaunch {
            userPreferences.recordRecentTask(id)
        }
    }

    fun quickSnoozeTask(id: String, preset: QuickSnoozePreset) {
        safeLaunch {
            taskOperations.quickSnooze(id, preset)
            userPreferences.recordRecentTask(id)
        }
    }

    fun bulkRescheduleTasks(ids: List<String>, preset: QuickSnoozePreset) {
        safeLaunch {
            taskOperations.bulkReschedule(ids, preset)
            ids.take(8).forEach { userPreferences.recordRecentTask(it) }
        }
    }

    fun deleteTask(task: Task) {
        safeLaunch {
            repository.deleteTask(task)
        }
    }

    // Trash
    val deletedTasks: StateFlow<List<Task>> = repository.getDeletedTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun restoreTask(id: String) {
        safeLaunch {
            repository.restoreTask(id)
        }
    }

    // Archive — shelved but intact, separate from Trash (see YataRepository.getArchivedTasks).
    val archivedTasks: StateFlow<List<Task>> = repository.getArchivedTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setTaskArchived(id: String, archived: Boolean) {
        safeLaunch {
            repository.setTaskArchived(id, archived)
        }
    }

    fun bulkArchiveTasks(ids: List<String>, archived: Boolean) {
        safeLaunch {
            ids.forEach { repository.setTaskArchived(it, archived) }
        }
    }

    fun permanentlyDeleteTask(task: Task) {
        safeLaunch {
            repository.permanentlyDeleteTask(task)
        }
    }

    fun emptyTrash() {
        safeLaunch {
            repository.emptyTrash()
        }
    }

    // Cached by taskId — without this, every call created its own independent stateIn()
    // subscriber tied to viewModelScope, so a caller that invoked this per-recomposition
    // instead of hoisting the result (e.g. via remember) would leak one hot flow per call.
    private val commentsFlowCache = mutableMapOf<String, StateFlow<List<TaskComment>>>()

    fun getCommentsForTask(taskId: String): StateFlow<List<TaskComment>> =
        commentsFlowCache.getOrPut(taskId) {
            repository.getCommentsForTask(taskId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        }

    fun addComment(taskId: String, body: String) {
        safeLaunch {
            val authorId = people.value.find { it.isMe }?.id
            repository.addComment(taskId, body, authorId)
        }
    }

    fun deleteComment(comment: TaskComment) {
        safeLaunch {
            repository.deleteComment(comment)
        }
    }

    fun addProject(name: String, color: String, icon: String = "layers", due: String? = null, commonTagIds: List<String> = emptyList(), defaultReminder: String? = null, description: String? = null, excludeFromToday: Boolean = false) {
        safeLaunch {
            val pid = "pr_" + UUID.randomUUID().toString()
            val project = Project(
                id = pid,
                name = name,
                color = color,
                icon = icon,
                due = due,
                commonTagIds = commonTagIds,
                defaultReminder = defaultReminder,
                description = description,
                excludeFromToday = excludeFromToday
            )
            repository.upsertProject(project)
        }
    }

    fun upsertProject(project: Project) {
        safeLaunch {
            repository.upsertProject(project)
        }
    }

    fun toggleProjectStarred(id: String) {
        safeLaunch {
            val project = projects.value.find { it.id == id } ?: return@safeLaunch
            repository.upsertProject(project.copy(starred = !project.starred))
        }
    }

    fun deleteProject(project: Project) {
        safeLaunch {
            repository.deleteProject(project)
        }
    }

    fun deleteProjectOnly(project: Project) {
        safeLaunch {
            repository.deleteProjectOnly(project)
        }
    }

    fun bulkDeleteProjects(ids: List<String>) {
        safeLaunch {
            val byId = projects.value.associateBy { it.id }
            ids.forEach { id -> byId[id]?.let { repository.deleteProject(it) } }
        }
    }

    /** Archiving hides a project from active project surfaces while keeping its tasks linked. */
    fun setProjectArchived(project: Project, archived: Boolean) {
        safeLaunch {
            repository.upsertProject(project.copy(archived = archived))
        }
    }

    fun bulkArchiveProjects(ids: List<String>) {
        safeLaunch {
            repository.setProjectsArchived(ids, true)
        }
    }

    fun bulkRestoreProjects(ids: List<String>) {
        safeLaunch {
            repository.setProjectsArchived(ids, false)
        }
    }

    fun addPerson(name: String, color: String, groupId: String? = null, photoUri: String? = null) {
        safeLaunch {
            val initials = name.split(" ")
                .mapNotNull { it.firstOrNull()?.toString() }
                .take(2)
                .joinToString("")
                .uppercase()

            val person = Person(
                id = "p_" + UUID.randomUUID().toString(),
                name = name,
                initials = if (initials.isEmpty()) "P" else initials,
                color = color,
                isMe = false,
                groupId = groupId,
                photoUri = photoUri
            )
            repository.upsertPerson(person)
        }
    }

    fun upsertPerson(person: Person) {
        safeLaunch {
            repository.upsertPerson(person)
        }
    }

    fun deletePerson(person: Person) {
        safeLaunch {
            repository.deletePerson(person)
        }
    }

    /** Archiving (rather than deleting) a person keeps their historical assigned-task stats
     * intact in Analytics/PersonDetail — used when a team member leaves. */
    fun setPersonArchived(person: Person, archived: Boolean) {
        safeLaunch {
            repository.upsertPerson(person.copy(archived = archived))
        }
    }

    fun togglePersonStarred(id: String) {
        safeLaunch {
            val person = people.value.find { it.id == id } ?: return@safeLaunch
            repository.upsertPerson(person.copy(starred = !person.starred))
        }
    }

    fun setPeopleGroup(personIds: List<String>, groupId: String?) {
        safeLaunch {
            val byId = people.value.associateBy { it.id }
            personIds.forEach { id ->
                byId[id]?.let { repository.upsertPerson(it.copy(groupId = groupId)) }
            }
        }
    }

    fun addPersonGroup(name: String, color: String) {
        safeLaunch {
            repository.upsertPersonGroup(PersonGroup(id = "pg_" + UUID.randomUUID().toString(), name = name, color = color))
        }
    }

    fun upsertPersonGroup(group: PersonGroup) {
        safeLaunch {
            repository.upsertPersonGroup(group)
        }
    }

    fun deletePersonGroup(group: PersonGroup) {
        safeLaunch {
            repository.deletePersonGroup(group)
        }
    }

    fun addTag(name: String, color: String, groupId: String? = null, hideCompletedByDefault: Boolean = false) {
        safeLaunch {
            val tag = Tag(
                id = "tag_" + UUID.randomUUID().toString(),
                name = name.lowercase().trim(),
                color = color,
                groupId = groupId,
                hideCompletedByDefault = hideCompletedByDefault
            )
            repository.upsertTag(tag)
        }
    }

    fun upsertTag(tag: Tag) {
        safeLaunch {
            repository.upsertTag(tag)
        }
    }

    fun deleteTag(tag: Tag) {
        safeLaunch {
            repository.deleteTag(tag)
        }
    }

    fun bulkDeleteTags(ids: List<String>) {
        safeLaunch {
            val byId = tags.value.associateBy { it.id }
            ids.forEach { id -> byId[id]?.let { repository.deleteTag(it) } }
        }
    }

    fun toggleTagStarred(id: String) {
        safeLaunch {
            val tag = tags.value.find { it.id == id } ?: return@safeLaunch
            repository.upsertTag(tag.copy(starred = !tag.starred))
        }
    }

    fun addTagGroup(name: String, color: String) {
        safeLaunch {
            repository.upsertTagGroup(TagGroup(id = "tg_" + UUID.randomUUID().toString(), name = name, color = color))
        }
    }

    fun upsertTagGroup(group: TagGroup) {
        safeLaunch {
            repository.upsertTagGroup(group)
        }
    }

    fun deleteTagGroup(group: TagGroup) {
        safeLaunch {
            repository.deleteTagGroup(group)
        }
    }

    fun addList(name: String, color: String, icon: String, excludeFromToday: Boolean = false) {
        safeLaunch {
            val yataList = YataList(
                id = "l_" + UUID.randomUUID().toString(),
                name = name,
                color = color,
                icon = icon,
                excludeFromToday = excludeFromToday
            )
            repository.upsertList(yataList)
        }
    }

    fun upsertList(list: YataList) {
        safeLaunch {
            repository.upsertList(list)
        }
    }

    fun toggleListStarred(id: String) {
        safeLaunch {
            val list = lists.value.find { it.id == id } ?: return@safeLaunch
            repository.upsertList(list.copy(starred = !list.starred))
        }
    }

    fun deleteList(list: YataList) {
        safeLaunch {
            repository.deleteList(list)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        safeLaunch {
            userPreferences.setThemeMode(mode)
        }
    }

    fun setColorIntensity(intensity: com.mj.yata.domain.model.ColorIntensity) {
        safeLaunch { userPreferences.setColorIntensity(intensity) }
    }

    fun setBackgroundTint(tint: com.mj.yata.domain.model.BackgroundTint) {
        safeLaunch { userPreferences.setBackgroundTint(tint) }
    }

    fun setTaskCardBackground(enabled: Boolean) {
        safeLaunch { userPreferences.setTaskCardBackground(enabled) }
    }

    fun setAppFont(font: AppFont) {
        safeLaunch {
            userPreferences.setAppFont(font)
        }
    }

    fun setReduceMotionEnabled(enabled: Boolean) {
        safeLaunch {
            userPreferences.setReduceMotionEnabled(enabled)
        }
    }

    fun setMotionMode(mode: MotionMode) {
        safeLaunch {
            userPreferences.setMotionMode(mode)
        }
    }

    fun setEnhancedM3ThemingEnabled(enabled: Boolean) {
        safeLaunch {
            userPreferences.setEnhancedM3ThemingEnabled(enabled)
        }
    }

    /** Pass null to clear back to the app's default warm coral palette. Only takes visual effect
     * while Material You dynamic color is off. */
    fun setCustomThemeSeedColor(argb: Int?) {
        safeLaunch {
            userPreferences.setCustomThemeSeedColor(argb)
        }
    }

    fun addDateAlias(alias: String, target: DateAliasTarget) {
        val normalized = alias.trim().lowercase()
        if (normalized.isBlank()) return
        safeLaunch {
            userPreferences.addDateAlias(DateAliasDefinition(normalized, target))
        }
    }

    fun removeDateAlias(encodedDefinition: String) {
        safeLaunch {
            userPreferences.removeDateAlias(encodedDefinition)
        }
    }

    fun saveCurrentThemePreset(
        name: String,
        themeMode: ThemeMode,
        seedColorArgb: Int?,
        colorIntensity: ColorIntensity,
        backgroundTint: BackgroundTint,
        appFont: AppFont,
        dynamicColorEnabled: Boolean
    ) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        safeLaunch {
            userPreferences.saveThemePreset(
                SavedThemePreset(
                    name = trimmed,
                    themeMode = themeMode,
                    seedColorArgb = seedColorArgb,
                    colorIntensity = colorIntensity,
                    backgroundTint = backgroundTint,
                    appFont = appFont,
                    dynamicColorEnabled = dynamicColorEnabled
                )
            )
        }
    }

    fun applyThemePreset(encodedPreset: String) {
        val preset = SavedThemePreset.decode(encodedPreset) ?: return
        safeLaunch {
            userPreferences.setThemeMode(preset.themeMode)
            userPreferences.setDynamicColorEnabled(preset.dynamicColorEnabled)
            userPreferences.setCustomThemeSeedColor(preset.seedColorArgb)
            userPreferences.setColorIntensity(preset.colorIntensity)
            userPreferences.setBackgroundTint(preset.backgroundTint)
            userPreferences.setAppFont(preset.appFont)
        }
    }

    fun removeThemePreset(encodedPreset: String) {
        safeLaunch {
            userPreferences.removeThemePreset(encodedPreset)
        }
    }

    fun setTaskerIntegrationEnabled(enabled: Boolean) {
        safeLaunch {
            userPreferences.setTaskerIntegrationEnabled(enabled)
        }
    }

    fun setFloatingBottomNavEnabled(enabled: Boolean) {
        safeLaunch {
            userPreferences.setFloatingBottomNavEnabled(enabled)
        }
    }

    fun setBottomNavLabelsEnabled(enabled: Boolean) {
        safeLaunch {
            userPreferences.setBottomNavLabelsEnabled(enabled)
        }
    }

    /** Toggled by tapping the logo on the Help & About screen. Demo mode swaps every screen's
     * data source to an in-memory sample dataset (see RoutingYataRepository) for taking store
     * screenshots — the real database is never read from or written to while it's active. */
    fun toggleDemoMode() {
        safeLaunch {
            userPreferences.setDemoModeEnabled(!demoModeEnabled.value)
        }
    }

    fun setTextScale(scale: Float) {
        safeLaunch {
            userPreferences.setTextScale(scale)
        }
    }

    fun setTaskRowDensity(density: com.mj.yata.domain.model.TaskRowDensity) {
        safeLaunch {
            userPreferences.setTaskRowDensity(density)
        }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        safeLaunch {
            userPreferences.setHapticsEnabled(enabled)
        }
    }

    fun setTaskSwipeActionsEnabled(enabled: Boolean) {
        safeLaunch {
            userPreferences.setTaskSwipeActionsEnabled(enabled)
        }
    }

    fun setCompletionSoundEnabled(enabled: Boolean) {
        safeLaunch {
            userPreferences.setCompletionSoundEnabled(enabled)
        }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        safeLaunch {
            userPreferences.setAppLockEnabled(enabled)
        }
    }

    fun setAppLockPin(pin: String?) {
        safeLaunch {
            userPreferences.setAppLockPin(pin)
        }
    }

    fun setAppLockTimeoutMinutes(minutes: Int) {
        safeLaunch {
            userPreferences.setAppLockTimeoutMinutes(minutes)
        }
    }

    fun setTodayTabEnabled(enabled: Boolean) {
        safeLaunch {
            userPreferences.setTodayTabEnabled(enabled)
        }
    }

    fun setUpcomingTabEnabled(enabled: Boolean) {
        safeLaunch {
            userPreferences.setUpcomingTabEnabled(enabled)
        }
    }

    fun setFabPosition(position: com.mj.yata.domain.model.FabPosition) {
        safeLaunch {
            userPreferences.setFabPosition(position)
        }
    }

    fun backupThenDeleteAllData(onResult: (backupFilename: String?) -> Unit) {
        safeLaunch { onResult(backupOperations.backupThenDeleteAllData()) }
    }

    fun setUserName(name: String) {
        safeLaunch {
            userPreferences.setUserName(name)
        }
    }

    fun setUserEmail(email: String) {
        safeLaunch {
            userPreferences.setUserEmail(email)
        }
    }

    fun setUserPhotoUri(uri: String?) {
        safeLaunch {
            userPreferences.setUserPhotoUri(uri)
            // Keeps the "me" Person's avatar (shown wherever a task is assigned to you) in sync
            // with the profile photo — see syncMePersonPhotoWithProfile for why this can't be
            // skipped in favor of a one-time backfill alone.
            val me = repository.getPeople().first().find { it.isMe }
            if (me != null && me.photoUri != uri) {
                repository.upsertPerson(me.copy(photoUri = uri))
            }
        }
    }

    fun setDefaultListId(id: String) {
        safeLaunch {
            userPreferences.setDefaultListId(id)
        }
    }

    fun setStartOfWeekSunday(sunday: Boolean) {
        safeLaunch {
            userPreferences.setStartOfWeekSunday(sunday)
        }
    }

    fun setDefaultReminderTime(hour: Int, minute: Int) {
        safeLaunch {
            userPreferences.setDefaultReminderTime(hour, minute)
        }
    }

    fun setUiScale(scale: Float) {
        safeLaunch {
            userPreferences.setUiScale(scale)
        }
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        safeLaunch {
            userPreferences.setDynamicColorEnabled(enabled)
        }
    }

    fun setHideCompletedToday(hide: Boolean) {
        safeLaunch {
            userPreferences.setHideCompletedToday(hide)
        }
    }

    fun setHideCompletedProject(hide: Boolean) {
        safeLaunch {
            userPreferences.setHideCompletedProject(hide)
        }
    }

    fun setHideCompletedList(hide: Boolean) {
        safeLaunch {
            userPreferences.setHideCompletedList(hide)
        }
    }

    fun setHideCompletedPerson(hide: Boolean) {
        safeLaunch {
            userPreferences.setHideCompletedPerson(hide)
        }
    }

    fun setSortModeToday(mode: com.mj.yata.util.TaskSortMode) {
        safeLaunch { userPreferences.setSortModeToday(mode) }
    }

    fun setSortModeProject(mode: com.mj.yata.util.TaskSortMode) {
        safeLaunch { userPreferences.setSortModeProject(mode) }
    }

    fun setSortModeList(mode: com.mj.yata.util.TaskSortMode) {
        safeLaunch { userPreferences.setSortModeList(mode) }
    }

    fun setSortModePerson(mode: com.mj.yata.util.TaskSortMode) {
        safeLaunch { userPreferences.setSortModePerson(mode) }
    }

    fun setSortModeTagDetail(mode: com.mj.yata.util.TaskSortMode) {
        safeLaunch { userPreferences.setSortModeTagDetail(mode) }
    }

    fun setSortModeTagsTab(mode: com.mj.yata.util.EntitySortMode) {
        safeLaunch { userPreferences.setSortModeTagsTab(mode) }
    }

    fun setSortModePeopleTab(mode: com.mj.yata.util.EntitySortMode) {
        safeLaunch { userPreferences.setSortModePeopleTab(mode) }
    }

    fun setHasSeenWelcome() {
        safeLaunch {
            userPreferences.setHasSeenWelcome(true)
        }
    }

    fun setPeopleFeatureEnabled(enabled: Boolean) {
        safeLaunch {
            userPreferences.setPeopleFeatureEnabled(enabled)
        }
    }

    fun setTagsFeatureEnabled(enabled: Boolean) {
        safeLaunch {
            userPreferences.setTagsFeatureEnabled(enabled)
        }
    }

    fun setProjectsFeatureEnabled(enabled: Boolean) {
        safeLaunch {
            userPreferences.setProjectsFeatureEnabled(enabled)
        }
    }

    fun saveSmartFilterSet(encodedFilters: String) {
        safeLaunch {
            userPreferences.addSavedSmartFilterSet(encodedFilters)
        }
    }

    fun removeSmartFilterSet(encodedFilters: String) {
        safeLaunch {
            userPreferences.removeSavedSmartFilterSet(encodedFilters)
        }
    }

    fun setLocalBackupEnabled(enabled: Boolean) {
        safeLaunch {
            userPreferences.setLocalBackupEnabled(enabled)
        }
    }

    fun backupLocalNow() {
        safeLaunch { backupOperations.backupLocalNow() }
    }

    fun restoreLocalBackup(onResult: (Boolean) -> Unit) {
        safeLaunch { onResult(backupOperations.restoreLatestLocalBackup()) }
    }

    fun setSftpBackupEnabled(enabled: Boolean) {
        safeLaunch {
            userPreferences.setSftpBackupEnabled(enabled)
            // Disabling clears the saved secrets rather than leaving them sitting encrypted on
            // disk for a feature the user just turned off — matches "delete, don't just hide."
            if (!enabled) remoteBackupCredentialsStore.clear()
        }
    }

    fun setSftpHost(host: String) {
        safeLaunch { userPreferences.setSftpHost(host) }
    }

    fun setSftpPort(port: Int) {
        safeLaunch { userPreferences.setSftpPort(port) }
    }

    fun setSftpUsername(username: String) {
        safeLaunch { userPreferences.setSftpUsername(username) }
    }

    fun setSftpAuthMethod(method: String) {
        safeLaunch { userPreferences.setSftpAuthMethod(method) }
    }

    fun setSftpRemoteDir(dir: String) {
        safeLaunch { userPreferences.setSftpRemoteDir(dir) }
    }

    fun setSftpIntervalMinutes(minutes: Long) {
        safeLaunch { userPreferences.setSftpIntervalMinutes(minutes) }
    }

    fun setRemoteBackupProtocol(protocol: com.mj.yata.domain.model.RemoteBackupProtocol) {
        safeLaunch { userPreferences.setRemoteBackupProtocol(protocol) }
    }

    fun setSftpKeepCount(count: Int) {
        safeLaunch { userPreferences.setSftpKeepCount(count) }
    }

    /** Backs up every enabled destination; one result per attempted destination. */
    fun backupAllNow(
        allowInitialJoinMerge: Boolean = false,
        onResult: (List<com.mj.yata.domain.model.BackupRunResult>) -> Unit
    ) {
        backupOperations.cancelDebouncedBackup()
        safeLaunch {
            onResult(backupOperations.backupAllConfigured(allowInitialJoinMerge = allowInitialJoinMerge))
        }
    }

    fun setBackupIntervalMinutes(minutes: Long) {
        safeLaunch { userPreferences.setBackupIntervalMinutes(minutes) }
        backupOperations.updateBackupInterval(minutes)
    }

    fun setFtpUseTls(useTls: Boolean) {
        safeLaunch { userPreferences.setFtpUseTls(useTls) }
    }

    fun setGitHubToken(token: String) {
        remoteBackupCredentialsStore.githubToken = token.ifBlank { null }
    }

    fun hasGitHubToken(): Boolean = remoteBackupCredentialsStore.githubToken != null

    fun saveGitHubConfiguration(
        owner: String,
        repo: String,
        branch: String,
        apiBase: String = "https://api.github.com",
        onSaved: () -> Unit = {}
    ) {
        safeLaunch {
            userPreferences.setGitHubConfiguration(
                owner = owner,
                repo = repo,
                branch = branch,
                apiBase = apiBase
            )
            onSaved()
        }
    }

    fun connectGitHubConfiguration(
        repoText: String,
        token: String,
        apiBase: String = "https://api.github.com",
        onResult: (Result<Unit>) -> Unit
    ) {
        safeLaunch {
            val normalizedApiBase = apiBase.trim().ifBlank { "https://api.github.com" }
            val tokenToUse = token.ifBlank { remoteBackupCredentialsStore.githubToken.orEmpty() }
            if (tokenToUse.isBlank()) {
                onResult(Result.failure(IllegalStateException("GitHub token is required")))
                return@safeLaunch
            }
            val api = HttpGitHubApi(
                tokenProvider = { tokenToUse },
                apiBaseProvider = { normalizedApiBase }
            )
            val parsed = parseGitHubRepo(repoText)
            val (owner, repo) = withContext(Dispatchers.IO) {
                if (parsed.first != null) {
                    parsed.first!! to parsed.second
                } else {
                    api.getUser().login to parsed.second
                }
            }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val remoteRepo = try {
                        api.getRepo(owner, repo)
                    } catch (e: GitHubNotFoundException) {
                        val userLogin = api.getUser().login
                        if (owner == userLogin) {
                            api.createRepo(repo, private = true)
                        } else {
                            throw e
                        }
                    }
                    if (!remoteRepo.canPush) {
                        throw GitHubPermissionException()
                    }
                    remoteBackupCredentialsStore.githubToken = tokenToUse
                    userPreferences.setGitHubConfiguration(
                        owner = owner,
                        repo = remoteRepo.name,
                        branch = remoteRepo.defaultBranch,
                        apiBase = normalizedApiBase
                    )
                    userPreferences.setGitHubTokenExpiresAt(api.tokenExpiresAtEpochMillis)
                }
            }
            onResult(result)
        }
    }

    private fun parseGitHubRepo(repoText: String): Pair<String?, String> {
        val trimmed = repoText.trim()
        require(trimmed.isNotBlank()) { "GitHub repo is required" }
        val parts = trimmed.split("/", limit = 2)
        return if (parts.size == 2) {
            val owner = parts[0].trim()
            val repo = parts[1].trim()
            require(owner.isNotBlank() && repo.isNotBlank()) { "Enter the repo as owner/name" }
            owner to repo
        } else {
            null to trimmed
        }
    }

    fun saveRemoteBackupConfiguration(
        protocol: com.mj.yata.domain.model.RemoteBackupProtocol,
        useTls: Boolean,
        host: String,
        port: Int,
        username: String,
        remoteDir: String,
        authMethod: String,
        onSaved: () -> Unit = {}
    ) {
        safeLaunch {
            userPreferences.setRemoteBackupConfiguration(
                protocol = protocol,
                useTls = useTls,
                host = host,
                port = port,
                username = username,
                remoteDir = remoteDir,
                authMethod = authMethod
            )
            onSaved()
        }
    }

    fun setSftpPassword(password: String) {
        remoteBackupCredentialsStore.password = password.ifBlank { null }
    }

    fun hasRemoteBackupPassword(): Boolean = remoteBackupCredentialsStore.password != null

    /** Passphrase the uploaded backup file is encrypted with; blank clears it (uploads in clear). */
    fun setRemoteBackupPassphrase(passphrase: String) {
        remoteBackupCredentialsStore.backupPassphrase = passphrase.ifBlank { null }
    }

    /** Only whether one is set — the value itself is never read back into the UI. */
    fun hasRemoteBackupPassphrase(): Boolean = remoteBackupCredentialsStore.backupPassphrase != null

    fun setSftpPrivateKey(pem: String, passphrase: String) {
        remoteBackupCredentialsStore.privateKeyPem = pem.ifBlank { null }
        remoteBackupCredentialsStore.passphrase = passphrase.ifBlank { null }
    }

    fun hasSftpKeyPassphrase(): Boolean = remoteBackupCredentialsStore.passphrase != null

    /** Host key isn't pinned here — the caller (Settings) decides whether to call
     * [pinSftpHostKey] with the returned fingerprint, since a first connection needs the user to
     * see and confirm it, and a changed one needs an explicit "trust anyway." */
    fun testSftpConnection(onResult: (SftpConnectionTestResult) -> Unit) {
        safeLaunch { onResult(backupOperations.testSftpConnection()) }
    }

    fun pinSftpHostKey(fingerprint: String) {
        safeLaunch { backupOperations.pinSftpHostKey(fingerprint) }
    }

    /** Persists an explicitly confirmed key, then performs the credential-authenticated test.
     * Keeping both operations in one coroutine prevents the test from racing the DataStore write. */
    fun pinAndTestSftpConnection(
        fingerprint: String,
        onResult: (SftpConnectionTestResult) -> Unit
    ) {
        safeLaunch {
            backupOperations.pinSftpHostKey(fingerprint)
            onResult(backupOperations.testSftpConnection())
        }
    }

    fun sftpBackupNow(onResult: (Result<Unit>) -> Unit) {
        backupOperations.cancelDebouncedBackup()
        safeLaunch { onResult(backupOperations.sftpBackupNow()) }
    }

    fun listSftpBackups(onResult: (Result<List<String>>) -> Unit) {
        safeLaunch { onResult(backupOperations.listSftpBackups()) }
    }

    fun restoreSftpBackup(filename: String, onResult: (Result<Unit>) -> Unit) {
        safeLaunch { onResult(backupOperations.restoreSftpBackup(filename)) }
    }

    fun listRemoteRestorePoints(onResult: (Result<List<RestorePoint>>) -> Unit) {
        safeLaunch { onResult(backupOperations.listRemoteRestorePoints()) }
    }

    fun restoreRemoteSnapshot(id: String, onResult: (Result<Unit>) -> Unit) {
        safeLaunch { onResult(backupOperations.restoreRemoteSnapshot(id)) }
    }

    fun inspectRemoteSnapshot(id: String, onResult: (Result<com.mj.yata.domain.model.BackupSummary>) -> Unit) {
        safeLaunch { onResult(backupOperations.inspectRemoteSnapshot(id)) }
    }

    fun testFtpConnection(onResult: (Result<Unit>) -> Unit) {
        safeLaunch { onResult(backupOperations.testFtpConnection()) }
    }

    fun clearSelfHostedSyncLock(onResult: (Result<Unit>) -> Unit) {
        safeLaunch { onResult(backupOperations.clearSelfHostedSyncLock()) }
    }

    fun ftpBackupNow(onResult: (Result<Unit>) -> Unit) {
        backupOperations.cancelDebouncedBackup()
        safeLaunch { onResult(backupOperations.ftpBackupNow()) }
    }

    fun listFtpBackups(onResult: (Result<List<String>>) -> Unit) {
        safeLaunch { onResult(backupOperations.listFtpBackups()) }
    }

    fun restoreFtpBackup(filename: String, onResult: (Result<Unit>) -> Unit) {
        safeLaunch { onResult(backupOperations.restoreFtpBackup(filename)) }
    }

    fun inspectFtpBackup(filename: String, onResult: (Result<com.mj.yata.domain.model.BackupSummary>) -> Unit) {
        safeLaunch { onResult(backupOperations.inspectFtpBackup(filename)) }
    }

    fun inspectSftpBackup(filename: String, onResult: (Result<com.mj.yata.domain.model.BackupSummary>) -> Unit) {
        safeLaunch { onResult(backupOperations.inspectSftpBackup(filename)) }
    }

    fun streakForTask(taskId: String, onResult: (Int) -> Unit) {
        safeLaunch {
            onResult(repository.getTaskStreak(taskId))
        }
    }

    /**
     * The one backup schedule, covering every enabled destination. Still writes the old legacy
     * key so a downgrade to a build with per-destination schedules keeps the user's chosen cadence
     * rather than silently reverting to the default.
     */
    /** One visible retention setting for every off-device backup destination. */
    fun setRemoteBackupKeepCount(count: Int) {
        safeLaunch {
            userPreferences.setSftpKeepCount(count)
        }
    }

    fun compareWithLastSelfHostedBackup(onResult: (Result<BackupDiff>) -> Unit) {
        safeLaunch { onResult(backupOperations.compareWithLastSelfHostedBackup(tasks.value)) }
    }
}
