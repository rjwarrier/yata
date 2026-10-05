package com.mj.yata.util.nl

import com.mj.yata.util.ClaimTracker
import com.mj.yata.util.QuickAddHighlightSpan
import com.mj.yata.util.QuickAddHighlightType
import java.time.LocalDate
import java.time.LocalTime

internal class ParserContext(
    val raw: String,
    val referenceDate: LocalDate,
    val referenceTime: LocalTime,
    val dayFirst: Boolean,
    val claims: ClaimTracker
) {
    fun isFree(range: IntRange): Boolean = claims.isFree(range)

    fun claim(range: IntRange, type: QuickAddHighlightType = QuickAddHighlightType.Other) {
        claims.claim(range, type)
    }

    fun claimDueDate(range: IntRange) = claim(range, QuickAddHighlightType.DueDate)
    fun claimStartDate(range: IntRange) = claim(range, QuickAddHighlightType.StartDate)
    fun claimTime(range: IntRange) = claim(range, QuickAddHighlightType.Time)
    fun claimRecurrence(range: IntRange) = claim(range, QuickAddHighlightType.Recurrence)
    fun claimReminder(range: IntRange) = claim(range, QuickAddHighlightType.Reminder)
    fun claimPriority(range: IntRange) = claim(range, QuickAddHighlightType.Priority)
    fun claimFlag(range: IntRange) = claim(range, QuickAddHighlightType.Flag)
    fun claimProject(range: IntRange) = claim(range, QuickAddHighlightType.Project)
    fun claimList(range: IntRange) = claim(range, QuickAddHighlightType.List)
    fun claimTag(range: IntRange) = claim(range, QuickAddHighlightType.Tag)
    fun claimAssignee(range: IntRange) = claim(range, QuickAddHighlightType.Assignee)

    fun firstFreeMatch(regex: Regex): MatchResult? = claims.firstFreeMatch(regex, raw)

    fun firstObstacleFrom(from: Int): Int? = claims.firstObstacleFrom(from)

    /**
     * First unclaimed whole-word occurrence of [word]. [wordRegex] must build a regex that matches
     * [word] as a literal (every caller passes the parser's cached `literalWordRegex`), which is
     * what makes the `contains` pre-check safe: such a regex, case-insensitive over ASCII, can
     * only match where `contains(ignoreCase = true)` — the more lenient test — already finds it.
     * The date, recurrence and alias rules call this for hundreds of multilingual words per
     * parse, almost none of which are in any given title, and the parse runs on every keystroke
     * of the new-task sheet; skipping the regex scan for absent words is most of its cost.
     */
    fun firstFreeWord(word: String, wordRegex: (String) -> Regex): MatchResult? =
        if (raw.contains(word, ignoreCase = true)) firstFreeMatch(wordRegex(word)) else null

    fun expandedSpans(prepositionRegex: Regex): List<QuickAddHighlightSpan> =
        claims.expandedSpans(raw, prepositionRegex)

    fun stripOnlyRanges(): List<IntRange> = claims.stripOnlyRanges()
}
