package com.mj.yata.util.export

import android.content.Context
import java.io.File

enum class ExportFormat { IMAGE, PDF }

enum class ExportDestination { SHARE, SAVE_TO_DOWNLOADS }

enum class ExportPdfPageSize(val label: String, val ratio: Float) {
    A4("A4", 1.414f),
    LETTER("Letter", 11f / 8.5f)
}

enum class ExportImageScale(val label: String, val widthDp: Float) {
    STANDARD("Standard", 420f),
    LARGE("Large", 560f)
}

data class ExportItemPreview(
    val done: Boolean,
    val completedAt: Long?
)

data class EntityExportOptions(
    val includeCompleted: Boolean,
    val excludeCompletedOlderThanDays: Int?,
    val density: ExportDensity,
    val strikeThroughCompleted: Boolean,
    val showTags: Boolean,
    val showAssignees: Boolean,
    val showMadeWithFooter: Boolean,
    val privacyMode: Boolean,
    val destination: ExportDestination,
    val fileNameBase: String,
    val pdfPageSize: ExportPdfPageSize,
    val imageScale: ExportImageScale
)

data class TaskExportOptions(
    val includeNotes: Boolean,
    val includeComments: Boolean,
    val includeSubtasks: Boolean,
    val includeScheduleDetails: Boolean,
    val showMadeWithFooter: Boolean,
    val privacyMode: Boolean,
    val destination: ExportDestination,
    val fileNameBase: String,
    val pdfPageSize: ExportPdfPageSize,
    val imageScale: ExportImageScale
)

data class ExportOutcome(
    val file: File,
    val destination: ExportDestination,
    val pageCount: Int = 1
) {
    fun userMessage(): String =
        if (destination == ExportDestination.SAVE_TO_DOWNLOADS) {
            "Export saved to Downloads (${pageCount} page${if (pageCount == 1) "" else "s"})."
        } else {
            "Export ready to share (${pageCount} page${if (pageCount == 1) "" else "s"})."
        }
}

private const val PREFS_NAME = "yata_export_options"

private object ExportPrefKeys {
    const val INCLUDE_COMPLETED = "include_completed"
    const val COMPLETED_DAYS = "completed_days"
    const val DENSITY = "density"
    const val STRIKE_COMPLETED = "strike_completed"
    const val SHOW_TAGS = "show_tags"
    const val SHOW_ASSIGNEES = "show_assignees"
    const val SHOW_FOOTER = "show_footer"
    const val PRIVACY = "privacy"
    const val DESTINATION = "destination"
    const val PDF_PAGE_SIZE = "pdf_page_size"
    const val IMAGE_SCALE = "image_scale"
    const val TASK_INCLUDE_NOTES = "task_include_notes"
    const val TASK_INCLUDE_COMMENTS = "task_include_comments"
    const val TASK_INCLUDE_SUBTASKS = "task_include_subtasks"
    const val TASK_INCLUDE_SCHEDULE = "task_include_schedule"
}

internal fun defaultEntityExportOptions(context: Context, entityName: String): EntityExportOptions {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val privacy = prefs.getBoolean(ExportPrefKeys.PRIVACY, false)
    return EntityExportOptions(
        includeCompleted = prefs.getBoolean(ExportPrefKeys.INCLUDE_COMPLETED, true),
        excludeCompletedOlderThanDays = prefs.getInt(ExportPrefKeys.COMPLETED_DAYS, 0).takeIf { it > 0 },
        density = enumValueOrDefault(prefs.getString(ExportPrefKeys.DENSITY, null), ExportDensity.RELAXED),
        strikeThroughCompleted = prefs.getBoolean(ExportPrefKeys.STRIKE_COMPLETED, false),
        showTags = !privacy && prefs.getBoolean(ExportPrefKeys.SHOW_TAGS, true),
        showAssignees = !privacy && prefs.getBoolean(ExportPrefKeys.SHOW_ASSIGNEES, true),
        showMadeWithFooter = prefs.getBoolean(ExportPrefKeys.SHOW_FOOTER, true),
        privacyMode = privacy,
        destination = enumValueOrDefault(prefs.getString(ExportPrefKeys.DESTINATION, null), ExportDestination.SHARE),
        fileNameBase = "yata_${sanitizeExportFileName(entityName)}",
        pdfPageSize = enumValueOrDefault(prefs.getString(ExportPrefKeys.PDF_PAGE_SIZE, null), ExportPdfPageSize.A4),
        imageScale = enumValueOrDefault(prefs.getString(ExportPrefKeys.IMAGE_SCALE, null), ExportImageScale.STANDARD)
    )
}

