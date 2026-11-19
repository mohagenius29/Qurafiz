package com.qurafiz.app

import android.media.AudioAttributes
import android.media.MediaPlayer

/** Streams recitation audio (needs internet): a whole ayah (Alafasy, everyayah.com) or a single word (quran.com). */
class AyahPlayer(private val onFinished: () -> Unit, private val onFailed: () -> Unit) {
    private var mp: MediaPlayer? = null

    fun play(surah: Int, ayah: Int) =
        playUrl("https://everyayah.com/data/Alafasy_128kbps/%03d%03d.mp3".format(surah, ayah))

    /** [word] is 1-based. */
    fun playWord(surah: Int, ayah: Int, word: Int) =
        playUrl("https://audio.qurancdn.com/wbw/%03d_%03d_%03d.mp3".format(surah, ayah, word))

    private fun playUrl(url: String) {
        stop()
        try {
            mp = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                setDataSource(url)
                setOnPreparedListener { it.start() }
                setOnCompletionListener { this@AyahPlayer.stop(); onFinished() }
                setOnErrorListener { _, _, _ -> this@AyahPlayer.stop(); onFailed(); true }
                prepareAsync()
            }
        } catch (e: Exception) { stop(); onFailed() }
    }

    fun stop() {
        mp?.runCatching { release() }
        mp = null
    }
}
