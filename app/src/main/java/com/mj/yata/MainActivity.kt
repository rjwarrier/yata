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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** Upper bound on how long the splash waits for the stored theme before giving up on it. */
private const val SPLASH_MAX_WAIT_MS = 1200L

@AndroidEntryPoint
class MainActivity : androidx.fragment.app.FragmentActivity() {

    @Inject lateinit var jsonExporter: JsonExporter
    @Inject lateinit var icsExporter: IcsExporter
    @Inject lateinit var plainTextImporter: PlainTextImporter
    @Inject lateinit var userPreferences: UserPreferences
    @Inject lateinit var cloudBackupManager: CloudBackupManager
    @Inject lateinit var errorBus: com.mj.yata.ui.error.AppErrorBus

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

    /** Cleared once the persisted theme is known — see the splash setup in [onCreate]. */
    private var themePrefLoaded = false

    // Deep-link intents (yata://task/<id>) delivered while the Activity is already alive. A cold
    // start is handled for free — NavController.setGraph() inspects the launching intent — but
    // that never re-runs for onNewIntent, so those would otherwise be silently dropped. Set only
    // from onNewIntent and cleared once consumed, so cold starts don't navigate twice.
    private var pendingDeepLinkIntent by mutableStateOf<Intent?>(null)

    /**
     * Which authenticators this device can actually offer.
     *
     * Device credential can only be combined with a biometric class from API 30; on 28-29 the
     * combination is rejected at build time, and this app supports back to 26. So the combined set
     * is only requested where it works, and older devices fall back to biometrics alone — with the
     * app's own PIN as the backstop, which is why that exists.
     */
    private fun allowedAuthenticators(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_WEAK or DEVICE_CREDENTIAL else BIOMETRIC_WEAK

    private fun canAuthenticate(): Boolean =
        BiometricManager.from(this).canAuthenticate(allowedAuthenticators()) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /**
     * @param pinAvailable whether to offer a negative button. Without a PIN there is nothing to
     *   fall back to, so offering "Use PIN" would dead-end the only way into the app. A device
     *   credential can't be combined with a negative button either — the platform forbids it,
     *   since the credential screen is itself the fallback.
     */
    private fun showBiometricPrompt(pinAvailable: Boolean) {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    AppLockState.isLocked = false
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Cancellation is the user choosing the PIN instead, which the lock screen is
                    // already showing — surfacing it as an error would be nagging. A real error
                    // (hardware unavailable, too many attempts) has to be said out loud, or the
                    // prompt just vanishes and nothing explains why.
                    val userDismissed = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    if (!userDismissed) {
                        Toast.makeText(this@MainActivity, errString, Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.lock_biometric_title))
            .setSubtitle(getString(R.string.lock_biometric_subtitle))
            .setAllowedAuthenticators(allowedAuthenticators())
            .apply {
                if (pinAvailable && allowedAuthenticators() and DEVICE_CREDENTIAL == 0) {
                    setNegativeButtonText(getString(R.string.lock_biometric_negative))
                }
            }
            .build()
        prompt.authenticate(promptInfo)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentIntent = intent
        if (intent.action == Intent.ACTION_VIEW &&
            intent.data?.scheme == com.mj.yata.ui.navigation.DeepLink.SCHEME
        ) {
            pendingDeepLinkIntent = intent
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate, which is where the library requires it: it has to replace the
        // launch theme with postSplashScreenTheme before the window is created. The condition
        // only reads a field, so it is safe this early.
        val splash = installSplashScreen()
        // Hold the splash until the theme preference has actually been read. Without this the
        // first frame renders with the SYSTEM default that collectAsState is seeded with, then
        // repaints once DataStore emits — visible as a flash of the wrong theme for anyone whose
        // choice differs from their system setting. The flag is set from a short timeout as well
        // as from the read, so a slow or failed DataStore delays startup briefly instead of
        // holding the app on the splash forever.
        splash.setKeepOnScreenCondition { !themePrefLoaded }

        super.onCreate(savedInstanceState)

        // Strictly after super.onCreate: that is when Hilt populates the @Inject lateinit fields,
        // and lifecycleScope dispatches on Main.immediate — so a launch placed above would run
        // its body synchronously, right there, and touch userPreferences before it exists.
        lifecycleScope.launch {
            withTimeoutOrNull(SPLASH_MAX_WAIT_MS) { userPreferences.themeModeFlow.first() }
            themePrefLoaded = true
        }

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
                    // The device's 12/24-hour setting can be changed while we're backgrounded, and
                    // nothing broadcasts it to us — re-read it whenever we come back to the front.
                    com.mj.yata.util.AppFormats.updateSystemClock(
                        android.text.format.DateFormat.is24HourFormat(this@MainActivity)
                    )
                    // Belt to YataApplication's midnight-loop braces: catches a day (or timezone)
                    // change that happened while the process wasn't alive to observe it.
                    com.mj.yata.util.AppClock.refresh()
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

            // AMOLED is a dark variant, so it resolves dark here and separately switches on the
            // true-black surface treatment below.
            val useDarkTheme = when (themeMode) {
                ThemeMode.LIGHT  -> false
                ThemeMode.DARK   -> true
                ThemeMode.AMOLED -> true
                ThemeMode.SYSTEM -> systemDark
            }

            val reduceMotionEnabled by userPreferences.reduceMotionEnabledFlow.collectAsState(initial = false)
            LaunchedEffect(reduceMotionEnabled) {
                com.mj.yata.ui.theme.YataDur.applyReduceMotion(reduceMotionEnabled)
            }

            val uiScale by userPreferences.uiScaleFlow.collectAsState(initial = 1.0f)
            val textScale by userPreferences.textScaleFlow.collectAsState(initial = 1.0f)
            val dynamicColorEnabled by userPreferences.dynamicColorEnabledFlow.collectAsState(initial = true)
            val undoWindowSeconds by userPreferences.undoWindowSecondsFlow.collectAsState(initial = 4)
            val customThemeSeedColorArgb by userPreferences.customThemeSeedColorFlow.collectAsState(initial = null)
            val appFont by userPreferences.appFontFlow.collectAsState(initial = com.mj.yata.domain.model.AppFont.INTER)
            val colorIntensity by userPreferences.colorIntensityFlow.collectAsState(initial = com.mj.yata.domain.model.ColorIntensity.NORMAL)
            val backgroundTint by userPreferences.backgroundTintFlow.collectAsState(initial = com.mj.yata.domain.model.BackgroundTint.SOFT)
            val enhancedM3ThemingEnabled by userPreferences.enhancedM3ThemingEnabledFlow.collectAsState(initial = false)
            val floatingBottomNavEnabled by userPreferences.floatingBottomNavEnabledFlow.collectAsState(initial = false)
            val bottomNavLabelsEnabled by userPreferences.bottomNavLabelsEnabledFlow.collectAsState(initial = true)
            val completionSoundEnabled by userPreferences.completionSoundEnabledFlow.collectAsState(initial = true)
            val hapticsEnabled by userPreferences.hapticsEnabledFlow.collectAsState(initial = true)
            val taskSwipeActionsEnabled by userPreferences.taskSwipeActionsEnabledFlow.collectAsState(initial = true)
            val taskCardBackground by userPreferences.taskCardBackgroundFlow.collectAsState(initial = false)
            val swipeRightAction by userPreferences.swipeRightActionFlow
                .collectAsState(initial = com.mj.yata.domain.model.SwipeAction.COMPLETE)
            val swipeLeftAction by userPreferences.swipeLeftActionFlow
                .collectAsState(initial = com.mj.yata.domain.model.SwipeAction.DELETE)

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
                com.mj.yata.ui.widgets.LocalUndoWindowSeconds provides undoWindowSeconds,
                com.mj.yata.ui.theme.LocalTaskSwipeActionsEnabled provides taskSwipeActionsEnabled,
                com.mj.yata.ui.theme.LocalTaskCardBackground provides taskCardBackground,
                com.mj.yata.ui.theme.LocalSwipeRightAction provides swipeRightAction,
                com.mj.yata.ui.theme.LocalSwipeLeftAction provides swipeLeftAction,
                com.mj.yata.ui.theme.LocalReduceMotion provides reduceMotionEnabled
            ) {
                YataTheme(
                    darkTheme = useDarkTheme,
                    useDynamicColor = dynamicColorEnabled,
                    amoledMode = themeMode == ThemeMode.AMOLED,
                    colorIntensity = colorIntensity,
                    backgroundTint = backgroundTint,
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
                            val pinLength by userPreferences.appLockPinLengthFlow.collectAsState(initial = 0)
                            val lockedUntil by userPreferences.appLockLockedUntilFlow.collectAsState(initial = 0L)
                            val biometricAvailable = remember { canAuthenticate() }

                            // App Lock can only be switched on while a device credential exists, but
                            // the user can remove every biometric and screen lock afterwards. With
                            // no credential and no PIN there is then no way in at all, and the only
                            // recovery would be clearing app data — every task gone, to protect a
                            // lock whose own precondition has been deleted. Opening is the lesser
                            // failure, so the lock stands down instead of stranding its owner.
                            LaunchedEffect(biometricAvailable, pinSet) {
                                if (!biometricAvailable && !pinSet) AppLockState.isLocked = false
                            }

                            LockScreen(
                                onUnlockClick = { showBiometricPrompt(pinAvailable = pinSet) },
                                pinAvailable = pinSet,
                                biometricAvailable = biometricAvailable,
                                pinLength = pinLength,
                                lockedUntilMillis = lockedUntil,
                                onVerifyPin = { pin -> userPreferences.verifyAppLockPin(pin) },
                                onPinFailed = { userPreferences.registerFailedAppLockAttempt() },
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

                        // Deep link arriving while the app is already running. Cold starts are
                        // already covered by setGraph(), so only onNewIntent-delivered ones
                        // land here (see pendingDeepLinkIntent).
                        LaunchedEffect(pendingDeepLinkIntent) {
                            val deepLinkIntent = pendingDeepLinkIntent ?: return@LaunchedEffect
                            pendingDeepLinkIntent = null
                            navController.handleDeepLink(deepLinkIntent)
                        }

                        // First launch after install (or after a fresh restore): show the
                        // one-time welcome tour on top of Main. Replaying it later from
                        // Settings navigates to the same route directly, bypassing this check.
                        LaunchedEffect(Unit) {
                            if (!userPreferences.hasSeenWelcomeFlow.first()) {
                                navController.navigate(com.mj.yata.ui.navigation.Screen.Welcome.route)
                            }
                        }

                        // Failures reported by background work (see MainViewModel.safeLaunch)
                        // surface here rather than on any one screen: the write that failed can
                        // have been started from any destination, and this sits above the whole
                        // NavHost so the message lands wherever the user currently is.
                        val errorHostState = remember { SnackbarHostState() }
                        LaunchedEffect(Unit) {
                            errorBus.messages.collect { messageRes ->
                                errorHostState.showSnackbar(getString(messageRes))
                            }
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            AppNavigation(
                                navController      = navController,
                                onExportRequested  = { exportLauncher.launch("yata_backup.json") },
                                onImportRequested  = { importLauncher.launch(arrayOf("application/json")) },
                                onImportPlainTextRequested = { plainTextImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")) },
                                onExportCsvRequested = { exportCsvLauncher.launch("yata_tasks.csv") },
                                onExportIcsRequested = { icsExportLauncher.launch("yata_calendar.ics") },
                                onCloudSignInRequested = { cloudSignInLauncher.launch(cloudBackupManager.signInIntent()) }
                            )
                            SnackbarHost(
                                hostState = errorHostState,
                                modifier = Modifier.align(Alignment.BottomCenter)
                            ) { data -> com.mj.yata.ui.widgets.YataSnackbar(data) }
                        }
                    }
                }
            }
        }
    }
}
