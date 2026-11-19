package com.qurafiz.app

import com.qurafiz.app.RecitationMatcher.MistakeType
import com.qurafiz.app.RecitationMatcher.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecitationMatcherTest {
    private val ayah = ArabicNormalizer.words("ٱلۡحَمۡدُ لِلَّهِ رَبِّ ٱلۡعَٰلَمِينَ")

    private fun run(spoken: String, final: Boolean = true, strict: Boolean = false, start: Int = 0) =
        RecitationMatcher.evaluate(ayah, ArabicNormalizer.words(spoken), start, final, strict)

    @Test fun normalizerStripsMarks() {
        assertEquals(listOf("الحمد", "لله", "رب", "العلمين"), ayah)
    }

    @Test fun perfectRecitationCompletes() {
        val r = run("الحمد لله رب العالمين") as Result.Progress
        assertTrue(r.completed)
    }

    @Test fun partialProgress() {
        val r = run("الحمد لله", final = false) as Result.Progress
        assertEquals(2, r.confirmed)
        assertTrue(!r.completed)
    }

    @Test fun skippedWordIsDetected() {
        val r = run("الحمد رب العالمين") as Result.Mistake
        assertEquals(1, r.at)
        assertEquals(MistakeType.SKIPPED_WORD, r.type)
    }

    @Test fun wrongWordIsDetected() {
        val r = run("الحمد لله ملك العالمين") as Result.Mistake
        assertEquals(2, r.at)
        assertEquals(MistakeType.WRONG_WORD, r.type)
    }

    @Test fun unfinishedLastWordIsNotJudged() {
        val r = run("الحمد لله ر", final = false) as Result.Progress
        assertEquals(2, r.confirmed)
    }

    @Test fun continuesFromStartIndex() {
        val r = run("رب العالمين", start = 2) as Result.Progress
        assertTrue(r.completed)
    }

    @Test fun spellingVariantsAreAccepted() {
        assertTrue(run("الهمد لله رب العلمين") is Result.Progress)
        assertTrue((run("الحمدلله رب العالمين") as Result.Progress).completed)
    }

    @Test fun droppedTinyWordIsTolerated() {
        val a = ArabicNormalizer.words("ٱلَّذِي يُوَسۡوِسُ فِي صُدُورِ ٱلنَّاسِ")
        val r = RecitationMatcher.evaluate(a, ArabicNormalizer.words("الذي يوسوس صدور الناس"), 0, true, false) as Result.Progress
        assertTrue(r.completed)
    }
}
