package com.qurafiz.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qurafiz.app.RecitationMatcher.MistakeType
import com.qurafiz.app.RecitationMatcher.Result
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Phase { Idle, Listening, Mistake, PlayingAudio, AyahDone, Finished }

data class MistakeInfo(val type: MistakeType, val expected: String, val heard: String, val attempt: Int)

data class ReciteUi(
    val surah: Int = 1,
    val surahName: String = "",
    val ayahNumber: Int = 1,
    val position: Int = 0,          // index in queue
    val queueSize: Int = 0,
    val words: List<Word> = emptyList(),
    val confirmed: Int = 0,
    val translation: String = "",
    val phase: Phase = Phase.Idle,
    val mistake: MistakeInfo? = null,
    val hideMode: Boolean = false,
    val strict: Boolean = false,
    val showTranslation: Boolean = true,
    val reviewMode: Boolean = false,
    val liveHeard: String = "",     // raw text from the recognizer, shown live
    val message: String? = null,
)

class ReciteViewModel(app: Application) : AndroidViewModel(app) {
    val store = ProgressStore(app)

    var ui by mutableStateOf(ReciteUi())
        private set

    private var queue: List<Pair<Int, Int>> = emptyList()
    private var base = 0                       // first expected word for the current utterance
    private var ayahMistakes = 0
    private val attempts = HashMap<Int, Int>() // word index -> tries in this ayah
    private var prevHeard: List<String> = emptyList()
    private var resumeAfterAudio = false
    private var advanceJob: Job? = null

    private val engine = SpeechEngine(app, ::onHeard, ::onProblem)
    private val player = AyahPlayer(
        onFinished = { if (ui.phase == Phase.PlayingAudio) afterAudio() },
        onFailed = {
            resumeAfterAudio = false
            ui = ui.copy(
                phase = if (ui.mistake != null) Phase.Mistake else Phase.Idle,
                message = "Couldn't play audio. Check your internet.",
            )
        },
    )

    // ---- starting a session -------------------------------------------------

    fun startSurah(surah: Int, fromAyah: Int) {
        val total = Quran.surahs[surah - 1].ayat.size
        queue = (fromAyah.coerceIn(1, total)..total).map { surah to it }
        begin(review = false)
    }

    fun startReview() {
        queue = store.due()
        begin(review = true)
    }

    private fun begin(review: Boolean) {
        ui = ui.copy(reviewMode = review, position = 0, queueSize = queue.size)
        if (queue.isEmpty()) { ui = ui.copy(phase = Phase.Finished); return }
        loadAyah(0)
    }

    private fun loadAyah(i: Int) {
        advanceJob?.cancel()
        engine.stop(); player.stop()
        val (s, a) = queue[i]
        val ayah = Quran.ayah(s, a)
        base = 0; ayahMistakes = 0; attempts.clear(); prevHeard = emptyList(); resumeAfterAudio = false
        engine.biasWords = ayah.words.map { it.norm }
        ui = ui.copy(
            surah = s, surahName = Quran.surahs[s - 1].name, ayahNumber = a,
            position = i, queueSize = queue.size, words = ayah.words, confirmed = 0,
            translation = ayah.translation, phase = Phase.Idle, mistake = null, message = null, liveHeard = "",
        )
    }

    // ---- controls -------------------------------------------------------------

    fun toggleMic() {
        when (ui.phase) {
            Phase.Listening -> { engine.stop(); ui = ui.copy(phase = Phase.Idle) }
            Phase.Idle, Phase.Mistake -> startListening()
            else -> Unit
        }
    }

    private fun startListening() {
        player.stop()
        base = ui.confirmed
        prevHeard = emptyList()
        ui = ui.copy(phase = Phase.Listening, mistake = null, message = null, liveHeard = "")
        engine.start()
    }

    /** "Try again" after a mistake: keep the words already right, resume from the wrong one. */
    fun retry() = startListening()

    /** Play the whole ayah. */
    fun listenToAyah() {
        engine.stop()
        resumeAfterAudio = false
        ui = ui.copy(phase = Phase.PlayingAudio, message = null)
        player.play(ui.surah, ui.ayahNumber)
    }

