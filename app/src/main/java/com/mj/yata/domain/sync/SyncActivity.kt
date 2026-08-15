package com.mj.yata.domain.sync

/**
 * The commit-message convention every published GitHub snapshot is written with, plus the parser
 * that reads it back for the sync activity feed. Both live here so they can't drift apart: the
 * feed's "which device" column is only ever as good as the format the publisher wrote, and a
 * one-sided change to either would silently degrade every row to unattributed.
 *
 * Format: `YATA sync from <device> - <summary>`, e.g. `YATA sync from Pixel 8 - 42 tasks, 5 projects`.
 *
 * This is a free-text convention inside the commit message, not structured metadata — GitHub's own
 * commit author is the token owner, which is identical across every device sharing one PAT and so
 * can't distinguish them. That makes parsing best-effort by nature: anything not matching the
 * format (a commit made by hand, by another tool, or by a YATA old enough to predate this format)
 * yields a null device and keeps the raw text as the summary, rather than being dropped. A feed
 * that silently omits commits would misrepresent the repo's history, which is the opposite of what
 * it exists to do; an unattributed row is honest about what's known.
 */
object SyncCommitMessage {
    private const val PREFIX = "YATA sync from "
    private const val SEPARATOR = " - "

    fun format(device: String, summary: String): String = "$PREFIX$device$SEPARATOR$summary"

    fun parse(message: String): ParsedSyncCommit {
        val firstLine = message.lineSequence().firstOrNull()?.trim().orEmpty()
        if (!firstLine.startsWith(PREFIX)) {
            return ParsedSyncCommit(device = null, summary = firstLine.ifBlank { null })
        }
        val body = firstLine.removePrefix(PREFIX)
        // lastIndexOf rather than indexOf: the summary half never contains the separator, but the
        // device half is vendor-supplied Build.MANUFACTURER/MODEL text and could.
        val separatorAt = body.lastIndexOf(SEPARATOR)
        if (separatorAt <= 0) {
            return ParsedSyncCommit(device = body.trim().ifBlank { null }, summary = null)
        }
        return ParsedSyncCommit(
            device = body.substring(0, separatorAt).trim().ifBlank { null },
            summary = body.substring(separatorAt + SEPARATOR.length).trim().ifBlank { null }
        )
    }
}

data class ParsedSyncCommit(
    /** Null when the commit wasn't written by YATA, or predates the device-labelled format. */
    val device: String?,
    val summary: String?
)

/**
 * How a device names itself in the commit messages it publishes.
 *
 * Prefers the name the user set in Android's Settings ("Device name" under About phone), falling
 * back to `Build.MANUFACTURER`/`Build.MODEL`. That ordering is what makes two identical handsets
 * tellable apart in the feed without this app inventing — and having to migrate, persist and sync —
 * a device identity of its own: the OS already has a user-owned name for the device, and renaming
 * it there is a place people already know to look. Not every ROM populates it, hence the fallback.
 *
 * Neither form is stable across a rename, so [fromModel] stays reachable on its own: commits
 * published before a rename (or before this preferred the Settings name at all) still carry the
 * model-derived label, and matching *both* is what keeps "this device" attribution working across
 * that change instead of quietly disowning a device's own history.
 *
 * The fallback string is intentionally untranslated: it is written *into git history* and read back
 * by other devices that may be running a different language, so it is data, not display text.
 */
object SyncDeviceLabel {
    const val UNKNOWN = "Unknown Android device"

    /** [deviceName] is Android's user-set `Settings.Global.DEVICE_NAME`; null/blank when unset. */
    fun of(deviceName: String?, manufacturer: String?, model: String?): String =
        deviceName?.trim()?.takeIf { it.isNotBlank() } ?: fromModel(manufacturer, model)

    fun fromModel(manufacturer: String?, model: String?): String {
        val cleanManufacturer = manufacturer.orEmpty().trim()
        val cleanModel = model.orEmpty().trim()
        val label = if (
            cleanManufacturer.isNotBlank() &&
            cleanModel.startsWith(cleanManufacturer, ignoreCase = true)
        ) {
            cleanModel
        } else {
            listOf(cleanManufacturer, cleanModel).filter { it.isNotBlank() }.joinToString(" ")
        }
        return label.ifBlank { UNKNOWN }
    }
}
