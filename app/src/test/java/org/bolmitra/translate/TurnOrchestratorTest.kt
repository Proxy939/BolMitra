package org.bolmitra.translate

import org.bolmitra.phrasebook.HindiNormalizer
import org.bolmitra.phrasebook.LookupResult
import org.bolmitra.phrasebook.Phrase
import org.bolmitra.phrasebook.Provenance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks for the degradation ladder (§6.2.1, §6.11).
 *
 * The property under protection is the one the document states most forcefully: **never a
 * silent stall**. Every path through the orchestrator must terminate in a named outcome, and
 * the expensive neural path must never be entered when the budget cannot pay for it.
 *
 * A fake clock is used throughout so "the budget ran out" is a deterministic fact rather than
 * a timing race.
 */
class TurnOrchestratorTest {

    // --- fakes ---------------------------------------------------------------------------

    private var clock = 0L

    private fun phrase(hi: String, audioRef: String? = "a1.wav") = Phrase(
        id = 1,
        lakshyaCode = "L-TEST",
        hiText = hi,
        hiNormalized = HindiNormalizer.normalize(hi),
        targetTextNative = "placeholder-unr",
        targetTextDeva = "placeholder-deva",
        audioRef = audioRef,
        verifiedBy = "test",
        packVersion = "test-v1",
    )

    private class FakePhrasebook(var result: LookupResult?) : PhrasebookEngine {
        override fun lookup(rawHindi: String): LookupResult? = result
    }

    private class FakeMt(var output: MtOutput?, val clock: () -> Unit = {}) : MtEngine {
        override fun translate(normalizedHindi: String): MtOutput? {
            clock()
            return output
        }
    }

    private class FakeTts(var clip: AudioClip?) : TtsEngine {
        override fun synthesizeUtterance(text: String): AudioClip? = clip
    }

    private class FakePlayer(var succeed: Boolean = true) : AudioPlayer {
        var playedRef: String? = null
        var playedClip: AudioClip? = null
        override fun play(audioRef: String): Boolean {
            playedRef = audioRef; return succeed
        }
        override fun play(clip: AudioClip): Boolean {
            playedClip = clip; return succeed
        }
    }

    private fun orchestrator(
        book: PhrasebookEngine,
        mt: MtEngine? = null,
        tts: TtsEngine = FakeTts(AudioClip(ShortArray(8))),
        player: AudioPlayer = FakePlayer(),
    ) = TurnOrchestrator(book, tts, player, mt, nowMs = { clock })

    // --- Path A --------------------------------------------------------------------------

    @Test
    fun `verified phrasebook hit plays pre-rendered audio`() {
        val book = FakePhrasebook(
            LookupResult(phrase("किताब खोलो"), Provenance.VERIFIED, 1.0),
        )
        val player = FakePlayer()
        val out = orchestrator(book, player = player).handle("किताब खोलो", turnStartMs = 0)

        assertTrue(out is TurnOutcome.VerifiedAudio)
        assertEquals(Provenance.VERIFIED, out.provenance)
        assertEquals("a1.wav", player.playedRef)
    }

    @Test
    fun `fuzzy hit is surfaced as APPROXIMATE, not VERIFIED`() {
        val book = FakePhrasebook(
            LookupResult(phrase("किताब खोलो"), Provenance.APPROXIMATE, 0.8),
        )
        val out = orchestrator(book).handle("किताब खोल", turnStartMs = 0)
        assertTrue(out is TurnOutcome.ApproximateAudio)
        assertEquals(Provenance.APPROXIMATE, out.provenance)
    }

    // --- the failure that must never become a silent substitution ------------------------

    @Test
    fun `verified phrase with missing audio degrades to text, it does NOT fall through to MT`() {
        // If pack audio is missing, serving neural output instead would silently swap verified
        // content for machine content. It must degrade to text and say why (§6.11 row 1).
        val book = FakePhrasebook(
            LookupResult(phrase("किताब खोलो", audioRef = null), Provenance.VERIFIED, 1.0),
        )
        val mt = FakeMt(MtOutput("should-not-be-used", "should-not-be-used"))
        val out = orchestrator(book, mt = mt).handle("किताब खोलो", turnStartMs = 0)

        assertTrue(out is TurnOutcome.TextOnly)
        assertEquals(DegradeReason.AUDIO_ASSET_MISSING, (out as TurnOutcome.TextOnly).reason)
        assertEquals("placeholder-deva", out.devanagariText)
    }

