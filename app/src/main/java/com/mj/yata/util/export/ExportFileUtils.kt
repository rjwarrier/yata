package com.mj.yata.util.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar

private fun exportsDir(context: Context): File =
    File(context.cacheDir, "exports").apply { mkdirs() }

private fun shareUriFor(context: Context, file: File) =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

/** Strips a tag/person name down to a safe export filename fragment. */
fun sanitizeExportFileName(name: String): String =
    name.replace(Regex("[^A-Za-z0-9_-]"), "_").trim('_').ifEmpty { "export" }.take(40)

fun saveBitmapAsPng(context: Context, bitmap: Bitmap, fileName: String): File {
    val file = File(exportsDir(context), fileName)
    FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
    return file
}

private data class PdfSlice(val startY: Int, val height: Int)

/**
 * Slices a single tall render into A4-ratio pages (at the render's own width plus margin), so
 * long task lists still produce a normal, printable multi-page PDF rather than one giant page.
 *
 * [rowBreaks] are the only y-coordinates (px, root-relative) a page is allowed to end at —
 * each is a task row's (or the footer's) bottom edge, reported by [BrandedExportCard]'s
 * onRowBoundary callback. Without them a page would be cut at a raw pixel offset, which can
 * land mid-row; picking the last available row-boundary at or before the target page height
 * guarantees every row stays whole. Every page gets an equal inset on all four sides (the
 * render itself is edge-to-edge, so without this the second-page-onward content butts
 * straight against the paper edge with no top margin) and a centered "Page X of Y" footer —
 * computed as a first pass over slice ranges so the total count is known before any page is
 * drawn.
 */
fun saveBitmapAsPdf(
    context: Context,
    bitmap: Bitmap,
    fileName: String,
    rowBreaks: List<Float> = emptyList()
): File {
    val density = context.resources.displayMetrics.density
    val margin = (32 * density).toInt().coerceAtLeast(1)
    val pageWidth = bitmap.width + margin * 2
    val pageHeight = (pageWidth * 1.414f).toInt().coerceAtLeast(margin * 4)
    val contentBudget = (pageHeight - margin * 2).coerceAtLeast(1)
    val candidates = rowBreaks.map { it.toInt() }.filter { it in 1 until bitmap.height }.sorted()

    val slices = mutableListOf<PdfSlice>()
    var y = 0
    while (y < bitmap.height) {
        val limit = (y + contentBudget).coerceAtMost(bitmap.height)
        // Last row boundary that both fits within this page's budget and actually makes
        // progress — falls back to a hard cut only if a single row is taller than a page.
        val breakAt = candidates.lastOrNull { it in (y + 1)..limit } ?: limit
        val sliceHeight = (breakAt - y).coerceIn(1, bitmap.height - y)
        slices += PdfSlice(y, sliceHeight)
        y += sliceHeight
    }

    // The footer is its own break-candidate group, so a nearly-full previous page can strand
    // it alone on a trailing page. If that trailing slice would still fit on the previous
    // page's budget, fold it back in instead — the footer belongs right after the content
    // that precedes it, not on a page by itself.
    while (slices.size >= 2) {
        val last = slices.last()
        val previous = slices[slices.size - 2]
        if (previous.height + last.height > contentBudget) break
        slices[slices.size - 2] = previous.copy(height = previous.height + last.height)
        slices.removeAt(slices.size - 1)
    }

    val pageNumberPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.GRAY
        textSize = 9f * density
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }

    val document = PdfDocument()
    slices.forEachIndexed { index, slice ->
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        canvas.drawColor(android.graphics.Color.WHITE)

        val bitmapSlice = Bitmap.createBitmap(bitmap, 0, slice.startY, bitmap.width, slice.height)
        canvas.drawBitmap(bitmapSlice, margin.toFloat(), margin.toFloat(), null)
        canvas.drawText(
            "Page ${index + 1} of ${slices.size}",
            pageWidth / 2f,
            pageHeight - margin / 2f,
            pageNumberPaint
        )

        document.finishPage(page)
    }

    val file = File(exportsDir(context), fileName)
    FileOutputStream(file).use { out -> document.writeTo(out) }
    document.close()
    return file
}

/**
 * Fills in the PDF's Info dictionary (Title/Author/Subject/Keywords/Creator/Producer) so the
 * exported file looks like a real, cohesive document rather than an anonymous image dump —
 * android.graphics.pdf.PdfDocument (used to render the pages themselves) has no metadata API,
 * so this reopens the already-written file through PDFBox purely to set that dictionary and
 * rewrite it in place.
 */
fun applyPdfMetadata(
    context: Context,
    file: File,
    title: String,
    subject: String,
    keywords: String,
    author: String = "YATA"
) {
    PDFBoxResourceLoader.init(context.applicationContext)
    PDDocument.load(file).use { document ->
        val info = PDDocumentInformation()
        info.title = title
        info.author = author
        info.subject = subject
        info.keywords = keywords
        info.creator = "YATA for Android"
        info.producer = "YATA for Android"
        val now = Calendar.getInstance()
        info.creationDate = now
        info.modificationDate = now
        document.documentInformation = info
        document.save(file)
    }
}

fun shareExportedFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
    val uri = shareUriFor(context, file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}
