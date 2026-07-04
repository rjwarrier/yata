package com.mj.yata

import android.Manifest
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.mj.yata.data.local.datastore.UserPreferences
import com.mj.yata.domain.model.ThemeMode
import com.mj.yata.ui.navigation.AppNavigation
import com.mj.yata.ui.theme.YataTheme
import com.mj.yata.util.JsonExporter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var jsonExporter: JsonExporter
    @Inject lateinit var userPreferences: UserPreferences

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

            YataTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    
                    // Route to task details if launched from a notification
                    LaunchedEffect(intent) {
                        val navigateTo = intent.getStringExtra("navigate_to")
                        val taskId = intent.getStringExtra("task_id")
                        if (navigateTo == "task_detail" && !taskId.isNullOrEmpty()) {
                            navController.navigate(com.mj.yata.ui.navigation.Screen.TaskDetail.createRoute(taskId))
                        }
                    }

                    AppNavigation(
                        navController      = navController,
                        onExportRequested  = { exportLauncher.launch("yata_backup.json") },
                        onImportRequested  = { importLauncher.launch(arrayOf("application/json")) }
                    )
                }
            }
        }
    }
}
