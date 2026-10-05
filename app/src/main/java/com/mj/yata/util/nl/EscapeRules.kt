package com.mj.yata.util.nl

internal object EscapeRules {
    private val escapedRecurrenceWords = setOf("every", "each", "cada", "todo", "toda", "chaque")
    private val escapedNextWordRegex = Regex("\\G\\s+([\\p{L}-]+)", RegexOption.IGNORE_CASE)

    fun apply(
        context: ParserContext,
        escapeRegex: Regex,
        weekdayNames: Set<String>
    ) {
        escapeRegex.findAll(context.raw).forEach { match ->
            val backslashIndex = match.range.first
            val escapedWordRange = match.groups[1]!!.range
            context.claims.addEscape(backslashIndex..backslashIndex, escapedWordRange)

            val escapedWord = match.groupValues[1].lowercase()
            if (escapedWord in escapedRecurrenceWords) {
                escapedNextWordRegex
                    .find(context.raw, escapedWordRange.last + 1)
                    ?.takeIf { weekdayNames.contains(it.groupValues[1].lowercase()) }
                    ?.let { context.claims.addProtectedRange(escapedWordRange.first..it.range.last) }
            }
        }
    }
}
