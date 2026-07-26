package com.mj.yata

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.mj.yata.widget.WidgetEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device smoke suite for the paths every user hits: launch, add a task, complete it, switch
 * tabs, delete with undo. Runs against the real app (real Hilt graph, real Room DB on the test
 * device) — task titles are timestamped so reruns never collide with leftover rows or trip the
 * similar-task duplicate dialog.
 *
 * Run: ./gradlew :app:connectedDebugAndroidTest --tests "com.mj.yata.MainScreenSmokeTest"
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class MainScreenSmokeTest {

    // Pre-grant so the POST_NOTIFICATIONS system prompt never covers the UI mid-test.
    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule =
        if (Build.VERSION.SDK_INT >= 33) GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
        else GrantPermissionRule.grant()

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun launch() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Skip the first-run Welcome screen — MainActivity redirects there when unseen, and the
        // rule launches before any test body could dismiss it.
        val prefs = EntryPointAccessors
            .fromApplication(context, WidgetEntryPoint::class.java)
            .userPreferences()
        runBlocking { prefs.setHasSeenWelcome(true) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun close() {
        if (::scenario.isInitialized) scenario.close()
    }

    private fun uniqueTitle(prefix: String) = "$prefix ${System.currentTimeMillis()}"

    private fun addTask(title: String) {
        compose.waitUntilAtLeastOneExists(hasText("New task"), 10_000)
        compose.onNodeWithText("New task").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("new_task_title_input"), 5_000)
        compose.onNodeWithTag("new_task_title_input").performTextInput(title)
        compose.onNodeWithContentDescription("Create task").performClick()
        compose.waitUntilAtLeastOneExists(hasText(title), 10_000)
    }

    @Test
    fun appLaunches_todayTabShown() {
        compose.waitUntilAtLeastOneExists(hasText("New task"), 10_000)
        compose.onNodeWithContentDescription("Today").assertExists()
    }

    @Test
    fun addTask_showsInToday() {
        val title = uniqueTitle("Smoke add")
        addTask(title)
        compose.onNodeWithText(title).assertExists()
    }

    @Test
    fun completeTask_showsCompletedSection() {
        val title = uniqueTitle("Smoke done")
        addTask(title)
        compose.onNodeWithTag("task_check:$title").performClick()
        compose.waitUntilAtLeastOneExists(hasText("COMPLETED"), 10_000)
    }

    @Test
    fun tabSwitching_rendersEachTab() {
        compose.waitUntilAtLeastOneExists(hasText("New task"), 10_000)
        // Bottom-nav icons carry their tab label as contentDescription; drawer rows use null,
        // so each label matches exactly one node.
        listOf("Projects", "People", "Tags", "Upcoming", "Today").forEach { tab ->
            compose.onNodeWithContentDescription(tab).performClick()
            compose.waitForIdle()
        }
        compose.onNodeWithText("New task").assertExists()
    }

    @Test
    fun deleteTask_undoRestoresIt() {
        val title = uniqueTitle("Smoke delete")
        addTask(title)
        compose.onNodeWithText(title).performClick() // open TaskDetail
        compose.waitUntilAtLeastOneExists(hasContentDescription("Delete task"), 5_000)
        compose.onNodeWithContentDescription("Delete task").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Undo"), 5_000)
        compose.onNodeWithText("Undo").performClick()
        // Undo keeps the task and stays on the detail screen — the title must survive.
        compose.waitUntilAtLeastOneExists(hasText(title), 10_000)
    }
}
