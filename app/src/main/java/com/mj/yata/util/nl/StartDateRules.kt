package com.mj.yata.util.nl

import com.mj.yata.util.ParsedQuickAdd
import java.time.LocalDate

internal object StartDateRules {
    fun apply(
        context: ParserContext,
        startDateRegex: Regex,
        parseNested: (String) -> ParsedQuickAdd,
        claimEndFor: (MatchResult, Int, ParsedQuickAdd) -> Int
    ): LocalDate? {
        context.firstFreeMatch(startDateRegex)?.let { match ->
            val phrase = match.groupValues[2]
            if (phrase.isNotBlank()) {
                val nested = parseNested(phrase)
                nested.due?.let { resolved ->
                    val startDate = runCatching { LocalDate.parse(resolved) }.getOrNull()
                    if (startDate != null) {
                        context.claimStartDate(match.range.first..claimEndFor(match, 2, nested))
                    }
                    return startDate
                }
            }
        }

        return null
    }
}
