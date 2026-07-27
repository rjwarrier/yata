package com.mj.yata.util.export

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import com.mj.yata.util.localized

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
    accentColor: Color,
    showMadeWithFooter: Boolean = true,
    recurrenceLabel: String? = null,
    reminderLabel: String? = null,
    subtasks: List<ExportSubtaskRow> = emptyList(),
    includeSubtasks: Boolean = true,
    includeScheduleDetails: Boolean = true,
    destination: ExportDestination = ExportDestination.SHARE,
    fileNameBase: String = "yata_${sanitizeExportFileName(title)}",
    pdfPageSize: ExportPdfPageSize = ExportPdfPageSize.A4,
    imageScale: ExportImageScale = ExportImageScale.STANDARD
): ExportOutcome {
    val displayDensity = context.resources.displayMetrics.density
    val widthPx = (imageScale.widthDp.dp.value * displayDensity).toInt()
    val generatedOn = LocalDateTime.now().localized()

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
            recurrenceLabel = recurrenceLabel,
            reminderLabel = reminderLabel,
            subtasks = subtasks,
            includeSubtasks = includeSubtasks,
            includeScheduleDetails = includeScheduleDetails,
            accentColor = accentColor,
            generatedOn = generatedOn,
            showMadeWithFooter = showMadeWithFooter,
            cardWidth = imageScale.widthDp.dp,
            onRowBoundary = { rowBreaks.add(it) }
        )
    }

    val baseName = sanitizeExportFileName(fileNameBase)
    when (format) {
        ExportFormat.IMAGE -> {
            val file = saveBitmapAsPng(context, bitmap, "$baseName.png")
            return deliverExportedFile(context, file, "image/png", "Share $title", destination)
        }
        ExportFormat.PDF -> {
            val file = saveBitmapAsPdf(context, bitmap, "$baseName.pdf", rowBreaks, pdfPageSize)
            applyPdfMetadata(
                context = context,
                file = file,
                title = "$title — YATA Task",
                subject = "YATA task export: $title",
                keywords = "YATA, task, $title"
            )
            return deliverExportedFile(context, file, "application/pdf", "Share $title", destination)
        }
    }
}
