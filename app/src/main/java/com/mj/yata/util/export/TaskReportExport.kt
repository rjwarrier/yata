package com.mj.yata.util.export

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Renders a [TaskExportCard] for a single task off-screen and shares it as PNG or PDF via the
 * system share sheet — the task-level counterpart to [exportEntityReport].
 */
suspend fun exportTaskReport(
    context: Context,
    format: ExportFormat,
    title: String,
    done: Boolean,
    priority: String,
    flagged: Boolean,
    dueLabel: String?,
    overdue: Boolean,
    completedAtLabel: String?,
    projectName: String?,
    listName: String?,
    assigneeNames: List<String>,
    tagChips: List<ExportTagChip>,
    notes: String?,
    includeNotes: Boolean,
    comments: List<ExportCommentRow>,
    includeComments: Boolean,
    accentColor: Color
) {
    val displayDensity = context.resources.displayMetrics.density
    val widthPx = (420.dp.value * displayDensity).toInt()
    val generatedOn = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a"))

    val rowBreaks = mutableListOf<Float>()
    val bitmap = captureComposableToBitmap(context, widthPx) {
        TaskExportCard(
            title = title,
            done = done,
            priority = priority,
            flagged = flagged,
            dueLabel = dueLabel,
            overdue = overdue,
            completedAtLabel = completedAtLabel,
            projectName = projectName,
            listName = listName,
            assigneeNames = assigneeNames,
            tagChips = tagChips,
            notes = notes,
            includeNotes = includeNotes,
            comments = comments,
            includeComments = includeComments,
            accentColor = accentColor,
            generatedOn = generatedOn,
            onRowBoundary = { rowBreaks.add(it) }
        )
    }

    val baseName = "yata_${sanitizeExportFileName(title)}"
    when (format) {
        ExportFormat.IMAGE -> {
            val file = saveBitmapAsPng(context, bitmap, "$baseName.png")
            shareExportedFile(context, file, "image/png", "Share $title")
        }
        ExportFormat.PDF -> {
            val file = saveBitmapAsPdf(context, bitmap, "$baseName.pdf", rowBreaks)
            applyPdfMetadata(
                context = context,
                file = file,
                title = "$title — YATA Task",
                subject = "YATA task export: $title",
                keywords = "YATA, task, $title"
            )
            shareExportedFile(context, file, "application/pdf", "Share $title")
        }
    }
}