internal fun defaultTaskExportOptions(context: Context, title: String): TaskExportOptions {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val privacy = prefs.getBoolean(ExportPrefKeys.PRIVACY, false)
    return TaskExportOptions(
        includeNotes = !privacy && prefs.getBoolean(ExportPrefKeys.TASK_INCLUDE_NOTES, true),
        includeComments = !privacy && prefs.getBoolean(ExportPrefKeys.TASK_INCLUDE_COMMENTS, true),
        includeSubtasks = prefs.getBoolean(ExportPrefKeys.TASK_INCLUDE_SUBTASKS, true),
        includeScheduleDetails = prefs.getBoolean(ExportPrefKeys.TASK_INCLUDE_SCHEDULE, true),
        showMadeWithFooter = prefs.getBoolean(ExportPrefKeys.SHOW_FOOTER, true),
        privacyMode = privacy,
        destination = enumValueOrDefault(prefs.getString(ExportPrefKeys.DESTINATION, null), ExportDestination.SHARE),
        fileNameBase = "yata_${sanitizeExportFileName(title)}",
        pdfPageSize = enumValueOrDefault(prefs.getString(ExportPrefKeys.PDF_PAGE_SIZE, null), ExportPdfPageSize.A4),
        imageScale = enumValueOrDefault(prefs.getString(ExportPrefKeys.IMAGE_SCALE, null), ExportImageScale.STANDARD)
    )
}

internal fun rememberEntityExportOptions(context: Context, options: EntityExportOptions) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putBoolean(ExportPrefKeys.INCLUDE_COMPLETED, options.includeCompleted)
        .putInt(ExportPrefKeys.COMPLETED_DAYS, options.excludeCompletedOlderThanDays ?: 0)
        .putString(ExportPrefKeys.DENSITY, options.density.name)
        .putBoolean(ExportPrefKeys.STRIKE_COMPLETED, options.strikeThroughCompleted)
        .putBoolean(ExportPrefKeys.SHOW_TAGS, options.showTags)
        .putBoolean(ExportPrefKeys.SHOW_ASSIGNEES, options.showAssignees)
        .putBoolean(ExportPrefKeys.SHOW_FOOTER, options.showMadeWithFooter)
        .putBoolean(ExportPrefKeys.PRIVACY, options.privacyMode)
        .putString(ExportPrefKeys.DESTINATION, options.destination.name)
        .putString(ExportPrefKeys.PDF_PAGE_SIZE, options.pdfPageSize.name)
        .putString(ExportPrefKeys.IMAGE_SCALE, options.imageScale.name)
        .apply()
}

internal fun rememberTaskExportOptions(context: Context, options: TaskExportOptions) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putBoolean(ExportPrefKeys.TASK_INCLUDE_NOTES, options.includeNotes)
        .putBoolean(ExportPrefKeys.TASK_INCLUDE_COMMENTS, options.includeComments)
        .putBoolean(ExportPrefKeys.TASK_INCLUDE_SUBTASKS, options.includeSubtasks)
        .putBoolean(ExportPrefKeys.TASK_INCLUDE_SCHEDULE, options.includeScheduleDetails)
        .putBoolean(ExportPrefKeys.SHOW_FOOTER, options.showMadeWithFooter)
        .putBoolean(ExportPrefKeys.PRIVACY, options.privacyMode)
        .putString(ExportPrefKeys.DESTINATION, options.destination.name)
        .putString(ExportPrefKeys.PDF_PAGE_SIZE, options.pdfPageSize.name)
        .putString(ExportPrefKeys.IMAGE_SCALE, options.imageScale.name)
        .apply()
}

fun estimateExportPdfPages(itemCount: Int, density: ExportDensity): Int {
    val rowsPerPage = if (density == ExportDensity.COMPACT) 15 else 11
    return ((itemCount.coerceAtLeast(1) + rowsPerPage - 1) / rowsPerPage).coerceAtLeast(1)
}

fun filteredExportPreviewCount(
    items: List<ExportItemPreview>,
    includeCompleted: Boolean,
    excludeCompletedOlderThanDays: Int?
): Int {
    val cutoffMillis = excludeCompletedOlderThanDays?.takeIf { it > 0 }?.let {
        System.currentTimeMillis() - it.toLong() * 24 * 60 * 60 * 1000
    }
    return items.count { item ->
        if (!item.done) true
        else includeCompleted && (cutoffMillis == null || (item.completedAt != null && item.completedAt >= cutoffMillis))
    }
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String?, defaultValue: T): T =
    name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: defaultValue
