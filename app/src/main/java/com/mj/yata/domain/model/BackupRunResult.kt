package com.mj.yata.domain.model

/** Where a backup can be written. Each is independent — configuring more than one is the point. */
enum class BackupDestination {
    LOCAL,
    SELF_HOSTED
}

/**
 * Outcome of backing up one destination during a run that covers all of them.
 *
 * Per-destination rather than a single overall verdict because destinations exist for redundancy:
 * "Local worked, your server didn't" is the useful thing to know, and collapsing that into one
 * boolean would either hide a real failure or cry wolf about a backup that did land somewhere.
 */
data class BackupRunResult(
    val destination: BackupDestination,
    val error: Throwable?
) {
    val isSuccess: Boolean get() = error == null
}
