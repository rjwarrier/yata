package com.mj.yata.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

/**
 * Records the ART baseline profile shipped with the app — the classes/methods AOT-compiled at
 * install time instead of being JIT'd on first run, which is what cuts cold start.
 *
 * Exercise the paths users actually hit on launch: the Today tab rendering, a tab switch, and
 * opening the new-task sheet. Anything not touched here stays interpreted on first run, so add
 * to this journey when a new screen becomes part of the common startup path.
 *
 * Generate (needs a rooted/userdebug device or emulator running API 28+):
 *   ./gradlew :baselineprofile:generateBaselineProfile
 * The output lands in app/src/main/baselineProfiles/ and is packaged automatically.
 */
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE_NAME) {
        pressHome()
        startActivityAndWait()

        // Today tab content — waits on the FAB rather than a fixed sleep, so the profile
        // captures a genuinely-settled first frame.
        device.wait(Until.hasObject(By.textContains("New task")), 10_000)

        // Tab switch: pulls in the other tab composables + their data flows.
        device.findObject(By.desc("Projects"))?.click()
        device.waitForIdle()
        device.findObject(By.desc("Today"))?.click()
        device.waitForIdle()

        // New-task sheet: the single most-used interactive surface after launch.
        device.findObject(By.textContains("New task"))?.click()
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()
    }

    private companion object {
        const val PACKAGE_NAME = "com.mj.yata"
    }
}
