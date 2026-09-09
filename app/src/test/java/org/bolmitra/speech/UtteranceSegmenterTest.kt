package org.bolmitra.speech

import org.bolmitra.speech.UtteranceSegmenter.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks on the logic that decides where one spoken sentence ends.
 *
 * This is the load-bearing new piece of the tap-to-start/tap-to-stop mic: with a fixed 4 s window
 * the cut point was a constant and could not be wrong, and now it is a decision. Both ways of
 * getting it wrong are invisible to a build and to a screenshot — cutting too early translates
 * fragments, never cutting holds the whole lesson in one buffer and translates once at the end.
 *
 * Pure arithmetic, so it runs in a plain JVM test with no device and no `AudioRecord`.
 */
class UtteranceSegmenterTest {

    /** One `AudioCapture.drain()` is a 2048-sample read = 128 ms at 16 kHz. */
    private val chunkMs = 128L

    /**
     * Feeds a constant level for [ms] and returns the first non-CONTINUE decision, or null.
     * [from] is the elapsed clock to start at, so calls can be chained.
     */
    private fun feed(
        seg: UtteranceSegmenter,
        rms: Float,
        ms: Long,
        from: Long,
    ): Pair<Decision?, Long> {
        var t = from
        val end = from + ms
        while (t < end) {
            val d = seg.feed(rms, t)
            if (d != Decision.CONTINUE) return d to t
            t += chunkMs
        }
        return null to t
    }

    @Test
    fun `the shipped self-check passes`() {
        assertEquals(emptyList<String>(), UtteranceSegmenter().validate())
    }

    @Test
    fun `speech then a pause ends the utterance on silence`() {
        val seg = UtteranceSegmenter()
        var t = 0L
        // Quiet room, then a sentence, then a pause longer than the hold.
        t = feed(seg, 0.002f, 400, t).second     // calibration
        t = feed(seg, 0.18f, 1_500, t).second    // speaking
        assertTrue("0.18 rms in a quiet room must count as speech", seg.hasHeardSpeech)
        val (decision, _) = feed(seg, 0.002f, 1_200, t)
        assertEquals(Decision.ENDED_ON_SILENCE, decision)
    }

    /**
     * The failure that would look like a broken microphone.
     *
     * A teacher taps the mic and then pauses to think. If leading silence could close an utterance,
     * the session would emit an empty turn immediately and keep doing it.
     */
    @Test
    fun `silence before any speech never ends the utterance`() {
        // maxUtteranceMs is explicit so this asserts the silence rule rather than whatever the
        // current limit happens to be. Running out the limit IS a legitimate way for a silent
        // utterance to end; ending it on SILENCE is not, because nothing was ever said.
        val seg = UtteranceSegmenter(maxUtteranceMs = 60_000L)
        val (decision, _) = feed(seg, 0.001f, 20_000, 0L)
        assertEquals("a silent start must not cut", null, decision)
        assertFalse(seg.hasHeardSpeech)
    }

    /**
     * The same rule stated against the shipped defaults: a session where nobody speaks must end on
     * the limit, never on silence, however long the limit is.
     */
    @Test
    fun `a silent utterance ends on the limit and not on silence`() {
        val seg = UtteranceSegmenter()
        var t = 0L
        var sawSilenceCut = false
        var sawLimitCut = false
        repeat(400) {
            when (seg.feed(0.001f, t)) {
                Decision.ENDED_ON_SILENCE -> sawSilenceCut = true
                Decision.ENDED_ON_LIMIT -> sawLimitCut = true
                Decision.CONTINUE -> Unit
            }
            t += chunkMs
        }
        assertFalse("cut on silence without ever hearing speech", sawSilenceCut)
        assertTrue("a silent utterance never terminated", sawLimitCut)
    }

    /**
     * The failure that would look like the mic ignoring the teacher.
     *
     * A fixed absolute threshold is the classic mistake here: a room with a fan sits permanently
     * above it, so the utterance never ends on silence and every cut is the 15 s limit. The gate has
     * to rise with the measured room.
     */
    @Test
    fun `the gate adapts to a noisy room instead of treating noise as speech`() {
        val noisy = UtteranceSegmenter()
        var t = feed(noisy, 0.03f, 400, 0L).second
        assertTrue(
            "gate ${noisy.gate} did not rise above a 0.03 noise floor",
            noisy.gate > 0.03f,
        )
        // Continued room noise at the same level is not speech...
        t = feed(noisy, 0.03f, 1_000, t).second
        assertFalse("room noise was counted as speech", noisy.hasHeardSpeech)
        // ...but a teacher speaking over it is.
        feed(noisy, 0.30f, 500, t)
        assertTrue("speech above a noisy floor was missed", noisy.hasHeardSpeech)
    }

