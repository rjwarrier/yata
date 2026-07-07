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
    }

    val themeModeFlow: Flow<ThemeMode> = dataStore.data.map { prefs ->
        when (prefs[THEME_MODE]) {
            ThemeMode.LIGHT.name -> ThemeMode.LIGHT
            ThemeMode.DARK.name  -> ThemeMode.DARK
            else                 -> ThemeMode.SYSTEM
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
}