    @Test
    fun `audio playback failure also degrades to text`() {
        val book = FakePhrasebook(
            LookupResult(phrase("किताब खोलो"), Provenance.VERIFIED, 1.0),
        )
        val out = orchestrator(book, player = FakePlayer(succeed = false))
            .handle("किताब खोलो", turnStartMs = 0)
        assertTrue(out is TurnOutcome.TextOnly)
    }

    // --- Path B and the budget -----------------------------------------------------------

    @Test
    fun `T0 miss with no MT engine is explicitly unavailable`() {
        // Expected state before Phase 3. Must be a named outcome, not a hang.
        val out = orchestrator(FakePhrasebook(null)).handle("आज मौसम अच्छा है", turnStartMs = 0)
        assertTrue(out is TurnOutcome.Unavailable)
        assertEquals(
            DegradeReason.NO_TRANSLATION_AVAILABLE,
            (out as TurnOutcome.Unavailable).reason,
        )
    }

    @Test
    fun `T0 miss with MT engine produces MACHINE audio`() {
        val mt = FakeMt(MtOutput("unr-text", "deva-text"))
        val out = orchestrator(FakePhrasebook(null), mt = mt).handle("नया वाक्य", turnStartMs = 0)
        assertTrue(out is TurnOutcome.MachineAudio)
        assertEquals(Provenance.MACHINE, out.provenance)
    }

    @Test
    fun `neural path is NOT entered when the budget cannot cover it`() {
        // 2.5 s already spent. Path B needs 1200 ms pessimistically, so 2500 + 1200 > 3000.
        clock = 2_500
        val mt = FakeMt(MtOutput("unr", "deva"))
        val out = orchestrator(FakePhrasebook(null), mt = mt).handle("नया वाक्य", turnStartMs = 0)

        assertTrue(out is TurnOutcome.Unavailable)
        assertEquals(DegradeReason.BUDGET_EXHAUSTED, (out as TurnOutcome.Unavailable).reason)
    }

    @Test
    fun `translation overrunning its estimate degrades to text instead of paying for TTS`() {
        // Budget allows starting Path B, but MT itself burns the remaining time. The second
        // check must fire — this is the case a naive implementation gets wrong.
        clock = 1_700
        val mt = FakeMt(MtOutput("unr", "deva-text"), clock = { clock = 2_600 })
        val out = orchestrator(FakePhrasebook(null), mt = mt).handle("नया वाक्य", turnStartMs = 0)

        assertTrue(out is TurnOutcome.TextOnly)
        val t = out as TurnOutcome.TextOnly
        assertEquals(DegradeReason.BUDGET_EXHAUSTED, t.reason)
        assertEquals("deva-text", t.devanagariText)
    }

    @Test
    fun `synthesis failure degrades to text with the translated Devanagari`() {
        val mt = FakeMt(MtOutput("unr", "deva-text"))
        val out = orchestrator(FakePhrasebook(null), mt = mt, tts = FakeTts(null))
            .handle("नया वाक्य", turnStartMs = 0)

        assertTrue(out is TurnOutcome.TextOnly)
        val t = out as TurnOutcome.TextOnly
        assertEquals(DegradeReason.SYNTHESIS_FAILED, t.reason)
        // The teacher can still read this aloud, so the lesson keeps moving.
        assertEquals("deva-text", t.devanagariText)
    }

    // --- ASR failure ---------------------------------------------------------------------

    @Test
    fun `null or blank transcript is an explicit outcome, never a stall`() {
        val book = FakePhrasebook(null)
        for (input in listOf(null, "", "   ")) {
            val out = orchestrator(book).handle(input, turnStartMs = 0)
            assertTrue("input=<$input>", out is TurnOutcome.Unavailable)
            assertEquals(
                DegradeReason.NO_SPEECH_RECOGNISED,
                (out as TurnOutcome.Unavailable).reason,
            )
        }
    }

    // --- the invariant that ties it together ---------------------------------------------

    @Test
    fun `a verified hit stays affordable even at the far edge of the budget`() {
        // Path A costs ~70 ms of orchestrator time, so it must still be served when the
        // neural path would have been refused. Losing this would mean a slow ASR turn
        // discards a perfectly good verified phrase.
        clock = 2_900
        val book = FakePhrasebook(
            LookupResult(phrase("किताब खोलो"), Provenance.VERIFIED, 1.0),
        )
        val out = orchestrator(book).handle("किताब खोलो", turnStartMs = 0)
        assertTrue(out is TurnOutcome.VerifiedAudio)
    }
}
