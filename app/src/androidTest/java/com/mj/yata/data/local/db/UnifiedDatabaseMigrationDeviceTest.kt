package com.mj.yata.data.local.db

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.RunWith

/** Run only on a disposable emulator; inherited tests create and delete test databases. */
@RunWith(AndroidJUnit4::class)
class UnifiedDatabaseMigrationDeviceTest : UnifiedDatabaseMigrationContract() {
    override val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    override fun fixture(name: String): String =
        InstrumentationRegistry.getInstrumentation().context.assets.open("legacy-schemas/$name.json")
            .bufferedReader().use { it.readText() }
}
