package com.qurafiz.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.qurafiz.app.RecitationMatcher.MistakeType
import com.qurafiz.app.RecitationMatcher.Result

enum class Phase { Idle, Listening, Mistake, PlayingAudio, AyahDone, Finished }

data class MistakeInfo(val type: MistakeType, val expected: String, val heard: String, val attempt: Int)

data class ReciteUi(
    val surah: Int = 1,
    val surahName: String = "",
    val ayahNumber: Int = 1,
    val position: Int = 0,          // index in queue
    val queueSize: Int = 0,
    val words: List<Word> = emptyList(),
    val confirmed: Int = 0,         // words of the CURRENT ayah recited correctly
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

/**
 * The session treats your recitation as ONE continuous stream of words: all remaining ayat of the
 * surah are laid end to end, so you can read fast across ayah boundaries and the app keeps up.
 * After a mistake you may continue from the wrong word OR restart the ayah from its first word.
 */
class ReciteViewModel(app: Application) : AndroidViewModel(app) {
    val store = ProgressStore(app)

    var ui by mutableStateOf(ReciteUi())
        private set

    private var queue: List<Pair<Int, Int>> = emptyList()

    // the stream: normalized words of consecutive ayat, plus where each ayah starts
    private var flat: List<String> = emptyList()
    private var starts: IntArray = IntArray(1)   // starts[i] = first word of ayah i, last = flat.size
    private var streamFirst = 0                  // queue index of stream item 0
    private var cur = 0                          // ayah (stream item) the user is on
    private var conf = 0                         // absolute index of the next expected word

    private var uA = 0                           // where this recognizer session continues from
    private var uB = 0                           // start of the ayah (to allow restarting it)
    private var ayahMistakes = 0
    private val attempts = HashMap<Int, Int>()
    private val lastWrong = HashMap<Int, String>()
    private var prevHeard: List<String> = emptyList()
    private var resumeAfterAudio = false

    private val engine = SpeechEngine(app, ::onHeard, ::onProblem)
    private val player = AyahPlayer(
        onFinished = { if (ui.phase == Phase.PlayingAudio) afterAudio() },
        onFailed = {
            val resume = resumeAfterAudio
            resumeAfterAudio = false
            if (resume) { startListening(); ui = ui.copy(message = "Couldn't play audio (check internet). Keep reciting.") }
            else ui = ui.copy(phase = Phase.Idle, message = "Couldn't play audio. Check your internet.")
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

    private fun buildStream(first: Int, contiguous: Boolean) {
        val last = if (contiguous) queue.lastIndex else first
        val words = ArrayList<String>()
        val st = ArrayList<Int>()
        for (i in first..last) {
            st.add(words.size)
            words.addAll(Quran.ayah(queue[i].first, queue[i].second).words.map { it.norm })
        }
        st.add(words.size)
        flat = words; starts = st.toIntArray(); streamFirst = first; cur = 0; conf = 0
    }

    private fun loadAyah(i: Int) {
        engine.stop(); player.stop()
        buildStream(i, contiguous = !ui.reviewMode)
        ayahMistakes = 0; attempts.clear(); lastWrong.clear(); prevHeard = emptyList(); resumeAfterAudio = false
        showAyah()
        ui = ui.copy(phase = Phase.Idle, message = null, liveHeard = "")
    }

    private fun showAyah() {
        val qi = streamFirst + cur
        val (s, a) = queue[qi]
        val ay = Quran.ayah(s, a)
        // bias the recognizer toward this ayah and the next one
        engine.biasWords = flat.subList(starts[cur], starts[minOf(cur + 2, starts.size - 1)])
        ui = ui.copy(
            surah = s, surahName = Quran.surahs[s - 1].name, ayahNumber = a,
            position = qi, queueSize = queue.size, words = ay.words,
            confirmed = conf - starts[cur], translation = ay.translation, mistake = null,
        )
    }

    // ---- controls -------------------------------------------------------------

    /** Called once when the screen opens: no need to tap the mic. */
    fun autoStart() { if (ui.phase == Phase.Idle) startListening() }

    fun toggleMic() {
        when (ui.phase) {
            Phase.Listening -> { engine.stop(); ui = ui.copy(phase = Phase.Idle, message = "Paused. Tap the mic to continue.") }
            Phase.Idle, Phase.Mistake -> startListening()
            else -> Unit
        }
    }

    private fun startListening() {
        engine.stop(); player.stop()
        markUtterance()
        prevHeard = emptyList()
        ui = ui.copy(phase = Phase.Listening, mistake = null, message = null, liveHeard = "")
        engine.start()
    }

    private fun markUtterance() { uA = conf; uB = starts[cur] }

    fun retry() = startListening()

    fun listenToAyah() {
        val resume = ui.phase != Phase.Idle
        engine.stop()
        resumeAfterAudio = resume
        ui = ui.copy(phase = Phase.PlayingAudio, message = null)
        player.play(ui.surah, ui.ayahNumber)
    }

    fun hearWord() {
        val resume = ui.phase != Phase.Idle
        engine.stop()
        resumeAfterAudio = resume
        ui = ui.copy(phase = Phase.PlayingAudio, message = null)
        player.playWord(ui.surah, ui.ayahNumber, (conf - starts[cur] + 1).coerceIn(1, ui.words.size))
    }

    private fun afterAudio() {
        if (resumeAfterAudio) { resumeAfterAudio = false; startListening() }
        else ui = ui.copy(phase = Phase.Idle)
    }

    /** "I said it right": count the current word as correct and carry on. */
    fun acceptWord() {
        if (ui.phase != Phase.Mistake && ui.phase != Phase.PlayingAudio) return
        player.stop(); resumeAfterAudio = false
        applyProgress(conf + 1)
        if (ui.phase != Phase.Finished) startListening()
    }

    fun skipAyah() {
        val resume = ui.phase == Phase.Listening || ui.phase == Phase.PlayingAudio
        if (ui.position + 1 < queue.size) { loadAyah(ui.position + 1); if (resume) startListening() } else finish()
    }

    fun toggleHide() { ui = ui.copy(hideMode = !ui.hideMode) }
    fun toggleStrict() { ui = ui.copy(strict = !ui.strict) }
    fun toggleTranslation() { ui = ui.copy(showTranslation = !ui.showTranslation) }

    fun leave() {
        engine.stop(); player.stop()
        ui = ui.copy(phase = Phase.Idle)
    }

    // ---- recognition callbacks (main thread) ----------------------------------------

    private fun progressOf(r: Result) = when (r) { is Result.Progress -> r.confirmed; is Result.Mistake -> r.at }

    /** Pick the reading that explains the speech best: continuing, or restarting the ayah. */
    private fun pick(a: Result, b: Result?): Result {
        if (b == null) return a
        val aOk = a is Result.Progress
        val bOk = b is Result.Progress
        val pa = progressOf(a)
        val pb = progressOf(b)
        return when {
            aOk && bOk -> if (pb > pa) b else a
            aOk -> a
            bOk -> b
            else -> if (pb > pa) b else a
        }
    }

    private fun onHeard(text: String, isFinal: Boolean) {
        if (ui.phase != Phase.Listening) return
        ui = ui.copy(liveHeard = text)
        val heard = ArabicNormalizer.words(text)
        val strict = ui.strict

        val rA = RecitationMatcher.evaluate(flat, heard, uA, isFinal, strict, lenientStart = true)
        val rB = if (uB < uA) RecitationMatcher.evaluate(flat, heard, uB, isFinal, strict) else null

        when (val r = pick(rA, rB)) {
            is Result.Progress -> {
                prevHeard = if (isFinal) emptyList() else heard
                applyProgress(r.confirmed)
                if (isFinal && ui.phase == Phase.Listening) markUtterance() // engine starts a new session
            }
            is Result.Mistake -> {
                // Partial results wobble, so on a partial only trust a wrong word once it has
                // shown up unchanged in two updates in a row.
                val stable = isFinal || prevHeard.getOrNull(r.heardIndex) == heard.getOrNull(r.heardIndex)
                prevHeard = if (isFinal) emptyList() else heard
                if (!stable) { applyProgress(r.at); return }

                engine.stop()
                if (r.at < conf) conf = maxOf(r.at, starts[cur]) else applyProgress(r.at)
                if (ui.phase == Phase.Finished) return
                val at = conf

                // Same "wrong" word heard again: the phone just hears it that way. Move on.
                val before = lastWrong[at]
                if (before != null && Phonetic.key(before, true) == Phonetic.key(r.heard, true)) {
                    applyProgress(at + 1)
                    if (ui.phase != Phase.Finished) startListening()
                    return
                }
                lastWrong[at] = r.heard
                ayahMistakes++
                val n = (attempts[at] ?: 0) + 1
                attempts[at] = n

                val wordNo = at - starts[cur] + 1
                ui = ui.copy(
                    confirmed = wordNo - 1,
                    phase = Phase.PlayingAudio,
                    mistake = MistakeInfo(r.type, ui.words[wordNo - 1].display, r.heard, n),
                    message = null,
                )
                // read the right word aloud, then listen again by itself (whole ayah after 3 misses)
                resumeAfterAudio = true
                if (n >= 3) player.play(ui.surah, ui.ayahNumber)
                else player.playWord(ui.surah, ui.ayahNumber, wordNo)
            }
        }
    }

    /** Moves the position forward, completing every ayah that has been fully recited. */
    private fun applyProgress(newConf: Int) {
        val before = conf
        conf = maxOf(conf, newConf)
        var advanced = false
        while (conf >= starts[cur + 1]) {
            val done = ui.ayahNumber
            completeCurrentAyah()
            if (cur + 1 >= starts.size - 1) { // that was the last ayah of the stream
                if (ui.reviewMode) nextReview() else finish()
                return
            }
            cur++
            ayahMistakes = 0
            showAyah()
            ui = ui.copy(message = "Ayah $done ✓")
            advanced = true
        }
        ui = ui.copy(confirmed = conf - starts[cur], message = if (!advanced && conf > before) null else ui.message)
    }

    private fun completeCurrentAyah() {
        store.record(ui.surah, ui.ayahNumber, ayahMistakes == 0)
    }

    private fun nextReview() {
        val next = ui.position + 1
        if (next < queue.size) { loadAyah(next); startListening() } else finish()
    }

    private fun onProblem(msg: String) {
        ui = ui.copy(phase = Phase.Idle, message = msg)
    }

    private fun finish() {
        engine.stop(); player.stop()
        ui = ui.copy(phase = Phase.Finished)
    }

    override fun onCleared() {
        engine.destroy(); player.stop()
    }
}
