package com.mj.yata.domain.model

/**
 * What's actually inside a remote backup file, read before restoring it.
 *
 * Restore overwrites live data, and a filename plus timestamp is not enough to tell a full backup
 * from one taken when the database was nearly empty — which is exactly the mistake worth making
 * expensive. Counting the contents first turns "restore the 2pm one" into a decision the user can
 * actually check.
 */
data class BackupSummary(
    val totalTasks: Int,
    val openTasks: Int,
    val totalProjects: Int,
    val createdByDevice: String? = null
)
