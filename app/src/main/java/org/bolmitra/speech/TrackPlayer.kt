package org.bolmitra.speech

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import org.bolmitra.translate.AudioClip
import org.bolmitra.translate.AudioPlayer
import org.bolmitra.translate.TtsEngine

private const val TAG = "BolMitra/play"

/**
 * Playback — pipeline stage 7, previously absent — plus the fix for a gap that would otherwise
 * make the app silent on its most common path.
 *
 * ### The pack-audio problem this exists to solve
 *
 * [org.bolmitra.translate.TurnOrchestrator] serves a T0 phrasebook hit by calling
 * `player.play(audioRef)`, because §6.3 pre-renders verified phrase audio at pack-build time so
 * synthesis stays off the ≤3 s critical path. That is the right design, and the measurement behind
 * it is real: synthesis runs slower than real time on this hardware.
 *
 * But no pack exists yet. Every `audioRef` in `DemoSeed` points at `demo/placeholder.wav`, which is
 * not in the source set, so a literal implementation returns false for every hit and the
 * orchestrator correctly degrades all eleven phrases to `TextOnly(AUDIO_ASSET_MISSING)`. The ladder
 * would be behaving exactly as designed while the tablet never made a sound.
 *
 * So this player renders a missing ref on first use and caches it in memory. That is not a new
 * design — it is the same pre-rendering §6.3 describes, moved from pack-build time to first-play
 * time because there is no pack builder yet. The first utterance of a given phrase pays the
 * synthesis cost (~900 ms measured) and every repeat is instant, which is the behaviour a real
 * pack would give from the start.
 *
 * `ponytail:` Cache is unbounded and process-lifetime, holding raw PCM16 for every phrase played.
 * At eleven short imperatives that is well under a megabyte, so it is not worth an LRU. Ceiling:
 * a real pack has thousands of phrases and must render to disk at import, not to a HashMap here.
 *
 * ### What still returns false
 *
 * If [textForRef] does not recognise a ref, playback genuinely fails and the
 * `AUDIO_ASSET_MISSING` rung fires for real. `DemoSeed` deliberately ships one phrase with
 * `audio = null` so that rung stays reachable on device.
 */
class RenderingAudioPlayer(
    private val tts: TtsEngine,
    /** TTS output rate. MMS VITS reports its own; do not assume it equals the capture rate. */
    private val sampleRate: Int,
    /** Resolves a pack audio ref to the target-language text it should speak. */
    private val textForRef: (String) -> String?,
) : AudioPlayer {

    private val rendered = HashMap<String, AudioClip>()

    /** Last reason a play call returned false, for the UI to show something specific. */
    @Volatile
    var lastFailure: String? = null
        private set

    override fun play(audioRef: String): Boolean {
        rendered[audioRef]?.let { return play(it) }

        val text = textForRef(audioRef)
        if (text.isNullOrBlank()) {
            lastFailure = "no text for ref '$audioRef'"
            Log.w(TAG, lastFailure!!)
            return false
        }
        val clip = tts.synthesizeUtterance(text)
        if (clip == null) {
            lastFailure = "synthesis produced nothing for '$text'"
            Log.w(TAG, lastFailure!!)
            return false
        }
        rendered[audioRef] = clip
        return play(clip)
    }

    /**
     * Writes the clip to an `AudioTrack` and blocks until it has been handed over.
     *
     * Blocking is intentional. A turn is over when the class has heard it, so the caller — which
     * is already off the main thread — wants to know when that happened. Fire-and-forget would let
     * two turns overlap, which in a classroom means two voices talking at once.
     */
    override fun play(clip: AudioClip): Boolean {
        val samples = clip.pcm16Mono16k
        if (samples.isEmpty()) {
            lastFailure = "empty clip"
            return false
        }

        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            lastFailure = "AudioTrack.getMinBufferSize failed: $minBuffer"
            Log.e(TAG, lastFailure!!)
            return false
        }

        // Size the buffer to the whole clip where possible: these are single short imperatives, so
        // one write and no underrun risk mid-word.
        val bufferBytes = maxOf(minBuffer, samples.size * 2)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    // The class needs to hear this over room noise, and it is speech content
                    // rather than UI feedback, so it follows the media stream the teacher has
                    // already turned up rather than the notification volume.
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferBytes)
            .build()

        return try {
            track.play()
            var written = 0
            while (written < samples.size) {
                val n = track.write(samples, written, samples.size - written)
                if (n < 0) {
                    lastFailure = "AudioTrack.write returned $n"
                    Log.e(TAG, lastFailure!!)
                    return false
                }
                if (n == 0) break
                written += n
            }
            // WRITE_BLOCKING returns once buffered, not once audible. Without this the track is
            // released mid-sentence and the class hears the first syllable only.
            val playedMs = (samples.size * 1000L) / sampleRate
            Thread.sleep(playedMs + 120)
            lastFailure = null
            true
        } catch (e: Exception) {
            lastFailure = "playback failed: ${e.message}"
            Log.e(TAG, "playback failed", e)
            false
        } finally {
            runCatching {
                track.stop()
                track.release()
            }
        }
    }

    /** Drops the render cache. Call if the phrasebook content changes under us. */
    fun clearCache() = rendered.clear()

    val cachedRefs: Int get() = rendered.size
}
