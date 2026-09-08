package org.bolmitra.speech

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.IOException

private const val TAG = "BolMitra/capture"

/**
 * Push-to-talk microphone capture — pipeline stage 1, previously absent.
 *
 * ### Why push-to-talk, and why that removes the need for a VAD
 *
 * The teacher holds a button while speaking and releases when done, so the end of the utterance
 * is known from the UI rather than inferred from the signal. [SherpaStreamingAsr.transcribe] is
 * already written for exactly this — "Push-to-talk means the caller already knows where the
 * utterance ends, so endpointing is not used here" — so the Silero VAD model, which is on device
 * but was never loaded, stays unused. That is a deliberate omission: a VAD earns its keep for
 * hands-free capture, and adding one here would mean tuning a threshold against classroom noise
 * to solve a problem a button already solves.
 *
 * It is also the right interaction for the room. Thirty children are audible the whole lesson; a
 * hands-free mic would try to translate them.
 *
 * ### Format is fixed by the model, not chosen
 *
 * 16 kHz mono PCM16, because that is what the NeMo CTC recogniser's `FeatureConfig` declares and
 * what `AsrEngine.transcribe(pcm16Mono16k: ShortArray)` is typed for. Nothing here resamples, so
 * these constants are not tunable.
 *
 * Not thread-safe; drive it from a single coroutine. [start] and [stop] must be called in pairs,
 * and [stop] is safe to call when not recording.
 */
class AudioCapture {

    /** Matches the recogniser's declared feature sample rate. Not a preference. */
    private val sampleRate = 16_000

    private var record: AudioRecord? = null

    /**
     * Grows as capture proceeds. A plain list of chunks rather than one big buffer, because the
     * utterance length is not known in advance and copying a growing array each read would be
     * quadratic.
     */
    private val chunks = mutableListOf<ShortArray>()

    val isRecording: Boolean get() = record != null

    /**
     * Opens the mic and begins buffering.
     *
     * @throws IllegalStateException if `RECORD_AUDIO` is not held, or the device gave us an
     *   `AudioRecord` that will not initialise. Both are surfaced rather than swallowed: silently
     *   returning no audio would look exactly like "the teacher said nothing", which is a
     *   different failure with a different fix.
     */
    @SuppressLint("MissingPermission") // Caller checks; see MainActivity's runtime request.
    fun start() {
        if (isRecording) return
        chunks.clear()

        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minBuffer > 0) { "AudioRecord.getMinBufferSize failed: $minBuffer" }

        // Four times the minimum. The minimum is the point at which the buffer *just* keeps up,
        // so any scheduling hiccup on a budget tablet's little cores drops samples, and dropped
        // samples inside a word are worse than latency: they change the transcript.
        val bufferBytes = minBuffer * 4

        val r = AudioRecord(
            // VOICE_RECOGNITION rather than MIC: it asks the platform for the AEC/NS tuning
            // intended for ASR, and notably not the AGC that MIC may apply, which would pump
            // classroom background noise up between the teacher's words.
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferBytes,
        )
        if (r.state != AudioRecord.STATE_INITIALIZED) {
            r.release()
            throw IllegalStateException(
                "AudioRecord did not initialise (state=${r.state}). Usually RECORD_AUDIO is not " +
                    "granted, or another app holds the mic.",
            )
        }

        record = r
        r.startRecording()
        if (r.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            stop()
            throw IllegalStateException("AudioRecord refused to start recording")
        }
        Log.d(TAG, "recording at $sampleRate Hz, buffer $bufferBytes B")
    }

    /**
     * Reads whatever is currently available into the buffer. Call repeatedly while the button is
     * held; returns the number of samples read, or 0 when not recording.
     */
    fun drain(): Int {
        val r = record ?: return 0
        val buf = ShortArray(2048)
        val n = r.read(buf, 0, buf.size)
        if (n <= 0) {
            // Negative values are AudioRecord's error codes. Log rather than throw: one bad read
            // mid-utterance should not lose the words already captured.
            if (n < 0) Log.w(TAG, "AudioRecord.read returned $n")
            return 0
        }
        chunks += buf.copyOf(n)
        return n
    }

    /**
     * Stops capture and returns everything buffered, or null if nothing usable was heard.
     *
     * The 100 ms floor rejects an accidental tap. Below that there is not enough signal for the
     * recogniser to do anything but guess, and a guess presented as a transcript is worse than
     * `NO_SPEECH_RECOGNISED`.
     */
    fun stop(): ShortArray? {
        val r = record ?: return null
        record = null
        try {
            r.stop()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "stop() on a non-recording AudioRecord", e)
        } finally {
            r.release()
        }

        val total = chunks.sumOf { it.size }
        val minSamples = sampleRate / 10
        if (total < minSamples) {
            Log.d(TAG, "discarding $total samples, under the ${minSamples}-sample floor")
            chunks.clear()
            return null
        }

        val out = ShortArray(total)
        var at = 0
        chunks.forEach { c ->
            c.copyInto(out, at)
            at += c.size
        }
        chunks.clear()
        Log.d(TAG, "captured $total samples (${"%.2f".format(total / sampleRate.toFloat())} s)")
        return out
    }

    /** Length of what is buffered so far, in seconds. For a live duration readout. */
    val bufferedSeconds: Float get() = chunks.sumOf { it.size } / sampleRate.toFloat()
}

/** Thrown when the mic cannot be opened for a reason the user can act on. */
class CaptureUnavailable(message: String, cause: Throwable? = null) : IOException(message, cause)
