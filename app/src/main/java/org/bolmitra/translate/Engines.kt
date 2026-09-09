package org.bolmitra.translate

import org.bolmitra.phrasebook.LookupResult

/**
 * Engine boundaries for the live translation path — ARCHITECTURE.md §6.2.
 *
 * These exist as interfaces for a reason the document states explicitly (§4.5): "MT sits
 * behind the `MtEngine` interface, so a tier-gated on-device model can be dropped in later if
 * field feedback demands it. Deferring is not the same as foreclosing."
 *
 * They also make the orchestrator testable without a device, which matters because §5.3's
 * memory numbers and §6.2.1's latency numbers are all still estimates. The orchestrator's
 * *decision logic* can be verified now; the engines' real timings cannot.
 */

/** Hindi speech to text. Implementation is the §4.3 Phase 0 decision, still open. */
interface AsrEngine {
    /**
     * Transcribes a completed utterance. Returns null if nothing intelligible was heard —
     * §6.11 row 4, ambient noise defeating the endpointer.
     */
    fun transcribe(pcm16Mono16k: ShortArray): String?
}

/** T0 phrasebook lookup. Backed by SQLite FTS4 + Kotlin trigram scoring (§4.7, V46). */
interface PhrasebookEngine {
    fun lookup(rawHindi: String): LookupResult?
}

/**
 * T1 compact neural MT (§4.5). Optional by design: if T1 never ships, T0 alone still satisfies
 * every stated requirement, so every call site must tolerate a null engine.
 */
interface MtEngine {
    /** Returns target-script text, or null if translation failed or was refused. */
    fun translate(normalizedHindi: String): MtOutput?
}

data class MtOutput(
    val targetTextNative: String,
    val targetTextDeva: String,
)

/** VITS synthesis via sherpa-onnx (§4.4). */
interface TtsEngine {
    /**
     * Synthesises the whole utterance.
     *
     * Named to reflect V49's correction: sherpa-onnx fires its TTS callback once per sentence
     * batch, and for the single short imperatives in this domain the "first chunk" *is* the
     * entire waveform. There is no intra-sentence streaming, so this is not a
     * `synthesizeFirstChunk`.
     */
    fun synthesizeUtterance(text: String): AudioClip?
}

/** Plays pre-rendered T0 audio shipped inside a language pack (§6.8.2). */
interface AudioPlayer {
    /** Starts playback of a pack audio reference. Returns false if the asset is missing. */
    fun play(audioRef: String): Boolean

    /** Plays synthesised audio held in memory. */
    fun play(clip: AudioClip): Boolean
}

/**
 * 16 kHz mono PCM. WAV rather than Opus, per V48: you cannot `mmap` a compressed bitstream
 * into `AudioTrack`, and the §5.4 size budget was computed for WAV.
 */
data class AudioClip(val pcm16Mono16k: ShortArray) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is AudioClip && pcm16Mono16k.contentEquals(other.pcm16Mono16k))

    override fun hashCode(): Int = pcm16Mono16k.contentHashCode()

    /**
     * Duration, not samples.
     *
     * The generated `data class` version prints the ShortArray's identity, which is useless, but the
     * enclosing `TurnOutcome` data classes print `clip=` through *this*, and a synthesised turn
     * carries tens of thousands of samples. Verifying a corpus hit on the tablet dumped the entire
     * waveform to logcat twice and buried every other line of the turn. Duration is the thing worth
     * knowing anyway: it says the voice produced audio rather than silence.
     */
    override fun toString(): String =
        "AudioClip(${pcm16Mono16k.size} samples, ${pcm16Mono16k.size / 16} ms)"
}
