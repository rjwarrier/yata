package com.mj.yata.util

import android.content.Context
import com.mj.yata.R
import com.mj.yata.domain.model.BackupDestination
import com.mj.yata.domain.model.BackupRunResult

/** [text] to show, and whether it should be styled as a failure. */
data class BackupResultMessage(val text: String, val isError: Boolean)

/**
 * Turns a multi-destination backup run into one message.
 *
 * Shared by every "back up now" entry point (the Today top bar and both Settings sections) so they
 * can't drift into describing the same run differently. Names the destinations that failed instead
 * of collapsing to "sync failed": once more than one destination is configured, a blanket failure
 * for a run where two of three succeeded is wrong in the direction that costs the user something —
 * it either hides a real failure or makes them distrust copies that are fine.
 */
fun backupResultMessage(results: List<BackupRunResult>, context: Context): BackupResultMessage {
    if (results.isEmpty()) {
        return BackupResultMessage(context.getString(R.string.settings_backup_none_configured), isError = true)
    }
    val failed = results.filter { !it.isSuccess }
    if (failed.isEmpty()) {
        return BackupResultMessage(
            context.resources.getQuantityString(R.plurals.settings_backup_all_ok, results.size, results.size),
            isError = false
        )
    }
    val failedNames = failed.joinToString(", ") { context.getString(backupDestinationLabel(it.destination)) }
    return if (failed.size == results.size) {
        BackupResultMessage(context.getString(R.string.settings_backup_all_failed, failedNames), isError = true)
    } else {
        BackupResultMessage(
            context.getString(R.string.settings_backup_partial, results.size - failed.size, failedNames),
            isError = true
        )
    }
}

fun backupDestinationLabel(destination: BackupDestination): Int = when (destination) {
    BackupDestination.LOCAL -> R.string.settings_backup_dest_local
    BackupDestination.SELF_HOSTED -> R.string.settings_backup_dest_self_hosted
}
