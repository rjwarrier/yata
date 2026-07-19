package com.mj.yata.ui.navigation

import android.net.Uri

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
    object Analytics : Screen("analytics")
    object Trash : Screen("trash")
    object NextDays : Screen("next_days")
}
