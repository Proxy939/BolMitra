package org.bolmitra.speech

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bolmitra.phrasebook.DemoSeed
import org.bolmitra.phrasebook.InMemoryPhrasebook
import org.bolmitra.translate.AudioPlayer
import org.bolmitra.translate.IndicTrans2MtEngine
import org.bolmitra.translate.PhrasebookEngine
import org.bolmitra.translate.TurnOrchestrator
import org.bolmitra.translate.TurnOutcome

private const val TAG = "BolMitra/turn"

/**
 * The live turn: microphone to Mundari audio, wired for the first time.
 *
 * Every piece below already existed. [SherpaStreamingAsr] and [SherpaMundariTts] were real
 * sherpa-onnx implementations reachable only from the diagnostics screen; [InMemoryPhrasebook] and
 * its matching ladder were real and covered by tests; [TurnOrchestrator] held the whole degradation
 * state machine and was constructed in exactly one place in the repository — its own unit test.
 * What was missing was audio at both ends ([AudioCapture], [RenderingAudioPlayer]) and something to
 * hold the four of them together. This is that.
 *
 * ### Process-scoped, and that is a correctness requirement
 *
 * `MainActivity`'s own notes flag this as O17: model handles must never be Activity-scoped. Rotation
 * destroys the Activity, §6.13 mandates both orientations, and these two models are ~220 MB of ONNX
 * — reloading them because a teacher turned the tablet would blow the ≤3 s turn budget by an order
 * of magnitude, mid-sentence. So instances live in [Companion.get], keyed to nothing and released
 * only when the process dies.
 *
 * ### Load is lazy and failure is a value, not an exception
 *
 * Construction touches no models. [ensureLoaded] does, and it returns a [LoadState] rather than
 * throwing, because "the ASR model is not on this tablet" is a normal condition with a specific
 * remedy the UI should state — not a crash. Model files arrive by `adb push` in development and as
 * signed packs in the field, so their absence is expected on a fresh device.
 */
