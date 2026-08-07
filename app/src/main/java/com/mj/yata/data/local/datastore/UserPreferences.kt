package com.mj.yata.data.local.datastore

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.mj.yata.domain.model.AppFont
import com.mj.yata.domain.model.BackgroundTint
import com.mj.yata.domain.model.ColorIntensity
import com.mj.yata.domain.model.DateAliasDefinition
import com.mj.yata.domain.model.DefaultDueDate
import com.mj.yata.domain.model.MotionMode
import com.mj.yata.domain.model.SavedThemePreset
import com.mj.yata.domain.model.ThemeMode
import com.mj.yata.util.decodeSalt
import com.mj.yata.util.encodeSalt
import com.mj.yata.util.EntitySortMode
import com.mj.yata.util.TaskSortMode
import com.mj.yata.util.generateSalt
import com.mj.yata.util.hashPin
import com.mj.yata.util.needsRehash
import com.mj.yata.util.verifyPin
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

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_settings",
    corruptionHandler = ReplaceFileCorruptionHandler {
        Log.e("UserPreferences", "Preferences file was corrupt; replacing with defaults", it)
        emptyPreferences()
    }
)

data class UserPreferencesSnapshot(
    val themeMode: ThemeMode,
    val dynamicColorEnabled: Boolean
)

/**
 * One preference, in a form a backup file can carry. The type tag travels with the value because
 * DataStore keys are typed: rebuilding `booleanPreferencesKey("x")` vs `intPreferencesKey("x")` on
 * restore needs to know which, and JSON alone can't distinguish Int from Long from Float.
 */
