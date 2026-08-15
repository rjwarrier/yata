package com.mj.yata.util

internal fun literalAlternation(tokens: Collection<String>): String =
    tokens.asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
        .sortedWith(compareByDescending<String> { it.length }.thenBy { it.lowercase() })
        .joinToString("|") { Regex.escape(it) }

internal fun literalWordRegex(token: String): Regex =
    Regex("(?<![\\p{L}\\p{N}_])${Regex.escape(token)}(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE)

internal fun literalIshWordRegex(token: String): Regex =
    Regex("(?<![\\p{L}\\p{N}_])${Regex.escape(token)}(?:\\s*-?\\s*ish)?(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE)
