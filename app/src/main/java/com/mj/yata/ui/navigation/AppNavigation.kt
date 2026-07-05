package com.mj.yata.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mj.yata.ui.screen.main.MainScreen
import com.mj.yata.ui.screen.main.MainViewModel
import com.mj.yata.ui.screen.taskdetail.TaskDetailScreen
import com.mj.yata.ui.screen.project.ProjectDetailScreen
import com.mj.yata.ui.screen.person.PersonDetailScreen
import com.mj.yata.ui.screen.tag.TagDetailScreen
import com.mj.yata.ui.screen.list.ListDetailScreen
import com.mj.yata.ui.screen.search.SearchScreen
import com.mj.yata.ui.screen.settings.SettingsScreen
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.YataEase
import androidx.compose.animation.core.tween

@Composable
fun AppNavigation(
    navController: NavHostController,
    onExportRequested: () -> Unit,
    onImportRequested: () -> Unit
) {
    val onNavigateToTab: (Int) -> Unit = { index ->
        navController.navigate(Screen.Main.createRoute(index)) {
            popUpTo(Screen.Main.route) { inclusive = true }
            launchSingleTop = true
        }
    }

    NavHost(
        navController    = navController,
        startDestination = Screen.Main.route,
        // Push: incoming slides 100%->0; outgoing shifts to -28% + fades to 0.5 (handoff m3-widgets.jsx nav motion)
        enterTransition  = { slideInHorizontally(tween(YataDur.nav, easing = YataEase.emphasized)) { it } + fadeIn(tween(YataDur.nav, easing = YataEase.emphDecel)) },
        exitTransition   = { slideOutHorizontally(tween(YataDur.nav, easing = YataEase.emphasized)) { -(it * 28 / 100) } + fadeOut(targetAlpha = 0.5f, animationSpec = tween(YataDur.nav)) },
        popEnterTransition  = { slideInHorizontally(tween(YataDur.nav, easing = YataEase.emphasized)) { -(it * 28 / 100) } + fadeIn(tween(YataDur.nav, easing = YataEase.emphDecel)) },
        popExitTransition   = { slideOutHorizontally(tween(YataDur.nav, easing = YataEase.emphasized)) { it } + fadeOut(tween(YataDur.fade)) }
    ) {
        // ── Main Shell (5-tab navigation) ───────────────────────────────────
        composable(
            route = Screen.Main.route,
            arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 })
        ) { backStackEntry ->
            val initialTab = backStackEntry.arguments?.getInt("tab") ?: 0
            val viewModel: MainViewModel = hiltViewModel()
            MainScreen(
                viewModel = viewModel,
                navController = navController,
                initialTab = initialTab,
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToSearch = { navController.navigate(Screen.Search.route) },
                onNavigateToTaskDetail = { taskId ->
                    navController.navigate(Screen.TaskDetail.createRoute(taskId))
                },
                onNavigateToProjectDetail = { projectId ->
                    navController.navigate(Screen.ProjectDetail.createRoute(projectId))
                },
                onNavigateToPersonDetail = { personId ->
                    navController.navigate(Screen.PersonDetail.createRoute(personId))
                },
                onNavigateToTagDetail = { tagId ->
                    navController.navigate(Screen.TagDetail.createRoute(tagId))
                },
                onNavigateToListDetail = { listId ->
                    navController.navigate(Screen.ListDetail.createRoute(listId))
                }
            )
        }

        // ── Task Detail ──────────────────────────────────────────────────────
        composable(
            route = Screen.TaskDetail.route,
            arguments = listOf(navArgument("taskId") { type = NavType.StringType })
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getString("taskId") ?: ""
            val viewModel: MainViewModel = hiltViewModel()
            TaskDetailScreen(
                viewModel = viewModel,
                taskId = taskId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTab = onNavigateToTab
            )
        }

        // ── Project Detail ───────────────────────────────────────────────────
        composable(
            route = Screen.ProjectDetail.route,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            val viewModel: MainViewModel = hiltViewModel()
            ProjectDetailScreen(
                viewModel = viewModel,
                projectId = projectId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTaskDetail = { taskId ->
                    navController.navigate(Screen.TaskDetail.createRoute(taskId))
                },
                onNavigateToTab = onNavigateToTab
            )
        }

        // ── Person Detail ────────────────────────────────────────────────────
        composable(
            route = Screen.PersonDetail.route,
            arguments = listOf(navArgument("personId") { type = NavType.StringType })
        ) { backStackEntry ->
            val personId = backStackEntry.arguments?.getString("personId") ?: ""
            val viewModel: MainViewModel = hiltViewModel()
            PersonDetailScreen(
                viewModel = viewModel,
                personId = personId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTaskDetail = { taskId ->
                    navController.navigate(Screen.TaskDetail.createRoute(taskId))
                },
                onNavigateToTab = onNavigateToTab
            )
        }

        // ── Tag Detail ───────────────────────────────────────────────────────
        composable(
            route = Screen.TagDetail.route,
            arguments = listOf(navArgument("tagId") { type = NavType.StringType })
        ) { backStackEntry ->
            val tagId = backStackEntry.arguments?.getString("tagId") ?: ""
            val viewModel: MainViewModel = hiltViewModel()
            TagDetailScreen(
                viewModel = viewModel,
                tagId = tagId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTaskDetail = { taskId ->
                    navController.navigate(Screen.TaskDetail.createRoute(taskId))
                },
                onNavigateToTab = onNavigateToTab
            )
        }

        // ── List Detail ───────────────────────────────────────────────────────
        composable(
            route = Screen.ListDetail.route,
            arguments = listOf(navArgument("listId") { type = NavType.StringType })
        ) { backStackEntry ->
            val listId = backStackEntry.arguments?.getString("listId") ?: ""
            val viewModel: MainViewModel = hiltViewModel()
            ListDetailScreen(
                viewModel = viewModel,
                listId = listId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTaskDetail = { taskId ->
                    navController.navigate(Screen.TaskDetail.createRoute(taskId))
                },
                onNavigateToTab = onNavigateToTab
            )
        }

        // ── Search ────────────────────────────────────────────────────────────
        composable(Screen.Search.route) {
            val viewModel: MainViewModel = hiltViewModel()
            SearchScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTaskDetail = { taskId ->
                    navController.navigate(Screen.TaskDetail.createRoute(taskId))
                },
                onNavigateToTab = onNavigateToTab
            )
        }

        // ── Settings ─────────────────────────────────────────────────────────
        composable(Screen.Settings.route) {
            val viewModel: MainViewModel = hiltViewModel()
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onExportRequested = onExportRequested,
                onImportRequested = onImportRequested,
                onNavigateToTab = onNavigateToTab
            )
        }
    }
}
