package com.mj.yata.util

import com.mj.yata.domain.model.Task

/** Fuzzy match on task titles, used to warn about likely duplicates at creation time. Blends
 * two signals and takes the stronger: normalized Levenshtein (catches typos/near-identical
 * strings, e.g. "Buy milk" vs "buy Milk ") and token Jaccard (catches reordered words, e.g.
 * "milk buy" vs "buy milk", which Levenshtein alone scores as barely similar). */
fun titleSimilarity(a: String, b: String): Double {
    val s1 = a.trim().lowercase()
    val s2 = b.trim().lowercase()
    if (s1.isEmpty() || s2.isEmpty()) return 0.0
    if (s1 == s2) return 1.0
    val maxLen = maxOf(s1.length, s2.length)
    val charSim = 1.0 - levenshtein(s1, s2).toDouble() / maxLen
    val tokenSim = tokenJaccard(s1, s2)
    return maxOf(charSim, tokenSim)
}

private fun tokenize(s: String): Set<String> =
    s.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }.toSet()

/** Word-overlap similarity, order-independent: |intersection| / |union| of token sets. */
private fun tokenJaccard(a: String, b: String): Double {
    val t1 = tokenize(a)
    val t2 = tokenize(b)
    if (t1.isEmpty() || t2.isEmpty()) return 0.0
    val intersection = t1.intersect(t2).size
    val union = t1.union(t2).size
    return intersection.toDouble() / union
}

private fun levenshtein(a: String, b: String): Int {
    val dp = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) dp[i][0] = i
    for (j in 0..b.length) dp[0][j] = j
    for (i in 1..a.length) {
        for (j in 1..b.length) {
            dp[i][j] = if (a[i - 1] == b[j - 1]) {
                dp[i - 1][j - 1]
            } else {
                1 + minOf(dp[i - 1][j - 1], dp[i - 1][j], dp[i][j - 1])
            }
        }
    }
    return dp[a.length][b.length]
}

/** Closest non-completed task whose title is at least [threshold] similar to [title], or
 * null if nothing crosses the bar. Completed tasks are excluded — a finished task with the
 * same name isn't a duplicate-in-progress. */
fun findSimilarTask(title: String, tasks: List<Task>, threshold: Double = 0.75): Task? =
    tasks.asSequence()
        .filter { !it.done }
        .map { it to titleSimilarity(title, it.title) }
        .filter { it.second >= threshold }
        .maxByOrNull { it.second }
        ?.first
