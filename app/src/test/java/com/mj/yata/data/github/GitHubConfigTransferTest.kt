package com.mj.yata.data.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GitHubConfigTransferTest {

    private val payload = GitHubConfigTransferPayload(
        owner = "owner",
        repo = "sync-repo",
        branch = "main",
        apiBase = "https://api.github.com",
        token = "github_pat_secret_token",
        tokenExpiresAt = 1_826_000_000_000L,
        backupPassphrase = "backup secret"
    )

    @Test
    fun roundTripsGitHubConfig() {
        val exportText = GitHubConfigTransfer.encryptToJson(payload, "transfer password")
        val imported = GitHubConfigTransfer.decryptFromJson(exportText, "transfer password")

        assertEquals(payload, imported)
    }

    @Test
    fun exportDoesNotExposeSensitiveFieldsAsPlaintext() {
        val exportText = GitHubConfigTransfer.encryptToJson(payload, "transfer password")

        assertFalse(exportText.contains("\n"))
        assertFalse(exportText.contains(payload.owner))
        assertFalse(exportText.contains(payload.repo))
        assertFalse(exportText.contains(payload.token))
        assertFalse(exportText.contains(payload.backupPassphrase!!))
    }

    @Test(expected = IllegalArgumentException::class)
    fun wrongPasswordFails() {
        val exportText = GitHubConfigTransfer.encryptToJson(payload, "right password")

        GitHubConfigTransfer.decryptFromJson(exportText, "wrong password")
    }

    @Test(expected = IllegalArgumentException::class)
    fun excessiveIterationsAreRejected() {
        val exportText = GitHubConfigTransfer.encryptToJson(payload, "transfer password")
            .replace("\"iterations\":150000", "\"iterations\":2147483647")

        GitHubConfigTransfer.decryptFromJson(exportText, "transfer password")
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingBackupPassphraseIsRejected() {
        val noPassphrase = payload.copy(backupPassphrase = null)

        GitHubConfigTransfer.encryptToJson(noPassphrase, "transfer password")
    }
}
