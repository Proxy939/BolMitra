package org.bolmitra.translate

import org.bolmitra.phrasebook.Provenance

/**
 * One classroom turn: teacher speaks Hindi, students hear Mundari — ARCHITECTURE.md §6.2.
 *
 * This class owns the guarantee that R3 actually rests on. §6.2.1's rule is blunt: "If a turn
 * is projected over budget: approximate T0 match -> text-only display (teacher reads the
 * Devanagari aloud) -> explicit 'couldn't translate'. **Never a silent eight-second stall.**
 * A predictable failure a teacher can work around beats an unpredictable wait."
 *
 * The word doing the work is **projected**. The budget is checked *before* committing to an
 * expensive stage, not discovered afterwards — by the time you have overrun, the teacher is
 * already standing in silence in front of thirty children.
 */

/** Stage cost estimates from §6.2.1, pessimistic ends. ALL ARE ESTIMATES, not measurements. */
object LatencyBudget {
    /** R3, from the problem statement. The only number here that is not negotiable. */
    const val DEADLINE_MS = 3_000L

    /** §6.2.1 Path A: T0 lookup — normalize + FTS4 + trigram. */
    const val T0_LOOKUP_MS = 20L

    /** §6.2.1 Path A: pre-rendered audio playback start, 16 kHz WAV, mmap-ed (V48). */
    const val PRERENDERED_PLAYBACK_MS = 50L

    /** §6.2.1 Path B: T1 compact MT, int8, greedy decode. Pessimistic end of 300–600 ms. */
    const val T1_TRANSLATE_MS = 600L

    /**
     * §6.2.1 Path B: VITS full-utterance synthesis. Pessimistic end of 400–600 ms, and marked
     * **unverified** in the document (V49) — this is the least trustworthy number in the budget.
     */
    const val TTS_SYNTHESIS_MS = 600L

    /** Headroom needed before it is safe to start the neural path at all. */
    const val NEURAL_PATH_COST_MS = T1_TRANSLATE_MS + TTS_SYNTHESIS_MS
}

/**
 * What the teacher and class actually got. Every branch is a *designed* outcome — there is no
 * implicit "something went wrong" state, because §6.11 requires each failure to have a defined
 * behaviour rather than a hang.
 */
sealed interface TurnOutcome {
    /** The provenance to surface in the UI (§4.5). Null where nothing was translated. */
    val provenance: Provenance?

    /** Path A. Native-speaker verified phrase, pre-rendered audio. The common case. */
    data class VerifiedAudio(val targetText: String, val audioRef: String) : TurnOutcome {
        override val provenance = Provenance.VERIFIED
    }

    /** Path A, fuzzy. Served but flagged, so the teacher can judge (§6.3 step 2). */
    data class ApproximateAudio(val targetText: String, val audioRef: String, val score: Double) :
        TurnOutcome {
        override val provenance = Provenance.APPROXIMATE
    }

    /** Path B. T1 output synthesised by VITS. No human has reviewed this string. */
    data class MachineAudio(val targetText: String, val clip: AudioClip) : TurnOutcome {
        override val provenance = Provenance.MACHINE
    }

    /**
     * Degraded rung. We have text but no audio — either synthesis failed or the budget would
     * not cover it. The teacher reads the Devanagari aloud themselves, which keeps the lesson
     * moving. This is the rung that makes the ≤3 s promise honest rather than aspirational.
     */
    data class TextOnly(val devanagariText: String, val reason: DegradeReason) : TurnOutcome {
        override val provenance = Provenance.MACHINE
    }

    /** Final rung. Explicit, immediate, and never a silent wait. */
    data class Unavailable(val reason: DegradeReason) : TurnOutcome {
        override val provenance: Provenance? = null
    }
}

enum class DegradeReason {
    /** Nothing intelligible was heard — §6.11 row 4. */
    NO_SPEECH_RECOGNISED,

    /** T0 missed and no T1 engine is installed. Expected before Phase 3. */
    NO_TRANSLATION_AVAILABLE,

    /** Continuing would have breached the 3 s deadline. The projection fired. */
    BUDGET_EXHAUSTED,

    /** T1 produced text but TTS could not synthesise it. */
    SYNTHESIS_FAILED,

    /** Pack audio referenced by a verified phrase was missing — §6.11 row 1. */
    AUDIO_ASSET_MISSING,
}

