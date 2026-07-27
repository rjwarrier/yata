package com.mj.yata.ui.screen.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mj.yata.data.cloud.CloudBackupEntry
import com.mj.yata.data.local.datastore.UserPreferences
import com.mj.yata.domain.model.*
import com.mj.yata.domain.repository.YataRepository
import com.mj.yata.domain.usecase.BackupOperations
import com.mj.yata.domain.usecase.TaskOperations
import com.mj.yata.util.AnalyticsPeriod
import com.mj.yata.util.AnalyticsUiState
import com.mj.yata.util.AnalyticsUtils
import com.mj.yata.util.NaturalLanguageParser
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val backupOperations: BackupOperations
) : ViewModel() {

    init {
        viewModelScope.launch {
            repository.seedInitialDataIfNeeded()
            repository.purgeOldTrash()
            repository.autoArchiveOldCompleted()
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
    val themeScheduleStartHour: Int = 21,
    val themeScheduleStartMinute: Int = 0,
    val themeScheduleEndHour: Int = 7,
    val themeScheduleEndMinute: Int = 0,
    val reduceMotionEnabled: Boolean = false,
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
    val cloudBackupEnabled: Boolean = false,
    val cloudBackupAccountEmail: String? = null,
    val cloudBackupLastAt: Long? = null,
    val cloudBackupWifiOnly: Boolean = true,
    val cloudBackupIntervalMinutes: Long = 1440L,
    val cloudBackupArchiveMonths: Int = 6,
    val localBackupEnabled: Boolean = false,
    val localBackupLastAt: Long? = null,
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
    val defaultReminderMinute: Int,
    val themeScheduleStartHour: Int
)

private data class SettingsDisplayFlags(
    val reduceMotionEnabled: Boolean,
    val enhancedM3ThemingEnabled: Boolean,
    val floatingBottomNavEnabled: Boolean,
    val bottomNavLabelsEnabled: Boolean,
    val textScale: Float
)

private data class SettingsDisplayState(
    val themeScheduleStartMinute: Int,
    val themeScheduleEndHour: Int,
    val themeScheduleEndMinute: Int,
    val reduceMotionEnabled: Boolean,
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

private data class SettingsCloudState(
    val lists: List<YataList>,
    val cloudBackupEnabled: Boolean,
    val cloudBackupAccountEmail: String?,
    val cloudBackupLastAt: Long?,
    val cloudBackupWifiOnly: Boolean
)

private data class SettingsCloudScheduleState(
    val cloudBackupIntervalMinutes: Long,
    val cloudBackupArchiveMonths: Int,
    val localBackupEnabled: Boolean,
    val localBackupLastAt: Long?
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

    /** Today's remaining (due, incomplete) task count — the badge shown on every bottom nav bar,
     * and consumed by [settingsUiState]/[mainScreenUiState] below instead of each recomputing it
     * independently (one of those inline copies had drifted and was missing the
     * hiddenFromMainTaskProjectIds() filter the others apply). Declared early (uses the raw
     * repository flows, not the [projects] StateFlow property, which is declared later) so it's
     * available to both of those combine chains. */
    val todayRemainingCount: StateFlow<Int> = combine(repository.getTasks(), repository.getProjects()) { list, projectList ->
        val todayStr = LocalDate.now().toString()
        val hiddenProjectIds = projectList.hiddenFromMainTaskProjectIds()
        list.count { it.due != null && it.due <= todayStr && !it.done && it.projectId !in hiddenProjectIds }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val voiceRecognitionLanguage: StateFlow<String> = userPreferences.voiceRecognitionLanguageFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "default")

    fun setVoiceRecognitionLanguage(lang: String) {
        viewModelScope.launch {
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
            userPreferences.defaultReminderMinuteFlow,
            userPreferences.themeScheduleStartHourFlow
        ) { defaultListId, startOfWeekSunday, defaultReminderHour, defaultReminderMinute, themeScheduleStartHour ->
            SettingsReminderState(defaultListId, startOfWeekSunday, defaultReminderHour, defaultReminderMinute, themeScheduleStartHour)
        },
        combine(
            userPreferences.themeScheduleStartMinuteFlow,
            userPreferences.themeScheduleEndHourFlow,
            userPreferences.themeScheduleEndMinuteFlow,
            combine(
                userPreferences.reduceMotionEnabledFlow,
                userPreferences.enhancedM3ThemingEnabledFlow,
                userPreferences.floatingBottomNavEnabledFlow,
                userPreferences.bottomNavLabelsEnabledFlow,
                userPreferences.textScaleFlow
            ) { reduceMotion, enhancedM3, floatingNav, bottomNavLabels, textScale ->
                SettingsDisplayFlags(reduceMotion, enhancedM3, floatingNav, bottomNavLabels, textScale)
            }
        ) { themeScheduleStartMinute, themeScheduleEndHour, themeScheduleEndMinute, flags ->
            SettingsDisplayState(
                themeScheduleStartMinute,
                themeScheduleEndHour,
                themeScheduleEndMinute,
                flags.reduceMotionEnabled,
                flags.enhancedM3ThemingEnabled,
                flags.floatingBottomNavEnabled,
                flags.bottomNavLabelsEnabled,
                flags.textScale
            )
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
            repository.getLists(),
            userPreferences.cloudBackupEnabledFlow,
            userPreferences.cloudBackupAccountEmailFlow,
            userPreferences.cloudBackupLastAtFlow,
            userPreferences.cloudBackupWifiOnlyFlow
        ) { lists, cloudBackupEnabled, cloudBackupAccountEmail, cloudBackupLastAt, cloudBackupWifiOnly ->
            SettingsCloudState(lists, cloudBackupEnabled, cloudBackupAccountEmail, cloudBackupLastAt, cloudBackupWifiOnly)
        },
        combine(
            userPreferences.cloudBackupIntervalMinutesFlow,
            userPreferences.cloudBackupArchiveMonthsFlow,
            userPreferences.localBackupEnabledFlow,
            userPreferences.localBackupLastAtFlow
        ) { cloudBackupIntervalMinutes, cloudBackupArchiveMonths, localBackupEnabled, localBackupLastAt ->
            SettingsCloudScheduleState(cloudBackupIntervalMinutes, cloudBackupArchiveMonths, localBackupEnabled, localBackupLastAt)
        },
        todayRemainingCount
    ) { core, cloud, cloudSchedule, count ->
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
            themeScheduleStartHour = core.reminder.themeScheduleStartHour,
            themeScheduleStartMinute = core.display.themeScheduleStartMinute,
            themeScheduleEndHour = core.display.themeScheduleEndHour,
            themeScheduleEndMinute = core.display.themeScheduleEndMinute,
            reduceMotionEnabled = core.display.reduceMotionEnabled,
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
            lists = cloud.lists,
            cloudBackupEnabled = cloud.cloudBackupEnabled,
            cloudBackupAccountEmail = cloud.cloudBackupAccountEmail,
            cloudBackupLastAt = cloud.cloudBackupLastAt,
            cloudBackupWifiOnly = cloud.cloudBackupWifiOnly,
            cloudBackupIntervalMinutes = cloudSchedule.cloudBackupIntervalMinutes,
            cloudBackupArchiveMonths = cloudSchedule.cloudBackupArchiveMonths,
            localBackupEnabled = cloudSchedule.localBackupEnabled,
            localBackupLastAt = cloudSchedule.localBackupLastAt,
            todayRemainingCount = count
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    val mainScreenUiState: StateFlow<MainScreenUiState> = combine(
        combine(
            repository.getTasks(),
            repository.getProjects(),
            repository.getActiveProjects(),
            repository.getLists(),
            repository.getPeople()
        ) { tasks, projects, activeProjects, lists, people ->
            MainDataState(tasks, projects, activeProjects, lists, people)
        },
        combine(
            repository.getActivePeople(),
            repository.getTags(),
            repository.getTagGroups(),
            repository.getPersonGroups()
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

    private val analyticsPeriodFlow = MutableStateFlow(AnalyticsPeriod.WEEK)
    val analyticsPeriod: StateFlow<AnalyticsPeriod> = analyticsPeriodFlow.asStateFlow()

    fun setAnalyticsPeriod(period: AnalyticsPeriod) {
        analyticsPeriodFlow.value = period
    }

    /** Every Analytics-screen metric, computed off the UI thread in [AnalyticsUtils.computeUiState]
     * whenever the underlying data or the selected period changes — the screen only renders this. */
    val analyticsUiState: StateFlow<AnalyticsUiState> = combine(
        tasks, projects, people, tags, analyticsPeriodFlow
    ) { taskList, projectList, personList, tagList, period ->
        AnalyticsUtils.computeUiState(taskList, projectList, personList, tagList, period)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AnalyticsUiState())

    // Preferences
    val themeMode: StateFlow<ThemeMode> = userPreferences.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val appFont: StateFlow<AppFont> = userPreferences.appFontFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppFont.INTER)

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

    val themeScheduleStartHour: StateFlow<Int> = userPreferences.themeScheduleStartHourFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 21)

    val themeScheduleStartMinute: StateFlow<Int> = userPreferences.themeScheduleStartMinuteFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val themeScheduleEndHour: StateFlow<Int> = userPreferences.themeScheduleEndHourFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 7)

    val themeScheduleEndMinute: StateFlow<Int> = userPreferences.themeScheduleEndMinuteFlow
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

    fun setDailyAgendaEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setDailyAgendaEnabled(enabled) }
    }

    fun setDailyAgendaTime(hour: Int, minute: Int) {
        viewModelScope.launch { userPreferences.setDailyAgendaTime(hour, minute) }
    }

    fun setOverdueNudgesEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setOverdueNudgesEnabled(enabled) }
    }

    fun setAutoArchiveDays(days: Int) {
        viewModelScope.launch { userPreferences.setAutoArchiveDays(days) }
    }

    fun setDefaultDueDate(mode: com.mj.yata.domain.model.DefaultDueDate) {
        viewModelScope.launch { userPreferences.setDefaultDueDate(mode) }
    }

    fun setDefaultPriority(priority: String) {
        viewModelScope.launch { userPreferences.setDefaultPriority(priority) }
    }

    fun setTrashRetentionDays(days: Int) {
        viewModelScope.launch { userPreferences.setTrashRetentionDays(days) }
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

    val cloudBackupEnabled: StateFlow<Boolean> = userPreferences.cloudBackupEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val cloudBackupAccountEmail: StateFlow<String?> = userPreferences.cloudBackupAccountEmailFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val cloudBackupLastAt: StateFlow<Long?> = userPreferences.cloudBackupLastAtFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val cloudBackupWifiOnly: StateFlow<Boolean> = userPreferences.cloudBackupWifiOnlyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val cloudBackupIntervalMinutes: StateFlow<Long> = userPreferences.cloudBackupIntervalMinutesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 24 * 60L)

    val cloudBackupArchiveMonths: StateFlow<Int> = userPreferences.cloudBackupArchiveMonthsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 6)

    val savedSmartFilterSets: StateFlow<Set<String>> = userPreferences.savedSmartFilterSetsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val recentTasks: StateFlow<List<Task>> = combine(
        userPreferences.recentTaskIdsFlow,
        tasks
    ) { ids, taskList ->
        val byId = taskList.associateBy { it.id }
        ids.mapNotNull { byId[it] }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lastHomeTab: StateFlow<Int> = userPreferences.lastHomeTabFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Actions
    fun toggleTaskDone(id: String, onDoneCallback: () -> Unit) {
        viewModelScope.launch {
            val task = tasks.value.find { it.id == id }
            val wasDone = task?.done ?: false
            repository.toggleTaskDone(id)
            if (!wasDone) {
                onDoneCallback() // Trigger confetti
            }
        }
    }

    fun skipTaskOccurrence(id: String) {
        viewModelScope.launch {
            repository.skipTaskOccurrence(id)
        }
    }

    // Multi-task orchestration lives in TaskOperations (domain/usecase) — these wrappers only
    // supply the coroutine scope, so screens keep their existing entry points.
    fun bulkCompleteTasks(ids: List<String>) {
        viewModelScope.launch { taskOperations.bulkComplete(ids) }
    }

    fun bulkDeleteTasks(ids: List<String>) {
        viewModelScope.launch { taskOperations.bulkDelete(ids) }
    }

    fun restoreTasks(previousTasks: List<Task>) {
        if (previousTasks.isEmpty()) return
        viewModelScope.launch {
            repository.upsertTasks(previousTasks, notify = true, resyncReminder = true)
        }
    }

    fun bulkAddTag(ids: List<String>, tagId: String) {
        viewModelScope.launch { taskOperations.bulkAddTag(ids, tagId) }
    }

    fun bulkSetProject(ids: List<String>, projectId: String?) {
        viewModelScope.launch { taskOperations.bulkSetProject(ids, projectId) }
    }

    fun bulkSetList(ids: List<String>, listId: String?) {
        viewModelScope.launch { taskOperations.bulkSetList(ids, listId) }
    }

    fun duplicateTask(taskId: String, dueAdjustment: (LocalDate) -> LocalDate = { it }) {
        viewModelScope.launch { taskOperations.duplicate(taskId, dueAdjustment) }
    }

    fun rolloverProjectTasks(projectId: String) {
        viewModelScope.launch { taskOperations.rolloverProjectTasks(projectId) }
    }

    fun rolloverOverdueProjectTasks(projectId: String) {
        viewModelScope.launch { taskOperations.rolloverOverdueProjectTasks(projectId) }
    }

    fun bulkDuplicateTasks(ids: List<String>) {
        viewModelScope.launch { taskOperations.bulkDuplicate(ids) }
    }

    fun commitTaskOrder(orderedTasks: List<Task>) {
        viewModelScope.launch { taskOperations.commitTaskOrder(orderedTasks) }
    }

    /** Persists a drag-and-drop reorder of the whole Projects tab (a single flat list). */
    fun commitProjectOrder(orderedProjects: List<Project>) {
        viewModelScope.launch {
            orderedProjects.forEachIndexed { index, project ->
                if (project.sortOrder != index) {
                    repository.upsertProject(project.copy(sortOrder = index))
                }
            }
        }
    }

    /** Persists a drag-and-drop reorder of the nav drawer's Lists section (a single flat list). */
    fun commitListOrder(orderedLists: List<YataList>) {
        viewModelScope.launch {
            orderedLists.forEachIndexed { index, list ->
                if (list.sortOrder != index) {
                    repository.upsertList(list.copy(sortOrder = index))
                }
            }
        }
    }

    fun moveTaskToList(taskId: String, targetListId: String?, targetProjectId: String? = null) {
        viewModelScope.launch { taskOperations.moveTaskToList(taskId, targetListId, targetProjectId) }
    }

    fun bulkAssignPerson(ids: List<String>, personId: String) {
        viewModelScope.launch { taskOperations.bulkAssignPerson(ids, personId) }
    }

    fun toggleTaskFlag(id: String) {
        viewModelScope.launch {
            val task = tasks.value.find { it.id == id } ?: return@launch
            repository.setTaskFlag(id, !task.flag)
        }
    }

    fun cycleTaskPriority(id: String) {
        viewModelScope.launch {
            val task = tasks.value.find { it.id == id } ?: return@launch
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

    fun addTask(
        title: String,
        listId: String?,
        priority: String,
        assigneeIds: List<String>,
        tagIds: List<String>,
        recurrence: Recurrence?,
        notes: String? = null,
        due: String? = LocalDate.now().toString(),
        time: String? = null,
        reminder: String? = null,
        section: String = "Afternoon",
        projectId: String? = null,
        subtasks: List<Subtask> = emptyList(),
        flag: Boolean = false
    ) {
        viewModelScope.launch {
            val newTask = Task(
                id = "t_" + UUID.randomUUID().toString(),
                title = title,
                listId = listId,
                projectId = projectId,
                section = section,
                due = due,
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
        viewModelScope.launch {
            repository.upsertTask(task)
        }
    }

    fun renameTask(id: String, title: String) {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val task = tasks.value.find { it.id == id } ?: return@launch
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

    fun setLastHomeTab(tab: Int) {
        viewModelScope.launch {
            userPreferences.setLastHomeTab(tab)
        }
    }

    fun recordTaskViewed(id: String) {
        viewModelScope.launch {
            userPreferences.recordRecentTask(id)
        }
    }

    fun quickSnoozeTask(id: String, preset: QuickSnoozePreset) {
        viewModelScope.launch {
            taskOperations.quickSnooze(id, preset)
            userPreferences.recordRecentTask(id)
        }
    }

    fun bulkRescheduleTasks(ids: List<String>, preset: QuickSnoozePreset) {
        viewModelScope.launch {
            taskOperations.bulkReschedule(ids, preset)
            ids.take(8).forEach { userPreferences.recordRecentTask(it) }
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.deleteTask(task)
        }
    }

    // Trash
    val deletedTasks: StateFlow<List<Task>> = repository.getDeletedTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun restoreTask(id: String) {
        viewModelScope.launch {
            repository.restoreTask(id)
        }
    }

    // Archive — shelved but intact, separate from Trash (see YataRepository.getArchivedTasks).
    val archivedTasks: StateFlow<List<Task>> = repository.getArchivedTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setTaskArchived(id: String, archived: Boolean) {
        viewModelScope.launch {
            repository.setTaskArchived(id, archived)
        }
    }

    fun bulkArchiveTasks(ids: List<String>, archived: Boolean) {
        viewModelScope.launch {
            ids.forEach { repository.setTaskArchived(it, archived) }
        }
    }

    fun permanentlyDeleteTask(task: Task) {
        viewModelScope.launch {
            repository.permanentlyDeleteTask(task)
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
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
        viewModelScope.launch {
            val authorId = people.value.find { it.isMe }?.id
            repository.addComment(taskId, body, authorId)
        }
    }

    fun deleteComment(comment: TaskComment) {
        viewModelScope.launch {
            repository.deleteComment(comment)
        }
    }

    fun addProject(name: String, color: String, icon: String = "layers", due: String? = null, commonTagIds: List<String> = emptyList(), defaultReminder: String? = null, description: String? = null, excludeFromToday: Boolean = false) {
        viewModelScope.launch {
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
        viewModelScope.launch {
            repository.upsertProject(project)
        }
    }

    fun toggleProjectStarred(id: String) {
        viewModelScope.launch {
            val project = projects.value.find { it.id == id } ?: return@launch
            repository.upsertProject(project.copy(starred = !project.starred))
        }
    }

    fun deleteProject(project: Project) {
        viewModelScope.launch {
            repository.deleteProject(project)
        }
    }

    fun deleteProjectOnly(project: Project) {
        viewModelScope.launch {
            repository.deleteProjectOnly(project)
        }
    }

    fun bulkDeleteProjects(ids: List<String>) {
        viewModelScope.launch {
            val byId = projects.value.associateBy { it.id }
            ids.forEach { id -> byId[id]?.let { repository.deleteProject(it) } }
        }
    }

    /** Archiving hides a project from active project surfaces while keeping its tasks linked. */
    fun setProjectArchived(project: Project, archived: Boolean) {
        viewModelScope.launch {
            repository.upsertProject(project.copy(archived = archived))
        }
    }

    fun bulkArchiveProjects(ids: List<String>) {
        viewModelScope.launch {
            repository.setProjectsArchived(ids, true)
        }
    }

    fun bulkRestoreProjects(ids: List<String>) {
        viewModelScope.launch {
            repository.setProjectsArchived(ids, false)
        }
    }

    fun addPerson(name: String, color: String, groupId: String? = null, photoUri: String? = null) {
        viewModelScope.launch {
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
        viewModelScope.launch {
            repository.upsertPerson(person)
        }
    }

    fun deletePerson(person: Person) {
        viewModelScope.launch {
            repository.deletePerson(person)
        }
    }

    /** Archiving (rather than deleting) a person keeps their historical assigned-task stats
     * intact in Analytics/PersonDetail — used when a team member leaves. */
    fun setPersonArchived(person: Person, archived: Boolean) {
        viewModelScope.launch {
            repository.upsertPerson(person.copy(archived = archived))
        }
    }

    fun togglePersonStarred(id: String) {
        viewModelScope.launch {
            val person = people.value.find { it.id == id } ?: return@launch
            repository.upsertPerson(person.copy(starred = !person.starred))
        }
    }

    fun setPeopleGroup(personIds: List<String>, groupId: String?) {
        viewModelScope.launch {
            val byId = people.value.associateBy { it.id }
            personIds.forEach { id ->
                byId[id]?.let { repository.upsertPerson(it.copy(groupId = groupId)) }
            }
        }
    }

    fun addPersonGroup(name: String, color: String) {
        viewModelScope.launch {
            repository.upsertPersonGroup(PersonGroup(id = "pg_" + UUID.randomUUID().toString(), name = name, color = color))
        }
    }

    fun upsertPersonGroup(group: PersonGroup) {
        viewModelScope.launch {
            repository.upsertPersonGroup(group)
        }
    }

    fun deletePersonGroup(group: PersonGroup) {
        viewModelScope.launch {
            repository.deletePersonGroup(group)
        }
    }

    fun addTag(name: String, color: String, groupId: String? = null, hideCompletedByDefault: Boolean = false) {
        viewModelScope.launch {
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
        viewModelScope.launch {
            repository.upsertTag(tag)
        }
    }

    fun deleteTag(tag: Tag) {
        viewModelScope.launch {
            repository.deleteTag(tag)
        }
    }

    fun bulkDeleteTags(ids: List<String>) {
        viewModelScope.launch {
            val byId = tags.value.associateBy { it.id }
            ids.forEach { id -> byId[id]?.let { repository.deleteTag(it) } }
        }
    }

    fun toggleTagStarred(id: String) {
        viewModelScope.launch {
            val tag = tags.value.find { it.id == id } ?: return@launch
            repository.upsertTag(tag.copy(starred = !tag.starred))
        }
    }

    fun addTagGroup(name: String, color: String) {
        viewModelScope.launch {
            repository.upsertTagGroup(TagGroup(id = "tg_" + UUID.randomUUID().toString(), name = name, color = color))
        }
    }

    fun upsertTagGroup(group: TagGroup) {
        viewModelScope.launch {
            repository.upsertTagGroup(group)
        }
    }

    fun deleteTagGroup(group: TagGroup) {
        viewModelScope.launch {
            repository.deleteTagGroup(group)
        }
    }

    fun addList(name: String, color: String, icon: String, excludeFromToday: Boolean = false) {
        viewModelScope.launch {
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
        viewModelScope.launch {
            repository.upsertList(list)
        }
    }

    fun toggleListStarred(id: String) {
        viewModelScope.launch {
            val list = lists.value.find { it.id == id } ?: return@launch
            repository.upsertList(list.copy(starred = !list.starred))
        }
    }

    fun deleteList(list: YataList) {
        viewModelScope.launch {
            repository.deleteList(list)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            userPreferences.setThemeMode(mode)
        }
    }

    fun setThemeSchedule(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        viewModelScope.launch {
            userPreferences.setThemeSchedule(startHour, startMinute, endHour, endMinute)
        }
    }

    fun setAppFont(font: AppFont) {
        viewModelScope.launch {
            userPreferences.setAppFont(font)
        }
    }

    fun setReduceMotionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setReduceMotionEnabled(enabled)
        }
    }

    fun setEnhancedM3ThemingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setEnhancedM3ThemingEnabled(enabled)
        }
    }

    /** Pass null to clear back to the app's default warm coral palette. Only takes visual effect
     * while Material You dynamic color is off. */
    fun setCustomThemeSeedColor(argb: Int?) {
        viewModelScope.launch {
            userPreferences.setCustomThemeSeedColor(argb)
        }
    }

    fun setFloatingBottomNavEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setFloatingBottomNavEnabled(enabled)
        }
    }

    fun setBottomNavLabelsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setBottomNavLabelsEnabled(enabled)
        }
    }

    /** Toggled by tapping the logo on the Help & About screen. Demo mode swaps every screen's
     * data source to an in-memory sample dataset (see RoutingYataRepository) for taking store
     * screenshots — the real database is never read from or written to while it's active. */
    fun toggleDemoMode() {
        viewModelScope.launch {
            userPreferences.setDemoModeEnabled(!demoModeEnabled.value)
        }
    }

    fun setTextScale(scale: Float) {
        viewModelScope.launch {
            userPreferences.setTextScale(scale)
        }
    }

    fun setTaskRowDensity(density: com.mj.yata.domain.model.TaskRowDensity) {
        viewModelScope.launch {
            userPreferences.setTaskRowDensity(density)
        }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setHapticsEnabled(enabled)
        }
    }

    fun setTaskSwipeActionsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setTaskSwipeActionsEnabled(enabled)
        }
    }

    fun setCompletionSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setCompletionSoundEnabled(enabled)
        }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setAppLockEnabled(enabled)
        }
    }

    fun setAppLockPin(pin: String?) {
        viewModelScope.launch {
            userPreferences.setAppLockPin(pin)
        }
    }

    fun setAppLockTimeoutMinutes(minutes: Int) {
        viewModelScope.launch {
            userPreferences.setAppLockTimeoutMinutes(minutes)
        }
    }

    fun setTodayTabEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setTodayTabEnabled(enabled)
        }
    }

    fun setUpcomingTabEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setUpcomingTabEnabled(enabled)
        }
    }

    fun setFabPosition(position: com.mj.yata.domain.model.FabPosition) {
        viewModelScope.launch {
            userPreferences.setFabPosition(position)
        }
    }

    fun backupThenDeleteAllData(onResult: (backupFilename: String?) -> Unit) {
        viewModelScope.launch { onResult(backupOperations.backupThenDeleteAllData()) }
    }

    fun setUserName(name: String) {
        viewModelScope.launch {
            userPreferences.setUserName(name)
        }
    }

    fun setUserEmail(email: String) {
        viewModelScope.launch {
            userPreferences.setUserEmail(email)
        }
    }

    fun setUserPhotoUri(uri: String?) {
        viewModelScope.launch {
            userPreferences.setUserPhotoUri(uri)
        }
    }

    fun setDefaultListId(id: String) {
        viewModelScope.launch {
            userPreferences.setDefaultListId(id)
        }
    }

    fun setStartOfWeekSunday(sunday: Boolean) {
        viewModelScope.launch {
            userPreferences.setStartOfWeekSunday(sunday)
        }
    }

    fun setDefaultReminderTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            userPreferences.setDefaultReminderTime(hour, minute)
        }
    }

    fun setUiScale(scale: Float) {
        viewModelScope.launch {
            userPreferences.setUiScale(scale)
        }
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setDynamicColorEnabled(enabled)
        }
    }

    fun setHideCompletedToday(hide: Boolean) {
        viewModelScope.launch {
            userPreferences.setHideCompletedToday(hide)
        }
    }

    fun setHideCompletedProject(hide: Boolean) {
        viewModelScope.launch {
            userPreferences.setHideCompletedProject(hide)
        }
    }

    fun setHideCompletedList(hide: Boolean) {
        viewModelScope.launch {
            userPreferences.setHideCompletedList(hide)
        }
    }

    fun setHideCompletedPerson(hide: Boolean) {
        viewModelScope.launch {
            userPreferences.setHideCompletedPerson(hide)
        }
    }

    fun setSortModeToday(mode: com.mj.yata.util.TaskSortMode) {
        viewModelScope.launch { userPreferences.setSortModeToday(mode) }
    }

    fun setSortModeProject(mode: com.mj.yata.util.TaskSortMode) {
        viewModelScope.launch { userPreferences.setSortModeProject(mode) }
    }

    fun setSortModeList(mode: com.mj.yata.util.TaskSortMode) {
        viewModelScope.launch { userPreferences.setSortModeList(mode) }
    }

    fun setSortModePerson(mode: com.mj.yata.util.TaskSortMode) {
        viewModelScope.launch { userPreferences.setSortModePerson(mode) }
    }

    fun setSortModeTagDetail(mode: com.mj.yata.util.TaskSortMode) {
        viewModelScope.launch { userPreferences.setSortModeTagDetail(mode) }
    }

    fun setSortModeTagsTab(mode: com.mj.yata.util.EntitySortMode) {
        viewModelScope.launch { userPreferences.setSortModeTagsTab(mode) }
    }

    fun setSortModePeopleTab(mode: com.mj.yata.util.EntitySortMode) {
        viewModelScope.launch { userPreferences.setSortModePeopleTab(mode) }
    }

    fun setHasSeenWelcome() {
        viewModelScope.launch {
            userPreferences.setHasSeenWelcome(true)
        }
    }

    fun setPeopleFeatureEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setPeopleFeatureEnabled(enabled)
        }
    }

    fun setTagsFeatureEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setTagsFeatureEnabled(enabled)
        }
    }

    fun setProjectsFeatureEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setProjectsFeatureEnabled(enabled)
        }
    }

    fun saveSmartFilterSet(encodedFilters: String) {
        viewModelScope.launch {
            userPreferences.addSavedSmartFilterSet(encodedFilters)
        }
    }

    fun removeSmartFilterSet(encodedFilters: String) {
        viewModelScope.launch {
            userPreferences.removeSavedSmartFilterSet(encodedFilters)
        }
    }

    fun cloudSignOut() {
        viewModelScope.launch { backupOperations.cloudSignOut() }
    }

    fun setCloudBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setCloudBackupEnabled(enabled)
        }
    }

    fun setLocalBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setLocalBackupEnabled(enabled)
        }
    }

    fun backupLocalNow() {
        viewModelScope.launch { backupOperations.backupLocalNow() }
    }

    fun restoreLocalBackup(onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(backupOperations.restoreLatestLocalBackup()) }
    }

    fun streakForTask(taskId: String, onResult: (Int) -> Unit) {
        viewModelScope.launch {
            onResult(repository.getTaskStreak(taskId))
        }
    }

    fun setCloudBackupWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch {
            userPreferences.setCloudBackupWifiOnly(wifiOnly)
        }
    }

    fun setCloudBackupIntervalMinutes(minutes: Long) {
        viewModelScope.launch {
            userPreferences.setCloudBackupIntervalMinutes(minutes)
        }
        backupOperations.updateCloudBackupInterval(minutes)
    }

    fun setCloudBackupArchiveMonths(months: Int) {
        viewModelScope.launch {
            userPreferences.setCloudBackupArchiveMonths(months)
        }
    }

    fun cloudBackupNow(onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch { onResult(backupOperations.cloudBackupNow()) }
    }

    fun listCloudBackups(onResult: (Result<List<CloudBackupEntry>>) -> Unit) {
        viewModelScope.launch { onResult(backupOperations.listCloudBackups()) }
    }

    fun restoreCloudBackup(fileId: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch { onResult(backupOperations.restoreCloudBackup(fileId)) }
    }

    fun compareWithLastBackup(onResult: (Result<com.mj.yata.data.cloud.CloudBackupDiff>) -> Unit) {
        viewModelScope.launch { onResult(backupOperations.compareWithLastBackup(tasks.value)) }
    }
}
