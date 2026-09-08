package org.bolmitra.translate

import android.util.Log

/**
 * Stand-in engines for the pre-Phase-1 build.
 *
 * These are deliberately honest rather than convincing. Nothing here pretends to translate or
 * speak: the point is to exercise the [TurnOrchestrator]'s decision logic on real hardware
 * before sherpa-onnx is wired in, and to make the degradation ladder observable.
 *
 * Every one of these is replaced in Phase 1 by a sherpa-onnx implementation behind the same
 * interface (§4.5's `MtEngine` boundary, extended to ASR and TTS for the same reason).
 */

private const val TAG = "BolMitra/stub"

/**
 * Reports that playback was requested, without producing sound.
 *
 * Returns false when [audioRef] is null-ish or clearly absent, so the
 * [DegradeReason.AUDIO_ASSET_MISSING] rung is genuinely reachable on device rather than
 * theoretical. Real playback is `AudioTrack` over 16 kHz mmap-ed WAV (V48).
 */
class StubAudioPlayer(
    /** Set of refs to treat as present. Anything else is reported missing. */
    private val availableRefs: Set<String>,
) : AudioPlayer {

    var lastPlayedRef: String? = null
        private set

    override fun play(audioRef: String): Boolean {
        val ok = audioRef in availableRefs
        lastPlayedRef = if (ok) audioRef else null
        Log.d(TAG, "play(ref=$audioRef) available=$ok")
        return ok
    }

    override fun play(clip: AudioClip): Boolean {
        Log.d(TAG, "play(clip samples=${clip.pcm16Mono16k.size})")
        return true
    }
}

/**
 * TTS that always fails.
 *
 * Not laziness — it is the accurate state of the system before Phase 1. Failing here means a
 * T0 miss surfaces as [TurnOutcome.TextOnly] with [DegradeReason.SYNTHESIS_FAILED], which is
 * exactly the behaviour that should be visible and testable today. A stub returning silent
 * audio would hide the rung instead of demonstrating it.
 */
class UnavailableTts : TtsEngine {
    override fun synthesizeUtterance(text: String): AudioClip? {
        Log.d(TAG, "synthesizeUtterance refused (no TTS model until Phase 1): $text")
        return null
    }
}

/**
 * Echoes the Hindi back, tagged so it can never be mistaken for a translation.
 *
 * Exists only so the Path B branch of the ladder can be walked before T1 exists. §4.5 is
 * explicit that T1 is optional and T0 alone satisfies every stated requirement, so the app
 * must behave correctly with `mt = null` too — pass null to see that path.
 */
class EchoMtEngine : MtEngine {
    override fun translate(normalizedHindi: String): MtOutput = MtOutput(
        targetTextNative = "[MT-लंबित] $normalizedHindi",
        targetTextDeva = "[MT-लंबित] $normalizedHindi",
    )
}
