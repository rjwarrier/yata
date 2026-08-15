package com.mj.yata.util.nl

internal fun normalizeNaturalLanguageInput(rawInput: String): String =
    rawInput
        .replace(Regex("\\b2\\s*day\\b", RegexOption.IGNORE_CASE), "today")
        .replace(Regex("\\bto\\s+day\\b", RegexOption.IGNORE_CASE), "today")
        .replace(Regex("\\b(to|two|2)\\s*morrow\\b", RegexOption.IGNORE_CASE), "tomorrow")
        .replace(Regex("\\bate\\s+(p\\.?\\s*m\\.?|a\\.?\\s*m\\.?|pm|am)\\b", RegexOption.IGNORE_CASE), "8 $1")
        .replace(Regex("\\bwon\\s+(p\\.?\\s*m\\.?|a\\.?\\s*m\\.?|pm|am)\\b", RegexOption.IGNORE_CASE), "1 $1")
        .replace(Regex("\\btoo\\s+(p\\.?\\s*m\\.?|a\\.?\\s*m\\.?|pm|am)\\b", RegexOption.IGNORE_CASE), "2 $1")
        .replace(Regex("\\bfor\\s+(p\\.?\\s*m\\.?|a\\.?\\s*m\\.?|pm|am)\\b", RegexOption.IGNORE_CASE), "4 $1")