    /**
     * The bug this caught: an utterance that BEGINS while the teacher is already speaking.
     *
     * It happens for real. A monologue with no pause is cut at `maxUtteranceMs`, and the next
     * utterance starts mid-word, so calibration measures the teacher's voice as the room. Without a
     * ceiling on the gate, `0.2 * 2.5 = 0.5` sits above anything a human produces — the teacher goes
     * inaudible to the segmenter and every following utterance also runs to the limit. Found by a
     * test that was itself written wrong, which is how the real case surfaced.
     */
    @Test
    fun `a gate calibrated on speech still admits that speech`() {
        val seg = UtteranceSegmenter()
        val t = feed(seg, 0.20f, 400, 0L).second
        assertTrue("gate ${seg.gate} closed over 0.20 speech", seg.gate < 0.20f)
        feed(seg, 0.20f, 500, t)
        assertTrue("speech was missed after calibrating on itself", seg.hasHeardSpeech)
    }

    /**
     * And the converse: in a silent room the relative gate collapses toward zero, so without an
     * absolute floor the recogniser's own noise would read as speech.
     */
    @Test
    fun `a near-silent room still has a usable floor`() {
        val seg = UtteranceSegmenter(minSpeechRms = 0.012f)
        feed(seg, 0.0f, 400, 0L)
        assertEquals(0.012f, seg.gate, 1e-6f)
    }

    /** A teacher who never pauses must still get translations, just on a timer. */
    @Test
    fun `an unbroken monologue ends on the limit`() {
        val seg = UtteranceSegmenter(maxUtteranceMs = 5_000L)
        val (decision, at) = feed(seg, 0.25f, 20_000, 0L)
        assertEquals(Decision.ENDED_ON_LIMIT, decision)
        assertTrue("cut at ${at}ms, before the 5s limit", at >= 5_000L)
    }

    /**
     * The complaint this fixes: "if my speech finishes under the limit, I should not have to wait".
     *
     * A classroom's ambience measured above the gate on the tablet, so "quieter than the room's
     * threshold" never became true and the pause was invisible — the teacher finished a sentence and
     * waited out the utterance limit. End of speech has to be detectable as a drop from the voice's
     * own level, not only as an absolute quiet.
     */
    @Test
    fun `a pause is detected in a room too noisy for the absolute gate`() {
        val seg = UtteranceSegmenter()
        var t = 0L
        // Ambience deliberately above maxGateRms, so the gate alone can never call this quiet.
        t = feed(seg, 0.10f, 400, t).second
        assertTrue("ambience 0.10 must sit above the clamped gate ${seg.gate}", 0.10f > seg.gate)

        t = feed(seg, 0.30f, 1_500, t).second
        assertTrue("teacher speaking over ambience was missed", seg.hasHeardSpeech)

        // Teacher stops. The room is unchanged and still above the gate.
        val (decision, at) = feed(seg, 0.10f, 2_000, t)
        assertEquals(
            "the pause was not detected, so the teacher waits for the limit",
            Decision.ENDED_ON_SILENCE,
            decision,
        )
        // And it must be found promptly, not eventually.
        assertTrue("took ${at - t}ms to notice the pause", (at - t) < 1_500)
    }

    /** A short pause inside a sentence must not split it. */
    @Test
    fun `a pause shorter than the hold does not cut`() {
        val seg = UtteranceSegmenter(silenceHoldMs = 800L)
        var t = feed(seg, 0.002f, 400, 0L).second
        t = feed(seg, 0.2f, 800, t).second
        // 500 ms gap - a breath between clauses, not the end of the sentence.
        val (decision, t2) = feed(seg, 0.002f, 500, t)
        assertEquals(null, decision)
        // Speaking resumes and the silence timer must have been cleared, so a later short gap is
        // also survivable rather than cumulative.
        val t3 = feed(seg, 0.2f, 500, t2).second
        assertEquals(null, feed(seg, 0.002f, 500, t3).first)
    }

    /**
     * The risk the relative-drop rule introduces, pinned so it cannot regress.
     *
     * Natural speech dips between words to well under half its average. If a dip alone could cut,
     * every sentence would be chopped into words and each fragment translated separately. The
     * [UtteranceSegmenter] silence hold is what makes the aggressive ratio safe, so this asserts a
     * realistic word-gap pattern survives.
     */
    @Test
    fun `dips between words do not split a sentence`() {
        val seg = UtteranceSegmenter()
        var t = feed(seg, 0.002f, 400, 0L).second
        // Six "words" of 380 ms each, separated by 130 ms near-silent gaps - one chunk of quiet.
        repeat(6) {
            val loud = feed(seg, 0.25f, 380, t)
            assertEquals("cut during a word", null, loud.first)
            t = loud.second
            val gap = feed(seg, 0.01f, 130, t)
            assertEquals("cut on a gap between words", null, gap.first)
            t = gap.second
        }
        assertTrue(seg.hasHeardSpeech)
    }

    @Test
    fun `reset forgets the room and the speech state`() {
        val seg = UtteranceSegmenter()
        feed(seg, 0.2f, 2_000, 0L)
        assertTrue(seg.hasHeardSpeech)
        seg.reset()
        assertFalse(seg.hasHeardSpeech)
        assertEquals(0f, seg.noiseFloor, 1e-6f)
    }
}
