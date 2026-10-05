package com.mj.yata.util.nl

internal fun normalizeNaturalLanguageInput(rawInput: String): String =
    normalizeNaturalLanguageInputWithRanges(rawInput).normalized

internal fun normalizeNaturalLanguageInputWithRanges(rawInput: String): NormalizedInput {
    var normalized = NormalizedInput(
        raw = rawInput,
        normalized = rawInput,
        normalizedIndexToRawIndex = IntArray(rawInput.length) { it }
    )

    for (rule in normalizationRules) {
        normalized = normalized.replace(rule.regex, rule.replacement)
    }

    return normalized
}

private data class NormalizationRule(
    val regex: Regex,
    val replacement: (MatchResult) -> String
)

private val normalizationRules = listOf(
    NormalizationRule(Regex("\\b2\\s*day\\b", RegexOption.IGNORE_CASE)) { "today" },
    NormalizationRule(Regex("\\bto\\s+day\\b", RegexOption.IGNORE_CASE)) { "today" },
    NormalizationRule(Regex("\\b(to|two|2)\\s*morrow\\b", RegexOption.IGNORE_CASE)) { "tomorrow" },
    NormalizationRule(Regex("\\bate\\s+(p\\.?\\s*m\\.?|a\\.?\\s*m\\.?|pm|am)\\b", RegexOption.IGNORE_CASE)) {
        "8 ${it.groupValues[1]}"
    },
    NormalizationRule(Regex("\\bwon\\s+(p\\.?\\s*m\\.?|a\\.?\\s*m\\.?|pm|am)\\b", RegexOption.IGNORE_CASE)) {
        "1 ${it.groupValues[1]}"
    },
    NormalizationRule(Regex("\\btoo\\s+(p\\.?\\s*m\\.?|a\\.?\\s*m\\.?|pm|am)\\b", RegexOption.IGNORE_CASE)) {
        "2 ${it.groupValues[1]}"
    },
    NormalizationRule(Regex("\\bfor\\s+(p\\.?\\s*m\\.?|a\\.?\\s*m\\.?|pm|am)\\b", RegexOption.IGNORE_CASE)) {
        "4 ${it.groupValues[1]}"
    }
)

private fun NormalizedInput.replace(regex: Regex, replacement: (MatchResult) -> String): NormalizedInput {
    val matches = regex.findAll(normalized).toList()
    if (matches.isEmpty()) return this

    val nextText = StringBuilder()
    val nextMap = mutableListOf<Int>()
    var cursor = 0

    for (match in matches) {
        appendSlice(cursor, match.range.first, nextText, nextMap)

        val replacementText = replacement(match)
        val matchLength = match.range.last - match.range.first + 1
        for (index in replacementText.indices) {
            nextText.append(replacementText[index])
            val sourceOffset = replacementSourceOffset(index, replacementText.length, matchLength)
            nextMap.add(normalizedIndexToRawIndex[match.range.first + sourceOffset])
        }

        cursor = match.range.last + 1
    }

    appendSlice(cursor, normalized.length, nextText, nextMap)

    return copy(
        normalized = nextText.toString(),
        normalizedIndexToRawIndex = nextMap.toIntArray()
    )
}

private fun replacementSourceOffset(replacementIndex: Int, replacementLength: Int, matchLength: Int): Int {
    if (matchLength <= 1 || replacementLength <= 1) return 0
    return ((replacementIndex.toDouble() / (replacementLength - 1)) * (matchLength - 1))
        .toInt()
        .coerceIn(0, matchLength - 1)
}

private fun NormalizedInput.appendSlice(
    start: Int,
    endExclusive: Int,
    text: StringBuilder,
    indexMap: MutableList<Int>
) {
    for (index in start until endExclusive) {
        text.append(normalized[index])
        indexMap.add(normalizedIndexToRawIndex[index])
    }
}
