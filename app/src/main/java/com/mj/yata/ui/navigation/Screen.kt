package com.mj.yata.ui.navigation

import android.net.Uri

/**
 * Custom scheme backing the app's deep links (`yata://task/<id>`). A custom scheme rather than
 * an https App Link because App Links require hosting a verified assetlinks.json on a domain —
 * this app has no web presence, and an unverified https filter would show a disambiguation
 * chooser instead of opening directly.
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
    object Main : Screen("main?tab={tab}&quickAdd={quickAdd}&quickAddListId={quickAddListId}") {
        fun createRoute(tab: Int, quickAdd: Boolean = false, quickAddListId: String? = null) =
            "main?tab=$tab&quickAdd=$quickAdd" + (quickAddListId?.let { "&quickAddListId=$it" } ?: "")
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
    object NextDays : Screen("next_days")
    object CrashLog : Screen("crash_log")
    object RemoteSync : Screen("remote_sync")
}
