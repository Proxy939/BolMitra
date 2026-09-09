package org.bolmitra.speech

/**
 * Decides where one spoken utterance ends, from signal level alone.
 *
 * ### Why this exists
 *
 * The mic used to be a fixed 4 s window per tap: one tap, one utterance, one translation, and a
 * teacher mid-sentence at 4 s was simply cut off. The mic is now a toggle that stays open, so
 * something has to say where each sentence ends — a teacher speaking for two minutes should hear
 * eight translations, not one two-minute transcript.
 *
 * ### Why not a VAD
 *
 * `ModelStore` already stages a Silero VAD and `hasVad()` already reports on it, and nothing loads
 * it. Keeping it that way is deliberate: this is ~30 lines of arithmetic whose failure mode is a
 * late cut, against an ONNX artifact that has never run on device and where sherpa-onnx calls
 * `exit(-1)` on malformed input (V67) — an uncatchable process death in front of a class.
 *
 * ### The one thing that makes it survive a real room
 *
 * The threshold is **relative to measured noise**, not absolute. A fixed dB gate is the standard way
 * to get this wrong: tuned in a quiet room it treats a ceiling fan as continuous speech, and tuned
 * for a fan it misses a soft-spoken teacher. So the first [calibrationMs] of every utterance
 * measures the room, and speech is whatever exceeds that by [speechOverNoise].
 *
 * The gate is then clamped at **both** ends, and each bound answers a failure that actually occurs:
 * [minSpeechRms] for a silent room, where a near-zero floor times any factor is still near zero and
 * the recogniser's own hiss would read as speech; [maxGateRms] for an utterance that begins while the
 * teacher is already talking, where the floor is measured at speech level and an unclamped gate would
 * close over their voice for the rest of the session.
 *
 * Not thread-safe; feed it from the one loop that owns the mic.
 */
