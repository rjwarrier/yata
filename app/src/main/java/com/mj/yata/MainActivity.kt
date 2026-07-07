package com.mj.yata

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.mj.yata.data.cloud.CloudBackupManager
import com.mj.yata.data.local.datastore.UserPreferences
import com.mj.yata.domain.model.ThemeMode
import com.mj.yata.ui.navigation.AppNavigation
import com.mj.yata.ui.theme.YataTheme
import com.mj.yata.util.IcsExporter
import com.mj.yata.util.JsonExporter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var jsonExporter: JsonExporter
    @Inject lateinit var icsExporter: IcsExporter
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentIntent = intent
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentIntent = intent

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val themeMode by userPreferences.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
            val systemDark = isSystemInDarkTheme()
            val useDarkTheme = when (themeMode) {
                ThemeMode.LIGHT  -> false
                ThemeMode.DARK   -> true
                ThemeMode.SYSTEM -> systemDark
            }

            val uiScale by userPreferences.uiScaleFlow.collectAsState(initial = 1.0f)
            val dynamicColorEnabled by userPreferences.dynamicColorEnabledFlow.collectAsState(initial = true)
            val appFont by userPreferences.appFontFlow.collectAsState(initial = com.mj.yata.domain.model.AppFont.INTER)
            val baseDensity = LocalDensity.current
            val scaledDensity = Density(
                density = baseDensity.density * uiScale,
                fontScale = baseDensity.fontScale * uiScale
            )

            CompositionLocalProvider(LocalDensity provides scaledDensity) {
                YataTheme(darkTheme = useDarkTheme, useDynamicColor = dynamicColorEnabled, appFont = appFont) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val navController = rememberNavController()

                        // Route to task details if launched from a notification, or to the
                        // relevant screen if launched from a long-press launcher shortcut.
                        LaunchedEffect(currentIntent) {
                            val intent = currentIntent ?: return@LaunchedEffect
                            val navigateTo = intent.getStringExtra("navigate_to")
                            val taskId = intent.getStringExtra("task_id")
                            val shortcutAction = intent.getStringExtra("shortcut_action")
                            val listId = intent.getStringExtra("list_id")
                            if (navigateTo == "task_detail" && !taskId.isNullOrEmpty()) {
                                navController.navigate(com.mj.yata.ui.navigation.Screen.TaskDetail.createRoute(taskId))
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

                        AppNavigation(
                            navController      = navController,
                            onExportRequested  = { exportLauncher.launch("yata_backup.json") },
                            onImportRequested  = { importLauncher.launch(arrayOf("application/json")) },
                            onExportIcsRequested = { icsExportLauncher.launch("yata_calendar.ics") },
                            onCloudSignInRequested = { cloudSignInLauncher.launch(cloudBackupManager.signInIntent()) }
                        )
                    }
                }
            }
        }
    }
}
