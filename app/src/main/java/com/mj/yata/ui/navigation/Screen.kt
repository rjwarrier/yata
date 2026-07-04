package com.mj.yata.ui.navigation

sealed class Screen(val route: String) {
    object Main : Screen("main")
    
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

    object Search : Screen("search")
    object Settings : Screen("settings")
}