/**
 * Runs the ladder for a single turn.
 *
 * [nowMs] is injected so the budget logic is deterministically testable. In production it is
 * `SystemClock.elapsedRealtime`, which unlike wall-clock time cannot jump backwards.
 */
class TurnOrchestrator(
    private val phrasebook: PhrasebookEngine,
    private val tts: TtsEngine,
    private val player: AudioPlayer,
    /** Null until Phase 3. T0 alone satisfies every requirement without it (§4.5). */
    private val mt: MtEngine? = null,
    private val deadlineMs: Long = LatencyBudget.DEADLINE_MS,
    private val nowMs: () -> Long,
) {

    /**
     * @param turnStartMs when the teacher began speaking. VAD and ASR time has already been
     *   spent by the time this is called, and it counts against the same deadline — measuring
     *   only from ASR completion would flatter the numbers.
     */
    fun handle(transcript: String?, turnStartMs: Long): TurnOutcome {
        if (transcript.isNullOrBlank()) {
            return TurnOutcome.Unavailable(DegradeReason.NO_SPEECH_RECOGNISED)
        }

        val hit = phrasebook.lookup(transcript)

        if (hit != null) {
            val audioRef = hit.phrase.audioRef
            // A verified phrase whose pack audio is missing must not silently fall through to
            // the neural path — that would swap verified content for machine output without
            // telling anyone. Degrade to text and name the reason (§6.11 row 1).
            if (audioRef == null || !player.play(audioRef)) {
                return TurnOutcome.TextOnly(
                    hit.phrase.targetTextDeva,
                    DegradeReason.AUDIO_ASSET_MISSING,
                )
            }
            return when (hit.provenance) {
                Provenance.VERIFIED ->
                    TurnOutcome.VerifiedAudio(hit.phrase.targetTextNative, audioRef)
                else ->
                    TurnOutcome.ApproximateAudio(
                        hit.phrase.targetTextNative,
                        audioRef,
                        hit.score,
                    )
            }
        }

        // T0 miss. Path B is only attempted if an engine exists AND the budget can cover it.
        val engine = mt
            ?: return TurnOutcome.Unavailable(DegradeReason.NO_TRANSLATION_AVAILABLE)

        if (!canAfford(turnStartMs, LatencyBudget.NEURAL_PATH_COST_MS)) {
            return TurnOutcome.Unavailable(DegradeReason.BUDGET_EXHAUSTED)
        }

        val translated = engine.translate(transcript)
            ?: return TurnOutcome.Unavailable(DegradeReason.NO_TRANSLATION_AVAILABLE)

        // Re-check before synthesis: translation may itself have overrun its estimate, and
        // that is exactly when a naive implementation commits to another 600 ms it cannot pay.
        if (!canAfford(turnStartMs, LatencyBudget.TTS_SYNTHESIS_MS)) {
            return TurnOutcome.TextOnly(translated.targetTextDeva, DegradeReason.BUDGET_EXHAUSTED)
        }

        // Synthesis takes the DEVANAGARI form, not the native one.
        //
        // The TTS stage is Devanagari-in by construction: the voice is trained on Odia orthography
        // and SherpaMundariTts runs DevanagariToOdia itself (V63). For Mundari the two fields hold
        // the same string, so this is invisible. For Santali it is the whole difference between
        // speech and silence — targetTextNative is Ol Chiki, and DevanagariToOdia has no mapping
        // for a single Ol Chiki character, so it would drop the entire utterance and synthesise
        // nothing. targetTextNative stays the field the class SEES; this is the field it HEARS.
        val clip = tts.synthesizeUtterance(translated.targetTextDeva)
            ?: return TurnOutcome.TextOnly(
                translated.targetTextDeva,
                DegradeReason.SYNTHESIS_FAILED,
            )

        if (!player.play(clip)) {
            return TurnOutcome.TextOnly(translated.targetTextDeva, DegradeReason.SYNTHESIS_FAILED)
        }
        return TurnOutcome.MachineAudio(translated.targetTextNative, clip)
    }

    /** True if [costMs] more work still lands inside the deadline. */
    private fun canAfford(turnStartMs: Long, costMs: Long): Boolean =
        (nowMs() - turnStartMs) + costMs <= deadlineMs
}
