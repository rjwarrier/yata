package com.mj.yata.ui.util

private val orderedListPattern = Regex("""^(\s*)(\d+|[A-Za-z])([.)])\s+(.+)$""")

fun autoContinueOrderedList(previous: String, current: String): String {
    if (current.length != previous.length + 1) return current

    val insertIndex = current.indexOfDifference(previous)
    if (insertIndex !in current.indices || current[insertIndex] != '\n') return current

    val lineStart = previous.lastIndexOf('\n', startIndex = (insertIndex - 1).coerceAtLeast(0))
        .let { if (it == -1) 0 else it + 1 }
    val previousLine = previous.substring(lineStart, insertIndex)
    val match = orderedListPattern.matchEntire(previousLine) ?: return current

    val indent = match.groupValues[1]
    val marker = match.groupValues[2]
    val separator = match.groupValues[3]

    val nextMarker = marker.toIntOrNull()?.plus(1)?.toString()
        ?: marker.singleOrNull()?.nextLetter()?.toString()
        ?: return current

    return buildString(current.length + indent.length + nextMarker.length + 2) {
        append(current)
        append(indent)
        append(nextMarker)
        append(separator)
        append(' ')
    }
}

private fun String.indexOfDifference(other: String): Int {
    val minLength = minOf(length, other.length)
    for (index in 0 until minLength) {
        if (this[index] != other[index]) return index
    }
    return minLength
}

private fun Char.nextLetter(): Char? = when (this) {
    in 'a'..'y' -> this + 1
    in 'A'..'Y' -> this + 1
    else -> null
}
