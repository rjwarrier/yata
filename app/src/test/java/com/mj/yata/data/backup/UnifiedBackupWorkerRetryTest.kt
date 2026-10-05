package com.mj.yata.data.backup

import com.mj.yata.data.github.GitHubAuthException
import com.mj.yata.data.github.GitHubConflictException
import com.mj.yata.data.github.GitHubHistoryRewrittenException
import com.mj.yata.data.github.GitHubNotFoundException
import com.mj.yata.data.github.GitHubPermissionException
import com.mj.yata.data.github.GitHubTransportException
import com.mj.yata.domain.model.BackupDestination
import com.mj.yata.domain.model.BackupRunResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedBackupWorkerRetryTest {

    @Test
    fun permanentGitHubFailuresDoNotRetryScheduledBackup() {
        listOf(
            GitHubAuthException(),
            GitHubPermissionException(),
            GitHubNotFoundException(),
            GitHubHistoryRewrittenException()
        ).forEach { error ->
            assertFalse(backupFailureShouldRetry(error.result()))
        }
    }

    @Test
    fun transientGitHubFailuresRetryScheduledBackup() {
        listOf(
            GitHubConflictException(),
            GitHubTransportException()
        ).forEach { error ->
            assertTrue(backupFailureShouldRetry(error.result()))
        }
    }

    private fun Throwable.result(): BackupRunResult =
        BackupRunResult(BackupDestination.SELF_HOSTED, this)
}
