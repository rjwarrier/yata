package com.mj.yata.util

fun capitalizeTaskSentence(input: String): String =
    input.replaceFirstLetter { it.uppercaseChar() }

private inline fun String.replaceFirstLetter(transform: (Char) -> Char): String {
    val index = indexOfFirst { it.isLetter() }
    if (index == -1) return this
    val current = this[index]
    val replacement = transform(current)
    if (replacement == current) return this
    return replaceRange(index, index + 1, replacement.toString())
}
