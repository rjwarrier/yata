package com.mj.yata.data.local.db

import android.app.Application
import android.content.Context
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class UnifiedDatabaseMigrationTest : UnifiedDatabaseMigrationContract() {
    override val context: Context get() = RuntimeEnvironment.getApplication()
    override fun fixture(name: String): String =
        File("src/androidTest/assets/legacy-schemas/$name.json").readText()
}
