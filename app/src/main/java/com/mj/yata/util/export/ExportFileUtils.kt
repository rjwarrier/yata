package com.mj.yata.util.export

import com.mj.yata.R
import android.content.Context
import android.content.Intent
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

internal fun exportsDir(context: Context): File =
    File(context.cacheDir, "exports").apply { mkdirs() }

internal fun shareUriFor(context: Context, file: File) =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

/** Strips a tag/person name down to a safe export filename fragment. */
fun sanitizeExportFileName(name: String): String =
    name.replace(Regex("[^A-Za-z0-9_-]"), "_").trim('_').ifEmpty { "export" }.take(40)

fun saveBitmapAsPng(context: Context, bitmap: Bitmap, fileName: String): File {
    val file = File(exportsDir(context), fileName)
    FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
    return file
}

/** JPEG has no alpha channel, so a fully opaque card (the task-share image) compresses smaller
 * than the PNG path above at a visually lossless quality — worth it since these are sized for
 * chat-app share sheets (WhatsApp/Telegram/Instagram) that re-compress anyway. */
fun saveBitmapAsJpeg(context: Context, bitmap: Bitmap, fileName: String, quality: Int = 92): File {
    val file = File(exportsDir(context), fileName)
    FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out) }
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
    rowBreaks: List<Float> = emptyList(),
    pageSize: ExportPdfPageSize = ExportPdfPageSize.A4
): File {
    val density = context.resources.displayMetrics.density
    val margin = (32 * density).toInt().coerceAtLeast(1)
    val pageWidth = bitmap.width + margin * 2
    val pageHeight = (pageWidth * pageSize.ratio).toInt().coerceAtLeast(margin * 4)
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

        val src = android.graphics.Rect(0, slice.startY, bitmap.width, slice.startY + slice.height)
        val dst = android.graphics.Rect(margin, margin, margin + bitmap.width, margin + slice.height)
        canvas.drawBitmap(bitmap, src, dst, null)
        canvas.drawText(
            context.getString(R.string.export_page_of, index + 1, slices.size),
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

fun shareExportedFile(context: Context, file: File, mimeType: String, chooserTitle: String, extraText: String? = null) {
    val uri = shareUriFor(context, file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        extraText?.takeIf { it.isNotBlank() }?.let { putExtra(Intent.EXTRA_TEXT, it) }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}

fun deliverExportedFile(
    context: Context,
    file: File,
    mimeType: String,
    chooserTitle: String,
    destination: ExportDestination,
    extraText: String? = null
): ExportOutcome {
    if (destination == ExportDestination.SHARE) {
        shareExportedFile(context, file, mimeType, chooserTitle, extraText)
        return ExportOutcome(file = file, destination = destination, pageCount = if (mimeType == "application/pdf") countPdfPages(file) else 1)
    }
    val saved = copyExportToDownloads(context, file, mimeType)
    return ExportOutcome(file = saved, destination = destination, pageCount = if (mimeType == "application/pdf") countPdfPages(saved) else 1)
}

private fun copyExportToDownloads(context: Context, file: File, mimeType: String): File {
    val resolver = context.contentResolver
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Could not create Downloads file")
        resolver.openOutputStream(uri)?.use { out ->
            FileInputStream(file).use { input -> input.copyTo(out) }
        } ?: throw IllegalStateException("Could not open Downloads file")
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return file
    }

    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    downloadsDir.mkdirs()
    val saved = File(downloadsDir, file.name)
    FileInputStream(file).use { input ->
        FileOutputStream(saved).use { out -> input.copyTo(out) }
    }
    return saved
}

private fun countPdfPages(file: File): Int = runCatching {
    PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { it.pageCount }
}.getOrDefault(1)
