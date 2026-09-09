package org.bolmitra.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks on when a mic session stops, and on the readout that tells the teacher.
 *
 * ### Why this file replaced `UtteranceSegmenterTest`
 *
 * The segmenter ended a recording ~800 ms after the signal level dropped, so it could tell a pause
 * for breath from the end of a sentence only by duration — which does not distinguish them for a
 * person thinking while they speak. Reported from the field: the teacher was cut off mid-sentence,
 * the translation started over the top of them, and the on-screen timer kept climbing to 15 s as
 * though the mic were still open.
 *
 * Its twelve tests all passed. They asserted that the gate behaved as designed, and the design was
 * the problem, which is the useful lesson: a test suite around a mechanism cannot tell you the
 * mechanism should not exist. What is protected now is the *rule* instead — recording ends when the
 * teacher says so or when the backstop fires, and never for any other reason.
 */
class MicSessionTest {

    private val deadline = 15_000L

    @Test
    fun `recording continues while the teacher is neither stopping nor out of time`() {
        assertTrue(shouldKeepRecording(stopRequested = false, nowMs = 0, deadlineMs = deadline))
        assertTrue(shouldKeepRecording(stopRequested = false, nowMs = 7_000, deadlineMs = deadline))
        assertTrue(shouldKeepRecording(stopRequested = false, nowMs = 14_999, deadlineMs = deadline))
    }

    @Test
    fun `a manual stop ends recording immediately, at any point`() {
        listOf(0L, 1L, 5_000L, 14_999L).forEach { now ->
            assertFalse(
                "a tap at ${now}ms must stop recording",
                shouldKeepRecording(stopRequested = true, nowMs = now, deadlineMs = deadline),
            )
        }
    }

    @Test
    fun `the deadline stops recording and not a millisecond later`() {
        assertTrue(shouldKeepRecording(false, deadline - 1, deadline))
        assertFalse(shouldKeepRecording(false, deadline, deadline))
        assertFalse(shouldKeepRecording(false, deadline + 1, deadline))
    }

    @Test
    fun `nothing between the start and the deadline can end a recording`() {
        // The regression this file exists for. A pause used to end it; the parameter list is now the
        // exhaustive set of reasons, so a sweep across the whole window with no stop requested must
        // keep recording throughout.
        var stops = 0
        var t = 0L
        while (t < deadline) {
            if (!shouldKeepRecording(stopRequested = false, nowMs = t, deadlineMs = deadline)) stops++
            t += 128      // one AudioCapture.drain() at 16 kHz
        }
        assertEquals("recording stopped early $stops time(s) inside the window", 0, stops)
    }

    @Test
    fun `a zero-length window stops at once rather than recording forever`() {
        assertFalse(shouldKeepRecording(false, 0, 0))
    }

    // --- the readout ------------------------------------------------------------------------

    @Test
    fun `the timer reads mm ss`() {
        assertEquals("00:00", formatMmSs(0))
        assertEquals("00:01", formatMmSs(1_000))
        assertEquals("00:15", formatMmSs(15_000))
        assertEquals("01:05", formatMmSs(65_000))
    }

    @Test
    fun `a partial second is not rounded up`() {
        // The readout must never show 00:15 while recording is still running, or a teacher watching
        // it to decide when to tap sees the cap arrive early.
        assertEquals("00:14", formatMmSs(14_999))
    }

    @Test
    fun `a negative elapsed time cannot render as a negative clock`() {
        assertEquals("00:00", formatMmSs(-1))
        assertEquals("00:00", formatMmSs(-10_000))
    }
}
