package com.qurafiz.app

import android.media.AudioAttributes
import android.media.MediaPlayer

/** Streams one ayah recited by Mishary Alafasy (everyayah.com). Needs internet. */
class AyahPlayer(private val onFinished: () -> Unit, private val onFailed: () -> Unit) {
    private var mp: MediaPlayer? = null

    fun play(surah: Int, ayah: Int) {
        stop()
        val url = "https://everyayah.com/data/Alafasy_128kbps/%03d%03d.mp3".format(surah, ayah)
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
