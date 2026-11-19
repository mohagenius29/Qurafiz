package com.qurafiz.app

import android.content.Context
import java.util.TimeZone

/**
 * Remembers how well each ayah is known and when to review it (simple spaced repetition),
 * plus streak and daily goal. Stored locally in SharedPreferences.
 */
class ProgressStore(ctx: Context) {
    private val p = ctx.getSharedPreferences("qurafiz_progress", Context.MODE_PRIVATE)

    /** Days since 1970 in the phone's local time zone. */
    private fun today(): Long {
        val now = System.currentTimeMillis()
        return (now + TimeZone.getDefault().getOffset(now)) / 86_400_000L
    }
    private fun key(s: Int, a: Int) = "a_${s}_$a"

    /** Review gaps in days: level 1 -> 1 day ... level 6 -> 60 days. */
    private val gaps = intArrayOf(1, 3, 7, 14, 30, 60)

    fun level(s: Int, a: Int): Int = p.getString(key(s, a), null)?.substringBefore('|')?.toIntOrNull() ?: 0

    fun record(s: Int, a: Int, perfect: Boolean) {
        val lvl = level(s, a)
        val newLvl = if (perfect) minOf(lvl + 1, gaps.size) else maxOf(0, lvl - 1)
        val due = today() + if (newLvl == 0) 1 else gaps[newLvl - 1]
        val days = (p.getStringSet("days", emptySet()) ?: emptySet()).toMutableSet().apply { add(today().toString()) }
        p.edit()
            .putString(key(s, a), "$newLvl|$due")
            .putStringSet("days", days)
            .putInt("count_${today()}", todayCount() + 1)
            .apply()
    }

    /** Ayat whose review date has come, most overdue first. */
    fun due(): List<Pair<Int, Int>> {
        val t = today()
        return p.all.entries.mapNotNull { (k, v) ->
            if (!k.startsWith("a_") || v !is String) return@mapNotNull null
            val dueDay = v.substringAfter('|').toLongOrNull() ?: return@mapNotNull null
            if (dueDay > t) return@mapNotNull null
            val parts = k.split('_')
            Triple(parts[1].toInt(), parts[2].toInt(), dueDay)
        }.sortedBy { it.third }.map { it.first to it.second }
    }

    fun learnedIn(surah: Int): Int = Quran.surahs[surah - 1].ayat.indices.count { level(surah, it + 1) >= 1 }

    fun firstUnlearned(surah: Int): Int =
        (1..Quran.surahs[surah - 1].ayat.size).firstOrNull { level(surah, it) == 0 } ?: 1

    fun totalLearned(): Int = p.all.count { (k, v) -> k.startsWith("a_") && (v as? String)?.substringBefore('|') != "0" }

    fun todayCount(): Int = p.getInt("count_${today()}", 0)

    var dailyGoal: Int
        get() = p.getInt("goal", 5)
        set(v) { p.edit().putInt("goal", v.coerceIn(1, 100)).apply() }

    fun streak(): Int {
        val days = (p.getStringSet("days", emptySet()) ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet()
        var d = today()
        if (d !in days) d-- // streak is still alive if you practiced yesterday
        var n = 0
        while (d in days) { n++; d-- }
        return n
    }
}
