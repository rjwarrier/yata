package com.mj.yata

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.mj.yata.data.cloud.CloudBackupManager
import com.mj.yata.data.local.datastore.UserPreferences
import com.mj.yata.domain.model.ThemeMode
import com.mj.yata.ui.navigation.AppNavigation
import com.mj.yata.ui.screen.lock.AppLockState
import com.mj.yata.ui.screen.lock.LockScreen
import com.mj.yata.ui.theme.YataTheme
import com.mj.yata.util.IcsExporter
import com.mj.yata.util.JsonExporter
import com.mj.yata.util.PlainTextImporter
import com.mj.yata.util.isDarkNow
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : androidx.fragment.app.FragmentActivity() {

    @Inject lateinit var jsonExporter: JsonExporter
    @Inject lateinit var icsExporter: IcsExporter
    @Inject lateinit var plainTextImporter: PlainTextImporter
    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var cloudBackupManager: CloudBackupManager

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* No-op: reminders simply won't show if denied. */ }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val ok = jsonExporter.exportData(uri)
            Toast.makeText(
                this@MainActivity,
                if (ok) "Data exported successfully" else "Export failed",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val ok = jsonExporter.importData(uri)
            Toast.makeText(
                this@MainActivity,
                        if (ok) "Data imported successfully" else "Import failed - check file format",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private val plainTextImportLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val outcome = plainTextImporter.importData(uri)
            Toast.makeText(
                this@MainActivity,
                outcome.fold(
                    onSuccess = { r ->
                        buildString {
                            append("Imported ${r.imported} tasks")
                            if (r.skippedDuplicates > 0) append(", skipped ${r.skippedDuplicates} duplicates")
                            if (r.skippedMalformed > 0) append(", skipped ${r.skippedMalformed} invalid rows")
                        }
                    },
                    onFailure = { "Import failed - check file format" }
                ),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val exportCsvLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val ok = plainTextImporter.exportCsv(uri)
            Toast.makeText(
                this@MainActivity,
                if (ok) "CSV exported successfully" else "Export failed",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private val icsExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val ok = icsExporter.exportIcs(uri)
            Toast.makeText(
                this@MainActivity,
                if (ok) "Calendar file exported" else "Export failed",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private val cloudSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        lifecycleScope.launch {
            val outcome = cloudBackupManager.handleSignInResult(result.data)
            Toast.makeText(
                this@MainActivity,
                outcome.fold(
                    onSuccess = { email -> "Signed in as $email" },
                    onFailure = { "Google sign-in failed or was cancelled" }
                ),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Tracked as Compose state so a new intent delivered via onNewIntent (app already running,
    // e.g. tapping a notification or launcher shortcut while YATA is in the foreground/background)
    // triggers the same routing effect as a cold start — `intent` alone isn't observable state.
    private var currentIntent by mutableStateOf<Intent?>(null)

    private fun canAuthenticate(): Boolean =
        BiometricManager.from(this).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) ==
            BiometricManager.BIOMETRIC_SUCCESS

    private fun showBiometricPrompt() {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    AppLockState.isLocked = false
                }
            }
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock YATA")
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(promptInfo)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentIntent = intent
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentIntent = intent

        // Registered once per process (not per Activity recreation) — ON_STOP here only fires
        // when every Activity in the process has stopped, i.e. true backgrounding, unlike this
        // Activity's own onStop which also fires on rotation/multi-window recreation.
        if (!AppLockState.hasRegisteredProcessObserver) {
            AppLockState.hasRegisteredProcessObserver = true
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    if (AppLockState.appLockEnabled) {
                        AppLockState.backgroundedAtMillis = System.currentTimeMillis()
                    }
                }

                override fun onStart(owner: LifecycleOwner) {
                    val backgroundedAt = AppLockState.backgroundedAtMillis ?: return
                    AppLockState.backgroundedAtMillis = null
                    if (!AppLockState.appLockEnabled) return
                    val elapsedMinutes = (System.currentTimeMillis() - backgroundedAt) / 60_000.0
                    if (elapsedMinutes >= AppLockState.appLockTimeoutMinutes) {
                        AppLockState.isLocked = true
                    }
                }
            })
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val themeMode by userPreferences.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
            val systemDark = isSystemInDarkTheme()

            val scheduleStartHour by userPreferences.themeScheduleStartHourFlow.collectAsState(initial = 21)
            val scheduleStartMinute by userPreferences.themeScheduleStartMinuteFlow.collectAsState(initial = 0)
            val scheduleEndHour by userPreferences.themeScheduleEndHourFlow.collectAsState(initial = 7)
            val scheduleEndMinute by userPreferences.themeScheduleEndMinuteFlow.collectAsState(initial = 0)

            // Only ticks while SCHEDULED is active, so the theme keeps up with the clock without
            // needing an AlarmManager/BroadcastReceiver round-trip for a live-in-app switch.
            var currentTime by remember { mutableStateOf(LocalTime.now()) }
            LaunchedEffect(themeMode) {
                if (themeMode == ThemeMode.SCHEDULED) {
                    while (true) {
                        currentTime = LocalTime.now()
                        delay(60_000)
                    }
                }
            }

            val useDarkTheme = when (themeMode) {
                ThemeMode.LIGHT     -> false
                ThemeMode.DARK      -> true
                ThemeMode.SYSTEM    -> systemDark
                ThemeMode.SCHEDULED -> isDarkNow(
                    LocalTime.of(scheduleStartHour, scheduleStartMinute),
                    LocalTime.of(scheduleEndHour, scheduleEndMinute),
                    currentTime
                )
            }

            val reduceMotionEnabled by userPreferences.reduceMotionEnabledFlow.collectAsState(initial = false)
            LaunchedEffect(reduceMotionEnabled) {
                com.mj.yata.ui.theme.YataDur.applyReduceMotion(reduceMotionEnabled)
            }

            val uiScale by userPreferences.uiScaleFlow.collectAsState(initial = 1.0f)
            val textScale by userPreferences.textScaleFlow.collectAsState(initial = 1.0f)
            val dynamicColorEnabled by userPreferences.dynamicColorEnabledFlow.collectAsState(initial = true)
            val customThemeSeedColorArgb by userPreferences.customThemeSeedColorFlow.collectAsState(initial = null)
            val appFont by userPreferences.appFontFlow.collectAsState(initial = com.mj.yata.domain.model.AppFont.INTER)
            val enhancedM3ThemingEnabled by userPreferences.enhancedM3ThemingEnabledFlow.collectAsState(initial = false)
            val floatingBottomNavEnabled by userPreferences.floatingBottomNavEnabledFlow.collectAsState(initial = false)
            val bottomNavLabelsEnabled by userPreferences.bottomNavLabelsEnabledFlow.collectAsState(initial = true)
            val completionSoundEnabled by userPreferences.completionSoundEnabledFlow.collectAsState(initial = true)
            val hapticsEnabled by userPreferences.hapticsEnabledFlow.collectAsState(initial = true)
            val taskSwipeActionsEnabled by userPreferences.taskSwipeActionsEnabledFlow.collectAsState(initial = true)

            val appLockEnabledPref by userPreferences.appLockEnabledFlow.collectAsState(initial = false)
            LaunchedEffect(appLockEnabledPref) { AppLockState.appLockEnabled = appLockEnabledPref }
            val appLockTimeoutPref by userPreferences.appLockTimeoutMinutesFlow.collectAsState(initial = 0)
            LaunchedEffect(appLockTimeoutPref) { AppLockState.appLockTimeoutMinutes = appLockTimeoutPref }
            LaunchedEffect(Unit) {
                // Only decide "start locked?" once per process — on Activity recreation
                // (rotation/multi-window) this same check must not re-run and spuriously relock.
                if (!AppLockState.hasCheckedInitialLock) {
                    AppLockState.hasCheckedInitialLock = true
                    if (userPreferences.appLockEnabledFlow.first() && canAuthenticate()) {
                        AppLockState.isLocked = true
                    }
                }
            }
            val baseDensity = LocalDensity.current
            val scaledDensity = Density(
                density = baseDensity.density * uiScale,
                fontScale = baseDensity.fontScale * uiScale * textScale
            )

            CompositionLocalProvider(
                LocalDensity provides scaledDensity,
                com.mj.yata.ui.theme.LocalHapticsEnabled provides hapticsEnabled,
                com.mj.yata.ui.theme.LocalTaskSwipeActionsEnabled provides taskSwipeActionsEnabled
            ) {
                YataTheme(
                    darkTheme = useDarkTheme,
                    useDynamicColor = dynamicColorEnabled,
                    customThemeSeedColor = customThemeSeedColorArgb?.let { androidx.compose.ui.graphics.Color(it) },
                    appFont = appFont,
                    enhancedM3Theming = enhancedM3ThemingEnabled,
                    floatingBottomNav = floatingBottomNavEnabled,
                    completionSound = completionSoundEnabled,
                    bottomNavLabelsEnabled = bottomNavLabelsEnabled,
                    edgeToEdge = true
                ) {
                    // Cache the actually-rendered primary color so background notifications/widgets
                    // can match it exactly, instead of re-resolving dynamic color in a receiver/worker
                    // context where it's been observed to diverge from what's shown here live.
                    val livePrimary = MaterialTheme.colorScheme.primary
                    LaunchedEffect(livePrimary) {
                        userPreferences.setLastPrimaryArgb(livePrimary.toArgb())
                    }

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        if (AppLockState.isLocked) {
                            val pinSet by userPreferences.appLockPinSetFlow.collectAsState(initial = false)
                            LockScreen(
                                onUnlockClick = { showBiometricPrompt() },
                                pinAvailable = pinSet,
                                onVerifyPin = { pin -> userPreferences.verifyAppLockPin(pin) },
                                onPinUnlocked = { AppLockState.isLocked = false }
                            )
                            return@Surface
                        }

                        val navController = rememberNavController()

                        // Route to task details if launched from a notification, or to the
                        // relevant screen if launched from a long-press launcher shortcut.
                        LaunchedEffect(currentIntent) {
                            val intent = currentIntent ?: return@LaunchedEffect
                            val navigateTo = intent.getStringExtra("navigate_to")
                            val taskId = intent.getStringExtra("task_id")
                            val shortcutAction = intent.getStringExtra("shortcut_action")
                            val listId = intent.getStringExtra("list_id")
                            val entityId = intent.getStringExtra("entity_id")
                            if (navigateTo == "task_detail" && !taskId.isNullOrEmpty()) {
                                navController.navigate(com.mj.yata.ui.navigation.Screen.TaskDetail.createRoute(taskId))
                            } else if (navigateTo == "list_detail" && !entityId.isNullOrEmpty()) {
                                navController.navigate(com.mj.yata.ui.navigation.Screen.ListDetail.createRoute(entityId))
                            } else if (navigateTo == "project_detail" && !entityId.isNullOrEmpty()) {
                                navController.navigate(com.mj.yata.ui.navigation.Screen.ProjectDetail.createRoute(entityId))
                            } else if (navigateTo == "person_detail" && !entityId.isNullOrEmpty()) {
                                navController.navigate(com.mj.yata.ui.navigation.Screen.PersonDetail.createRoute(entityId))
                            } else if (navigateTo == "people") {
                                navController.navigate(com.mj.yata.ui.navigation.Screen.Main.createRoute(tab = 2)) {
                                    popUpTo(com.mj.yata.ui.navigation.Screen.Main.route) { inclusive = true }
                                    launchSingleTop = true
                                }
                            } else if (navigateTo == "upcoming") {
                                navController.navigate(com.mj.yata.ui.navigation.Screen.Main.createRoute(tab = 4)) {
                                    popUpTo(com.mj.yata.ui.navigation.Screen.Main.route) { inclusive = true }
                                    launchSingleTop = true
                                }
                            } else if (shortcutAction == "quick_add") {
                                navController.navigate(
                                    com.mj.yata.ui.navigation.Screen.Main.createRoute(tab = 0, quickAdd = true, quickAddListId = listId)
                                ) {
                                    popUpTo(com.mj.yata.ui.navigation.Screen.Main.route) { inclusive = true }
                                    launchSingleTop = true
                                }
                            } else if (shortcutAction == "today") {
                                navController.navigate(com.mj.yata.ui.navigation.Screen.Main.createRoute(tab = 0)) {
                                    popUpTo(com.mj.yata.ui.navigation.Screen.Main.route) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        }

                        // First launch after install (or after a fresh restore): show the
                        // one-time welcome tour on top of Main. Replaying it later from
                        // Settings navigates to the same route directly, bypassing this check.
                        LaunchedEffect(Unit) {
                            if (!userPreferences.hasSeenWelcomeFlow.first()) {
                                navController.navigate(com.mj.yata.ui.navigation.Screen.Welcome.route)
                            }
                        }

                        AppNavigation(
                            navController      = navController,
                            onExportRequested  = { exportLauncher.launch("yata_backup.json") },
                            onImportRequested  = { importLauncher.launch(arrayOf("application/json")) },
                            onImportPlainTextRequested = { plainTextImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")) },
                            onExportCsvRequested = { exportCsvLauncher.launch("yata_tasks.csv") },
                            onExportIcsRequested = { icsExportLauncher.launch("yata_calendar.ics") },
                            onCloudSignInRequested = { cloudSignInLauncher.launch(cloudBackupManager.signInIntent()) }
                        )
                    }
                }
            }
        }
    }
}
