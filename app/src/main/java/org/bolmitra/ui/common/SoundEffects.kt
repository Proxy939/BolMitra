package org.bolmitra.ui.common

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import androidx.annotation.RawRes
import org.bolmitra.R

/**
 * The app's two UI sounds: the startup chime and the notification chime.
 *
 * Both ship in `res/raw`, so this never touches the network — the landing screen's promise that
 * nothing leaves the tablet covers audio assets too.
 *
 * ### Why this is not [org.bolmitra.speech.RenderingAudioPlayer]
 *
 * That class exists to play **content the class hears** — a verified phrase or a synthesised
 * translation — and it blocks on purpose so two turns cannot overlap into two voices at once. These
 * are UI chrome. They must never block, never queue behind a turn, and never be mistaken for
 * content, so they get their own player and their own `AudioAttributes`:
 *
 *  * the notification uses `USAGE_ASSISTANCE_SONIFICATION`, the Android usage for UI feedback, so
 *    the platform ducks or mixes it against speech rather than replacing it;
 *  * the startup chime uses `USAGE_MEDIA`, because it is a several-second piece of audio and the
 *    teacher's media volume is the right control for it.
 *
 * ### Failure is silence, never a crash
 *
 * Catching [Throwable] rather than [Exception] is deliberate and consistent with the rest of the
 * project: a truncated or re-encoded MP3 surfaces from the platform decoder as an `Error`, not an
 * exception, and a missing sound is worth exactly zero interruptions to a lesson in progress. Every
 * failure here degrades to "no sound" and a log line.
 */
object SoundEffects {

    private const val TAG = "BolMitra/sound"

    /** Plays the startup chime. Fire-and-forget; returns immediately. */
    fun playStartup(context: Context) {
        play(context, R.raw.startup_sound, AudioAttributes.USAGE_MEDIA, AudioAttributes.CONTENT_TYPE_MUSIC)
    }

    /** Plays the notification chime that accompanies an in-app popup. */
    fun playNotification(context: Context) {
        play(
            context,
            R.raw.notification,
            AudioAttributes.USAGE_ASSISTANCE_SONIFICATION,
            AudioAttributes.CONTENT_TYPE_SONIFICATION,
        )
    }

    private fun play(context: Context, @RawRes resId: Int, usage: Int, contentType: Int) {
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(contentType)
                .build()

            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val sessionId = audioManager?.generateAudioSessionId()
                ?: AudioManager.AUDIO_SESSION_ID_GENERATE

            // The four-arg overload rather than create(context, resId): it is the only one that
            // lets the attributes be set before prepare, and after prepare they are ignored.
            val player = MediaPlayer.create(context.applicationContext, resId, attrs, sessionId)
            if (player == null) {
                // create() returns null rather than throwing when the resource cannot be decoded.
                Log.w(TAG, "MediaPlayer.create returned null for resource $resId")
                return
            }

            // Released on completion, not held for reuse. These fire rarely — app start, and a
            // finished worksheet — so a live MediaPlayer per sound is cheaper than a pool that
            // holds decoder resources for the life of the process on a 2 GB tablet.
            player.setOnCompletionListener { mp ->
                runCatching { mp.release() }
            }
            player.setOnErrorListener { mp, what, extra ->
                Log.w(TAG, "playback error what=$what extra=$extra")
                runCatching { mp.release() }
                true // handled; do not also invoke the completion listener
            }
            player.start()
        } catch (t: Throwable) {
            Log.w(TAG, "could not play sound resource $resId", t)
        }
    }
}
