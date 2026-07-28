package com.mj.yata.notification

import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat

/**
 * Exact-alarm scheduling and battery-optimization exemption are both OS-level special
 * permissions — unlike POST_NOTIFICATIONS, there's no in-app runtime prompt for either. The
 * user has to grant them from system settings, which is why these need explicit status checks
 * and deep-link intents rather than a `registerForActivityResult` permission launcher.
 */
object NotificationPermissionUtils {

    fun areNotificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true // no such restriction before Android 12
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        startOrFallBackToAppSettings(context, intent)
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        startOrFallBackToAppSettings(context, intent)
    }

    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        startOrFallBackToAppSettings(context, intent)
    }

    /**
     * None of the three deep-links above is guaranteed to resolve: the exact-alarm and
     * battery-optimization screens are absent on Android Go and on OEM ROMs that strip them, and
     * an unresolved implicit intent throws [ActivityNotFoundException] rather than no-opping. The
     * app-details screen is part of the platform on every device and reaches the same settings in
     * two more taps, so it's the fallback; if even that fails there's nowhere left to send the
     * user, and crashing over an informational deep-link would be worse than doing nothing.
     */
    private fun startOrFallBackToAppSettings(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
            return
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No activity for ${intent.action}; falling back to app details", e)
        }
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            )
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No activity for app details settings either", e)
        }
    }

    private const val TAG = "NotificationPermissionUtils"
}
