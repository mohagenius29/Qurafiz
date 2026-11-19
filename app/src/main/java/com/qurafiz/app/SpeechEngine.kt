package com.qurafiz.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Continuous Arabic listening on top of Android's SpeechRecognizer.
 * Kept behind this small class so a Quran-specific recognizer can replace it later.
 */
class SpeechEngine(
    private val ctx: Context,
    private val onHeard: (text: String, isFinal: Boolean) -> Unit,
    private val onProblem: (message: String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null
    private var active = false
    private var failStreak = 0

    /** Words of the current ayah; Android 13+ uses them to bias recognition toward the Quran. */
    var biasWords: List<String> = emptyList()

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(ctx)

    fun start() {
        if (!available) {
            onProblem("This phone has no speech recognition service. Install or enable the Google app (Google Speech Services) and try again.")
            return
        }
        active = true
        failStreak = 0
        if (rec == null) {
            rec = SpeechRecognizer.createSpeechRecognizer(ctx).apply { setRecognitionListener(listener) }
        }
        listen()
    }

    fun stop() {
        active = false
        main.removeCallbacksAndMessages(null)
        rec?.cancel()
    }

    fun destroy() {
        stop()
        rec?.destroy()
        rec = null
    }

    private fun listen() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-SA")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            // recitation has long pauses: don't cut the user off early
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            if (Build.VERSION.SDK_INT >= 33 && biasWords.isNotEmpty()) {
                putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(biasWords))
            }
        }
        rec?.startListening(i)
    }

    private fun restart(delayMs: Long = 120) {
        if (!active) return
        main.postDelayed({ if (active) listen() }, delayMs)
    }

    private fun best(b: Bundle?): String? =
        b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull { it.isNotBlank() }

    private val listener = object : RecognitionListener {
        override fun onPartialResults(b: Bundle?) {
            best(b)?.let { failStreak = 0; if (active) onHeard(it, false) }
        }

        override fun onResults(b: Bundle?) {
            best(b)?.let { failStreak = 0; if (active) onHeard(it, true) }
            restart()
        }

        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> restart()
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> { rec?.cancel(); restart(400) }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> { active = false; onProblem("Microphone permission is needed.") }
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> {
                    active = false
                    onProblem("Speech service unreachable (code $error). Check your internet connection.")
                }
                12, 13 -> { // LANGUAGE_NOT_SUPPORTED / LANGUAGE_UNAVAILABLE
                    active = false
                    onProblem("Arabic speech recognition isn't available on this phone (code $error). Update the Google app and add Arabic in Settings → Google → Voice → Languages.")
                }
                else -> {
                    if (++failStreak >= 4) {
                        active = false
                        onProblem("Speech recognition keeps failing (code $error). Close other apps using the mic and try again.")
                    } else restart(400)
                }
            }
        }

        override fun onReadyForSpeech(p: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
