package com.mj.yata.util.export

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class ExportFormat { IMAGE, PDF }

private val cardWidth: Dp = 420.dp

/**
 * Renders a [BrandedExportCard] for a tag/person off-screen and shares it as PNG or PDF via
 * the system share sheet. Shared by TagDetailScreen and PersonDetailScreen so both entity
 * types get the exact same branded report — see [BrandedExportCard] for why it builds its own
 * theme rather than reusing YataTheme.
 */
suspend fun exportEntityReport(
    context: Context,
    format: ExportFormat,
    entityKind: String,
    entityName: String,
    accentColor: Color,
    doneCount: Int,
    totalCount: Int,
    overdueCount: Int,
    tasks: List<ExportTaskRow>,
    layoutDensity: ExportDensity = ExportDensity.RELAXED,
    strikeThroughCompleted: Boolean = false,
    showTags: Boolean = true,
    showAssignees: Boolean = true
) {
    val displayDensity = context.resources.displayMetrics.density
    val widthPx = (cardWidth.value * displayDensity).toInt()
    val generatedOn = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a"))

    // Collected as a side effect of BrandedExportCard's layout pass — see saveBitmapAsPdf for
    // why the PDF path needs these (only legal page-break points, so rows never split).
    val rowBreaks = mutableListOf<Float>()
    val bitmap = captureComposableToBitmap(context, widthPx) {
        BrandedExportCard(
            entityKind = entityKind,
            entityName = entityName,
            accentColor = accentColor,
            doneCount = doneCount,
            totalCount = totalCount,
            overdueCount = overdueCount,
            tasks = tasks,
            generatedOn = generatedOn,
            density = layoutDensity,
            strikeThroughCompleted = strikeThroughCompleted,
            showTags = showTags,
            showAssignees = showAssignees,
            onRowBoundary = { rowBreaks.add(it) }
        )
    }

    val baseName = "yata_${sanitizeExportFileName(entityName)}"
    when (format) {
        ExportFormat.IMAGE -> {
            val file = saveBitmapAsPng(context, bitmap, "$baseName.png")
            shareExportedFile(context, file, "image/png", "Share $entityName")
        }
        ExportFormat.PDF -> {
            val file = saveBitmapAsPdf(context, bitmap, "$baseName.pdf", rowBreaks)
            applyPdfMetadata(
                context = context,
                file = file,
                title = "$entityName — YATA $entityKind Report",
                subject = "$entityKind task report for $entityName ($doneCount/$totalCount done)",
                keywords = "YATA, $entityKind, $entityName, tasks, report"
            )
            shareExportedFile(context, file, "application/pdf", "Share $entityName")
        }
    }
}