    /** Play just the word the user is on (or got wrong). */
    fun hearWord() {
        engine.stop()
        resumeAfterAudio = false
        ui = ui.copy(phase = Phase.PlayingAudio, message = null)
        player.playWord(ui.surah, ui.ayahNumber, (ui.confirmed + 1).coerceAtMost(ui.words.size))
    }

    private fun afterAudio() {
        if (ui.mistake != null && resumeAfterAudio) {
            resumeAfterAudio = false
            startListening() // word was read to them: now they say it, hands-free
        } else {
            ui = ui.copy(phase = if (ui.mistake != null) Phase.Mistake else Phase.Idle)
        }
    }

    fun skipAyah() { if (ui.position + 1 < queue.size) loadAyah(ui.position + 1) else finish() }

    fun toggleHide() { ui = ui.copy(hideMode = !ui.hideMode) }
    fun toggleStrict() { ui = ui.copy(strict = !ui.strict) }
    fun toggleTranslation() { ui = ui.copy(showTranslation = !ui.showTranslation) }

    fun leave() {
        advanceJob?.cancel()
        engine.stop(); player.stop()
        ui = ui.copy(phase = Phase.Idle)
    }

    // ---- recognition callbacks (main thread) ----------------------------------------

    private fun onHeard(text: String, isFinal: Boolean) {
        if (ui.phase != Phase.Listening) return
        ui = ui.copy(liveHeard = text)
        val expected = ui.words.map { it.norm }
        val heard = ArabicNormalizer.words(text)

        when (val r = RecitationMatcher.evaluate(expected, heard, base, isFinal, ui.strict)) {
            is Result.Progress -> {
                prevHeard = if (isFinal) emptyList() else heard
                ui = ui.copy(confirmed = maxOf(ui.confirmed, r.confirmed))
                if (r.completed) ayahCompleted() else if (isFinal) base = ui.confirmed
            }
            is Result.Mistake -> {
                // Partial results wobble while the recognizer is still thinking, so on a partial
                // only trust a wrong word once it appeared unchanged in two updates in a row.
                val stable = isFinal || prevHeard.getOrNull(r.heardIndex) == heard.getOrNull(r.heardIndex)
                prevHeard = if (isFinal) emptyList() else heard
                if (!stable) {
                    ui = ui.copy(confirmed = maxOf(ui.confirmed, r.at))
                    return
                }
                engine.stop()
                ayahMistakes++
                val n = (attempts[r.at] ?: 0) + 1
                attempts[r.at] = n
                ui = ui.copy(
                    confirmed = maxOf(ui.confirmed, r.at),
                    phase = Phase.PlayingAudio,
                    mistake = MistakeInfo(r.type, ui.words[r.at].display, r.heard, n),
                    message = null,
                )
                // the app reads the correct word aloud, then listens again;
                // after 3 failures on the same word it reads the whole ayah
                resumeAfterAudio = true
                if (n >= 3) player.play(ui.surah, ui.ayahNumber)
                else player.playWord(ui.surah, ui.ayahNumber, r.at + 1)
            }
        }
    }

    private fun onProblem(msg: String) {
        ui = ui.copy(phase = Phase.Idle, message = msg)
    }

    private fun ayahCompleted() {
        engine.stop()
        val perfect = ayahMistakes == 0
        store.record(ui.surah, ui.ayahNumber, perfect)
        ui = ui.copy(confirmed = ui.words.size, phase = Phase.AyahDone, mistake = null,
            message = if (perfect) "Perfect! ✓" else "Done. This one will come back sooner for review.")
        advanceJob = viewModelScope.launch {
            delay(1400)
            if (ui.position + 1 < queue.size) loadAyah(ui.position + 1) else finish()
        }
    }

    private fun finish() {
        engine.stop(); player.stop()
        ui = ui.copy(phase = Phase.Finished)
    }

    override fun onCleared() {
        engine.destroy(); player.stop()
    }
}
