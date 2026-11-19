package com.qurafiz.app

import android.content.Context
import org.json.JSONArray

/** One word: [display] keeps the tashkeel for showing, [norm] is for matching. */
data class Word(val display: String, val norm: String)

data class Ayah(val number: Int, val words: List<Word>, val translation: String) {
    val text: String get() = words.joinToString(" ") { it.display }
}

data class Surah(
    val number: Int,
    val arabicName: String,
    val name: String,
    val meaning: String,
    val type: String,
    val ayat: List<Ayah>,
)

object Quran {
    @Volatile private var data: List<Surah>? = null

    val surahs: List<Surah> get() = data ?: error("Quran not loaded")

    @Synchronized
    fun ensureLoaded(ctx: Context) {
        if (data != null) return
        val raw = ctx.assets.open("quran.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val arr = JSONArray(raw)
        val list = ArrayList<Surah>(arr.length())
        for (i in 0 until arr.length()) {
            val s = arr.getJSONObject(i)
            val vs = s.getJSONArray("v")
            val ayat = ArrayList<Ayah>(vs.length())
            for (j in 0 until vs.length()) {
                val pair = vs.getJSONArray(j)
                val words = pair.getString(0).trim().split(Regex("\\s+"))
                    .map { Word(it, ArabicNormalizer.normalize(it)) }
                    .filter { it.norm.isNotEmpty() } // drop stand-alone pause marks
                ayat.add(Ayah(j + 1, words, pair.getString(1)))
            }
            list.add(Surah(s.getInt("n"), s.getString("a"), s.getString("t"), s.getString("e"), s.getString("k"), ayat))
        }
        data = list
    }

    fun ayah(surah: Int, ayah: Int): Ayah = surahs[surah - 1].ayat[ayah - 1]
}