data class PortableSetting(val name: String, val type: String, val value: Any)

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
            dynamicColorEnabled = prefs[DYNAMIC_COLOR_ENABLED] ?: true
        )
    }

    companion object {
        /**
         * Preferences deliberately left out of a backup, by key name.
         *
         * - The app-lock PIN hash and its salt: a backup file is not the place for an
         *   authentication credential, especially when it can travel off-device. Restoring leaves
         *   app lock off, to be set again.
         * - Legacy cloud account values: old OAuth grants were per-device, so restored values would
         *   name an account the install could not actually use.
         * - The cached primary colour: derived from the live theme on every start.
         * - The profile photo Uri: an absolute path into *this* install's filesDir, meaningless in
         *   another. `JsonExporter` carries the photo bytes and rewrites the Uri itself.
         */
        val NON_PORTABLE_KEYS = setOf(
            "app_lock_pin_hash",
            "app_lock_pin_salt",
            "cloud_backup_enabled",
            "cloud_backup_account_email",
            "cloud_backup_last_at",
            "cloud_backup_wifi_only",
            "cloud_backup_interval_minutes",
            "cloud_backup_archive_months",
            "cloud_backup_keep_count",
            "last_primary_argb",
            "user_photo_uri"
        )

        val AUTO_ASSIGN_TO_ME       = booleanPreferencesKey("auto_assign_to_me")
        val THEME_MODE              = stringPreferencesKey("theme_mode")
        val APP_FONT                = stringPreferencesKey("app_font")
        val COLOR_INTENSITY         = stringPreferencesKey("color_intensity")
        val BACKGROUND_TINT         = stringPreferencesKey("background_tint")
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
        val CLOUD_BACKUP_INTERVAL_MINUTES = longPreferencesKey("cloud_backup_interval_minutes")
        // Legacy Drive-era keys are kept only so snapshot export/import can ignore them and old
        // installs can carry their chosen cadence forward.
        val LOCAL_BACKUP_ENABLED    = booleanPreferencesKey("local_backup_enabled")
        val LOCAL_BACKUP_LAST_AT    = longPreferencesKey("local_backup_last_at")
        val LOCAL_BACKUP_INTERVAL_MINUTES = longPreferencesKey("local_backup_interval_minutes")
        // Self-hosted backup (SFTP or FTP/FTPS) -- key names keep the "sftp" prefix from when
        // this was SFTP-only, but enabled/host/port/username/remoteDir/interval/lastBackupAt are
        // now shared by both protocols: a self-hosted user has one server, not two, and the
        // REMOTE_BACKUP_PROTOCOL choice below just picks which transport talks to it. authMethod
        // and hostKeyFingerprint stay SFTP-only -- FTP has no key-based auth and no host-key
        // concept. Only non-secret config lives here -- password/private key/passphrase are in
        // RemoteBackupCredentialsStore's EncryptedSharedPreferences instead, never in plain
        // DataStore. hostKeyFingerprint is TOFU-pinned on first successful SFTP connection (not
        // itself sensitive -- it's the server's public key fingerprint) and every later
        // connection must match it exactly or the connection is refused.
        val SFTP_BACKUP_ENABLED     = booleanPreferencesKey("sftp_backup_enabled")
        val SFTP_HOST               = stringPreferencesKey("sftp_host")
        val SFTP_PORT               = intPreferencesKey("sftp_port")
        val SFTP_USERNAME           = stringPreferencesKey("sftp_username")
        val SFTP_AUTH_METHOD        = stringPreferencesKey("sftp_auth_method")
        val SFTP_REMOTE_DIR         = stringPreferencesKey("sftp_remote_dir")
        val SFTP_INTERVAL_MINUTES   = longPreferencesKey("sftp_interval_minutes")
        val SFTP_LAST_BACKUP_AT     = longPreferencesKey("sftp_last_backup_at")
        val SFTP_KEEP_COUNT         = intPreferencesKey("sftp_keep_count")
        val BACKUP_INTERVAL_MINUTES = longPreferencesKey("backup_interval_minutes")
        val SFTP_HOST_KEY_FINGERPRINT = stringPreferencesKey("sftp_host_key_fingerprint")
        val REMOTE_BACKUP_PROTOCOL  = stringPreferencesKey("remote_backup_protocol")
        // Plain FTP sends the password and the whole backup in the clear -- defaults to true
        // (FTPS, explicit AUTH TLS) and is only ever false if the user deliberately opts out,
        // which the config dialog makes an explicit, warned choice rather than a quiet toggle.
        val FTP_USE_TLS             = booleanPreferencesKey("ftp_use_tls")
        val GITHUB_OWNER            = stringPreferencesKey("github_owner")
        val GITHUB_REPO             = stringPreferencesKey("github_repo")
        val GITHUB_BRANCH           = stringPreferencesKey("github_branch")
        val GITHUB_API_BASE         = stringPreferencesKey("github_api_base")
        val GITHUB_TOKEN_EXPIRES_AT = longPreferencesKey("github_token_expires_at")
        val GITHUB_LAST_HEAD_SHA    = stringPreferencesKey("github_last_head_sha")
        // The theme_schedule_* keys that lived here went with the SCHEDULED theme mode. Any values
        // already written stay in the file harmlessly — nothing reads that name any more.
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
        val SWIPE_RIGHT_ACTION      = stringPreferencesKey("swipe_right_action")
        val SWIPE_LEFT_ACTION       = stringPreferencesKey("swipe_left_action")
        val STARTUP_TAB             = stringPreferencesKey("startup_tab")
        val CONFETTI_ENABLED        = booleanPreferencesKey("confetti_enabled")
        val TIME_FORMAT             = stringPreferencesKey("time_format")
        val DATE_FORMAT             = stringPreferencesKey("date_format")
        val TASK_CARD_BACKGROUND    = booleanPreferencesKey("task_card_background")
        val APP_LOCK_ENABLED        = booleanPreferencesKey("app_lock_enabled")
        val APP_LOCK_PIN_SALT       = stringPreferencesKey("app_lock_pin_salt")
        val APP_LOCK_PIN_HASH       = stringPreferencesKey("app_lock_pin_hash")
        val APP_LOCK_TIMEOUT_MINUTES = intPreferencesKey("app_lock_timeout_minutes")
        val APP_LOCK_PIN_LENGTH     = intPreferencesKey("app_lock_pin_length")
        val APP_LOCK_FAILED_ATTEMPTS = intPreferencesKey("app_lock_failed_attempts")
        val APP_LOCK_LOCKED_UNTIL   = longPreferencesKey("app_lock_locked_until")
        val TODAY_TAB_ENABLED       = booleanPreferencesKey("today_tab_enabled")
        val UPCOMING_TAB_ENABLED    = booleanPreferencesKey("upcoming_tab_enabled")
        val FAB_POSITION            = stringPreferencesKey("fab_position")
        val HIDE_COMPLETED_TODAY    = booleanPreferencesKey("hide_completed_today")
        val TODAY_SHOW_UPCOMING_WHEN_EMPTY = booleanPreferencesKey("today_show_upcoming_when_empty")
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
        val MOTION_MODE           = stringPreferencesKey("motion_mode")
        val DATE_ALIASES          = stringSetPreferencesKey("date_aliases")
        val SAVED_THEME_PRESETS   = stringSetPreferencesKey("saved_theme_presets")
        val TASKER_INTEGRATION_ENABLED = booleanPreferencesKey("tasker_integration_enabled")
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

    val motionModeFlow: Flow<MotionMode> = prefsFlow.map { prefs ->
        MotionMode.entries.firstOrNull { it.name == prefs[MOTION_MODE] }
            ?: if (prefs[REDUCE_MOTION_ENABLED] == true) MotionMode.REDUCED else MotionMode.FULL
    }
    val reduceMotionEnabledFlow: Flow<Boolean> = prefsFlow.map { prefs ->
        val mode = MotionMode.entries.firstOrNull { it.name == prefs[MOTION_MODE] }
        when (mode) {
            MotionMode.REDUCED, MotionMode.OFF -> true
            MotionMode.FULL -> false
            null -> prefs[REDUCE_MOTION_ENABLED] ?: false
        }
    }
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
    // Defaults reproduce the behaviour from before the directions were configurable.
    val swipeRightActionFlow: Flow<com.mj.yata.domain.model.SwipeAction> = prefsFlow.map { prefs ->
        com.mj.yata.domain.model.SwipeAction.entries.firstOrNull { it.name == prefs[SWIPE_RIGHT_ACTION] }
            ?: com.mj.yata.domain.model.SwipeAction.COMPLETE
    }
    val swipeLeftActionFlow: Flow<com.mj.yata.domain.model.SwipeAction> = prefsFlow.map { prefs ->
        com.mj.yata.domain.model.SwipeAction.entries.firstOrNull { it.name == prefs[SWIPE_LEFT_ACTION] }
            ?: com.mj.yata.domain.model.SwipeAction.DELETE
    }
    val startupTabFlow: Flow<com.mj.yata.domain.model.StartupTab> = prefsFlow.map { prefs ->
        com.mj.yata.domain.model.StartupTab.entries.firstOrNull { it.name == prefs[STARTUP_TAB] }
            ?: com.mj.yata.domain.model.StartupTab.LAST_USED
    }
    // Separate from Reduce Motion on purpose: turning the confetti off shouldn't cost you every
    // other animation in the app, which is the only way it could be done before.
    val confettiEnabledFlow: Flow<Boolean> = prefsFlow.map { it[CONFETTI_ENABLED] ?: true }
    val timeFormatFlow: Flow<com.mj.yata.domain.model.TimeFormat> = prefsFlow.map { prefs ->
        com.mj.yata.domain.model.TimeFormat.entries.firstOrNull { it.name == prefs[TIME_FORMAT] }
            ?: com.mj.yata.domain.model.TimeFormat.SYSTEM
    }
    val dateFormatFlow: Flow<com.mj.yata.domain.model.DateFormat> = prefsFlow.map { prefs ->
        com.mj.yata.domain.model.DateFormat.entries.firstOrNull { it.name == prefs[DATE_FORMAT] }
            ?: com.mj.yata.domain.model.DateFormat.SYSTEM
    }
    val dateAliasDefinitionsFlow: Flow<Set<String>> = prefsFlow.map { it[DATE_ALIASES] ?: emptySet() }
    val savedThemePresetsFlow: Flow<Set<String>> = prefsFlow.map { it[SAVED_THEME_PRESETS] ?: emptySet() }
    val taskerIntegrationEnabledFlow: Flow<Boolean> = prefsFlow.map { it[TASKER_INTEGRATION_ENABLED] ?: true }

    // Off by default: the flat list is the app's existing look, and this changes every task list
    // at once, so it has to be something a user opts into rather than finds applied after update.
    val taskCardBackgroundFlow: Flow<Boolean> = prefsFlow.map { it[TASK_CARD_BACKGROUND] ?: false }
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

    // Both default to their no-op stop, so an existing install looks identical until the slider
    // is actually moved. Unknown names fall back the same way every other enum preference here
    // does, rather than throwing on a value written by a newer build.
    val colorIntensityFlow: Flow<ColorIntensity> = prefsFlow.map { prefs ->
        ColorIntensity.entries.firstOrNull { it.name == prefs[COLOR_INTENSITY] } ?: ColorIntensity.NORMAL
    }

    val backgroundTintFlow: Flow<BackgroundTint> = prefsFlow.map { prefs ->
        BackgroundTint.entries.firstOrNull { it.name == prefs[BACKGROUND_TINT] } ?: BackgroundTint.SOFT
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
    // Default matches the old periodic backup schedule (1 day) — WorkManager enforces a
    // 15-minute floor on periodic work, so this is clamped the same way on write.
    val localBackupEnabledFlow: Flow<Boolean> = prefsFlow.map { it[LOCAL_BACKUP_ENABLED] ?: false }
    val localBackupLastAtFlow: Flow<Long?> = prefsFlow.map { it[LOCAL_BACKUP_LAST_AT] }
    val localBackupIntervalMinutesFlow: Flow<Long> = prefsFlow.map { it[LOCAL_BACKUP_INTERVAL_MINUTES] ?: (24 * 60L) }
    val sftpBackupEnabledFlow: Flow<Boolean> = prefsFlow.map { it[SFTP_BACKUP_ENABLED] ?: false }
    val sftpHostFlow: Flow<String> = prefsFlow.map { it[SFTP_HOST] ?: "" }
    val sftpPortFlow: Flow<Int> = prefsFlow.map { it[SFTP_PORT] ?: 22 }
    val sftpUsernameFlow: Flow<String> = prefsFlow.map { it[SFTP_USERNAME] ?: "" }
    val sftpAuthMethodFlow: Flow<String> = prefsFlow.map { it[SFTP_AUTH_METHOD]?.takeIf { m -> m == "PASSWORD" || m == "PRIVATE_KEY" } ?: "PASSWORD" }
    val sftpRemoteDirFlow: Flow<String> = prefsFlow.map { it[SFTP_REMOTE_DIR] ?: "/yata-backups" }
    val sftpIntervalMinutesFlow: Flow<Long> = prefsFlow.map { it[SFTP_INTERVAL_MINUTES] ?: (24 * 60L) }
    val sftpLastBackupAtFlow: Flow<Long?> = prefsFlow.map { it[SFTP_LAST_BACKUP_AT] }
    /** Shared by both self-hosted protocols. Clamped on
     * read so a corrupt or hand-edited value can't prune every backup off the server. */
    val sftpKeepCountFlow: Flow<Int> = prefsFlow.map { (it[SFTP_KEEP_COUNT] ?: 5).coerceIn(2, 15) }

    /**
     * How often the single scheduled backup runs, covering every enabled destination.
     *
     * Falls back to whatever the legacy remote schedule was set to before the schedules
     * were merged, so an existing user's backups carry on at the cadence they chose rather than
     * silently resetting to the default.
     */
    val backupIntervalMinutesFlow: Flow<Long> = prefsFlow.map { prefs ->
        prefs[BACKUP_INTERVAL_MINUTES]
            ?: prefs[CLOUD_BACKUP_INTERVAL_MINUTES]
            ?: (24 * 60L)
    }
    val sftpHostKeyFingerprintFlow: Flow<String?> = prefsFlow.map { it[SFTP_HOST_KEY_FINGERPRINT] }
    val remoteBackupProtocolFlow: Flow<com.mj.yata.domain.model.RemoteBackupProtocol> = prefsFlow.map { prefs ->
        when (prefs[REMOTE_BACKUP_PROTOCOL]) {
            com.mj.yata.domain.model.RemoteBackupProtocol.FTP.name -> com.mj.yata.domain.model.RemoteBackupProtocol.FTP
            com.mj.yata.domain.model.RemoteBackupProtocol.GITHUB.name -> com.mj.yata.domain.model.RemoteBackupProtocol.GITHUB
            else -> com.mj.yata.domain.model.RemoteBackupProtocol.SFTP
        }
    }
    val ftpUseTlsFlow: Flow<Boolean> = prefsFlow.map { it[FTP_USE_TLS] ?: true }
    val githubOwnerFlow: Flow<String> = prefsFlow.map { it[GITHUB_OWNER] ?: "" }
    val githubRepoFlow: Flow<String> = prefsFlow.map { it[GITHUB_REPO] ?: "" }
    val githubBranchFlow: Flow<String> = prefsFlow.map { it[GITHUB_BRANCH] ?: "" }
    val githubApiBaseFlow: Flow<String> = prefsFlow.map { it[GITHUB_API_BASE] ?: "https://api.github.com" }
    val githubTokenExpiresAtFlow: Flow<Long?> = prefsFlow.map { it[GITHUB_TOKEN_EXPIRES_AT] }
    val githubLastHeadShaFlow: Flow<String?> = prefsFlow.map { it[GITHUB_LAST_HEAD_SHA] }
    val hideCompletedTodayFlow: Flow<Boolean> = prefsFlow.map { it[HIDE_COMPLETED_TODAY] ?: false }
    val todayShowUpcomingWhenEmptyFlow: Flow<Boolean> = prefsFlow.map { it[TODAY_SHOW_UPCOMING_WHEN_EMPTY] ?: false }
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

    suspend fun setColorIntensity(intensity: ColorIntensity) {
        dataStore.edit { it[COLOR_INTENSITY] = intensity.name }
    }

    suspend fun setBackgroundTint(tint: BackgroundTint) {
        dataStore.edit { it[BACKGROUND_TINT] = tint.name }
    }

    /**
     * Every preference worth carrying to another install, read straight off the DataStore map
     * rather than from a hand-written list of the 74 keys. A per-key list would be correct today
     * and silently incomplete the first time someone adds a setting without remembering to add it
     * here — the whole point being that a restore shouldn't quietly drop half the user's config.
     *
     * See [NON_PORTABLE_KEYS] for the deliberate omissions.
     */
    suspend fun exportPortableSettings(): List<PortableSetting> {
        val prefs = prefsFlow.first()
        return prefs.asMap().mapNotNull { (key, value) ->
            if (key.name in NON_PORTABLE_KEYS) return@mapNotNull null
            val type = when (value) {
                is Boolean -> "bool"
                is Int -> "int"
                is Long -> "long"
                is Float -> "float"
                is Double -> "double"
                is String -> "string"
                is Set<*> -> "stringSet"
                else -> return@mapNotNull null
            }
            PortableSetting(key.name, type, value)
        }.sortedBy { it.name }
    }

    /**
     * Applies settings from a backup. Unknown names are written anyway — a key retired in a later
     * version costs nothing sitting unread in DataStore, whereas skipping unknown names would mean
     * a backup taken on a *newer* build silently loses settings when restored on an older one.
     *
     * Numbers are coerced through [Number] because JSON parsing collapses integral types: a Long
     * written as 1440 reads back as an Int, and a typed key would reject it.
     */
    suspend fun importPortableSettings(settings: List<PortableSetting>) {
        if (settings.isEmpty()) return
        dataStore.edit { prefs ->
            settings.forEach { setting ->
                if (setting.name in NON_PORTABLE_KEYS) return@forEach
                val v = setting.value
                when (setting.type) {
                    "bool" -> (v as? Boolean)?.let { prefs[booleanPreferencesKey(setting.name)] = it }
                    "int" -> (v as? Number)?.let { prefs[intPreferencesKey(setting.name)] = it.toInt() }
                    "long" -> (v as? Number)?.let { prefs[longPreferencesKey(setting.name)] = it.toLong() }
                    "float" -> (v as? Number)?.let { prefs[floatPreferencesKey(setting.name)] = it.toFloat() }
                    "double" -> (v as? Number)?.let { prefs[doublePreferencesKey(setting.name)] = it.toDouble() }
                    "string" -> (v as? String)?.let { prefs[stringPreferencesKey(setting.name)] = it }
                    "stringSet" -> {
                        @Suppress("UNCHECKED_CAST")
                        (v as? Collection<*>)?.let { raw ->
                            prefs[stringSetPreferencesKey(setting.name)] = raw.filterIsInstance<String>().toSet()
                        }
                    }
                }
            }
        }
    }

    /**
     * Replaces the portable portion of DataStore with [settings], while leaving explicitly
     * preserved and inherently non-portable keys untouched. Unlike [importPortableSettings], this
     * removes portable values that are absent from the incoming snapshot so a synchronized delete
     * does not leave stale device state behind.
     */
    suspend fun replacePortableSettings(
        settings: List<PortableSetting>,
        preservedNames: Set<String>,
        preservedPrefixes: Set<String>
    ) {
        val targetNames = settings.asSequence()
            .map { it.name }
            .filterNot { it in NON_PORTABLE_KEYS }
            .toSet()

        dataStore.edit { prefs ->
            prefs.asMap().toList().forEach { (storedKey, storedValue) ->
                val name = storedKey.name
                val preserved = name in NON_PORTABLE_KEYS ||
                    name in preservedNames ||
                    preservedPrefixes.any(name::startsWith)
                if (name !in targetNames && !preserved) {
                    when (storedValue) {
                        is Boolean -> prefs.remove(booleanPreferencesKey(name))
                        is Int -> prefs.remove(intPreferencesKey(name))
                        is Long -> prefs.remove(longPreferencesKey(name))
                        is Float -> prefs.remove(floatPreferencesKey(name))
                        is Double -> prefs.remove(doublePreferencesKey(name))
                        is String -> prefs.remove(stringPreferencesKey(name))
                        is Set<*> -> prefs.remove(stringSetPreferencesKey(name))
                    }
                }
            }

            settings.forEach { setting ->
                if (setting.name in NON_PORTABLE_KEYS) return@forEach
                val v = setting.value
                when (setting.type) {
                    "bool" -> (v as? Boolean)?.let { prefs[booleanPreferencesKey(setting.name)] = it }
                    "int" -> (v as? Number)?.let { prefs[intPreferencesKey(setting.name)] = it.toInt() }
                    "long" -> (v as? Number)?.let { prefs[longPreferencesKey(setting.name)] = it.toLong() }
                    "float" -> (v as? Number)?.let { prefs[floatPreferencesKey(setting.name)] = it.toFloat() }
                    "double" -> (v as? Number)?.let { prefs[doublePreferencesKey(setting.name)] = it.toDouble() }
                    "string" -> (v as? String)?.let { prefs[stringPreferencesKey(setting.name)] = it }
                    "stringSet" -> {
                        @Suppress("UNCHECKED_CAST")
                        (v as? Collection<*>)?.let { raw ->
                            prefs[stringSetPreferencesKey(setting.name)] = raw.filterIsInstance<String>().toSet()
                        }
                    }
                }
            }
        }
    }

    /** Default true: new tasks were unconditionally assigned to the user before this existed, so
     * anything else would silently change behaviour for everyone on upgrade. */
    val autoAssignToMeFlow: Flow<Boolean> = prefsFlow.map { it[AUTO_ASSIGN_TO_ME] ?: true }

    suspend fun setAutoAssignToMe(enabled: Boolean) {
        dataStore.edit { it[AUTO_ASSIGN_TO_ME] = enabled }
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

    suspend fun setHideCompletedToday(hide: Boolean) {
        dataStore.edit { it[HIDE_COMPLETED_TODAY] = hide }
    }

    suspend fun setTodayShowUpcomingWhenEmpty(enabled: Boolean) {
        dataStore.edit { it[TODAY_SHOW_UPCOMING_WHEN_EMPTY] = enabled }
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

    suspend fun setLocalBackupEnabled(enabled: Boolean) {
        dataStore.edit { it[LOCAL_BACKUP_ENABLED] = enabled }
    }

    suspend fun setLocalBackupLastAt(epochMillis: Long) {
        dataStore.edit { it[LOCAL_BACKUP_LAST_AT] = epochMillis }
    }

    suspend fun setLocalBackupIntervalMinutes(minutes: Long) {
        dataStore.edit { it[LOCAL_BACKUP_INTERVAL_MINUTES] = minutes.coerceAtLeast(15L) }
    }

    suspend fun setSftpBackupEnabled(enabled: Boolean) {
        dataStore.edit { it[SFTP_BACKUP_ENABLED] = enabled }
    }

    suspend fun setSftpHost(host: String) {
        dataStore.edit { it[SFTP_HOST] = host.trim() }
    }

    suspend fun setSftpPort(port: Int) {
        dataStore.edit { it[SFTP_PORT] = port.coerceIn(1, 65535) }
    }

    suspend fun setSftpUsername(username: String) {
        dataStore.edit { it[SFTP_USERNAME] = username.trim() }
    }

    suspend fun setSftpAuthMethod(method: String) {
        dataStore.edit { it[SFTP_AUTH_METHOD] = if (method == "PRIVATE_KEY") "PRIVATE_KEY" else "PASSWORD" }
    }

    suspend fun setSftpRemoteDir(dir: String) {
        val trimmed = dir.trim().ifBlank { "/yata-backups" }
        dataStore.edit { it[SFTP_REMOTE_DIR] = if (trimmed.startsWith("/")) trimmed else "/$trimmed" }
    }

    /** Persists one coherent server configuration. Connection tests call this once and await it,
     * rather than racing a group of independent DataStore edits and reading a hybrid config. */
    suspend fun setRemoteBackupConfiguration(
        protocol: com.mj.yata.domain.model.RemoteBackupProtocol,
        useTls: Boolean,
        host: String,
        port: Int,
        username: String,
        remoteDir: String,
        authMethod: String
    ) {
        val normalizedHost = host.trim()
        val normalizedPort = port.coerceIn(1, 65535)
        val normalizedDir = remoteDir.trim().ifBlank { "/yata-backups" }.let {
            if (it.startsWith("/")) it else "/$it"
        }
        dataStore.edit { prefs ->
            val identityChanged = prefs[SFTP_HOST] != normalizedHost ||
                prefs[SFTP_PORT] != normalizedPort
            prefs[REMOTE_BACKUP_PROTOCOL] = protocol.name
            prefs[FTP_USE_TLS] = useTls
            prefs[SFTP_HOST] = normalizedHost
            prefs[SFTP_PORT] = normalizedPort
            prefs[SFTP_USERNAME] = username.trim()
            prefs[SFTP_REMOTE_DIR] = normalizedDir
            prefs[SFTP_AUTH_METHOD] = if (authMethod == "PRIVATE_KEY") "PRIVATE_KEY" else "PASSWORD"
            if (identityChanged) prefs.remove(SFTP_HOST_KEY_FINGERPRINT)
        }
    }

    suspend fun setSftpIntervalMinutes(minutes: Long) {
        dataStore.edit { it[SFTP_INTERVAL_MINUTES] = minutes.coerceAtLeast(15L) }
    }

    suspend fun setSftpLastBackupAt(epochMillis: Long) {
        dataStore.edit { it[SFTP_LAST_BACKUP_AT] = epochMillis }
    }

    suspend fun setSftpKeepCount(count: Int) {
        dataStore.edit { it[SFTP_KEEP_COUNT] = count.coerceIn(2, 15) }
    }

    suspend fun setBackupIntervalMinutes(minutes: Long) {
        dataStore.edit { it[BACKUP_INTERVAL_MINUTES] = minutes }
    }

    /** Pass null to un-pin -- used when the user deliberately accepts a changed host key, or
     * clears the SFTP configuration entirely. */
    suspend fun setSftpHostKeyFingerprint(fingerprint: String?) {
        dataStore.edit {
            if (fingerprint != null) it[SFTP_HOST_KEY_FINGERPRINT] = fingerprint else it.remove(SFTP_HOST_KEY_FINGERPRINT)
        }
    }

    suspend fun setRemoteBackupProtocol(protocol: com.mj.yata.domain.model.RemoteBackupProtocol) {
        dataStore.edit { it[REMOTE_BACKUP_PROTOCOL] = protocol.name }
    }

    suspend fun setFtpUseTls(useTls: Boolean) {
        dataStore.edit { it[FTP_USE_TLS] = useTls }
    }

    suspend fun setGitHubConfiguration(
        owner: String,
        repo: String,
        branch: String,
        apiBase: String = "https://api.github.com"
    ) {
        dataStore.edit { prefs ->
            prefs[GITHUB_OWNER] = owner.trim()
            prefs[GITHUB_REPO] = repo.trim()
            prefs[GITHUB_BRANCH] = branch.trim().ifBlank { "main" }
            prefs[GITHUB_API_BASE] = apiBase.trim().ifBlank { "https://api.github.com" }
            prefs[REMOTE_BACKUP_PROTOCOL] = com.mj.yata.domain.model.RemoteBackupProtocol.GITHUB.name
        }
    }

    suspend fun setGitHubTokenExpiresAt(epochMillis: Long?) {
        dataStore.edit { prefs ->
            if (epochMillis != null) prefs[GITHUB_TOKEN_EXPIRES_AT] = epochMillis else prefs.remove(GITHUB_TOKEN_EXPIRES_AT)
        }
    }

    suspend fun setGitHubLastHeadSha(sha: String?) {
        dataStore.edit { prefs ->
            if (!sha.isNullOrBlank()) prefs[GITHUB_LAST_HEAD_SHA] = sha else prefs.remove(GITHUB_LAST_HEAD_SHA)
        }
    }

    suspend fun setReduceMotionEnabled(enabled: Boolean) {
        dataStore.edit {
            it[REDUCE_MOTION_ENABLED] = enabled
            it[MOTION_MODE] = if (enabled) MotionMode.REDUCED.name else MotionMode.FULL.name
        }
    }

    suspend fun setMotionMode(mode: MotionMode) {
        dataStore.edit {
            it[MOTION_MODE] = mode.name
            it[REDUCE_MOTION_ENABLED] = mode != MotionMode.FULL
        }
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

    suspend fun addDateAlias(definition: DateAliasDefinition) {
        val encoded = definition.encode()
        if (encoded.substringBefore("|").isBlank()) return
        dataStore.edit { prefs ->
            val existing = prefs[DATE_ALIASES] ?: emptySet()
            val withoutSameAlias = existing.filterNot {
                DateAliasDefinition.decode(it)?.alias == definition.alias.trim().lowercase()
            }.toSet()
            prefs[DATE_ALIASES] = withoutSameAlias + encoded
        }
    }

    suspend fun removeDateAlias(encodedDefinition: String) {
        dataStore.edit { prefs ->
            val updated = (prefs[DATE_ALIASES] ?: emptySet()) - encodedDefinition
            if (updated.isEmpty()) prefs.remove(DATE_ALIASES) else prefs[DATE_ALIASES] = updated
        }
    }

    suspend fun saveThemePreset(preset: SavedThemePreset) {
        val encoded = preset.encode()
        if (preset.name.isBlank()) return
        dataStore.edit { prefs ->
            val existing = prefs[SAVED_THEME_PRESETS] ?: emptySet()
            val withoutSameName = existing.filterNot {
                SavedThemePreset.decode(it)?.name.equals(preset.name, ignoreCase = true)
            }.toSet()
            prefs[SAVED_THEME_PRESETS] = withoutSameName + encoded
        }
    }

    suspend fun removeThemePreset(encodedPreset: String) {
        dataStore.edit { prefs ->
            val updated = (prefs[SAVED_THEME_PRESETS] ?: emptySet()) - encodedPreset
            if (updated.isEmpty()) prefs.remove(SAVED_THEME_PRESETS) else prefs[SAVED_THEME_PRESETS] = updated
        }
    }

    suspend fun setTaskerIntegrationEnabled(enabled: Boolean) {
        dataStore.edit { it[TASKER_INTEGRATION_ENABLED] = enabled }
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

    suspend fun setSwipeRightAction(action: com.mj.yata.domain.model.SwipeAction) {
        dataStore.edit { it[SWIPE_RIGHT_ACTION] = action.name }
    }

    suspend fun setSwipeLeftAction(action: com.mj.yata.domain.model.SwipeAction) {
        dataStore.edit { it[SWIPE_LEFT_ACTION] = action.name }
    }

    suspend fun setStartupTab(tab: com.mj.yata.domain.model.StartupTab) {
        dataStore.edit { it[STARTUP_TAB] = tab.name }
    }

    suspend fun setConfettiEnabled(enabled: Boolean) {
        dataStore.edit { it[CONFETTI_ENABLED] = enabled }
    }

    suspend fun setTimeFormat(format: com.mj.yata.domain.model.TimeFormat) {
        dataStore.edit { it[TIME_FORMAT] = format.name }
    }

    suspend fun setDateFormat(format: com.mj.yata.domain.model.DateFormat) {
        dataStore.edit { it[DATE_FORMAT] = format.name }
    }

    suspend fun setTaskCardBackground(enabled: Boolean) {
        dataStore.edit { it[TASK_CARD_BACKGROUND] = enabled }
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
                it.remove(APP_LOCK_PIN_LENGTH)
                it.remove(APP_LOCK_FAILED_ATTEMPTS)
                it.remove(APP_LOCK_LOCKED_UNTIL)
            }
        } else {
            val salt = generateSalt()
            val hashed = hashPin(pin, salt)
            dataStore.edit {
                it[APP_LOCK_PIN_SALT] = encodeSalt(salt)
                it[APP_LOCK_PIN_HASH] = hashed
                // Stored so the lock screen knows when the PIN is complete and can verify without
                // a confirm key. A dot count gives the length away on any lock screen anyway.
                it[APP_LOCK_PIN_LENGTH] = pin.length
                it.remove(APP_LOCK_FAILED_ATTEMPTS)
                it.remove(APP_LOCK_LOCKED_UNTIL)
            }
        }
    }

    /**
     * Checks [pin], and on success clears the failure count and quietly upgrades a legacy hash.
     *
     * The re-hash happens here rather than at set-time because this is the only moment the plain
     * PIN is known for an already-configured lock. It runs after the match, so a wrong guess never
     * touches what's stored.
     */
    suspend fun verifyAppLockPin(pin: String): Boolean {
        val prefs = prefsFlow.first()
        val saltEncoded = prefs[APP_LOCK_PIN_SALT] ?: return false
        val storedHash = prefs[APP_LOCK_PIN_HASH] ?: return false
        val matches = verifyPin(pin, storedHash, decodeSalt(saltEncoded))
        if (matches) {
            val upgraded = if (needsRehash(storedHash)) {
                val salt = generateSalt()
                encodeSalt(salt) to hashPin(pin, salt)
            } else null
            dataStore.edit {
                it.remove(APP_LOCK_FAILED_ATTEMPTS)
                it.remove(APP_LOCK_LOCKED_UNTIL)
                if (upgraded != null) {
                    it[APP_LOCK_PIN_SALT] = upgraded.first
                    it[APP_LOCK_PIN_HASH] = upgraded.second
                }
                // Backfills the length for a PIN set before it was recorded.
                if (it[APP_LOCK_PIN_LENGTH] == null) it[APP_LOCK_PIN_LENGTH] = pin.length
            }
        }
        return matches
    }

    /**
     * Records a wrong PIN and returns when entry unlocks again (epoch millis), or null if it's
     * still open.
     *
     * The backoff is persisted rather than held in memory because in-memory state resets when the
     * process dies — and force-stopping the app is neither difficult nor unusual, which would make
     * an in-memory limit no limit at all. The first few attempts are free: a mistyped digit is far
     * more common than an attack, and punishing it immediately would only annoy the owner.
     */
    suspend fun registerFailedAppLockAttempt(): Long? {
        var lockedUntil: Long? = null
        dataStore.edit { prefs ->
            val attempts = (prefs[APP_LOCK_FAILED_ATTEMPTS] ?: 0) + 1
            prefs[APP_LOCK_FAILED_ATTEMPTS] = attempts
            val delaySeconds = when {
                attempts < 5 -> 0L
                attempts < 8 -> 30L
                attempts < 11 -> 60L
                else -> 300L
            }
            if (delaySeconds > 0) {
                val until = System.currentTimeMillis() + delaySeconds * 1000L
                prefs[APP_LOCK_LOCKED_UNTIL] = until
                lockedUntil = until
            }
        }
        return lockedUntil
    }

    val appLockPinLengthFlow: Flow<Int> = prefsFlow.map { it[APP_LOCK_PIN_LENGTH] ?: 0 }
    val appLockLockedUntilFlow: Flow<Long> = prefsFlow.map { it[APP_LOCK_LOCKED_UNTIL] ?: 0L }
    val appLockFailedAttemptsFlow: Flow<Int> = prefsFlow.map { it[APP_LOCK_FAILED_ATTEMPTS] ?: 0 }

    suspend fun setTodayTabEnabled(enabled: Boolean) {
        dataStore.edit { it[TODAY_TAB_ENABLED] = enabled }
    }

    suspend fun setUpcomingTabEnabled(enabled: Boolean) {
        dataStore.edit { it[UPCOMING_TAB_ENABLED] = enabled }
    }

    suspend fun setFabPosition(position: com.mj.yata.domain.model.FabPosition) {
        dataStore.edit { it[FAB_POSITION] = position.name }
    }

    /** Restores user-facing behavior defaults while preserving profile identity, app-lock
     * credentials, backup accounts/history, saved searches, and all task data. */
    suspend fun resetAppSettings() {
        dataStore.edit { prefs ->
            prefs.remove(THEME_MODE); prefs.remove(APP_FONT); prefs.remove(DEFAULT_LIST_ID)
            prefs.remove(COLOR_INTENSITY); prefs.remove(BACKGROUND_TINT)
            prefs.remove(START_OF_WEEK_SUNDAY); prefs.remove(DEFAULT_REMINDER_HOUR); prefs.remove(DEFAULT_REMINDER_MINUTE)
            prefs.remove(UI_SCALE); prefs.remove(TEXT_SCALE); prefs.remove(DYNAMIC_COLOR_ENABLED)
            prefs.remove(CUSTOM_THEME_SEED_COLOR); prefs.remove(REDUCE_MOTION_ENABLED); prefs.remove(ENHANCED_M3_THEMING_ENABLED)
            prefs.remove(MOTION_MODE); prefs.remove(DATE_ALIASES)
            prefs.remove(FLOATING_BOTTOM_NAV_ENABLED); prefs.remove(BOTTOM_NAV_LABELS_ENABLED); prefs.remove(COMPLETION_SOUND_ENABLED)
            prefs.remove(HAPTICS_ENABLED); prefs.remove(TASK_SWIPE_ACTIONS_ENABLED); prefs.remove(TASK_ROW_DENSITY)
            prefs.remove(TASK_CARD_BACKGROUND)
            prefs.remove(TODAY_TAB_ENABLED); prefs.remove(UPCOMING_TAB_ENABLED); prefs.remove(FAB_POSITION)
            prefs.remove(DEFAULT_DUE_DATE); prefs.remove(DEFAULT_PRIORITY); prefs.remove(DAILY_AGENDA_ENABLED)
            prefs.remove(DAILY_AGENDA_HOUR); prefs.remove(DAILY_AGENDA_MINUTE); prefs.remove(OVERDUE_NUDGES_ENABLED)
            prefs.remove(UNDO_WINDOW_SECONDS); prefs.remove(TRASH_RETENTION_DAYS); prefs.remove(AUTO_ARCHIVE_DAYS)
            prefs.remove(SNOOZE_TONIGHT_HOUR); prefs.remove(SNOOZE_TONIGHT_MINUTE)
            prefs.remove(SNOOZE_TOMORROW_HOUR); prefs.remove(SNOOZE_TOMORROW_MINUTE)
            prefs.remove(SWIPE_RIGHT_ACTION); prefs.remove(SWIPE_LEFT_ACTION); prefs.remove(STARTUP_TAB)
            prefs.remove(CONFETTI_ENABLED); prefs.remove(TIME_FORMAT); prefs.remove(DATE_FORMAT)
            prefs.remove(VOICE_RECOGNITION_LANGUAGE); prefs.remove(TASKER_INTEGRATION_ENABLED)
        }
    }
}
