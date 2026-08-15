package com.mj.yata.util.nl

internal const val NATURAL_LANGUAGE_NUMBER_COUNT =
    "(?:(?:twenty|twnty|thirty|forty|fourty|fifty|sixty|seventy|eighty|ninety)(?:[-\\s]+(?:one|two|three|thre|tree|four|five|fiv|six|seven|eight|eigth|nine))?|one|two|three|thre|tree|four|five|fiv|six|seven|eight|eigth|nine|ten|eleven|elevenn|twelve|twelv|tweleve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|twnty|thirty|forty|fourty|fifty|sixty|seventy|eighty|ninety|\\d+)"

private val countWordValues = mapOf(
    "a" to 1L,
    "an" to 1L,
    "un" to 1L,
    "una" to 1L,
    "um" to 1L,
    "uma" to 1L,
    "une" to 1L,
    "one" to 1L,
    "two" to 2L,
    "three" to 3L,
    "thre" to 3L,
    "tree" to 3L,
    "four" to 4L,
    "five" to 5L,
    "fiv" to 5L,
    "six" to 6L,
    "seven" to 7L,
    "eight" to 8L,
    "eigth" to 8L,
    "nine" to 9L,
    "ten" to 10L,
    "eleven" to 11L,
    "elevenn" to 11L,
    "twelve" to 12L,
    "twelv" to 12L,
    "tweleve" to 12L,
    "thirteen" to 13L,
    "fourteen" to 14L,
    "fifteen" to 15L,
    "sixteen" to 16L,
    "seventeen" to 17L,
    "eighteen" to 18L,
    "nineteen" to 19L,
    "couple" to 2L,
    "a couple" to 2L,
    "a couple of" to 2L,
    "few" to 3L,
    "a few" to 3L,
    "several" to 4L,
    "unos" to 2L,
    "unas" to 2L,
    "uns" to 2L,
    "umas" to 2L,
    "varios" to 4L,
    "varias" to 4L,
    "v\u00e1rios" to 4L,
    "v\u00e1rias" to 4L,
    "quelques" to 3L,
    "plusieurs" to 4L
)

private val countTensValues = mapOf(
    "twenty" to 20L,
    "twnty" to 20L,
    "thirty" to 30L,
    "forty" to 40L,
    "fourty" to 40L,
    "fifty" to 50L,
    "sixty" to 60L,
    "seventy" to 70L,
    "eighty" to 80L,
    "ninety" to 90L
)

internal fun naturalLanguageCountOrOne(token: String): Long {
    val normalized = token.trim().lowercase().replace('-', ' ').replace(Regex("\\s+"), " ")
    normalized.toLongOrNull()?.let { return it }
    countWordValues[normalized]?.let { return it }
    countTensValues[normalized]?.let { return it }
    val parts = normalized.split(" ")
    if (parts.size == 2) {
        val tens = countTensValues[parts[0]]
        val unit = countWordValues[parts[1]]
        if (tens != null && unit != null && unit in 1..9) return tens + unit
    }
    return 1
}
