package com.mj.yata.util.export

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import com.mj.yata.R
import com.mj.yata.util.localized

private fun cardWidth(scale: ExportImageScale) = scale.widthDp.dp

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
    showAssignees: Boolean = true,
    showMadeWithFooter: Boolean = true,
    destination: ExportDestination = ExportDestination.SHARE,
    fileNameBase: String = "yata_${sanitizeExportFileName(entityName)}",
    pdfPageSize: ExportPdfPageSize = ExportPdfPageSize.A4,
    imageScale: ExportImageScale = ExportImageScale.STANDARD
): ExportOutcome {
    val displayDensity = context.resources.displayMetrics.density
    val widthPx = (cardWidth(imageScale).value * displayDensity).toInt()
    val generatedOn = LocalDateTime.now().localized()

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
            showMadeWithFooter = showMadeWithFooter,
            cardWidth = cardWidth(imageScale),
            onRowBoundary = { rowBreaks.add(it) }
        )
    }

    val baseName = sanitizeExportFileName(fileNameBase)
    when (format) {
        ExportFormat.IMAGE -> {
            val file = saveBitmapAsPng(context, bitmap, "$baseName.png")
            return deliverExportedFile(context, file, "image/png", "Share $entityName", destination)
        }
        ExportFormat.PDF -> {
            val file = saveBitmapAsPdf(context, bitmap, "$baseName.pdf", rowBreaks, pdfPageSize)
            applyPdfMetadata(
                context = context,
                file = file,
                title = context.getString(R.string.entity_report_pdf_title, entityName, entityKind),
                subject = "$entityKind task report for $entityName ($doneCount/$totalCount done)",
                keywords = "YATA, $entityKind, $entityName, tasks, report"
            )
            return deliverExportedFile(context, file, "application/pdf", "Share $entityName", destination)
        }
    }
}
