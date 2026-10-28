package com.mj.yata.util

import com.mj.yata.data.github.*
import com.mj.yata.domain.model.BackupDestination
import com.mj.yata.domain.model.BackupRunResult
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.crypto.AEADBadTagException

/** Only a category crosses into the help UI; exception text can contain server or account data. */
enum class SyncFailureHelp {
    EMPTY_REPOSITORY, ACCESS, RATE_LIMIT, CONFLICT, HISTORY, PASSPHRASE, NETWORK, OTHER;

    companion object {
        fun from(error: Throwable): SyncFailureHelp {
            // Bounded and cycle-safe: exceptions from clients may wrap the useful cause.
            val causes = generateSequence(error) { it.cause }.take(8).toList()
            return when {
                causes.any { it is GitHubEmptyRepositoryException } -> EMPTY_REPOSITORY
                causes.any { it is GitHubRateLimitException } -> RATE_LIMIT
                causes.any { it is GitHubHistoryRewrittenException } -> HISTORY
                causes.any { it is GitHubConflictException } -> CONFLICT
                causes.any { it is GitHubAuthException || it is GitHubPermissionException ||
                    it is GitHubNotFoundException || it is GitHubPublicRepoException } -> ACCESS
                causes.any { it is AEADBadTagException ||
                    it.message?.contains("Wrong backup passphrase", ignoreCase = true) == true ||
                    it.message?.contains("backup passphrase", ignoreCase = true) == true } -> PASSPHRASE
                causes.any { it is UnknownHostException || it is SocketTimeoutException } -> NETWORK
                // Do not classify all IO errors as network errors: a full disk also throws IO.
                else -> OTHER
            }
        }
    }
}

fun List<BackupRunResult>.syncFailureHelp(): SyncFailureHelp? =
    firstOrNull { it.destination == BackupDestination.SELF_HOSTED && it.error != null }
        ?.error?.let { SyncFailureHelp.from(it) }
