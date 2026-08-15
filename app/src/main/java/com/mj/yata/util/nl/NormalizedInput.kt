package com.mj.yata.util.nl

internal data class NormalizedInput(
    val raw: String,
    val normalized: String,
    val normalizedIndexToRawIndex: IntArray
) {
    init {
        require(normalized.length == normalizedIndexToRawIndex.size) {
            "Normalized index map must match normalized text length"
        }
    }

    fun toRawRange(range: IntRange): IntRange {
        if (range.isEmpty()) return range
        if (normalizedIndexToRawIndex.isEmpty()) return range
        val first = range.first.coerceIn(normalizedIndexToRawIndex.indices)
        val last = range.last.coerceIn(normalizedIndexToRawIndex.indices)
        val rawStart = normalizedIndexToRawIndex[first]
        val rawEnd = normalizedIndexToRawIndex[last]
        return rawStart..rawEnd
    }
}
