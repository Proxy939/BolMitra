package org.bolmitra.speech

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/**
 * Plays a decoded WAV at the rate the file declares.
 *
 * ### Why not `RenderingAudioPlayer`
 *
 * That player is constructed with a single `sampleRate` — the voice's — and every clip it plays
 * assumes it. The Recordings tab plays two kinds of file: the teacher's microphone at 16 kHz, and the
 * synthesised output at whatever VITS reports. Sending one through a track built for the other is not
 * a subtle error; it is a chipmunk or a drawl, and on a lesson recording it is the difference between
 * useful and unusable.
 *
 * ### Blocking, like the rest of the audio path
 *
 * [play] returns when the clip has finished, for the same reason `RenderingAudioPlayer.play` does:
 * two overlapping calls are two voices in the room at once. Callers are already off the main thread.
 */
object WavPlayer {

    private const val TAG = "BolMitra/wavplay"

    /** Plays [decoded] and blocks until it has finished. Returns false if it could not start. */
    fun play(decoded: WavFile.Decoded): Boolean {
        val samples = decoded.samples
        if (samples.isEmpty()) return false

        val minBuffer = AudioTrack.getMinBufferSize(
            decoded.sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            Log.w(TAG, "getMinBufferSize failed for ${decoded.sampleRate} Hz: $minBuffer")
            return false
        }

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    // Same reasoning as the live path: this is speech the room needs to hear, so it
                    // follows the media volume the teacher has already set.
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(decoded.sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minBuffer, samples.size * 2))
            .build()

        return try {
            track.play()
            var written = 0
            while (written < samples.size) {
                val n = track.write(samples, written, samples.size - written)
                if (n <= 0) break
                written += n
            }
            // write() returns once buffered, not once audible. Without this the track is released
            // mid-sentence and only the first syllable is heard.
            Thread.sleep(decoded.durationMs + 120)
            true
        } catch (e: Exception) {
            Log.w(TAG, "playback failed: ${e.message}")
            false
        } finally {
            runCatching {
                track.stop()
                track.release()
            }
        }
    }
}
