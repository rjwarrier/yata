package com.mj.yata.util

internal class ClaimTracker {
    private val claimed = mutableListOf<IntRange>()
    private val claimTypes = mutableMapOf<IntRange, QuickAddHighlightType>()
    private val escapedRanges = mutableListOf<IntRange>()
    private val stripOnly = mutableListOf<IntRange>()

    fun addEscape(backslashRange: IntRange, protectedRange: IntRange) {
        stripOnly.add(backslashRange)
        addProtectedRange(protectedRange)
    }

    fun addProtectedRange(range: IntRange) {
        escapedRanges.add(range)
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

    /**
     * Earliest already-claimed-or-escaped position at or after [from], or null if nothing from
     * there to the end of the string is claimed. Lets a caller salvage a lazily-captured span
     * that ran past its own keyword boundary list straight into a claim it had no way to know
     * about (see [com.mj.yata.util.nl.EntityRules] for the concrete case this exists for) by
     * truncating right before the obstacle instead of discarding the whole match.
     */
    fun firstObstacleFrom(from: Int): Int? =
        (claimed.asSequence() + escapedRanges.asSequence())
            .map { it.first }
            .filter { it >= from }
            .minOrNull()

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
