package com.mj.yata.domain.sync

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HISTORY_NAME =
    Regex("""yata_backup_(\d{8})_(\d{6})\.(json|zip)(\.enc)?""")

fun restorePointFromHistoryName(nameOrPath: String): RestorePoint {
    val name = nameOrPath.substringAfterLast('/')
    return RestorePoint(
        id = nameOrPath,
        label = name,
        createdAt = backupCreatedInstantFromName(name)
    )
}

fun backupCreatedInstantFromName(nameOrPath: String): Instant? {
    val name = nameOrPath.substringAfterLast('/')
    val match = HISTORY_NAME.matchEntire(name) ?: return null
    return runCatching {
        val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.US)
        LocalDateTime.parse(match.groupValues[1] + match.groupValues[2], formatter)
            .atZone(ZoneId.systemDefault())
            .toInstant()
    }.getOrNull()
}
