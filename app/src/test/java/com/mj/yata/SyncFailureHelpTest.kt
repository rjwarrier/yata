package com.mj.yata

import com.mj.yata.data.github.*
import com.mj.yata.domain.model.BackupDestination
import com.mj.yata.domain.model.BackupRunResult
import com.mj.yata.util.SyncFailureHelp
import com.mj.yata.util.syncFailureHelp
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

class SyncFailureHelpTest {
    @Test fun diagnosesWrappedFailuresWithoutExposingTheirMessages() {
        val cases = listOf(
            GitHubEmptyRepositoryException() to SyncFailureHelp.EMPTY_REPOSITORY,
            GitHubAuthException("secret token") to SyncFailureHelp.ACCESS,
            GitHubPermissionException() to SyncFailureHelp.ACCESS,
            GitHubNotFoundException() to SyncFailureHelp.ACCESS,
            GitHubPublicRepoException() to SyncFailureHelp.ACCESS,
            GitHubRateLimitException(null) to SyncFailureHelp.RATE_LIMIT,
            GitHubConflictException() to SyncFailureHelp.CONFLICT,
            GitHubHistoryRewrittenException() to SyncFailureHelp.HISTORY,
            IllegalStateException("Wrong backup passphrase, or the GitHub snapshot is damaged") to SyncFailureHelp.PASSPHRASE,
            UnknownHostException("private host") to SyncFailureHelp.NETWORK,
            java.security.cert.CertificateException("TLS certificate") to SyncFailureHelp.OTHER,
            IOException("Disk full") to SyncFailureHelp.OTHER
        )
        cases.forEach { (error, expected) ->
            assertEquals(expected, SyncFailureHelp.from(IllegalStateException("wrapper", error)))
        }
    }

    @Test fun localBackupFailureDoesNotOpenRemoteSyncHelp() {
        assertNull(listOf(BackupRunResult(BackupDestination.LOCAL, IOException())).syncFailureHelp())
        assertNull(listOf(BackupRunResult(BackupDestination.SELF_HOSTED, null)).syncFailureHelp())
        assertEquals(SyncFailureHelp.EMPTY_REPOSITORY, listOf(
            BackupRunResult(BackupDestination.LOCAL, IOException()),
            BackupRunResult(BackupDestination.SELF_HOSTED, GitHubEmptyRepositoryException())
        ).syncFailureHelp())
    }

    @Test fun cyclicCausesDoNotHangTheHelpDialog() {
        val first = Exception()
        val second = Exception(first)
        first.initCause(second)
        assertEquals(SyncFailureHelp.OTHER, SyncFailureHelp.from(first))
    }
}
