package com.mj.yata

import com.mj.yata.domain.model.BackupDestination
import com.mj.yata.domain.model.BackupRunResult
import com.mj.yata.domain.usecase.runScheduledBackup
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ScheduledBackupRetryTest {
    @Test fun `partial failure retries same fingerprint until all destinations succeed`() = runTest {
        var settled: String? = null
        var attempts = 0
        var remoteFails = true
        suspend fun tick() = runScheduledBackup(settled, "same-data", false, {
            attempts++
            listOf(BackupRunResult(BackupDestination.LOCAL, null),
                BackupRunResult(BackupDestination.SELF_HOSTED, if (remoteFails) java.io.IOException("offline") else null))
        }, { settled = "same-data" })
        assertEquals(false, tick()!!.last().isSuccess)
        assertNull(settled)
        remoteFails = false
        assertTrue(tick()!!.all { it.isSuccess })
        assertEquals("same-data", settled)
        assertNull(tick())
        assertEquals(2, attempts)
    }

    @Test fun `remote polling continues without local edits`() = runTest {
        var attempts = 0
        repeat(2) {
            runScheduledBackup("same", "same", true, {
                attempts++
                listOf(BackupRunResult(BackupDestination.SELF_HOSTED, null))
            }, {})
        }
        assertEquals(2, attempts)
    }
}
