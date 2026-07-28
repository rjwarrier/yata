package com.mj.yata.data.local.datastore

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.mj.yata.domain.model.AppFont
import com.mj.yata.domain.model.DefaultDueDate
import com.mj.yata.domain.model.ThemeMode
import com.mj.yata.util.decodeSalt
import com.mj.yata.util.encodeSalt
import com.mj.yata.util.EntitySortMode
import com.mj.yata.util.TaskSortMode
import com.mj.yata.util.generateSalt
import com.mj.yata.util.hashPin
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

interface TaskListPreferences {
    val defaultListIdFlow: Flow<String>
}

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

data class UserPreferencesSnapshot(
    val themeMode: ThemeMode,
    val dynamicColorEnabled: Boolean,
    val themeScheduleStartHour: Int,
    val themeScheduleStartMinute: Int,
    val themeScheduleEndHour: Int,
    val themeScheduleEndMinute: Int
)

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext context: Context
) : TaskListPreferences {

    private val dataStore = context.dataStore

    /**
     * Every read below goes through this rather than `dataStore.data` directly. DataStore
     * surfaces a corrupted or unreadable prefs file as an IOException thrown *into the
     * collector*, which — since these flows back the theme, the feature flags and most of
     * Settings — would crash the UI on collection with no way for the user to recover. Falling
     * back to an empty Preferences means every `?:` default below applies instead, so a corrupt
     * file degrades to first-launch settings rather than an unopenable app. Only reads are
     * wrapped: a failing write should still surface to its caller.
     */
    private val prefsFlow: Flow<Preferences> = dataStore.data.catch { e ->
        if (e is IOException) {
            Log.e("UserPreferences", "Could not read preferences; falling back to defaults", e)
            emit(emptyPreferences())
        } else {
            throw e
        }
    }

    val snapshotFlow: Flow<UserPreferencesSnapshot> = prefsFlow.map { prefs ->
        UserPreferencesSnapshot(
            themeMode = when (prefs[THEME_MODE]) {
                ThemeMode.LIGHT.name     -> ThemeMode.LIGHT
                ThemeMode.DARK.name      -> ThemeMode.DARK
                ThemeMode.AMOLED.name    -> ThemeMode.AMOLED
                else                     -> ThemeMode.SYSTEM
            },
            dynamicColorEnabled = prefs[DYNAMIC_COLOR_ENABLED] ?: true,
            themeScheduleStartHour = prefs[THEME_SCHEDULE_START_HOUR] ?: 21,
            themeScheduleStartMinute = prefs[THEME_SCHEDULE_START_MINUTE] ?: 0,
            themeScheduleEndHour = prefs[THEME_SCHEDULE_END_HOUR] ?: 7,
            themeScheduleEndMinute = prefs[THEME_SCHEDULE_END_MINUTE] ?: 0
        )
    }

    companion object {
        val THEME_MODE              = stringPreferencesKey("theme_mode")
        val APP_FONT                = stringPreferencesKey("app_font")
        val USER_NAME               = stringPreferencesKey("user_name")
        val USER_EMAIL              = stringPreferencesKey("user_email")
        val USER_PHOTO_URI          = stringPreferencesKey("user_photo_uri")
        val DEFAULT_LIST_ID         = stringPreferencesKey("default_list_id")
        val START_OF_WEEK_SUNDAY    = booleanPreferencesKey("start_of_week_sunday")
        val DEFAULT_REMINDER_HOUR   = intPreferencesKey("default_reminder_hour")
        val DEFAULT_REMINDER_MINUTE = intPreferencesKey("default_reminder_minute")
        val UI_SCALE                = floatPreferencesKey("ui_scale")
        val DYNAMIC_COLOR_ENABLED   = booleanPreferencesKey("dynamic_color_enabled")
        val PEOPLE_FEATURE_ENABLED   = booleanPreferencesKey("people_feature_enabled")
        val TAGS_FEATURE_ENABLED     = booleanPreferencesKey("tags_feature_enabled")
        val PROJECTS_FEATURE_ENABLED = booleanPreferencesKey("projects_feature_enabled")
        val CLOUD_BACKUP_ENABLED     = booleanPreferencesKey("cloud_backup_enabled")
        val CLOUD_BACKUP_ACCOUNT     = stringPreferencesKey("cloud_backup_account_email")
        val CLOUD_BACKUP_LAST_AT     = longPreferencesKey("cloud_backup_last_at")
        val CLOUD_BACKUP_WIFI_ONLY   = booleanPreferencesKey("cloud_backup_wifi_only")
        val CLOUD_BACKUP_INTERVAL_MINUTES = longPreferencesKey("cloud_backup_interval_minutes")
        // Completed tasks older than this move out of the small, frequently-uploaded primary
        // cloud backup into a separate archive file so the primary doesn't grow forever. 0 means
        // "never archive" (always back up everything in one file).
        val CLOUD_BACKUP_ARCHIVE_MONTHS = intPreferencesKey("cloud_backup_archive_months")
        val LOCAL_BACKUP_ENABLED    = booleanPreferencesKey("local_backup_enabled")
        val LOCAL_BACKUP_LAST_AT    = longPreferencesKey("local_backup_last_at")
        val LOCAL_BACKUP_INTERVAL_MINUTES = longPreferencesKey("local_backup_interval_minutes")
        val THEME_SCHEDULE_START_HOUR   = intPreferencesKey("theme_schedule_start_hour")
        val THEME_SCHEDULE_START_MINUTE = intPreferencesKey("theme_schedule_start_minute")
        val THEME_SCHEDULE_END_HOUR     = intPreferencesKey("theme_schedule_end_hour")
        val THEME_SCHEDULE_END_MINUTE   = intPreferencesKey("theme_schedule_end_minute")
        val REDUCE_MOTION_ENABLED   = booleanPreferencesKey("reduce_motion_enabled")
        val ENHANCED_M3_THEMING_ENABLED = booleanPreferencesKey("enhanced_m3_theming_enabled")
        val FLOATING_BOTTOM_NAV_ENABLED = booleanPreferencesKey("floating_bottom_nav_enabled")
        val BOTTOM_NAV_LABELS_ENABLED = booleanPreferencesKey("bottom_nav_labels_enabled")
        val DEMO_MODE_ENABLED = booleanPreferencesKey("demo_mode_enabled")
        val CUSTOM_THEME_SEED_COLOR = intPreferencesKey("custom_theme_seed_color")
        val COMPLETION_SOUND_ENABLED = booleanPreferencesKey("completion_sound_enabled")
        val VOICE_RECOGNITION_LANGUAGE = stringPreferencesKey("voice_recognition_language")
        val TEXT_SCALE              = floatPreferencesKey("text_scale")
        val TASK_ROW_DENSITY        = stringPreferencesKey("task_row_density")
        val HAPTICS_ENABLED         = booleanPreferencesKey("haptics_enabled")
        val TASK_SWIPE_ACTIONS_ENABLED = booleanPreferencesKey("task_swipe_actions_enabled")
        val APP_LOCK_ENABLED        = booleanPreferencesKey("app_lock_enabled")
        val APP_LOCK_PIN_SALT       = stringPreferencesKey("app_lock_pin_salt")
        val APP_LOCK_PIN_HASH       = stringPreferencesKey("app_lock_pin_hash")
        val APP_LOCK_TIMEOUT_MINUTES = intPreferencesKey("app_lock_timeout_minutes")
        val TODAY_TAB_ENABLED       = booleanPreferencesKey("today_tab_enabled")
        val UPCOMING_TAB_ENABLED    = booleanPreferencesKey("upcoming_tab_enabled")
        val FAB_POSITION            = stringPreferencesKey("fab_position")
        val HIDE_COMPLETED_TODAY    = booleanPreferencesKey("hide_completed_today")
        val HIDE_COMPLETED_PROJECT  = booleanPreferencesKey("hide_completed_project")
        val HIDE_COMPLETED_LIST     = booleanPreferencesKey("hide_completed_list")
        val HIDE_COMPLETED_PERSON   = booleanPreferencesKey("hide_completed_person")
        val LAST_HOME_TAB           = intPreferencesKey("last_home_tab")
        val HAS_SEEN_WELCOME       = booleanPreferencesKey("has_seen_welcome")
        val LAST_PRIMARY_ARGB      = intPreferencesKey("last_primary_argb")
        val SAVED_SMART_FILTER_SETS = stringSetPreferencesKey("saved_smart_filter_sets")
        val RECENT_TASK_IDS       = stringPreferencesKey("recent_task_ids")
        // Sort mode per screen, persisted alongside HIDE_COMPLETED_* above — both are
        // per-screen view state the user expects to survive navigating away and back.
        val SORT_MODE_TODAY       = stringPreferencesKey("sort_mode_today")
        val SORT_MODE_PROJECT     = stringPreferencesKey("sort_mode_project")
        val SORT_MODE_LIST        = stringPreferencesKey("sort_mode_list")
        val SORT_MODE_PERSON      = stringPreferencesKey("sort_mode_person")
        val DEFAULT_DUE_DATE      = stringPreferencesKey("default_due_date")
        val DEFAULT_PRIORITY      = stringPreferencesKey("default_priority")
        // Days a soft-deleted task stays in Trash before purgeOldTrash removes it. 0 = keep
        // forever; the purge is skipped entirely rather than treating 0 as "delete immediately".
        val TRASH_RETENTION_DAYS  = intPreferencesKey("trash_retention_days")
        // Days after completion before a task is auto-archived. 0 = off (default),
        // so this never shelves anything unless the user opts in.
        val AUTO_ARCHIVE_DAYS     = intPreferencesKey("auto_archive_days")
        // Background notification controls. Both default to on, matching the behaviour before
        // they were configurable — these workers previously ran unconditionally.
        val DAILY_AGENDA_ENABLED  = booleanPreferencesKey("daily_agenda_enabled")
        val DAILY_AGENDA_HOUR     = intPreferencesKey("daily_agenda_hour")
        val DAILY_AGENDA_MINUTE   = intPreferencesKey("daily_agenda_minute")
        val OVERDUE_NUDGES_ENABLED = booleanPreferencesKey("overdue_nudges_enabled")
        // Seconds an undo stays available after a delete. 4 matches the old SnackbarDuration.Short.
        val UNDO_WINDOW_SECONDS   = intPreferencesKey("undo_window_seconds")
        val SNOOZE_TONIGHT_HOUR   = intPreferencesKey("snooze_tonight_hour")
        val SNOOZE_TONIGHT_MINUTE = intPreferencesKey("snooze_tonight_minute")
        val SNOOZE_TOMORROW_HOUR  = intPreferencesKey("snooze_tomorrow_hour")
        val SNOOZE_TOMORROW_MINUTE = intPreferencesKey("snooze_tomorrow_minute")
        val SORT_MODE_TAG_DETAIL  = stringPreferencesKey("sort_mode_tag_detail")
        val SORT_MODE_TAGS_TAB    = stringPreferencesKey("sort_mode_tags_tab")
        val SORT_MODE_PEOPLE_TAB  = stringPreferencesKey("sort_mode_people_tab")
    }

    /** Unknown/absent values fall back to the enum default rather than throwing — a persisted
     * name can go stale if an enum constant is ever renamed or removed. */
    private fun taskSortModeOf(raw: String?): TaskSortMode =
        TaskSortMode.entries.firstOrNull { it.name == raw } ?: TaskSortMode.MANUAL

    private fun entitySortModeOf(raw: String?): EntitySortMode =
        EntitySortMode.entries.firstOrNull { it.name == raw } ?: EntitySortMode.NAME_ASC

    val themeModeFlow: Flow<ThemeMode> = prefsFlow.map { prefs ->
        when (prefs[THEME_MODE]) {
            ThemeMode.LIGHT.name     -> ThemeMode.LIGHT
            ThemeMode.DARK.name      -> ThemeMode.DARK
            ThemeMode.AMOLED.name    -> ThemeMode.AMOLED
            else                     -> ThemeMode.SYSTEM
        }
    }

    // Dark from 9pm to 7am by default.
    val themeScheduleStartHourFlow: Flow<Int> = prefsFlow.map { it[THEME_SCHEDULE_START_HOUR] ?: 21 }
    val themeScheduleStartMinuteFlow: Flow<Int> = prefsFlow.map { it[THEME_SCHEDULE_START_MINUTE] ?: 0 }
    val themeScheduleEndHourFlow: Flow<Int> = prefsFlow.map { it[THEME_SCHEDULE_END_HOUR] ?: 7 }
    val themeScheduleEndMinuteFlow: Flow<Int> = prefsFlow.map { it[THEME_SCHEDULE_END_MINUTE] ?: 0 }

    val reduceMotionEnabledFlow: Flow<Boolean> = prefsFlow.map { it[REDUCE_MOTION_ENABLED] ?: false }
    val enhancedM3ThemingEnabledFlow: Flow<Boolean> = prefsFlow.map { it[ENHANCED_M3_THEMING_ENABLED] ?: false }
    val floatingBottomNavEnabledFlow: Flow<Boolean> = prefsFlow.map { it[FLOATING_BOTTOM_NAV_ENABLED] ?: false }
    val bottomNavLabelsEnabledFlow: Flow<Boolean> = prefsFlow.map { it[BOTTOM_NAV_LABELS_ENABLED] ?: true }
    val demoModeEnabledFlow: Flow<Boolean> = prefsFlow.map { it[DEMO_MODE_ENABLED] ?: false }

    /** Non-null means a seed-color theme (preset or custom) is active; null means the app's
     * default warm coral palette. Ignored entirely when Material You dynamic color is on. */
    val customThemeSeedColorFlow: Flow<Int?> = prefsFlow.map { it[CUSTOM_THEME_SEED_COLOR] }
    val completionSoundEnabledFlow: Flow<Boolean> = prefsFlow.map { it[COMPLETION_SOUND_ENABLED] ?: true }
    val voiceRecognitionLanguageFlow: Flow<String> = prefsFlow.map { it[VOICE_RECOGNITION_LANGUAGE] ?: "default" }
    val textScaleFlow: Flow<Float> = prefsFlow.map { it[TEXT_SCALE] ?: 1.0f }
    val taskRowDensityFlow: Flow<com.mj.yata.domain.model.TaskRowDensity> = prefsFlow.map { prefs ->
        when (prefs[TASK_ROW_DENSITY]) {
            com.mj.yata.domain.model.TaskRowDensity.COMPACT.name -> com.mj.yata.domain.model.TaskRowDensity.COMPACT
            com.mj.yata.domain.model.TaskRowDensity.SPACIOUS.name -> com.mj.yata.domain.model.TaskRowDensity.SPACIOUS
            else -> com.mj.yata.domain.model.TaskRowDensity.COMFORTABLE
        }
    }
    val hapticsEnabledFlow: Flow<Boolean> = prefsFlow.map { it[HAPTICS_ENABLED] ?: true }
    val taskSwipeActionsEnabledFlow: Flow<Boolean> = prefsFlow.map { it[TASK_SWIPE_ACTIONS_ENABLED] ?: true }
    val appLockEnabledFlow: Flow<Boolean> = prefsFlow.map { it[APP_LOCK_ENABLED] ?: false }
    val appLockPinSetFlow: Flow<Boolean> = prefsFlow.map { !it[APP_LOCK_PIN_HASH].isNullOrBlank() }
    val appLockTimeoutMinutesFlow: Flow<Int> = prefsFlow.map { it[APP_LOCK_TIMEOUT_MINUTES] ?: 0 }
    val todayTabEnabledFlow: Flow<Boolean> = prefsFlow.map { it[TODAY_TAB_ENABLED] ?: true }
    val upcomingTabEnabledFlow: Flow<Boolean> = prefsFlow.map { it[UPCOMING_TAB_ENABLED] ?: true }
    val fabPositionFlow: Flow<com.mj.yata.domain.model.FabPosition> = prefsFlow.map { prefs ->
        when (prefs[FAB_POSITION]) {
            com.mj.yata.domain.model.FabPosition.LEFT.name -> com.mj.yata.domain.model.FabPosition.LEFT
            com.mj.yata.domain.model.FabPosition.HIDDEN.name -> com.mj.yata.domain.model.FabPosition.HIDDEN
            else -> com.mj.yata.domain.model.FabPosition.RIGHT
        }
    }

    val appFontFlow: Flow<AppFont> = prefsFlow.map { prefs ->
        when (prefs[APP_FONT]) {
            AppFont.JETBRAINS_MONO.name -> AppFont.JETBRAINS_MONO
            else                        -> AppFont.INTER
        }
    }

    val userNameFlow: Flow<String> = prefsFlow.map { it[USER_NAME] ?: "" }
    val userEmailFlow: Flow<String> = prefsFlow.map { it[USER_EMAIL] ?: "" }
    val userPhotoUriFlow: Flow<String?> = prefsFlow.map { it[USER_PHOTO_URI] }

    override val defaultListIdFlow: Flow<String> = prefsFlow.map { it[DEFAULT_LIST_ID] ?: "" }

    val startOfWeekSundayFlow: Flow<Boolean> = prefsFlow.map { it[START_OF_WEEK_SUNDAY] ?: true }
    val defaultReminderHourFlow: Flow<Int> = prefsFlow.map { it[DEFAULT_REMINDER_HOUR] ?: 9 }
    val defaultReminderMinuteFlow: Flow<Int> = prefsFlow.map { it[DEFAULT_REMINDER_MINUTE] ?: 0 }
    val uiScaleFlow: Flow<Float> = prefsFlow.map { it[UI_SCALE] ?: 1.0f }
    val dynamicColorEnabledFlow: Flow<Boolean> = prefsFlow.map { it[DYNAMIC_COLOR_ENABLED] ?: true }
    val peopleFeatureEnabledFlow: Flow<Boolean> = prefsFlow.map { it[PEOPLE_FEATURE_ENABLED] ?: true }
    val tagsFeatureEnabledFlow: Flow<Boolean> = prefsFlow.map { it[TAGS_FEATURE_ENABLED] ?: true }
    val projectsFeatureEnabledFlow: Flow<Boolean> = prefsFlow.map { it[PROJECTS_FEATURE_ENABLED] ?: true }
    val cloudBackupEnabledFlow: Flow<Boolean> = prefsFlow.map { it[CLOUD_BACKUP_ENABLED] ?: false }
    val cloudBackupAccountEmailFlow: Flow<String?> = prefsFlow.map { it[CLOUD_BACKUP_ACCOUNT] }
    val cloudBackupLastAtFlow: Flow<Long?> = prefsFlow.map { it[CLOUD_BACKUP_LAST_AT] }
    val cloudBackupWifiOnlyFlow: Flow<Boolean> = prefsFlow.map { it[CLOUD_BACKUP_WIFI_ONLY] ?: true }
    // Default matches CloudBackupWorker's default schedule (1 day) — WorkManager enforces a
    // 15-minute floor on periodic work, so this is clamped the same way on write.
    val cloudBackupIntervalMinutesFlow: Flow<Long> = prefsFlow.map { it[CLOUD_BACKUP_INTERVAL_MINUTES] ?: (24 * 60L) }
    val cloudBackupArchiveMonthsFlow: Flow<Int> = prefsFlow.map { it[CLOUD_BACKUP_ARCHIVE_MONTHS] ?: 6 }
    val localBackupEnabledFlow: Flow<Boolean> = prefsFlow.map { it[LOCAL_BACKUP_ENABLED] ?: false }
    val localBackupLastAtFlow: Flow<Long?> = prefsFlow.map { it[LOCAL_BACKUP_LAST_AT] }
    val localBackupIntervalMinutesFlow: Flow<Long> = prefsFlow.map { it[LOCAL_BACKUP_INTERVAL_MINUTES] ?: (24 * 60L) }
    val hideCompletedTodayFlow: Flow<Boolean> = prefsFlow.map { it[HIDE_COMPLETED_TODAY] ?: false }
    val hideCompletedProjectFlow: Flow<Boolean> = prefsFlow.map { it[HIDE_COMPLETED_PROJECT] ?: false }
    val hideCompletedListFlow: Flow<Boolean> = prefsFlow.map { it[HIDE_COMPLETED_LIST] ?: false }
    val hideCompletedPersonFlow: Flow<Boolean> = prefsFlow.map { it[HIDE_COMPLETED_PERSON] ?: false }
    val sortModeTodayFlow: Flow<TaskSortMode> = prefsFlow.map { taskSortModeOf(it[SORT_MODE_TODAY]) }
    val sortModeProjectFlow: Flow<TaskSortMode> = prefsFlow.map { taskSortModeOf(it[SORT_MODE_PROJECT]) }
    val sortModeListFlow: Flow<TaskSortMode> = prefsFlow.map { taskSortModeOf(it[SORT_MODE_LIST]) }
    val sortModePersonFlow: Flow<TaskSortMode> = prefsFlow.map { taskSortModeOf(it[SORT_MODE_PERSON]) }
    /** Defaults applied to a newly created task. TODAY preserves the previous hardcoded behavior. */
    val defaultDueDateFlow: Flow<DefaultDueDate> = prefsFlow.map { prefs ->
        DefaultDueDate.entries.firstOrNull { it.name == prefs[DEFAULT_DUE_DATE] } ?: DefaultDueDate.TODAY
    }
    /** One of Task.priority's values: "none" | "low" | "med" | "high". */
    val defaultPriorityFlow: Flow<String> = prefsFlow.map { prefs ->
        prefs[DEFAULT_PRIORITY]?.takeIf { it in setOf("none", "low", "med", "high") } ?: "none"
    }
    val trashRetentionDaysFlow: Flow<Int> = prefsFlow.map { it[TRASH_RETENTION_DAYS] ?: 30 }
    val autoArchiveDaysFlow: Flow<Int> = prefsFlow.map { it[AUTO_ARCHIVE_DAYS] ?: 0 }
    val dailyAgendaEnabledFlow: Flow<Boolean> = prefsFlow.map { it[DAILY_AGENDA_ENABLED] ?: true }
    val dailyAgendaHourFlow: Flow<Int> = prefsFlow.map { it[DAILY_AGENDA_HOUR] ?: 7 }
    val dailyAgendaMinuteFlow: Flow<Int> = prefsFlow.map { it[DAILY_AGENDA_MINUTE] ?: 30 }
    val overdueNudgesEnabledFlow: Flow<Boolean> = prefsFlow.map { it[OVERDUE_NUDGES_ENABLED] ?: true }
    val undoWindowSecondsFlow: Flow<Int> = prefsFlow.map { it[UNDO_WINDOW_SECONDS] ?: 4 }
    val snoozeTonightHourFlow: Flow<Int> = prefsFlow.map { it[SNOOZE_TONIGHT_HOUR] ?: 18 }
    val snoozeTonightMinuteFlow: Flow<Int> = prefsFlow.map { it[SNOOZE_TONIGHT_MINUTE] ?: 0 }
    val snoozeTomorrowHourFlow: Flow<Int> = prefsFlow.map { it[SNOOZE_TOMORROW_HOUR] ?: 9 }
    val snoozeTomorrowMinuteFlow: Flow<Int> = prefsFlow.map { it[SNOOZE_TOMORROW_MINUTE] ?: 0 }
    val sortModeTagDetailFlow: Flow<TaskSortMode> = prefsFlow.map { taskSortModeOf(it[SORT_MODE_TAG_DETAIL]) }
    val sortModeTagsTabFlow: Flow<EntitySortMode> = prefsFlow.map { entitySortModeOf(it[SORT_MODE_TAGS_TAB]) }
    val sortModePeopleTabFlow: Flow<EntitySortMode> = prefsFlow.map { entitySortModeOf(it[SORT_MODE_PEOPLE_TAB]) }
    val lastHomeTabFlow: Flow<Int> = prefsFlow.map { (it[LAST_HOME_TAB] ?: 0).coerceIn(0, 4) }
    val hasSeenWelcomeFlow: Flow<Boolean> = prefsFlow.map { it[HAS_SEEN_WELCOME] ?: false }
    val savedSmartFilterSetsFlow: Flow<Set<String>> = prefsFlow.map { it[SAVED_SMART_FILTER_SETS] ?: emptySet() }
    val recentTaskIdsFlow: Flow<List<String>> = prefsFlow.map { prefs ->
        prefs[RECENT_TASK_IDS]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    }
    /** Last `MaterialTheme.colorScheme.primary` actually rendered by the foreground Activity —
     * background notification/widget code reads this instead of re-deriving dynamic color in a
     * receiver/worker context, where it can resolve differently than in the live Activity. Null
     * (no cached value yet) until the app has been opened at least once. */
    val lastPrimaryArgbFlow: Flow<Int?> = prefsFlow.map { it[LAST_PRIMARY_ARGB] }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setAppFont(font: AppFont) {
        dataStore.edit { it[APP_FONT] = font.name }
    }

    suspend fun setUserName(name: String) {
        dataStore.edit { it[USER_NAME] = name }
    }

    suspend fun setUserEmail(email: String) {
        dataStore.edit { it[USER_EMAIL] = email }
    }

    suspend fun setUserPhotoUri(uri: String?) {
        dataStore.edit {
            if (uri != null) it[USER_PHOTO_URI] = uri else it.remove(USER_PHOTO_URI)
        }
    }

    suspend fun setDefaultListId(id: String) {
        dataStore.edit { it[DEFAULT_LIST_ID] = id }
    }

    suspend fun setStartOfWeekSunday(sunday: Boolean) {
        dataStore.edit { it[START_OF_WEEK_SUNDAY] = sunday }
    }

    suspend fun setDefaultReminderTime(hour: Int, minute: Int) {
        dataStore.edit {
            it[DEFAULT_REMINDER_HOUR] = hour
            it[DEFAULT_REMINDER_MINUTE] = minute
        }
    }

    suspend fun setUiScale(scale: Float) {
        dataStore.edit { it[UI_SCALE] = scale }
    }

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        dataStore.edit { it[DYNAMIC_COLOR_ENABLED] = enabled }
    }

    suspend fun setPeopleFeatureEnabled(enabled: Boolean) {
        dataStore.edit { it[PEOPLE_FEATURE_ENABLED] = enabled }
    }

    suspend fun setTagsFeatureEnabled(enabled: Boolean) {
        dataStore.edit { it[TAGS_FEATURE_ENABLED] = enabled }
    }

    suspend fun setProjectsFeatureEnabled(enabled: Boolean) {
        dataStore.edit { it[PROJECTS_FEATURE_ENABLED] = enabled }
    }

    suspend fun setCloudBackupEnabled(enabled: Boolean) {
        dataStore.edit { it[CLOUD_BACKUP_ENABLED] = enabled }
    }

    suspend fun setHideCompletedToday(hide: Boolean) {
        dataStore.edit { it[HIDE_COMPLETED_TODAY] = hide }
    }

    suspend fun setHideCompletedProject(hide: Boolean) {
        dataStore.edit { it[HIDE_COMPLETED_PROJECT] = hide }
    }

    suspend fun setHideCompletedList(hide: Boolean) {
        dataStore.edit { it[HIDE_COMPLETED_LIST] = hide }
    }

    suspend fun setHideCompletedPerson(hide: Boolean) {
        dataStore.edit { it[HIDE_COMPLETED_PERSON] = hide }
    }

    suspend fun setSortModeToday(mode: TaskSortMode) {
        dataStore.edit { it[SORT_MODE_TODAY] = mode.name }
    }

    suspend fun setSortModeProject(mode: TaskSortMode) {
        dataStore.edit { it[SORT_MODE_PROJECT] = mode.name }
    }

    suspend fun setSortModeList(mode: TaskSortMode) {
        dataStore.edit { it[SORT_MODE_LIST] = mode.name }
    }

    suspend fun setSortModePerson(mode: TaskSortMode) {
        dataStore.edit { it[SORT_MODE_PERSON] = mode.name }
    }

    suspend fun setDefaultDueDate(mode: DefaultDueDate) {
        dataStore.edit { it[DEFAULT_DUE_DATE] = mode.name }
    }

    suspend fun setDefaultPriority(priority: String) {
        dataStore.edit { it[DEFAULT_PRIORITY] = priority }
    }

    suspend fun setDailyAgendaEnabled(enabled: Boolean) {
        dataStore.edit { it[DAILY_AGENDA_ENABLED] = enabled }
    }

    suspend fun setDailyAgendaTime(hour: Int, minute: Int) {
        dataStore.edit { it[DAILY_AGENDA_HOUR] = hour; it[DAILY_AGENDA_MINUTE] = minute }
    }

    suspend fun setUndoWindowSeconds(seconds: Int) {
        dataStore.edit { it[UNDO_WINDOW_SECONDS] = seconds }
    }

    suspend fun setSnoozeTonightTime(hour: Int, minute: Int) {
        dataStore.edit { it[SNOOZE_TONIGHT_HOUR] = hour; it[SNOOZE_TONIGHT_MINUTE] = minute }
    }

    suspend fun setSnoozeTomorrowTime(hour: Int, minute: Int) {
        dataStore.edit { it[SNOOZE_TOMORROW_HOUR] = hour; it[SNOOZE_TOMORROW_MINUTE] = minute }
    }

    suspend fun setOverdueNudgesEnabled(enabled: Boolean) {
        dataStore.edit { it[OVERDUE_NUDGES_ENABLED] = enabled }
    }

    suspend fun setAutoArchiveDays(days: Int) {
        dataStore.edit { it[AUTO_ARCHIVE_DAYS] = days }
    }

    suspend fun setTrashRetentionDays(days: Int) {
        dataStore.edit { it[TRASH_RETENTION_DAYS] = days }
    }

    suspend fun setSortModeTagDetail(mode: TaskSortMode) {
        dataStore.edit { it[SORT_MODE_TAG_DETAIL] = mode.name }
    }

    suspend fun setSortModeTagsTab(mode: EntitySortMode) {
        dataStore.edit { it[SORT_MODE_TAGS_TAB] = mode.name }
    }

    suspend fun setSortModePeopleTab(mode: EntitySortMode) {
        dataStore.edit { it[SORT_MODE_PEOPLE_TAB] = mode.name }
    }

    suspend fun setLastHomeTab(tab: Int) {
        dataStore.edit { it[LAST_HOME_TAB] = tab.coerceIn(0, 4) }
    }

    suspend fun setHasSeenWelcome(seen: Boolean) {
        dataStore.edit { it[HAS_SEEN_WELCOME] = seen }
    }

    suspend fun setLastPrimaryArgb(argb: Int) {
        dataStore.edit { it[LAST_PRIMARY_ARGB] = argb }
    }

    suspend fun addSavedSmartFilterSet(encodedFilters: String) {
        if (encodedFilters.isBlank()) return
        dataStore.edit { prefs ->
            prefs[SAVED_SMART_FILTER_SETS] = (prefs[SAVED_SMART_FILTER_SETS] ?: emptySet()) + encodedFilters
        }
    }

    suspend fun removeSavedSmartFilterSet(encodedFilters: String) {
        dataStore.edit { prefs ->
            val updated = (prefs[SAVED_SMART_FILTER_SETS] ?: emptySet()) - encodedFilters
            if (updated.isEmpty()) {
                prefs.remove(SAVED_SMART_FILTER_SETS)
            } else {
                prefs[SAVED_SMART_FILTER_SETS] = updated
            }
        }
    }

    suspend fun recordRecentTask(id: String) {
        if (id.isBlank()) return
        dataStore.edit { prefs ->
            val existing = prefs[RECENT_TASK_IDS]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            prefs[RECENT_TASK_IDS] = (listOf(id) + existing.filterNot { it == id }).take(8).joinToString(",")
        }
    }

    suspend fun setCloudBackupAccountEmail(email: String?) {
        dataStore.edit {
            if (email != null) it[CLOUD_BACKUP_ACCOUNT] = email else it.remove(CLOUD_BACKUP_ACCOUNT)
        }
    }

    suspend fun setCloudBackupLastAt(epochMillis: Long) {
        dataStore.edit { it[CLOUD_BACKUP_LAST_AT] = epochMillis }
    }

    suspend fun setCloudBackupWifiOnly(wifiOnly: Boolean) {
        dataStore.edit { it[CLOUD_BACKUP_WIFI_ONLY] = wifiOnly }
    }

    suspend fun setCloudBackupIntervalMinutes(minutes: Long) {
        dataStore.edit { it[CLOUD_BACKUP_INTERVAL_MINUTES] = minutes.coerceAtLeast(15L) }
    }

    suspend fun setCloudBackupArchiveMonths(months: Int) {
        dataStore.edit { it[CLOUD_BACKUP_ARCHIVE_MONTHS] = months.coerceAtLeast(0) }
    }

    suspend fun setLocalBackupEnabled(enabled: Boolean) {
        dataStore.edit { it[LOCAL_BACKUP_ENABLED] = enabled }
    }

    suspend fun setLocalBackupLastAt(epochMillis: Long) {
        dataStore.edit { it[LOCAL_BACKUP_LAST_AT] = epochMillis }
    }

    suspend fun setLocalBackupIntervalMinutes(minutes: Long) {
        dataStore.edit { it[LOCAL_BACKUP_INTERVAL_MINUTES] = minutes.coerceAtLeast(15L) }
    }

    suspend fun setReduceMotionEnabled(enabled: Boolean) {
        dataStore.edit { it[REDUCE_MOTION_ENABLED] = enabled }
    }

    suspend fun setEnhancedM3ThemingEnabled(enabled: Boolean) {
        dataStore.edit { it[ENHANCED_M3_THEMING_ENABLED] = enabled }
    }

    suspend fun setFloatingBottomNavEnabled(enabled: Boolean) {
        dataStore.edit { it[FLOATING_BOTTOM_NAV_ENABLED] = enabled }
    }

    suspend fun setBottomNavLabelsEnabled(enabled: Boolean) {
        dataStore.edit { it[BOTTOM_NAV_LABELS_ENABLED] = enabled }
    }

    suspend fun setDemoModeEnabled(enabled: Boolean) {
        dataStore.edit { it[DEMO_MODE_ENABLED] = enabled }
    }

    suspend fun setCustomThemeSeedColor(argb: Int?) {
        dataStore.edit {
            if (argb == null) it.remove(CUSTOM_THEME_SEED_COLOR) else it[CUSTOM_THEME_SEED_COLOR] = argb
        }
    }

    suspend fun setCompletionSoundEnabled(enabled: Boolean) {
        dataStore.edit { it[COMPLETION_SOUND_ENABLED] = enabled }
    }

    suspend fun setVoiceRecognitionLanguage(lang: String) {
        dataStore.edit { it[VOICE_RECOGNITION_LANGUAGE] = lang }
    }

    suspend fun setTextScale(scale: Float) {
        dataStore.edit { it[TEXT_SCALE] = scale }
    }

    suspend fun setTaskRowDensity(density: com.mj.yata.domain.model.TaskRowDensity) {
        dataStore.edit { it[TASK_ROW_DENSITY] = density.name }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        dataStore.edit { it[HAPTICS_ENABLED] = enabled }
    }

    suspend fun setTaskSwipeActionsEnabled(enabled: Boolean) {
        dataStore.edit { it[TASK_SWIPE_ACTIONS_ENABLED] = enabled }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        dataStore.edit { it[APP_LOCK_ENABLED] = enabled }
    }

    suspend fun setAppLockTimeoutMinutes(minutes: Int) {
        dataStore.edit { it[APP_LOCK_TIMEOUT_MINUTES] = minutes }
    }

    /** [pin] null or blank clears the PIN (removes both keys); otherwise generates a fresh salt
     * and stores only the salted hash, never the plaintext PIN. */
    suspend fun setAppLockPin(pin: String?) {
        if (pin.isNullOrBlank()) {
            dataStore.edit {
                it.remove(APP_LOCK_PIN_SALT)
                it.remove(APP_LOCK_PIN_HASH)
            }
        } else {
            val salt = generateSalt()
            dataStore.edit {
                it[APP_LOCK_PIN_SALT] = encodeSalt(salt)
                it[APP_LOCK_PIN_HASH] = hashPin(pin, salt)
            }
        }
    }

    suspend fun verifyAppLockPin(pin: String): Boolean {
        val prefs = prefsFlow.first()
        val saltEncoded = prefs[APP_LOCK_PIN_SALT] ?: return false
        val storedHash = prefs[APP_LOCK_PIN_HASH] ?: return false
        return hashPin(pin, decodeSalt(saltEncoded)) == storedHash
    }

    suspend fun setTodayTabEnabled(enabled: Boolean) {
        dataStore.edit { it[TODAY_TAB_ENABLED] = enabled }
    }

    suspend fun setUpcomingTabEnabled(enabled: Boolean) {
        dataStore.edit { it[UPCOMING_TAB_ENABLED] = enabled }
    }

    suspend fun setFabPosition(position: com.mj.yata.domain.model.FabPosition) {
        dataStore.edit { it[FAB_POSITION] = position.name }
    }

    suspend fun setThemeSchedule(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        dataStore.edit {
            it[THEME_SCHEDULE_START_HOUR] = startHour
            it[THEME_SCHEDULE_START_MINUTE] = startMinute
            it[THEME_SCHEDULE_END_HOUR] = endHour
            it[THEME_SCHEDULE_END_MINUTE] = endMinute
        }
    }

    /** Restores user-facing behavior defaults while preserving profile identity, app-lock
     * credentials, backup accounts/history, saved searches, and all task data. */
    suspend fun resetAppSettings() {
        dataStore.edit { prefs ->
            prefs.remove(THEME_MODE); prefs.remove(APP_FONT); prefs.remove(DEFAULT_LIST_ID)
            prefs.remove(START_OF_WEEK_SUNDAY); prefs.remove(DEFAULT_REMINDER_HOUR); prefs.remove(DEFAULT_REMINDER_MINUTE)
            prefs.remove(UI_SCALE); prefs.remove(TEXT_SCALE); prefs.remove(DYNAMIC_COLOR_ENABLED)
            prefs.remove(CUSTOM_THEME_SEED_COLOR); prefs.remove(REDUCE_MOTION_ENABLED); prefs.remove(ENHANCED_M3_THEMING_ENABLED)
            prefs.remove(FLOATING_BOTTOM_NAV_ENABLED); prefs.remove(BOTTOM_NAV_LABELS_ENABLED); prefs.remove(COMPLETION_SOUND_ENABLED)
            prefs.remove(HAPTICS_ENABLED); prefs.remove(TASK_SWIPE_ACTIONS_ENABLED); prefs.remove(TASK_ROW_DENSITY)
            prefs.remove(TODAY_TAB_ENABLED); prefs.remove(UPCOMING_TAB_ENABLED); prefs.remove(FAB_POSITION)
            prefs.remove(DEFAULT_DUE_DATE); prefs.remove(DEFAULT_PRIORITY); prefs.remove(DAILY_AGENDA_ENABLED)
            prefs.remove(DAILY_AGENDA_HOUR); prefs.remove(DAILY_AGENDA_MINUTE); prefs.remove(OVERDUE_NUDGES_ENABLED)
            prefs.remove(UNDO_WINDOW_SECONDS); prefs.remove(TRASH_RETENTION_DAYS); prefs.remove(AUTO_ARCHIVE_DAYS)
            prefs.remove(SNOOZE_TONIGHT_HOUR); prefs.remove(SNOOZE_TONIGHT_MINUTE)
            prefs.remove(SNOOZE_TOMORROW_HOUR); prefs.remove(SNOOZE_TOMORROW_MINUTE)
            prefs.remove(THEME_SCHEDULE_START_HOUR); prefs.remove(THEME_SCHEDULE_START_MINUTE)
            prefs.remove(THEME_SCHEDULE_END_HOUR); prefs.remove(THEME_SCHEDULE_END_MINUTE)
            prefs.remove(VOICE_RECOGNITION_LANGUAGE)
        }
    }
}
