package org.bolmitra.data

import android.content.Context
import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bolmitra.phrasebook.Provenance
import org.bolmitra.speech.LiveTurnEngine
import org.bolmitra.speech.TargetLanguage
import org.bolmitra.speech.WavFile
import org.bolmitra.translate.AudioClip
import org.bolmitra.translate.TurnOutcome

/**
 * Saves a completed turn so History, Recordings and the generated worksheet have something real to
 * read.
 *
 * ### Why this is not inside `LiveTurnEngine`
 *
 * `runTurn` is the translation path, and the translation path works. Persistence has no business
 * inside it: a database insert or a file write there would add a failure mode and a latency cost to
 * the one code path that has a 3 s deadline (§6.2.1), and it would put disk I/O on
 * `Dispatchers.Default`. So this runs *after* the turn, from the caller, reading only the result.
 * Nothing here can change what a class hears.
 *
 * ### Never fails a turn
 *
 * Every method swallows its failures into a log line. The class has already heard the translation by
 * the time any of this runs, so a full disk must cost a history row and nothing else.
 *
 * ### Audio and §6.9.4
 *
 * Clips are written under `filesDir/recordings`, which is app-private storage. §6.9.4 says audio
 * never leaves the device and that still holds: nothing here exports, uploads or shares, and the
 * correction outbox remains text-only. What this adds is local retention, which is why Settings has a
 * real "Clear All History" that deletes the rows *and* the files.
 */
class TurnRecorder(context: Context) {

    private val appContext = context.applicationContext
    private val db by lazy { BolMitraDatabase.get(appContext) }

    /**
     * Read per call, not cached: a teacher can turn history off mid-lesson and the very next turn must
     * respect it. A cached copy would keep writing until the screen was rebuilt.
     */
    private val settings get() = Settings(appContext)

    /** App-private, not external storage: recordings of children must not land in shared media. */
    private val recordingsDir: File
        get() = File(appContext.filesDir, DIR_NAME)

    /**
     * Persists one turn, writing whatever audio it left behind.
     *
     * [teacherPcm] is the microphone buffer, always 16 kHz by [org.bolmitra.speech.AudioCapture]'s
     * contract. [ttsSampleRate] is the voice's own rate and is **not** the same number — passing the
     * capture rate for the output clip would store a file that plays back at the wrong speed.
     */
    suspend fun record(
        language: TargetLanguage,
        result: LiveTurnEngine.TurnResult,
        teacherPcm: ShortArray?,
        ttsSampleRate: Int?,
    ): Long = withContext(Dispatchers.IO) {
        try {
            val prefs = settings
            // The setting is real: with history off, nothing is written and no audio touches the disk.
            // Returning early rather than writing-then-deleting means a teacher who turns this off is
            // not trusting us to clean up after ourselves.
            if (!prefs.storeHistory) return@withContext -1L

            val now = System.currentTimeMillis()
            val stamp = "$now-${language.name.lowercase()}"

            val teacherFile = teacherPcm
                ?.takeIf { it.isNotEmpty() && prefs.keepRecordings }
                ?.let { pcm ->
                    val f = File(recordingsDir, "teacher-$stamp.wav")
                    if (WavFile.write(f, pcm, CAPTURE_SAMPLE_RATE)) f.name else null
                }

            val outputFile = result.outcome.spokenClip()
                ?.takeIf { ttsSampleRate != null && ttsSampleRate > 0 && prefs.keepRecordings }
                ?.let { clip ->
                    val f = File(recordingsDir, "class-$stamp.wav")
                    if (WavFile.write(f, clip, ttsSampleRate!!)) f.name else null
                }

            val row = TurnEntity(
                createdAtMs = now,
                language = language.name,
                hiText = result.transcript.orEmpty(),
                targetNative = result.outcome.nativeText(),
                targetDeva = result.outcome.devaText(),
                // Carried from the outcome, never re-derived. See TurnEntity's docs.
                provenance = result.outcome.provenance?.name,
                src = (result.outcome as? TurnOutcome.CorpusAudio)?.src,
                srcEn = (result.outcome as? TurnOutcome.CorpusAudio)?.srcEn,
                degradeReason = result.outcome.degradeReasonName(),
                typed = result.typed,
                asrMs = result.asrMs,
                totalMs = result.totalMs,
                teacherAudio = teacherFile,
                outputAudio = outputFile,
            )
            db.turnDao().insert(row)
        } catch (t: Throwable) {
            // The lesson already happened. Losing the archive of it must not surface as an error in
            // front of a class.
            Log.w(TAG, "could not record turn: ${t.javaClass.simpleName}: ${t.message}")
            -1L
        }
    }

    suspend fun recent(language: TargetLanguage, limit: Int = 50): List<TurnEntity> =
        safeRead { db.turnDao().recent(language.name, limit) }

    suspend fun recordings(language: TargetLanguage, limit: Int = 50): List<TurnEntity> =
        safeRead { db.turnDao().recordings(language.name, limit) }

