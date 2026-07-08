package com.mj.yata.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.mj.yata.domain.model.AppFont
import com.mj.yata.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface TaskListPreferences {
    val defaultListIdFlow: Flow<String>
}

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext context: Context
) : TaskListPreferences {

    private val dataStore = context.dataStore

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
        val THEME_SCHEDULE_START_HOUR   = intPreferencesKey("theme_schedule_start_hour")
        val THEME_SCHEDULE_START_MINUTE = intPreferencesKey("theme_schedule_start_minute")
        val THEME_SCHEDULE_END_HOUR     = intPreferencesKey("theme_schedule_end_hour")
        val THEME_SCHEDULE_END_MINUTE   = intPreferencesKey("theme_schedule_end_minute")
        val REDUCE_MOTION_ENABLED   = booleanPreferencesKey("reduce_motion_enabled")
        val TEXT_SCALE              = floatPreferencesKey("text_scale")
        val TASK_ROW_DENSITY        = stringPreferencesKey("task_row_density")
        val HAPTICS_ENABLED         = booleanPreferencesKey("haptics_enabled")
        val TODAY_TAB_ENABLED       = booleanPreferencesKey("today_tab_enabled")
        val UPCOMING_TAB_ENABLED    = booleanPreferencesKey("upcoming_tab_enabled")
        val FAB_POSITION            = stringPreferencesKey("fab_position")
        val HIDE_COMPLETED_TODAY    = booleanPreferencesKey("hide_completed_today")
        val HIDE_COMPLETED_PROJECT  = booleanPreferencesKey("hide_completed_project")
        val HIDE_COMPLETED_LIST     = booleanPreferencesKey("hide_completed_list")
        val HIDE_COMPLETED_PERSON   = booleanPreferencesKey("hide_completed_person")
    }

    val themeModeFlow: Flow<ThemeMode> = dataStore.data.map { prefs ->
        when (prefs[THEME_MODE]) {
            ThemeMode.LIGHT.name     -> ThemeMode.LIGHT
            ThemeMode.DARK.name      -> ThemeMode.DARK
            ThemeMode.SCHEDULED.name -> ThemeMode.SCHEDULED
            else                     -> ThemeMode.SYSTEM
        }
    }

    // Dark from 9pm to 7am by default.
    val themeScheduleStartHourFlow: Flow<Int> = dataStore.data.map { it[THEME_SCHEDULE_START_HOUR] ?: 21 }
    val themeScheduleStartMinuteFlow: Flow<Int> = dataStore.data.map { it[THEME_SCHEDULE_START_MINUTE] ?: 0 }
    val themeScheduleEndHourFlow: Flow<Int> = dataStore.data.map { it[THEME_SCHEDULE_END_HOUR] ?: 7 }
    val themeScheduleEndMinuteFlow: Flow<Int> = dataStore.data.map { it[THEME_SCHEDULE_END_MINUTE] ?: 0 }

    val reduceMotionEnabledFlow: Flow<Boolean> = dataStore.data.map { it[REDUCE_MOTION_ENABLED] ?: false }
    val textScaleFlow: Flow<Float> = dataStore.data.map { it[TEXT_SCALE] ?: 1.0f }
    val taskRowDensityFlow: Flow<com.mj.yata.domain.model.TaskRowDensity> = dataStore.data.map { prefs ->
        when (prefs[TASK_ROW_DENSITY]) {
            com.mj.yata.domain.model.TaskRowDensity.COMPACT.name -> com.mj.yata.domain.model.TaskRowDensity.COMPACT
            com.mj.yata.domain.model.TaskRowDensity.SPACIOUS.name -> com.mj.yata.domain.model.TaskRowDensity.SPACIOUS
            else -> com.mj.yata.domain.model.TaskRowDensity.COMFORTABLE
        }
    }
    val hapticsEnabledFlow: Flow<Boolean> = dataStore.data.map { it[HAPTICS_ENABLED] ?: true }
    val todayTabEnabledFlow: Flow<Boolean> = dataStore.data.map { it[TODAY_TAB_ENABLED] ?: true }
    val upcomingTabEnabledFlow: Flow<Boolean> = dataStore.data.map { it[UPCOMING_TAB_ENABLED] ?: true }
    val fabPositionFlow: Flow<com.mj.yata.domain.model.FabPosition> = dataStore.data.map { prefs ->
        when (prefs[FAB_POSITION]) {
            com.mj.yata.domain.model.FabPosition.LEFT.name -> com.mj.yata.domain.model.FabPosition.LEFT
            com.mj.yata.domain.model.FabPosition.HIDDEN.name -> com.mj.yata.domain.model.FabPosition.HIDDEN
            else -> com.mj.yata.domain.model.FabPosition.RIGHT
        }
    }

    val appFontFlow: Flow<AppFont> = dataStore.data.map { prefs ->
        when (prefs[APP_FONT]) {
            AppFont.JETBRAINS_MONO.name -> AppFont.JETBRAINS_MONO
            else                        -> AppFont.INTER
        }
    }

    val userNameFlow: Flow<String> = dataStore.data.map { it[USER_NAME] ?: "" }
    val userEmailFlow: Flow<String> = dataStore.data.map { it[USER_EMAIL] ?: "" }
    val userPhotoUriFlow: Flow<String?> = dataStore.data.map { it[USER_PHOTO_URI] }

    override val defaultListIdFlow: Flow<String> = dataStore.data.map { it[DEFAULT_LIST_ID] ?: "" }

    val startOfWeekSundayFlow: Flow<Boolean> = dataStore.data.map { it[START_OF_WEEK_SUNDAY] ?: true }
    val defaultReminderHourFlow: Flow<Int> = dataStore.data.map { it[DEFAULT_REMINDER_HOUR] ?: 9 }
    val defaultReminderMinuteFlow: Flow<Int> = dataStore.data.map { it[DEFAULT_REMINDER_MINUTE] ?: 0 }
    val uiScaleFlow: Flow<Float> = dataStore.data.map { it[UI_SCALE] ?: 1.0f }
    val dynamicColorEnabledFlow: Flow<Boolean> = dataStore.data.map { it[DYNAMIC_COLOR_ENABLED] ?: true }
    val peopleFeatureEnabledFlow: Flow<Boolean> = dataStore.data.map { it[PEOPLE_FEATURE_ENABLED] ?: true }
    val tagsFeatureEnabledFlow: Flow<Boolean> = dataStore.data.map { it[TAGS_FEATURE_ENABLED] ?: true }
    val projectsFeatureEnabledFlow: Flow<Boolean> = dataStore.data.map { it[PROJECTS_FEATURE_ENABLED] ?: true }
    val cloudBackupEnabledFlow: Flow<Boolean> = dataStore.data.map { it[CLOUD_BACKUP_ENABLED] ?: false }
    val cloudBackupAccountEmailFlow: Flow<String?> = dataStore.data.map { it[CLOUD_BACKUP_ACCOUNT] }
    val cloudBackupLastAtFlow: Flow<Long?> = dataStore.data.map { it[CLOUD_BACKUP_LAST_AT] }
    val cloudBackupWifiOnlyFlow: Flow<Boolean> = dataStore.data.map { it[CLOUD_BACKUP_WIFI_ONLY] ?: true }
    // Default matches CloudBackupWorker's default schedule (1 day) — WorkManager enforces a
    // 15-minute floor on periodic work, so this is clamped the same way on write.
    val cloudBackupIntervalMinutesFlow: Flow<Long> = dataStore.data.map { it[CLOUD_BACKUP_INTERVAL_MINUTES] ?: (24 * 60L) }
    val hideCompletedTodayFlow: Flow<Boolean> = dataStore.data.map { it[HIDE_COMPLETED_TODAY] ?: false }
    val hideCompletedProjectFlow: Flow<Boolean> = dataStore.data.map { it[HIDE_COMPLETED_PROJECT] ?: false }
    val hideCompletedListFlow: Flow<Boolean> = dataStore.data.map { it[HIDE_COMPLETED_LIST] ?: false }
    val hideCompletedPersonFlow: Flow<Boolean> = dataStore.data.map { it[HIDE_COMPLETED_PERSON] ?: false }

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

    suspend fun setReduceMotionEnabled(enabled: Boolean) {
        dataStore.edit { it[REDUCE_MOTION_ENABLED] = enabled }
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
}
