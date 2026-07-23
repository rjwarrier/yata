package com.mj.yata.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import android.net.Uri
import com.mj.yata.ui.screen.analytics.AnalyticsScreen
import com.mj.yata.ui.screen.main.MainScreen
import com.mj.yata.ui.screen.main.MainViewModel
import com.mj.yata.ui.screen.nextdays.NextDaysScreen
import com.mj.yata.ui.screen.taskdetail.TaskDetailScreen
import com.mj.yata.ui.screen.project.ProjectDetailScreen
import com.mj.yata.ui.screen.person.PersonDetailScreen
import com.mj.yata.ui.screen.tag.TagDetailScreen
import com.mj.yata.ui.screen.list.ListDetailScreen
import com.mj.yata.ui.screen.search.SearchScreen
import com.mj.yata.ui.screen.settings.HelpAboutScreen
import com.mj.yata.ui.screen.settings.SettingsScreen
import com.mj.yata.ui.screen.trash.TrashScreen
import com.mj.yata.ui.screen.welcome.WelcomeScreen
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.YataEase

@Composable
fun AppNavigation(
    navController: NavHostController,
    onExportRequested: () -> Unit,
    onImportRequested: () -> Unit,
    onImportPlainTextRequested: () -> Unit,
    onExportCsvRequested: () -> Unit,
    onExportIcsRequested: () -> Unit,
    onCloudSignInRequested: () -> Unit
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
            arguments = listOf(
                navArgument("tab") { type = NavType.IntType; defaultValue = -1 },
                navArgument("quickAdd") { type = NavType.BoolType; defaultValue = false },
                navArgument("quickAddListId") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            val initialTab = backStackEntry.arguments?.getInt("tab") ?: -1
            val initialShowNewTaskSheet = backStackEntry.arguments?.getBoolean("quickAdd") ?: false
            val initialQuickAddListId = backStackEntry.arguments?.getString("quickAddListId")
            val viewModel: MainViewModel = hiltViewModel()
            MainScreen(
                viewModel = viewModel,
                navController = navController,
                initialTab = initialTab,
                initialShowNewTaskSheet = initialShowNewTaskSheet,
                initialQuickAddListId = initialQuickAddListId,
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToAnalytics = { navController.navigate(Screen.Analytics.route) },
                onNavigateToNextDays = { navController.navigate(Screen.NextDays.route) },
                onNavigateToSearch = { navController.navigate(Screen.Search.createRoute()) },
                onNavigateToSavedSearch = { filters -> navController.navigate(Screen.Search.createRoute(filters)) },
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
            val viewModel: MainViewModel = backStackEntry.sharedViewModel(navController)
            LaunchedEffect(taskId) {
                viewModel.recordTaskViewed(taskId)
            }
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
            val viewModel: MainViewModel = backStackEntry.sharedViewModel(navController)
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
            val viewModel: MainViewModel = backStackEntry.sharedViewModel(navController)
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
            val viewModel: MainViewModel = backStackEntry.sharedViewModel(navController)
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
            val viewModel: MainViewModel = backStackEntry.sharedViewModel(navController)
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

        // ── Welcome / onboarding ─────────────────────────────────────────────
        composable(Screen.Welcome.route) { backStackEntry ->
            val viewModel: MainViewModel = backStackEntry.sharedViewModel(navController)
            WelcomeScreen(
                onFinish = {
                    viewModel.setHasSeenWelcome()
                    navController.popBackStack()
                }
            )
        }

        // ── Search ────────────────────────────────────────────────────────────
        composable(
            route = Screen.Search.route,
            arguments = listOf(navArgument("filters") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val viewModel: MainViewModel = backStackEntry.sharedViewModel(navController)
            val initialFilters = backStackEntry.arguments?.getString("filters")?.let { Uri.decode(it) }
            SearchScreen(
                viewModel = viewModel,
                initialSmartFilterSet = initialFilters,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTaskDetail = { taskId ->
                    navController.navigate(Screen.TaskDetail.createRoute(taskId))
                },
                onNavigateToTab = onNavigateToTab
            )
        }

        // ── Settings ─────────────────────────────────────────────────────────
        composable(Screen.Settings.route) { backStackEntry ->
            val viewModel: MainViewModel = backStackEntry.sharedViewModel(navController)
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onExportRequested = onExportRequested,
                onImportRequested = onImportRequested,
                onImportPlainTextRequested = onImportPlainTextRequested,
                onExportCsvRequested = onExportCsvRequested,
                onExportIcsRequested = onExportIcsRequested,
                onCloudSignInRequested = onCloudSignInRequested,
                onNavigateToTab = onNavigateToTab,
                onNavigateToTrash = { navController.navigate(Screen.Trash.route) },
                onNavigateToWelcome = { navController.navigate(Screen.Welcome.route) },
                onNavigateToHelpAbout = { navController.navigate(Screen.HelpAbout.route) }
            )
        }

        // -- Help & About ----------------------------------------------------
        composable(Screen.HelpAbout.route) { backStackEntry ->
            val viewModel: MainViewModel = backStackEntry.sharedViewModel(navController)
            HelpAboutScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTab = onNavigateToTab
            )
        }

        // ── Analytics ────────────────────────────────────────────────────────
        composable(Screen.Analytics.route) { backStackEntry ->
            val viewModel: MainViewModel = backStackEntry.sharedViewModel(navController)
            AnalyticsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTab = onNavigateToTab
            )
        }

        // ── Trash ────────────────────────────────────────────────────────────
        composable(Screen.Trash.route) { backStackEntry ->
            val viewModel: MainViewModel = backStackEntry.sharedViewModel(navController)
            TrashScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTab = onNavigateToTab
            )
        }

        // ── Next 10 Days ─────────────────────────────────────────────────────
        composable(Screen.NextDays.route) { backStackEntry ->
            val viewModel: MainViewModel = backStackEntry.sharedViewModel(navController)
            NextDaysScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTaskDetail = { taskId ->
                    navController.navigate(Screen.TaskDetail.createRoute(taskId))
                },
                onNavigateToTab = onNavigateToTab
            )
        }
    }
}

@Composable
private fun NavBackStackEntry.sharedViewModel(navController: NavHostController): MainViewModel {
    val mainEntry = remember(this) {
        try {
            navController.getBackStackEntry(Screen.Main.route)
        } catch (e: Exception) {
            null
        }
    }
    return if (mainEntry != null) {
        hiltViewModel(mainEntry)
    } else {
        hiltViewModel()
    }
}
