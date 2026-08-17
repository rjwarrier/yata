package com.mj.yata.util.export

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import com.mj.yata.R
import com.mj.yata.util.localized

private const val ShareImageStandardWidthPx = 2160
private const val ShareImageLargeWidthPx = 3240

/**
 * Renders a [TaskExportCard] for a single task off-screen and shares it as PDF, or a
 * [TaskShareCard] for a portrait chat-share JPEG, via the system share sheet — the task-level
 * counterpart to [exportEntityReport]. The two formats intentionally use different cards: the
 * PDF report is a dense, print-oriented layout (subtasks, schedule details, project), while the
 * IMAGE path is sized and composed for WhatsApp/Telegram/Instagram share-sheet previews and
 * deliberately omits subtasks/schedule to match its reference design.
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
    assignees: List<ExportPersonChip>,
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
    sharedByName: String = "",
    sharedByInitials: String = "",
    sharedByAccentKey: String = "accentA",
    sharedByPhotoUri: String? = null,
    darkTheme: Boolean = false,
    destination: ExportDestination = ExportDestination.SHARE,
    fileNameBase: String = "yata_${sanitizeExportFileName(title)}",
    pdfPageSize: ExportPdfPageSize = ExportPdfPageSize.A4,
    imageScale: ExportImageScale = ExportImageScale.STANDARD,
    transferText: String? = null
): ExportOutcome {
    val generatedOn = LocalDateTime.now().localized()
    val baseName = sanitizeExportFileName(fileNameBase)

    when (format) {
        ExportFormat.IMAGE -> {
            val widthPx = if (imageScale == ExportImageScale.LARGE) ShareImageLargeWidthPx else ShareImageStandardWidthPx
            val sharedOnLabel = context.getString(R.string.export_shared_on, generatedOn)
            val bitmap = captureTaskShareBitmap(context, widthPx) {
                TaskShareCard(
                    title = title,
                    done = done,
                    priority = priority,
                    flagged = flagged,
                    dueLabel = dueLabel,
                    overdue = overdue,
                    completedAtLabel = completedAtLabel,
                    listName = listName,
                    assignees = assignees,
                    tagChips = tagChips,
                    notes = notes,
                    includeNotes = includeNotes,
                    comments = comments,
                    includeComments = includeComments,
                    sharedByName = sharedByName,
                    sharedByInitials = sharedByInitials,
                    sharedByAccentKey = sharedByAccentKey,
                    sharedByPhotoUri = sharedByPhotoUri,
                    sharedOnLabel = sharedOnLabel,
                    darkTheme = darkTheme
                )
            }
            val file = saveBitmapAsJpeg(context, bitmap, "$baseName.jpg")
            return deliverExportedFile(context, file, "image/jpeg", "Share $title", destination, transferText)
        }
        ExportFormat.PDF -> {
            val displayDensity = context.resources.displayMetrics.density
            val widthPx = (imageScale.widthDp.dp.value * displayDensity).toInt()
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
                    assigneeNames = assignees.map { it.name },
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
            val file = saveBitmapAsPdf(context, bitmap, "$baseName.pdf", rowBreaks, pdfPageSize)
            applyPdfMetadata(
                context = context,
                file = file,
                title = "$title — YATA Task",
                subject = "YATA task export: $title",
                keywords = "YATA, task, $title"
            )
            return deliverExportedFile(context, file, "application/pdf", "Share $title", destination, transferText)
        }
    }
}