class LiveTurnEngine private constructor(
    private val store: ModelStore,
    /** Which target language this instance speaks. One instance per language; see [get]. */
    val language: TargetLanguage,
) {

    sealed interface LoadState {
        data object Ready : LoadState

        /** Model files are not on the device. [detail] names which. */
        data class Missing(val detail: String) : LoadState

        /** Files were there but sherpa refused them. */
        data class Failed(val detail: String) : LoadState

        /**
         * The selected language cannot run a turn at all, and no download would change that.
         *
         * Distinct from [Missing] on purpose. Missing means "fetch the file"; this means "the file
         * does not exist upstream, or the runtime cannot execute it". Collapsing the two would put
         * "run fetch-models.ps1" in front of a user for whom that is useless advice.
         */
        data class Unsupported(val language: TargetLanguage, val detail: String) : LoadState
    }

    /**
     * Candidate B, not A. A's export has an empty `normalize_type` and returns nothing for real
     * speech — see [SherpaBatchAsr]'s docs for the metadata comparison that settled it.
     */
    private var asr: SherpaBatchAsr? = null
    private var tts: SherpaMundariTts? = null
    private var player: RenderingAudioPlayer? = null
    private var orchestrator: TurnOrchestrator? = null

    /**
     * T1 machine translation, or null where no model exists for this language.
     *
     * Held alongside the other engines because it is the largest of them: ~406 MB of native memory
     * for the fused int8 graphs. The per-language instance map in the companion object is what
     * keeps that from being paid twice.
     */
    private var mt: IndicTrans2MtEngine? = null

    /**
     * T0 for THIS language only.
     *
     * Was `InMemoryPhrasebook(DemoSeed.phrases)` — unconditional, and a real bug: `DemoSeed` holds
     * Mundari placeholders, so selecting Santali and speaking a seeded phrase returned Mundari text
     * tagged `Provenance.VERIFIED`. The app would have claimed native-speaker review of content in
     * the wrong language. A language with no pack now gets an empty T0 and falls through to T1,
     * which labels itself `MACHINE`.
     */
    private val phrasebook: PhrasebookEngine = InMemoryPhrasebook(DemoSeed.phrasesFor(language))

    /** Resolves a pack ref back to the phrase text, for [RenderingAudioPlayer]. */
    private val textForRef: (String) -> String? = { ref ->
        DemoSeed.phrases.firstOrNull { it.audioRef == ref }?.targetTextNative
    }

    @Volatile
    var loadState: LoadState? = null
        private set

    val isReady: Boolean get() = loadState is LoadState.Ready

    /**
     * Loads both models if they are not already loaded. Safe to call repeatedly; idempotent once
     * [LoadState.Ready].
     *
     * Runs on [Dispatchers.Default] rather than IO: this is ~1.1 s of CPU-bound ONNX graph
     * initialisation, measured, not a disk wait.
     */
    suspend fun ensureLoaded(): LoadState = withContext(Dispatchers.Default) {
        loadState?.let { if (it is LoadState.Ready) return@withContext it }

        // Checked before file presence, because for Santali and Ho the files are not merely absent
        // — the runtime could not execute them if they were there.
        if (!language.canRunFullTurn) {
            val state = LoadState.Unsupported(language, language.statusLine)
            loadState = state
            return@withContext state
        }

        val missing = buildList {
            if (!store.hasBatchAsr()) add("Hindi ASR (asr-batch/)")
            if (!store.hasTts(language)) add("${language.englishName} voice (${language.voiceDir}/)")
            // Not listed as missing when absent: T1 is optional by design (§4.5) and T0 alone
            // satisfies every stated requirement, so a language without an MT model must still
            // load and run its phrasebook rather than refusing to start.
        }
        if (missing.isNotEmpty()) {
            val state = LoadState.Missing(
                "Not on this tablet: ${missing.joinToString(", ")}. Expected under " +
                    store.rootPath,
            )
            loadState = state
            return@withContext state
        }

        val state = try {
            // Shared across languages, not per-instance. See the companion object: the Hindi ASR is
            // identical whatever the target, and Santali is spoken by Mundari's voice, so keying
            // these per language loaded the same files twice.
            val a = asr ?: sharedAsr(store).also { asr = it }
            val t = tts ?: sharedTts(store, language).also { tts = it }

            val p = player ?: RenderingAudioPlayer(
                tts = t,
                sampleRate = t.sampleRate,
                textForRef = textForRef,
            ).also { player = it }

            // Real T1 where a model exists, null where none does — which is still most languages.
            // Mundari and Ho are absent from IndicTrans2 and NLLB-200 because they are not
            // scheduled languages, so for them this stays null and a T0 miss correctly reports
            // NO_TRANSLATION_AVAILABLE. loadOrNull also returns null when the pack simply has not
            // been sideloaded yet, which must degrade to the phrasebook rather than fail the load.
            val m = mt ?: sharedMt(store, language)?.also { mt = it }
            Log.i(TAG, "T1 for ${language.englishName}: ${if (m != null) "loaded" else "none"}")

            orchestrator = TurnOrchestrator(
                phrasebook = phrasebook,
                tts = t,
                player = p,
                mt = m,
                nowMs = SystemClock::elapsedRealtime,
            )
            LoadState.Ready
        } catch (e: Throwable) {
            // Throwable, not Exception: a bad tokens.txt or a missing .so surfaces as
            // UnsatisfiedLinkError or ExceptionInInitializerError, and those must not take the
            // process down in front of a class.
            Log.e(TAG, "engine load failed", e)
            LoadState.Failed(e.message ?: e::class.java.simpleName)
        }
        loadState = state
        state
    }

    /**
     * Runs one complete turn on captured audio.
     *
     * [turnStartMs] is when the teacher began speaking, so ASR time counts against the deadline —
     * the orchestrator is explicit that measuring only from ASR completion would flatter the
     * numbers.
     */
    suspend fun runTurn(pcm: ShortArray, turnStartMs: Long): TurnResult =
        withContext(Dispatchers.Default) {
            val a = asr
            val o = orchestrator
            if (a == null || o == null) {
                return@withContext TurnResult(
                    transcript = null,
                    outcome = TurnOutcome.Unavailable(
                        org.bolmitra.translate.DegradeReason.NO_SPEECH_RECOGNISED,
                    ),
                    asrMs = 0,
                    totalMs = 0,
                    note = "engines not loaded",
                )
            }

            val asrStart = SystemClock.elapsedRealtime()
            val transcript = a.transcribe(pcm)
            val asrMs = SystemClock.elapsedRealtime() - asrStart

            val outcome = o.handle(transcript, turnStartMs)
            val totalMs = SystemClock.elapsedRealtime() - turnStartMs

            Log.d(TAG, "turn: '$transcript' -> $outcome in ${totalMs}ms (asr ${asrMs}ms)")
            TurnResult(
                transcript = transcript,
                outcome = outcome,
                asrMs = asrMs,
                totalMs = totalMs,
                note = (player as? RenderingAudioPlayer)?.lastFailure,
            )
        }

    /** Everything the UI needs about one turn, including the timings §11.2 wants shown. */
    data class TurnResult(
        val transcript: String?,
        val outcome: TurnOutcome,
        val asrMs: Long,
        val totalMs: Long,
        val note: String?,
    )

    val audioPlayer: AudioPlayer? get() = player

    companion object {
        /**
         * One instance per language, all process-scoped.
         *
         * A map rather than a single instance, and not an LRU: switching target language must not
         * discard a loaded model, because switching back would then pay ~1.1 s of ONNX
         * initialisation again. Three languages of models is the worst case and two of them cannot
         * load at all today, so the memory ceiling is not reachable yet.
         *
         * `ponytail:` Ceiling noted — when every language has a working voice this holds every model
         * ever selected for the life of the process, and §5.3's memory budget decides whether that
         * is acceptable. At that point it becomes keep-one-plus-previous, not a general cache.
         */
        private val instances = HashMap<TargetLanguage, LiveTurnEngine>()

        // ---- shared model caches -------------------------------------------------------------
        //
        // The `ponytail:` ceiling noted above arrived sooner than expected, and measurably. With
        // Mundari and Santali both selected the process reached **2.03 GB PSS with 582 MB swapped**,
        // because a per-language engine loaded its own copy of everything. Two of the three models
        // are not per-language at all:
        //
        //   * The Hindi ASR is the SOURCE side. It is identical for every target language, so there
        //     is never a reason to hold two.
        //   * A voice belongs to a DIRECTORY, not a language. Santali is Support.BORROWED and points
        //     at tts-unr, so Mundari and Santali are the same 109 MB model — loaded twice.
        //
        // Keying the caches on what the model actually depends on, rather than on the language that
        // asked for it, means switching target language now costs only the MT model.
        //
        // Guarded by `instances` for want of a second lock: every path here is already reached from
        // the synchronized `get` or from `ensureLoaded`, and contention is a user tapping a picker.
        private var asrShared: SherpaBatchAsr? = null
        private val ttsShared = HashMap<String, SherpaMundariTts>()
        private val mtShared = HashMap<String, IndicTrans2MtEngine>()

        private fun sharedAsr(store: ModelStore): SherpaBatchAsr = synchronized(instances) {
            asrShared ?: SherpaBatchAsr(
                modelPath = store.asrBatchModel.absolutePath,
                tokensPath = store.asrBatchTokens.absolutePath,
            ).also { asrShared = it }
        }

        private fun sharedTts(store: ModelStore, language: TargetLanguage): SherpaMundariTts =
            synchronized(instances) {
                ttsShared.getOrPut(language.voiceDir) {
                    SherpaMundariTts(
                        modelPath = store.ttsModel(language).absolutePath,
                        tokensPath = store.ttsTokens(language).absolutePath,
                    )
                }
            }

        /**
         * MT for a language, or null where no model exists — still the common case, since Mundari
         * and Ho are absent from IndicTrans2 and NLLB-200 for not being scheduled languages.
         *
         * Keyed on [TargetLanguage.mtDir] because a checkpoint can serve more than one pair: this
         * is IndicTrans2's indic→indic model, and a second Munda language added later would share
         * the same 406 MB rather than double it.
         */
        private fun sharedMt(store: ModelStore, language: TargetLanguage): IndicTrans2MtEngine? {
            val dir = language.mtDir ?: return null
            return synchronized(instances) {
                mtShared[dir] ?: IndicTrans2MtEngine.loadOrNull(store, language)
                    ?.also { mtShared[dir] = it }
            }
        }

        /** Process-scoped. See the class docs on O17. */
        fun get(
            context: Context,
            language: TargetLanguage = TargetLanguage.DEFAULT,
        ): LiveTurnEngine = synchronized(instances) {
            instances.getOrPut(language) {
                LiveTurnEngine(ModelStore(context.applicationContext), language)
            }
        }
    }
}
