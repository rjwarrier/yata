package com.mj.yata.util.nl

internal fun cleanNaturalLanguageTitle(raw: String, stripRanges: List<IntRange>): String {
    val titleRaw = buildString {
        var cursor = 0
        for (range in stripRanges.sortedBy { it.first }) {
            if (range.first > cursor) append(raw, cursor, range.first)
            cursor = (range.last + 1).coerceAtLeast(cursor)
        }
        if (cursor < raw.length) append(raw, cursor, raw.length)
    }.replace(Regex("\\s{2,}"), " ").trim()

    var titleClean = titleRaw
    repeat(3) {
        titleClean = titleClean
            .replace(Regex("\\b(a\\.?\\s*m\\.?|p\\.?\\s*m\\.?)\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[.,;:_\\-/\\\\]+$"), "")
            .replace(Regex("^\\s*[.,;:_\\-/\\\\]+"), "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }

    return if (titleClean.isNotBlank()) titleClean else raw.replace(Regex("[.,;:_\\-/\\\\]+$"), "").trim()
}
