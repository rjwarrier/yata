package com.mj.yata.util

/**
 * The one way to turn a name into the letters shown on an avatar.
 *
 * This existed as a copy-pasted `name.split(" ")…take(2)` expression in eight screens, which made
 * it three rules in one app alongside the stored `Person.initials` and the tag monogram. The
 * copies split on spaces alone, so a hyphenated or underscored name collapsed to a single letter,
 * and they took the first `Char` rather than the first code point — enough to slice an emoji or
 * any character outside the BMP in half and render the remainder as tofu.
 *
 * Falls back to "?" rather than an empty string: a blank avatar reads as a rendering failure,
 * where a placeholder reads as a name we don't have.
 */
fun initialsFor(name: String): String {
    val words = name.trim().split(' ', '-', '_', '.', '/').filter { it.isNotBlank() }
    if (words.isEmpty()) return "?"
    return words.take(2)
        .joinToString("") { String(Character.toChars(it.codePointAt(0))) }
        .uppercase()
}
