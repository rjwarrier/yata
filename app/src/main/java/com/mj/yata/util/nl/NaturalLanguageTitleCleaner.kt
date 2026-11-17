package com.mj.yata.util.nl

/** Bare connector words that only make sense glued to whatever they're connecting — left behind
 * between two recognized spans (e.g. "flight to +travel from tomorrow" once "+travel" and
 * "tomorrow" are claimed) they read as a dangling fragment, not part of the title. Deliberately a
 * short, unambiguous set: this only fires when the word sits *between two claimed ranges with
 * nothing else around it* (see [bridgeFillerGaps]), never against a plain occurrence elsewhere in
 * the title, so "go to school" or "log in to the portal" are untouched — nothing on both sides of
 * "to"/"in" there is a recognized span. */
private val FILLER_CONNECTOR_WORDS = setOf("from", "to", "at", "in", "on", "by", "for")

/** Extends [ranges] to also cover any gap between two consecutive claimed ranges whose *entire*
 * trimmed content is one bare word from [FILLER_CONNECTOR_WORDS] — e.g. the "to" left stranded
 * between a claimed project mention and a claimed date. A gap containing anything else (even one
 * extra word) is left alone; this only removes a word that has nothing to connect to anymore, not
 * one still doing a job in the sentence. */
private fun bridgeFillerGaps(raw: String, ranges: List<IntRange>, protectedRanges: List<IntRange>): List<IntRange> {
    val sorted = ranges.sortedBy { it.first }
    if (sorted.size < 2) return sorted
    val bridged = mutableListOf<IntRange>()
    for (range in sorted) {
        val previous = bridged.lastOrNull()
        if (previous != null && range.first > previous.last + 1) {
            val gap = raw.substring(previous.last + 1, range.first).trim()
            if (gap.lowercase() in FILLER_CONNECTOR_WORDS && protectedRanges.none {
                    it.first < range.first && it.last > previous.last
                }) {
                bridged[bridged.lastIndex] = previous.first..range.last
                continue
            }
        }
        bridged.add(range)
    }
    return bridged
}

/** Strip recognized metadata and its escape markers, preserving protected literal characters.
 * AM/PM belongs to a time only when a time rule claimed it; cleanup must not infer new metadata. */
internal fun cleanNaturalLanguageTitle(
    raw: String,
    stripRanges: List<IntRange>,
    protectedRanges: List<IntRange> = emptyList()
): String {
    val bridgedRanges = bridgeFillerGaps(raw, stripRanges, protectedRanges)
    val retained = mutableListOf<Int>()
    var cursor = 0
    for (range in bridgedRanges.sortedBy { it.first }) {
        if (range.first > cursor) retained.addAll(cursor until range.first)
        cursor = (range.last + 1).coerceAtLeast(cursor)
    }
    if (cursor < raw.length) retained.addAll(cursor until raw.length)

    fun removable(index: Int): Boolean = raw[index].isWhitespace() ||
        (raw[index] in ".,;:_-/\\" && protectedRanges.none { index in it })
    var first = 0
    var last = retained.lastIndex
    while (first <= last && removable(retained[first])) first++
    while (last >= first && removable(retained[last])) last--
    val title = buildString {
        for (position in first..last) append(raw[retained[position]])
    }.replace(Regex("\\s{2,}"), " ").trim()

    return if (title.isNotBlank()) title else raw.replace(Regex("[.,;:_\\-/\\\\]+$"), "").trim()
}
