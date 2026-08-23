package com.mj.yata.ui.navigation

import android.net.Uri

/**
 * Custom scheme backing the app's deep links (`yata://task/<id>`). Kept as a custom scheme for
 * these rather than moved to the verified https App Link now used for shared task links
 * (`https://ranjithj.in/yata/i`, see `TaskTransferLink.kt`) — that domain only claims the one
 * import path, not this whole family of in-app links.
 *
 * These are stable, user-visible identifiers once anyone pastes one into a note or a message —
 * treat the patterns as an API and don't rename them casually.
 */
object DeepLink {
    const val SCHEME = "yata"

    fun task(taskId: String) = "$SCHEME://task/$taskId"
    fun project(projectId: String) = "$SCHEME://project/$projectId"
    fun person(personId: String) = "$SCHEME://person/$personId"
    fun tag(tagId: String) = "$SCHEME://tag/$tagId"
    fun list(listId: String) = "$SCHEME://list/$listId"
}

sealed class Screen(val route: String) {
    object Main : Screen("main?tab={tab}&quickAdd={quickAdd}&quickAddListId={quickAddListId}&quickCapture={quickCapture}") {
        fun createRoute(
            tab: Int,
            quickAdd: Boolean = false,
            quickAddListId: String? = null,
            quickCapture: Boolean = false
        ) = "main?tab=$tab&quickAdd=$quickAdd" +
            (quickAddListId?.let { "&quickAddListId=$it" } ?: "") +
            "&quickCapture=$quickCapture"
    }
    
    object TaskDetail : Screen("task_detail/{taskId}") {
        fun createRoute(taskId: String) = "task_detail/$taskId"
    }
    
    object ProjectDetail : Screen("project_detail/{projectId}") {
        fun createRoute(projectId: String) = "project_detail/$projectId"
    }

    object PersonDetail : Screen("person_detail/{personId}") {
        fun createRoute(personId: String) = "person_detail/$personId"
    }

    object PersonAnalytics : Screen("person_analytics/{personId}") {
        fun createRoute(personId: String) = "person_analytics/$personId"
    }

    object StaffAnalytics : Screen("staff_analytics")

    object TagDetail : Screen("tag_detail/{tagId}") {
        fun createRoute(tagId: String) = "tag_detail/$tagId"
    }

    object ListDetail : Screen("list_detail/{listId}") {
        fun createRoute(listId: String) = "list_detail/$listId"
    }

    object Welcome : Screen("welcome")
    object Search : Screen("search?filters={filters}") {
        fun createRoute(filters: String? = null) =
            "search" + (filters?.let { "?filters=${Uri.encode(it)}" } ?: "")
    }
    object Settings : Screen("settings")
    object SettingsSection : Screen("settings_section/{section}") {
        fun createRoute(section: String) = "settings_section/$section"
    }
    object HelpAbout : Screen("help_about")
    object Analytics : Screen("analytics")
    object Trash : Screen("trash")
    object Archive : Screen("archive")
    object Inbox : Screen("inbox")
    object RecurringTasks : Screen("recurring_tasks")
    object NextDays : Screen("next_days")
    object HolidayCalendar : Screen("holiday_calendar")
    object CrashLog : Screen("crash_log")
    object ShareApp : Screen("share_app")
    object RemoteSync : Screen("remote_sync")
    object SyncHistory : Screen("sync_history")
    object SharedTaskImport : Screen("shared_task_import?link={link}") {
        fun createRoute(link: String) = "shared_task_import?link=${Uri.encode(link)}"
    }
}