class UtteranceSegmenter(
    private val calibrationMs: Long = 400L,
    private val speechOverNoise: Float = 2.5f,
    private val minSpeechRms: Float = 0.012f,
    /**
     * Upper bound on the adaptive gate. **This is not a tuning nicety, it fixes a real failure.**
     *
     * Calibration measures whatever arrives in the first [calibrationMs], and that is not always the
     * room. When an utterance is cut at [maxUtteranceMs] mid-monologue, the next one begins while the
     * teacher is still talking, so the floor is measured at *speech* level and `floor *
     * speechOverNoise` lands above anything the teacher will produce — the gate closes over their
     * voice and every subsequent utterance also runs to the limit. Capping the gate means even a
     * floor mismeasured at 0.2 still admits speech at 0.18.
     */
    private val maxGateRms: Float = 0.08f,
    /**
     * End of speech is also a **drop relative to the speech itself**, not only a fall below the
     * room's gate. A chunk quieter than this fraction of the running speech level counts as a pause.
     *
     * This is what makes a pause detectable in a room the gate cannot separate. Measured on the
     * tablet, classroom ambience sits *above* [maxGateRms], so "below the gate" never becomes true
     * and every utterance ran to its limit — the teacher finished a sentence and then waited seconds
     * for a translation. A teacher speaking near the tablet is comfortably louder than the room, so
     * the ratio catches the pause even though the absolute level does not.
     *
     * The ratio can be this aggressive only because [silenceHoldMs] follows it: the dip between two
     * words lasts ~50-150 ms, while the gap between two sentences lasts far longer. Without the hold
     * this would cut mid-sentence.
     */
    private val speechDropRatio: Float = 0.55f,
    private val silenceHoldMs: Long = 800L,
    private val maxUtteranceMs: Long = 7_000L,
) {

    /** What the caller should do after the most recent chunk. */
    enum class Decision {
        /** Keep recording. */
        CONTINUE,

        /** The teacher paused. Cut here and translate. */
        ENDED_ON_SILENCE,

        /** No pause arrived in time. Cut anyway so the class is not left waiting. */
        ENDED_ON_LIMIT,
    }

    private var noiseSum = 0.0
    private var noiseReads = 0
    private var heardSpeech = false
    private var silenceSinceMs = 0L

    /**
     * Running level of the teacher's voice, as an EMA over chunks judged to be speech.
     *
     * An average rather than a peak, so one loud syllable cannot raise the drop threshold above
     * everything that follows it.
     */
    private var speechLevel = 0f

    /** True once any chunk has counted as speech. Nothing is cut before this. */
    val hasHeardSpeech: Boolean get() = heardSpeech

    /** The measured level of the voice so far, or 0 before any speech. Exposed for logging. */
    val currentSpeechLevel: Float get() = speechLevel

    /** The measured room level, or 0 before calibration completes. Exposed for logging. */
    val noiseFloor: Float get() = if (noiseReads > 0) (noiseSum / noiseReads).toFloat() else 0f

    /**
     * The level a chunk must exceed to count as speech: the measured room scaled up, bounded at both
     * ends. See [minSpeechRms] for the silent-room case and [maxGateRms] for the calibrated-on-speech
     * case.
     */
    val gate: Float get() = maxOf(minOf(noiseFloor * speechOverNoise, maxGateRms), minSpeechRms)

    /** Forgets the room and the speech state, for reuse on the next utterance. */
    fun reset() {
        noiseSum = 0.0
        noiseReads = 0
        heardSpeech = false
        silenceSinceMs = 0L
        speechLevel = 0f
    }

    /**
     * Feeds one chunk's RMS.
     *
     * @param rms normalised 0..1 amplitude, i.e. `AudioCapture.lastChunkRms`.
     * @param elapsedMs milliseconds since this utterance started recording.
     */
    fun feed(rms: Float, elapsedMs: Long): Decision {
        if (elapsedMs >= maxUtteranceMs) return Decision.ENDED_ON_LIMIT

        // Measure the room before judging it. The audio itself is still being captured by the
        // caller during this window -- only the decision waits.
        if (elapsedMs < calibrationMs) {
            noiseSum += rms
            noiseReads++
            return Decision.CONTINUE
        }

        // Two ways to still be speaking, and BOTH must hold. Above the room's gate, and not a
        // marked drop from the voice's own level -- see speechDropRatio for why the second one is
        // what actually detects a pause in a classroom.
        val aboveRoom = rms >= gate
        val notADrop = speechLevel <= 0f || rms >= speechLevel * speechDropRatio

        if (aboveRoom && notADrop) {
            heardSpeech = true
            silenceSinceMs = 0L
            speechLevel = if (speechLevel <= 0f) rms else speechLevel * 0.8f + rms * 0.2f
            return Decision.CONTINUE
        }

        // Silence counts only after speech. A teacher who taps and then pauses to collect their
        // thoughts must not have the utterance closed before they have said anything.
        if (!heardSpeech) return Decision.CONTINUE

        if (silenceSinceMs == 0L) {
            silenceSinceMs = elapsedMs
            return Decision.CONTINUE
        }
        return if (elapsedMs - silenceSinceMs >= silenceHoldMs) {
            Decision.ENDED_ON_SILENCE
        } else {
            Decision.CONTINUE
        }
    }

    /**
     * Self-check for the property that fails silently: that a pause ends an utterance and a quiet
     * start does not.
     *
     * A segmenter that cut on the leading silence would translate nothing and look like a broken
     * mic; one that never cut would hold the whole lesson in one buffer and translate once. Neither
     * shows up in a build or a screenshot.
     */
    fun validate(): List<String> = buildList {
        // Noisy room: the gate must rise above the room but stay under real speech.
        val noisy = UtteranceSegmenter()
        var t = 0L
        repeat(4) { noisy.feed(0.03f, t); t += 128 }          // calibrate on a 0.03 floor
        if (noisy.gate <= 0.03f) add("gate did not rise above a 0.03 noise floor")
        if (noisy.feed(0.03f, t) != Decision.CONTINUE) {
            add("room noise at the floor was treated as speech")
        }

        // Calibrated on speech: the clamp must keep the teacher audible to the gate.
        val midSpeech = UtteranceSegmenter()
        t = 0L
        repeat(4) { midSpeech.feed(0.20f, t); t += 128 }
        midSpeech.feed(0.20f, t)
        if (!midSpeech.hasHeardSpeech) {
            add("gate closed over the teacher after calibrating on their own voice")
        }

        val q = UtteranceSegmenter()
        t = 0L
        repeat(4) { q.feed(0.001f, t); t += 128 }
        // Leading silence must not end anything.
        repeat(20) {
            if (q.feed(0.001f, t) != Decision.CONTINUE) add("cut before any speech was heard")
            t += 128
        }
        // Speech, then a pause longer than the hold.
        repeat(10) { q.feed(0.2f, t); t += 128 }
        if (!q.hasHeardSpeech) add("0.2 rms in a quiet room was not counted as speech")
        var ended = false
        repeat(12) {
            if (q.feed(0.001f, t) == Decision.ENDED_ON_SILENCE) ended = true
            t += 128
        }
        if (!ended) add("a pause after speech did not end the utterance")

        // The case the teacher actually complained about: a room whose ambience never drops below
        // the gate. The pause must still be found, from the drop relative to the voice.
        val noisyRoom = UtteranceSegmenter()
        t = 0L
        repeat(4) { noisyRoom.feed(0.10f, t); t += 128 }        // ambience above maxGateRms
        repeat(10) { noisyRoom.feed(0.30f, t); t += 128 }       // teacher over the top of it
        if (!noisyRoom.hasHeardSpeech) add("speech over loud ambience was not detected")
        var cut = false
        repeat(12) {
            // Back to ambience only. Still above the gate, so only the relative drop can see it.
            if (noisyRoom.feed(0.10f, t) == Decision.ENDED_ON_SILENCE) cut = true
            t += 128
        }
        if (!cut) add("a pause in a noisy room was not detected; teacher would wait for the limit")
    }
}
