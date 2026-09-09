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
 * ### The session is now a toggle, and that changed the VAD argument
 *
 * This class originally documented "why push-to-talk removes the need for a VAD": the teacher held
 * a button, so the end of the utterance was known from the UI rather than inferred from the signal.
 * **The mic is now tap-to-start / tap-to-stop**, because a fixed window cut teachers off
 * mid-sentence, so within one session the utterance boundaries have to come from somewhere.
 *
 * They come from [lastChunkRms] and an adaptive threshold in the caller, not from a VAD model. The
 * Silero VAD staged at `ModelStore.vadModel` is still unloaded, and still deliberately so: an
 * energy gate is ~20 lines with a graceful failure mode, whereas sherpa-onnx calls `exit(-1)` on a
 * malformed artifact (V67) and that model has never been exercised on device.
 *
 * What has NOT changed is the reason the mic must not be open during playback. Thirty children are
 * audible the whole lesson, and the tablet's own translation is the loudest thing in the room — so
 * the caller stops capture for the duration of each turn rather than filtering its own voice back
 * out. `VOICE_RECOGNITION` below asks for AEC, but AEC is not a licence to listen to yourself.
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
     * Root-mean-square amplitude of the most recent [drain], normalised to 0..1. Zero before the
     * first read.
     *
     * Exposed so a caller can find the gap between two sentences without a VAD model. One [drain]
     * is a 2048-sample read, i.e. **128 ms at 16 kHz**, which is already the right window for that
     * decision — short enough to catch a pause, long enough not to trip on a single glottal stop.
     */
    @Volatile
    var lastChunkRms: Float = 0f
        private set

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
        // Stale level from the previous utterance would otherwise be read as speech on the first
        // loop iteration, before any sample of the new one has arrived.
        lastChunkRms = 0f

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
        lastChunkRms = rms(buf, n)
        return n
    }

    /**
     * RMS over [n] samples, normalised by [Short.MAX_VALUE].
     *
     * Accumulates in `Double`, not `Float`: 2048 squared 16-bit samples reach ~4.4e9, which
     * overflows `Int` and loses precision in `Float` well before the end of the window.
     */
    private fun rms(buf: ShortArray, n: Int): Float {
        if (n <= 0) return 0f
        var sum = 0.0
        for (i in 0 until n) {
            val s = buf[i].toDouble()
            sum += s * s
        }
        return (kotlin.math.sqrt(sum / n) / Short.MAX_VALUE).toFloat()
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
