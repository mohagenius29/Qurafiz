package com.qurafiz.app

import kotlin.math.max
import kotlin.math.min

/**
 * Pure logic (no Android) that compares what the user said with the ayah they should be reciting.
 * Both inputs must already be normalized with [ArabicNormalizer].
 */
object RecitationMatcher {

    enum class MistakeType { WRONG_WORD, SKIPPED_WORD }

    sealed interface Result {
        /** [confirmed] = number of ayah words recited correctly so far (absolute, from word 0). */
        data class Progress(val confirmed: Int, val completed: Boolean) : Result

        /** Mistake at expected word index [at]; everything before it was fine. */
        data class Mistake(val at: Int, val type: MistakeType, val heard: String) : Result
    }

    /**
     * @param start index of the first expected word this utterance should begin with
     * @param isFinal false for partial results: the last heard word may still be incomplete,
     *                so it is never judged as wrong (it only counts if it already matches)
     * @param strict strict = recognizer noise barely tolerated; gentle = forgiving
     */
    fun evaluate(
        expected: List<String>,
        heard: List<String>,
        start: Int,
        isFinal: Boolean,
        strict: Boolean,
    ): Result {
        var e = start
        var h = 0
        val judgedUpTo = if (isFinal) heard.size else heard.size - 1 // exclusive index

        while (h < heard.size && e < expected.size) {
            val hw = heard[h]

            if (same(hw, expected[e], strict)) { e++; h++; continue }

            // recognizer split one word in two: "عليهم" -> "عليه" "م"
            if (h + 1 < heard.size && same(hw + heard[h + 1], expected[e], strict)) { e++; h += 2; continue }

            // recognizer glued two words: expected "من" "ذا" heard "منذا"
            if (e + 1 < expected.size && same(hw, expected[e] + expected[e + 1], strict)) { e += 2; h++; continue }

            if (h >= judgedUpTo) break // still-being-spoken last word: wait

            if (hw.length >= 2) {
                for (k in 1..3) {
                    if (e + k < expected.size && same(hw, expected[e + k], strict)) {
                        return Result.Mistake(e, MistakeType.SKIPPED_WORD, hw)
                    }
                }
            }
            return Result.Mistake(e, MistakeType.WRONG_WORD, hw)
        }
        return Result.Progress(e, e >= expected.size)
    }

    fun same(a: String, b: String, strict: Boolean): Boolean {
        if (a == b) return true
        if (min(a.length, b.length) <= 2) return !strict && a.length == b.length && distance(a, b) <= 1 && a.length == 2
        return similarity(a, b) >= if (strict) 0.8 else 0.62
    }

    fun similarity(a: String, b: String): Double {
        val m = max(a.length, b.length)
        return if (m == 0) 1.0 else 1.0 - distance(a, b).toDouble() / m
    }

    fun distance(a: String, b: String): Int {
        if (a == b) return 0
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}
