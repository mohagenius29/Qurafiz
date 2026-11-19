package com.qurafiz.app

/** Turns Uthmani Quran text and speech-recognizer output into one comparable form. */
object ArabicNormalizer {
    // tashkeel, Quranic marks, dagger alif, tatweel
    private val marks = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u08D3-\\u08FF\\u0640]")
    private val spaces = Regex("\\s+")

    fun normalize(s: String): String {
        val t = marks.replace(s, "")
        val sb = StringBuilder(t.length)
        for (c in t) {
            when (c) {
                '\u0623', '\u0625', '\u0622', '\u0671' -> sb.append('\u0627') // أ إ آ ٱ -> ا
                '\u0649', '\u06CC' -> sb.append('\u064A')                     // ى ی -> ي
                '\u0629' -> sb.append('\u0647')                               // ة -> ه
                '\u0624' -> sb.append('\u0648')                               // ؤ -> و
                '\u0626' -> sb.append('\u064A')                               // ئ -> ي
                '\u06A9' -> sb.append('\u0643')                               // ک -> ك
                else -> sb.append(c)
            }
        }
        return sb.toString().trim()
    }

    fun words(s: String): List<String> =
        normalize(s).split(spaces).filter { it.isNotEmpty() }
}
