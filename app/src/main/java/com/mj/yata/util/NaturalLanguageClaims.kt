package com.mj.yata.util

internal class ClaimTracker {
    private val claimed = mutableListOf<IntRange>()
    private val claimTypes = mutableMapOf<IntRange, QuickAddHighlightType>()
    private val escapedRanges = mutableListOf<IntRange>()
    private val stripOnly = mutableListOf<IntRange>()

    fun addEscape(backslashRange: IntRange, protectedRange: IntRange) {
        stripOnly.add(backslashRange)
        escapedRanges.add(protectedRange)
    }

    fun addStripOnly(range: IntRange) {
        stripOnly.add(range)
    }

    fun isFree(range: IntRange): Boolean =
        claimed.none { it.first <= range.last && range.first <= it.last } &&
            escapedRanges.none { it.first <= range.last && range.first <= it.last }

    fun claim(range: IntRange, type: QuickAddHighlightType = QuickAddHighlightType.Other) {
        claimed.add(range)
        claimTypes[range] = type
    }

    fun firstFreeMatch(regex: Regex, raw: String): MatchResult? =
        regex.findAll(raw).firstOrNull { isFree(it.range) }

    fun expandedSpans(raw: String, prepositionRegex: Regex): List<QuickAddHighlightSpan> =
        claimed.map { range ->
            var start = range.first
            val prefix = raw.substring(0, start)
            prepositionRegex.find(prefix)?.let { m ->
                if (isFree(m.range)) {
                    start = m.range.first
                }
            }
            QuickAddHighlightSpan(start..range.last, claimTypes[range] ?: QuickAddHighlightType.Other)
        }

    fun stripOnlyRanges(): List<IntRange> = stripOnly.toList()
}
