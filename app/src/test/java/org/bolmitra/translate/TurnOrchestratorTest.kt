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
        /** Counted so a test can assert the model was never consulted, not merely unused. */
        var calls = 0
        override fun translate(normalizedHindi: String): MtOutput? {
            calls++
            clock()
            return output
        }
    }

    private class FakeTts(var clip: AudioClip?) : TtsEngine {
        override fun synthesizeUtterance(text: String): AudioClip? = clip
    }

    /** Records what was handed to the voice, so a test can prove WHICH text got spoken. */
    private class RecordingTts(var clip: AudioClip?) : TtsEngine {
        var lastText: String? = null
        override fun synthesizeUtterance(text: String): AudioClip? {
            lastText = text
            return clip
        }
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

    /**
     * The invariant, restated after a deliberate behaviour change.
     *
     * This test used to assert that a T0 hit with no pack audio degraded to [TurnOutcome.TextOnly].
     * That was the mechanism, not the point. The point — stated in the original comment — is that
     * pack audio going missing must never cause **neural output to be substituted for reviewed
     * content**.
     *
     * A T0 row with no pack audio is now synthesised from ITS OWN `targetTextDeva`, because the
     * shipped glossary carries 375 rows that have no pre-rendered audio and would otherwise every
     * one of them be a silent hit. Only the voice is synthetic; the words are unchanged, and the
     * voice is already synthetic on every other rung.
     *
     * So what is asserted here now is the thing that actually matters: MT is never consulted, and
     * the spoken text is the phrasebook's, not the model's.
     */
    @Test
    fun `T0 hit with missing audio is synthesised from its OWN text and never falls through to MT`() {
        val book = FakePhrasebook(
            LookupResult(phrase("किताब खोलो", audioRef = null), Provenance.VERIFIED, 1.0),
        )
        val mt = FakeMt(MtOutput("should-not-be-used", "should-not-be-used"))
        val tts = RecordingTts(AudioClip(ShortArray(8)))
        val player = FakePlayer()
        val out = orchestrator(book, mt = mt, tts = tts, player = player)
            .handle("किताब खोलो", turnStartMs = 0)

        // Provenance survives: this is still reviewed content, spoken by a synthetic voice.
        assertTrue("got $out", out is TurnOutcome.VerifiedAudio)
        assertEquals(Provenance.VERIFIED, out.provenance)

        // The load-bearing assertion: the model was never asked, and the text spoken is the
        // phrasebook row's.
        assertEquals("placeholder-deva", tts.lastText)
        assertEquals(0, mt.calls)
        assertEquals(TurnOrchestrator.SYNTHESISED, (out as TurnOutcome.VerifiedAudio).audioRef)
        // The clip travels with the outcome so a replay does not try to load SYNTHESISED as a file.
        assertTrue("clip should be carried for replay", out.clip != null)
    }

    @Test
    fun `T0 hit degrades to text only when synthesis ALSO fails`() {
        // The rung is still reachable, which is what keeps the promise honest rather than
        // aspirational — it just needs both the pack audio and the voice to be unavailable.
        val book = FakePhrasebook(
            LookupResult(phrase("किताब खोलो", audioRef = null), Provenance.VERIFIED, 1.0),
        )
        val out = orchestrator(book, tts = FakeTts(null)).handle("किताब खोलो", turnStartMs = 0)

        assertTrue("got $out", out is TurnOutcome.TextOnly)
        assertEquals(DegradeReason.AUDIO_ASSET_MISSING, (out as TurnOutcome.TextOnly).reason)
        assertEquals("placeholder-deva", out.devanagariText)
    }

    @Test
    fun `a CORPUS row keeps its provenance and citation when synthesised`() {
        val row = phrase("पानी", audioRef = null).copy(
            provenance = Provenance.CORPUS,
            src = "GATITOS",
            srcEn = "water",
        )
        val book = FakePhrasebook(LookupResult(row, Provenance.CORPUS, 1.0))
        val out = orchestrator(book).handle("पानी", turnStartMs = 0)

        assertTrue("got $out", out is TurnOutcome.CorpusAudio)
        out as TurnOutcome.CorpusAudio
        // Not promoted to VERIFIED, and the citation the teacher needs is carried through.
        assertEquals(Provenance.CORPUS, out.provenance)
        assertEquals("GATITOS", out.src)
        assertEquals("water", out.srcEn)
    }

    @Test
    fun `pack audio playback failure falls back to synthesis, not to MT`() {
        val book = FakePhrasebook(
            LookupResult(phrase("किताब खोलो"), Provenance.VERIFIED, 1.0),
        )
        val mt = FakeMt(MtOutput("should-not-be-used", "should-not-be-used"))
        // succeed=false fails the pack ref; the clip path is then tried and also uses this player,
        // so it fails too, landing on TextOnly.
        val out = orchestrator(book, mt = mt, player = FakePlayer(succeed = false))
            .handle("किताब खोलो", turnStartMs = 0)
        assertTrue("got $out", out is TurnOutcome.TextOnly)
        assertEquals(0, mt.calls)
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