    suspend fun forWorksheet(language: TargetLanguage, limit: Int = 40): List<TurnEntity> =
        safeRead { db.turnDao().translatedForWorksheet(language.name, limit) }

    suspend fun search(language: TargetLanguage, query: String, limit: Int = 50): List<TurnEntity> =
        safeRead {
            if (query.isBlank()) {
                db.turnDao().recent(language.name, limit)
            } else {
                db.turnDao().search(language.name, query.trim(), limit)
            }
        }

    suspend fun count(): Int = safeRead { listOf(db.turnDao().count()) }.firstOrNull() ?: 0

    /** Resolves a stored file name to a readable file, or null if it has gone missing. */
    fun audioFile(name: String?): File? =
        name?.let { File(recordingsDir, it) }?.takeIf { it.isFile }

    /**
     * Deletes every history row and every recording. Backs Settings' "Clear All History".
     *
     * Rows first, then files: if the process dies between the two, the result is orphaned files that
     * nothing references and [pruneOrphans] can clear, rather than history rows pointing at audio
     * that no longer exists, which would give a teacher play buttons that fail.
     */
    suspend fun clearAll(): Boolean = withContext(Dispatchers.IO) {
        try {
            db.turnDao().deleteAll()
            recordingsDir.listFiles()?.forEach { it.delete() }
            true
        } catch (t: Throwable) {
            Log.w(TAG, "could not clear history: ${t.message}")
            false
        }
    }

    /** Total bytes held by saved recordings, so Settings can state the real cost of keeping them. */
    fun recordingsBytes(): Long =
        recordingsDir.listFiles()?.sumOf { it.length() } ?: 0L

    /** Drops WAVs no row references. Cheap insurance against an interrupted [clearAll]. */
    suspend fun pruneOrphans(): Int = withContext(Dispatchers.IO) {
        try {
            val live = db.turnDao().referencedAudioFiles().toSet()
            recordingsDir.listFiles()
                ?.filter { it.name !in live }
                ?.count { it.delete() }
                ?: 0
        } catch (t: Throwable) {
            Log.w(TAG, "prune failed: ${t.message}")
            0
        }
    }

    private suspend fun <T> safeRead(block: suspend () -> List<T>): List<T> =
        withContext(Dispatchers.IO) {
            try {
                block()
            } catch (t: Throwable) {
                Log.w(TAG, "history read failed: ${t.javaClass.simpleName}: ${t.message}")
                emptyList()
            }
        }

    companion object {
        private const val TAG = "BolMitra/history"
        private const val DIR_NAME = "recordings"

        /** Fixed by the recogniser's FeatureConfig; `AudioCapture` does not resample. */
        const val CAPTURE_SAMPLE_RATE = 16_000
    }
}

/**
 * The clip this outcome actually played, or null if it played nothing.
 *
 * Mirrors the branch logic `LiveClassPane.hasReplayableAudio` already encodes: `clip` is populated on
 * the three pack-audio rungs only when synthesis stood in for a missing asset, while `MachineAudio`
 * always carries its PCM. `TextOnly` and `Unavailable` produced no audio by definition.
 */
private fun TurnOutcome.spokenClip(): AudioClip? = when (this) {
    is TurnOutcome.VerifiedAudio -> clip
    is TurnOutcome.CorpusAudio -> clip
    is TurnOutcome.ApproximateAudio -> clip
    is TurnOutcome.MachineAudio -> clip
    is TurnOutcome.ComposedAudio -> clip
    is TurnOutcome.TextOnly -> null
    is TurnOutcome.Unavailable -> null
}

/** What the class SAW. Empty on the rungs that showed nothing in the target script. */
private fun TurnOutcome.nativeText(): String = when (this) {
    is TurnOutcome.VerifiedAudio -> targetText
    is TurnOutcome.CorpusAudio -> targetText
    is TurnOutcome.ApproximateAudio -> targetText
    is TurnOutcome.MachineAudio -> targetText
    is TurnOutcome.ComposedAudio -> targetText
    // Devanagari, which the teacher reads aloud themselves on this rung. Recorded in both columns
    // so a history row is never blank where something was actually shown.
    is TurnOutcome.TextOnly -> devanagariText
    is TurnOutcome.Unavailable -> ""
}

/** What the voice was given, where there was one. */
private fun TurnOutcome.devaText(): String = when (this) {
    is TurnOutcome.TextOnly -> devanagariText
    // The other rungs carry only the native string in the outcome; the Devanagari that fed the
    // synthesiser is not exposed on them, and re-deriving it here would mean running the
    // transliterator again outside the translation path.
    else -> ""
}

/** The degrade reason's name, for an honest history row. Null when the turn succeeded. */
private fun TurnOutcome.degradeReasonName(): String? = when (this) {
    is TurnOutcome.TextOnly -> reason.name
    is TurnOutcome.Unavailable -> reason.name
    else -> null
}

/** Convenience so the UI can label a row without re-deriving anything. */
val TurnEntity.provenanceOrNull: Provenance?
    get() = provenance?.let { name -> runCatching { Provenance.valueOf(name) }.getOrNull() }
