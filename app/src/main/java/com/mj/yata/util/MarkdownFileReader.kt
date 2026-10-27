package com.mj.yata.util

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

data class MarkdownFile(val name: String?, val document: MarkdownDocument)

/** Called on IO. Bound the stream itself: cloud providers need not report a truthful size. */
fun readMarkdownFile(resolver: ContentResolver, uri: Uri): MarkdownFile {
    val bytes = resolver.openInputStream(uri)?.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            if (output.size() + count > MAX_MARKDOWN_BYTES) throw MarkdownImportTooLargeException()
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    } ?: throw IllegalArgumentException("Unreadable Markdown file")
    val text = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()
    require('\u0000' !in text) { "Not a text file" }
    val name = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
    return MarkdownFile(name, MarkdownTaskParser.parse(text))
}
