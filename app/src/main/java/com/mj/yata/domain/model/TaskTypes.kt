package com.mj.yata.domain.model

const val MAX_TASK_TYPES = 30
const val MAX_TASK_TYPE_LENGTH = 40

/** Trims, drops blanks and case-insensitive duplicates (first spelling wins), caps count and length. */
fun normalizeTaskTypes(raw: Iterable<String>): List<String> {
    val seen = HashSet<String>()
    return raw.asSequence()
        .map { it.trim().take(MAX_TASK_TYPE_LENGTH).trim() }
        .filter { it.isNotEmpty() && seen.add(it.lowercase()) }
        .take(MAX_TASK_TYPES)
        .toList()
}

private val HINT_SEPARATOR = Regex("""\s*[:|–—]\s*|\s+-\s+""")

/** What joins a type to the rest of the title, copied from the title hint's own punctuation
 * ("Work Type : Client Name" -> " : "), or a plain space when the hint has none. A hyphen only
 * counts with spaces around it, so a hint like "Follow-up for Client" isn't split. */
fun taskTypeSeparator(titleHint: String?): String =
    titleHint?.let { HINT_SEPARATOR.find(it)?.value } ?: " "

/**
 * Types worth offering for the title typed so far: all of them while it is blank, those where
 * the typed text starts the type or one of its words ("ta" -> Tax Audit, not Stat Audit; "audit"
 * -> both), and none once a type has been chosen (the title starts with "type + separator") or
 * the user typed the separator themselves.
 */
fun taskTypeSuggestions(types: List<String>, typed: String, separator: String): List<String> {
    if (types.isEmpty()) return emptyList()
    val separatorMark = separator.trim()
    if (separatorMark.isNotEmpty() && typed.contains(separatorMark)) return emptyList()
    if (types.any { typed.startsWith(it + separator, ignoreCase = true) }) return emptyList()
    val query = typed.trim().lowercase()
    if (query.isEmpty()) return types
    return types.filter { type ->
        val lower = type.lowercase()
        lower.startsWith(query) || lower.split(' ', '-', '/').any { it.startsWith(query) }
    }
}
