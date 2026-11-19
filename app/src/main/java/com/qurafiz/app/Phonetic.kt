package com.qurafiz.app

/**
 * Sound-based key for a normalized Arabic word. Speech recognizers spell Quranic words in many
 * slightly different ways (long vowels, ة/ه, dropped "ال"), so words are compared by their key.
 * Gentle mode also merges look-alike consonants the recognizer often confuses.
 */
object Phonetic {
    private val gentleMap = mapOf(
        'ث' to 'س', 'ص' to 'س', 'ذ' to 'ز', 'ظ' to 'ز', 'ض' to 'د', 'ط' to 'ت',
        'ح' to 'ه', 'ق' to 'ك', 'غ' to 'خ', 'ع' to 'ا', 'ء' to 'ا',
    )

    fun key(word: String, strict: Boolean): String {
        var w = word
        if (w.length > 3 && w.startsWith("ال")) w = w.substring(2)
        val sb = StringBuilder()
        for (c in w) {
            val m = if (strict) c else (gentleMap[c] ?: c)
            if (!strict && (m == 'ا' || m == 'و' || m == 'ي')) continue // skeleton: drop long vowels
            if (sb.isNotEmpty() && sb.last() == m) continue
            sb.append(m)
        }
        return if (sb.isEmpty()) w else sb.toString()
    }
}
