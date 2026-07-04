package com.mj.yata.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
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
        val USER_NAME               = stringPreferencesKey("user_name")
        val USER_EMAIL              = stringPreferencesKey("user_email")
        val DEFAULT_LIST_ID         = stringPreferencesKey("default_list_id")
        val START_OF_WEEK_SUNDAY    = booleanPreferencesKey("start_of_week_sunday")
        val DEFAULT_REMINDER_HOUR   = intPreferencesKey("default_reminder_hour")
        val DEFAULT_REMINDER_MINUTE = intPreferencesKey("default_reminder_minute")
    }

    val themeModeFlow: Flow<ThemeMode> = dataStore.data.map { prefs ->
        when (prefs[THEME_MODE]) {
            ThemeMode.LIGHT.name -> ThemeMode.LIGHT
            ThemeMode.DARK.name  -> ThemeMode.DARK
            else                 -> ThemeMode.SYSTEM
        }
    }

    val userNameFlow: Flow<String> = dataStore.data.map { it[USER_NAME] ?: "" }
    val userEmailFlow: Flow<String> = dataStore.data.map { it[USER_EMAIL] ?: "" }

    override val defaultListIdFlow: Flow<String> = dataStore.data.map { it[DEFAULT_LIST_ID] ?: "" }

    val startOfWeekSundayFlow: Flow<Boolean> = dataStore.data.map { it[START_OF_WEEK_SUNDAY] ?: true }
    val defaultReminderHourFlow: Flow<Int> = dataStore.data.map { it[DEFAULT_REMINDER_HOUR] ?: 9 }
    val defaultReminderMinuteFlow: Flow<Int> = dataStore.data.map { it[DEFAULT_REMINDER_MINUTE] ?: 0 }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setUserName(name: String) {
        dataStore.edit { it[USER_NAME] = name }
    }

    suspend fun setUserEmail(email: String) {
        dataStore.edit { it[USER_EMAIL] = email }
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
}
