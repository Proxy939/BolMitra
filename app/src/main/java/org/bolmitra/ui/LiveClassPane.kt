package org.bolmitra.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import org.bolmitra.phrasebook.Provenance
import org.bolmitra.translate.AudioPlayer
import org.bolmitra.translate.DegradeReason
import org.bolmitra.translate.TurnOutcome
import org.bolmitra.ui.common.BolMitraIcons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bolmitra.data.TurnEntity
import org.bolmitra.data.TurnRecorder
import org.bolmitra.data.provenanceOrNull
import org.bolmitra.phrasebook.DemoSeed
import org.bolmitra.phrasebook.InMemoryPhrasebook
import org.bolmitra.speech.AudioCapture
import org.bolmitra.speech.LiveTurnEngine
import org.bolmitra.speech.TargetLanguage
import org.bolmitra.speech.WavFile
import org.bolmitra.speech.WavPlayer
import org.bolmitra.ui.theme.BolmitraColors
import org.bolmitra.ui.common.ConcentricCircleButton
import org.bolmitra.ui.common.OlChikiFont
import org.bolmitra.ui.common.SoundwaveVisualizer

/**
 * The mic closes itself after this long, even untouched.
 *
 * One tap opens the mic, and it records until the teacher taps again or this deadline arrives —
 * whichever comes first. **A pause in speech is not an ending.** That is the whole of the rule now,
 * and it replaces an energy gate that inferred sentence boundaries from signal level.
 *
 * The gate (`UtteranceSegmenter`, deleted) cut ~800 ms after the level dropped and translated each
 * fragment. In a classroom it cut teachers off mid-sentence: a pause for breath and the end of a
 * sentence are the same signal at different durations, and no threshold separates them for someone
 * thinking while they speak. Lengthening the hold moves the cut rather than preventing it. Re-adding
 * it is roughly 60 lines plus a call site, and git has the original — but do not, unless the failure
 * it caused has an answer.
 *
 * This is also a backstop, not only a limit: an open mic left running keeps transcribing a room full
 * of children, so a tablet put down mid-lesson must not do that indefinitely. Fifteen seconds at
 * 16 kHz mono PCM16 is 480 KB, which `AudioCapture`'s chunk list holds without a cap.
 */
private const val MAX_SESSION_MS = 15_000L

/** Consecutive failed reads before a session gives up rather than spinning on a dead mic. */
private const val MAX_DEAD_READS = 40

/**
 * The complete rule for when recording continues. Two ways to stop, and no third.
 *
 * Extracted as a pure function because this is precisely what the bug was about: an energy gate
 * added a third exit that fired on a pause, and nothing in a build or a screenshot showed it. A
 * predicate can be asserted; a condition buried in a blocking mic loop cannot be, because testing it
 * would need `AudioRecord` and a device.
 *
 * Anything that wants to end a recording must go through [stopRequested] or move the deadline. Adding
 * a parameter here should feel like the deliberate act it is.
 */
internal fun shouldKeepRecording(stopRequested: Boolean, nowMs: Long, deadlineMs: Long): Boolean =
    !stopRequested && nowMs < deadlineMs

/** `mm:ss` for the session readout. Minutes are not clamped to two digits; the cap is 30. */
internal fun formatMmSs(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(total / 60, total % 60)
}

enum class TurnPhase { IDLE, LOADING, LISTENING, THINKING }

// `ChatHistoryItem` lived here: a five-row hardcoded list whose Mundari ("Buku kholoko", "Apeko enda
// jokeda") was invented, carried no provenance, and stored `isToday` as a boolean fixed at
// construction. It is gone — History and Recordings read `TurnEntity` from the database now, so the
// panel shows what the class actually heard and can label how much to trust it.

/**
 * Live Class Screen — Exact replica of Image 1.
 */
@Composable
fun LiveClassPane(
    wide: Boolean,
    micGranted: Boolean,
    language: TargetLanguage,
    onLanguageChange: (TargetLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val engine = remember(language) { LiveTurnEngine.get(context, language) }

    // phrasesFor(language), NOT phrases. The unfiltered list is Mundari placeholders, so under a
    // Santali selection it returned Mundari text and played the Mundari clip. LiveTurnEngine fixed
    // this for its own T0; the pane's copy still had the bug on the play path.
    val phrasebook = remember(language) { InMemoryPhrasebook(DemoSeed.phrasesFor(language)) }

    var loadState by remember { mutableStateOf(engine.loadState) }
    var phase by remember { mutableStateOf(TurnPhase.IDLE) }
    var result by remember { mutableStateOf<LiveTurnEngine.TurnResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var speakingAudio by remember { mutableStateOf(false) }

    /**
     * Whether the mic session is open. Distinct from [phase], which cycles
     * LISTENING -> THINKING -> LISTENING many times inside one session — the button must read "stop"
     * for all of it, including while a translation is being spoken.
     */
    var sessionActive by remember { mutableStateOf(false) }

    /**
     * Graceful stop signal, read from the capture loop on [Dispatchers.Default].
     *
     * An `AtomicBoolean` rather than Compose state because it is written from the UI thread and read
     * from a background loop, and rather than cancelling the job because a stop should let the
     * sentence in flight finish and be translated.
     */
    val stopRequested = remember { AtomicBoolean(false) }

    var sessionElapsedMs by remember { mutableStateOf(0L) }

    var inputTab by remember { mutableStateOf(0) } // 0: Type & Translate, 1: Quick Phrases
    var typedHindi by remember { mutableStateOf("") }
    var historyTab by remember { mutableStateOf(0) } // 0: History, 1: Recordings
    var historySearch by remember { mutableStateOf("") }

    /**
     * Real turns from the database, replacing five hardcoded rows whose Mundari was invented.
     *
     * [historyReloads] is bumped after every completed turn and after a search edit, which is what
     * re-runs the read. A Flow from the DAO would be tidier, but this panel is read in exactly two
     * situations — a turn finished, or the teacher typed in the search box — and both are already
     * places the pane knows about.
     */
    val recorder = remember { TurnRecorder(context) }
    var historyRows by remember { mutableStateOf<List<TurnEntity>>(emptyList()) }
    var recordingRows by remember { mutableStateOf<List<TurnEntity>>(emptyList()) }
    var historyReloads by remember { mutableStateOf(0) }
    var playingRecording by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(engine) {
        result = null
        error = null
        phase = TurnPhase.LOADING
        loadState = engine.ensureLoaded()
        phase = TurnPhase.IDLE
    }

    // Loads History and Recordings. Re-keyed on language because a Santali Ol Chiki line has no
    // business in a Mundari lesson's history, and on historyReloads so a finished turn appears
    // without the teacher navigating away and back.
    LaunchedEffect(language, historyReloads, historySearch) {
        historyRows = recorder.search(language, historySearch)
        recordingRows = recorder.recordings(language)
    }

    // Drives the mm:ss readout. A display concern only, never a source of truth for when recording
    // ends.
    //
    // Keyed on LISTENING rather than sessionActive, which was the reported bug: sessionActive stays
    // true through translation and playback, so the clock kept climbing towards 15 s while the mic
    // was already shut and the class was hearing the answer. A timer that runs when nothing is being
    // recorded is a screen stating something untrue.
    //
    // Leaving LISTENING freezes the number where it stopped instead of zeroing it, so a teacher can
    // see how long the recording they just made actually was. `toggleMic` resets it on the next tap.
    // The 200 ms tick is finer than the old 500 ms because the number is now something a teacher may
    // be watching in order to decide when to tap stop.
    LaunchedEffect(phase) {
        if (phase != TurnPhase.LISTENING) return@LaunchedEffect
        val start = SystemClock.elapsedRealtime()
        while (true) {
            sessionElapsedMs = SystemClock.elapsedRealtime() - start
            delay(200)
        }
    }

    val canSpeak = micGranted && loadState is LiveTurnEngine.LoadState.Ready

    /**
     * Records everything the teacher says in one session, and stops for exactly two reasons.
     *
     * ### A pause no longer ends the recording, and that is the point
     *
     * This used to run an energy gate ([UtteranceSegmenter], now deleted) that ended the recording
     * ~800 ms after the signal dropped, translating each sentence as it was finished. It read well on
     * paper and was wrong in a classroom: a teacher drawing breath mid-sentence was cut off, the
     * translation began over the top of them, and the on-screen timer carried on to 15 s as though
     * still listening — so the one visible cue disagreed with what the mic was doing.
     *
     * The trouble is that "a pause" and "the end of a sentence" are the same signal at different
     * durations, and no threshold separates them reliably for a person who is thinking while they
     * speak. Tuning the hold longer only moves the cut; it does not stop it happening mid-thought.
     * So the decision is handed back to the person who knows: recording ends when the **teacher taps
     * stop**, or at [MAX_SESSION_MS] as a backstop, and nothing in between.
     *
     * One session is now one utterance and one translation, which also removes the mid-session
     * interleaving of speech and playback — the mic is closed for the whole of the turn, so the
     * tablet cannot hear its own voice.
     *
     * Runs on [Dispatchers.Default]; `AudioRecord.read` blocks for ~128 ms per call, which is what
     * paces this loop rather than a `delay`. That blocking read is also why cancellation is checked
     * every iteration instead of relied upon: a blocked read cannot be interrupted, so the worst
     * case for noticing a cancelled session is one read.
     *
     * Returns null when nothing usable was heard — [AudioCapture.stop]'s 100 ms floor — which is the
     * normal result when the teacher stops the session without saying anything.
     */
    suspend fun captureSession(
        capture: AudioCapture,
        stopRequested: AtomicBoolean,
        /**
         * Absolute clock at which the mic closes itself. Whatever has been said so far is still
         * returned and still translated, so the cap costs the teacher nothing they already said.
         */
        sessionDeadlineMs: Long,
    ): ShortArray? {
        capture.start()
        var deadReads = 0

        while (true) {
            // Cooperative cancellation. Without this the loop is unbounded, and a cancelled scope
            // (navigating away, rotation) would leave AudioRecord holding the mic for the life of
            // the process. `AudioRecord.read` blocks and cannot be interrupted, so the worst case
            // for noticing a cancellation is one read, ~128 ms.
            currentCoroutineContext().ensureActive()

            // The only two ways recording ends. A pause is deliberately not one of them — see the
            // note on this function, and `shouldKeepRecording`, which is where the rule lives.
            if (!shouldKeepRecording(
                    stopRequested = stopRequested.get(),
                    nowMs = SystemClock.elapsedRealtime(),
                    deadlineMs = sessionDeadlineMs,
                )
            ) {
                break
            }

            if (capture.drain() == 0) {
                // A dead mic returns immediately instead of blocking, which would spin this loop.
                if (++deadReads >= MAX_DEAD_READS) break
                continue
            }
            deadReads = 0
        }
        return capture.stop()
    }

    /**
     * Starts the mic session, or asks a running one to stop.
     *
     * Tap once and the mic stays open, translating each sentence as the teacher pauses, until it is
     * tapped again. Stopping is a *request*, not a cancellation: the sentence already being spoken
     * finishes and gets translated, because throwing away words the teacher just said in order to
     * honour a tap 200 ms sooner is the wrong trade in front of a class.
     */
    fun toggleMic() {
        if (!canSpeak) return

        if (sessionActive) {
            stopRequested.set(true)
            return
        }

        stopRequested.set(false)
        error = null
        result = null
        // Reset here rather than when the session ends, so the previous recording's length stays on
        // screen until a new one starts.
        sessionElapsedMs = 0L
        sessionActive = true

        scope.launch {
            val capture = AudioCapture()
            val sessionDeadline = SystemClock.elapsedRealtime() + MAX_SESSION_MS
            try {
                // One recording per tap, not a loop over utterances.
                //
                // This was a `while` that captured, translated, then went back for more until the
                // teacher tapped stop, and that structure is what made a pause cut them off: every
                // pass ended at the energy gate. With the gate gone there is exactly one recording,
                // bounded by the tap or by MAX_SESSION_MS, and exactly one translation after it.
                phase = TurnPhase.LISTENING
                val pcm = withContext(Dispatchers.Default) {
                    captureSession(capture, stopRequested, sessionDeadline)
                }

                if (pcm == null) {
                    // Under the 100 ms floor. Tapping stop straight away is a legitimate way to
                    // cancel, so that case stays silent; otherwise the mic delivered nothing usable
                    // and the teacher needs to know why they are about to hear nothing.
                    if (!stopRequested.get()) {
                        error = "Nothing audible was captured. Hold the tablet closer and speak up."
                    }
                } else {
                    // END OF SPEECH, not session start. The 3 s R3 deadline is measured from here,
                    // and one long recording makes that more load-bearing rather than less: a
                    // timestamp taken when the teacher tapped would charge the entire listening
                    // window against the budget and put every turn into BUDGET_EXHAUSTED.
                    val turnStart = SystemClock.elapsedRealtime()

                    phase = TurnPhase.THINKING
                    // The mic is already closed by captureSession, which is what keeps the tablet
                    // from hearing its own translation and translating it back. runTurn plays the
                    // audio synchronously, so returning from it means the room is quiet again.
                    val turn = engine.runTurn(pcm, turnStart)
                    result = turn

                    // Archived AFTER the class has heard it, never before. Persistence is not on the
                    // 3 s critical path and must not be: a slow disk would otherwise delay a lesson.
                    recorder.record(language, turn, teacherPcm = pcm, ttsSampleRate = engine.ttsSampleRate)
                    historyReloads++
                }
            } catch (e: CancellationException) {
                // Rethrown, never turned into `error`: the composition is going away, so there is
                // no UI left to show a message in, and swallowing it breaks structured concurrency.
                throw e
            } catch (e: Throwable) {
                // Swallowed on purpose. This runs in a composition scope with no exception handler,
                // so rethrowing a mic failure would take the process down in front of a class.
                error = e.message ?: e::class.java.simpleName
            } finally {
                // NonCancellable: this runs on the cancellation path too, and releasing the mic is
                // the one thing that must happen even then.
                withContext(NonCancellable) { runCatching { capture.stop() } }
                sessionActive = false
                stopRequested.set(false)
                phase = TurnPhase.IDLE
            }
        }
    }

    /**
     * Translates typed Hindi through the same ladder speech uses.
     *
     * Was a bare `phrasebook.lookup` + `player.play(ref)`, which never touched the translator and
     * never set [result] — so typed text outside the seeded list produced silence, no on-screen
     * translation and no provenance chip. Now it runs the orchestrator, so the transcript box
     * fills for typing exactly as it does for speech.
     */
    fun translateTyped(textHi: String) {
        val hindi = textHi.trim()
        if (hindi.isEmpty() || phase != TurnPhase.IDLE) return
        error = null
        result = null
        scope.launch {
            phase = TurnPhase.THINKING
            speakingAudio = true
            try {
                if (engine.ensureLoaded() !is LiveTurnEngine.LoadState.Ready) {
                    loadState = engine.loadState
                    return@launch
                }
                val turn = engine.runTextTurn(hindi, SystemClock.elapsedRealtime())
                result = turn
                // No teacher audio for a typed turn — there was no microphone involved, and a
                // Recordings row with a play button that produced nothing would be a lie.
                recorder.record(language, turn, teacherPcm = null, ttsSampleRate = engine.ttsSampleRate)
                historyReloads++
            } catch (e: Throwable) {
                error = e.message ?: e::class.java.simpleName
            } finally {
                speakingAudio = false
                phase = TurnPhase.IDLE
            }
        }
    }

    /**
     * Plays this turn's translation again, for the child who missed it.
     *
     * The point of this button is that nothing is recomputed: the teacher does not repeat the
     * sentence, ASR does not run, T1 does not run, and VITS does not re-synthesise. The audio is
     * already in [LiveTurnEngine.TurnResult]'s outcome, so this is a buffer write.
     *
     * Guarded on [speakingAudio] because `RenderingAudioPlayer.play` blocks until the clip has
     * finished on purpose — two overlapping calls would be two voices in the room at once.
     */
    fun replayTranslation() {
        val outcome = result?.outcome ?: return
        if (!outcome.hasReplayableAudio() || speakingAudio || phase != TurnPhase.IDLE) return
        scope.launch {
            speakingAudio = true
            try {
                val played = withContext(Dispatchers.Default) {
                    engine.audioPlayer?.replay(outcome) ?: false
                }
                if (!played) {
                    // Never a silent no-op: if the pack asset vanished or the track failed, say so.
                    error = "Could not play that again — " +
                        (result?.note ?: "the audio is no longer available")
                }
            } finally {
                speakingAudio = false
            }
        }
    }

    /**
     * Plays a saved recording straight off disk.
     *
     * This is what makes the Recordings tab real. It deliberately does NOT go through
     * `playPhraseAudio` below: that resolves a phrase through the phrasebook and re-synthesises,
     * which for any turn that came from T1 finds nothing and plays silence. A WAV that was written at
     * the time is the only honest record of what the room actually heard.
     *
     * The stored sample rate is used, not a constant: the teacher's clip is 16 kHz and the voice's is
     * whatever VITS reports, and playing either at the other's rate is audibly wrong.
     */
    fun playSavedAudio(turn: TurnEntity, teacherSide: Boolean) {
        if (speakingAudio) return
        val file = recorder.audioFile(if (teacherSide) turn.teacherAudio else turn.outputAudio)
            ?: return
        scope.launch {
            speakingAudio = true
            playingRecording = turn.id
            try {
                withContext(Dispatchers.Default) {
                    val decoded = WavFile.read(file) ?: return@withContext
                    // A dedicated track rather than the engine's player: RenderingAudioPlayer is
                    // fixed to the TTS rate at construction, and these files are not all that rate.
                    WavPlayer.play(decoded)
                }
            } catch (e: Throwable) {
                error = e.message ?: e::class.java.simpleName
            } finally {
                playingRecording = null
                speakingAudio = false
            }
        }
    }

    /** Replays a phrase that already has pack audio. Used by the history rows. */
    fun playPhraseAudio(textHi: String) {
        scope.launch {
            val lookup = phrasebook.lookup(textHi)
            speakingAudio = true
            withContext(Dispatchers.Default) {
                when (engine.ensureLoaded()) {
                    is LiveTurnEngine.LoadState.Ready -> {
                        val ref = lookup?.phrase?.audioRef
                        val player = engine.audioPlayer
                        if (player != null && ref != null) {
                            player.play(ref)
                        }
                    }
                    else -> Unit
                }
            }
            speakingAudio = false
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 1. Top Illustrated Header Banner
        BannerHeaderCard(
            icon = BolMitraIcons.Mic,
            title = "Live Class",
            subtitleEn = "Speak in Hindi, help every child learn in their language",
            subtitleHi = "आप बोलिए हिंदी में, बच्चे सुनेंगे उनकी भाषा में",
            badgeLines = listOf("Different", "Languages", "Brighter", "Futures"),
        )

        // 2. Target Language Selector
        LanguageSelectorBar(
            currentLanguage = language,
            onSelectLanguage = onLanguageChange,
            label = "Target Language (Child's Language)",
            trailingContent = {
                // Direction dropdown pill
                Row(
                    modifier = Modifier
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("⇄", color = Color(0xFF16A34A), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Column {
                        Text(
                            "Translate:",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                color = Color(0xFF64748B),
                            ),
                        )
                        Text(
                            "Hindi → ${language.englishName}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = Color(0xFF1E293B),
                            ),
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(16.dp),
                    )
                }
            },
        )

        // Warnings. Every one of these was previously computed and then never rendered, so a
        // failed load or a thrown turn left the mic button silently doing nothing.
        if (!micGranted) {
            WarningBanner("Microphone permission is not granted. Grant it in Settings > Apps > BolMitra.")
        }

        when (val ls = loadState) {
            is LiveTurnEngine.LoadState.Missing -> WarningBanner(ls.detail)
            is LiveTurnEngine.LoadState.Failed ->
                WarningBanner("The models on this tablet could not be loaded: ${ls.detail}")
            is LiveTurnEngine.LoadState.Unsupported -> WarningBanner(ls.detail)
            else -> Unit
        }

        // A borrowed voice is a separate admission from unreviewed words, and it belongs on the
        // screen where the class hears it, not only in Settings.
        language.voiceNote?.takeIf { loadState is LiveTurnEngine.LoadState.Ready }?.let {
            WarningBanner(it)
        }

        error?.let { WarningBanner(it) }

        // 3. Main Center Area: Two Big Voice Cards + Right History Column
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Center Voice & Input Column (Left 65%)
            Column(
                modifier = Modifier.weight(1.8f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Two Voice Cards side-by-side
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Teacher Card (Orange)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // Top Row: Avatar + Auto-detect
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Box(
                                        modifier = Modifier.size(36.dp).background(Color(0xFFFED7AA), CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Person,
                                            contentDescription = null,
                                            tint = Color(0xFFEA580C),
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    Column {
                                        Text(
                                            "You (Teacher)",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = Color(0xFF1E293B),
                                            ),
                                        )
                                        Text(
                                            "Speak in Hindi",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 11.5.sp,
                                                color = Color(0xFF64748B),
                                            ),
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text("Auto-detect", fontSize = 10.sp, color = Color(0xFF475569))
                                    Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(12.dp), tint = Color(0xFF475569))
                                }
                            }

                            Spacer(Modifier.height(18.dp))

                            // Soundwave + Mic button + Soundwave
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                SoundwaveVisualizer(
                                    color = Color(0xFFF97316),
                                    isActive = phase == TurnPhase.LISTENING,
                                    modifier = Modifier.size(width = 56.dp, height = 36.dp),
                                )

                                Spacer(Modifier.width(8.dp))

                                ConcentricCircleButton(
                                    // Shape carries the state, not just the pulse: a stop square
                                    // while the session is open, a mic when it is closed.
                                    icon = if (sessionActive) {
                                        BolMitraIcons.Stop
                                    } else {
                                        BolMitraIcons.Mic
                                    },
                                    primaryColor = Color(0xFFF97316),
                                    isPulsing = phase == TurnPhase.LISTENING,
                                    onClick = ::toggleMic,
                                    modifier = Modifier.size(92.dp),
                                    contentDescription = if (sessionActive) {
                                        "Stop listening and translate now"
                                    } else {
                                        "Start listening"
                                    },
                                )

                                Spacer(Modifier.width(8.dp))

                                SoundwaveVisualizer(
                                    color = Color(0xFFF97316),
                                    isActive = phase == TurnPhase.LISTENING,
                                    modifier = Modifier.size(width = 56.dp, height = 36.dp),
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            Text(
                                // The label has to say that stopping TRANSLATES, not merely that it
                                // stops. Tapping has always cut the sentence short and translated it
                                // straight away, but a button that only promises to "stop" reads
                                // like it will throw the sentence away — so a teacher who had
                                // finished speaking waited for the timer instead of tapping.
                                text = when {
                                    phase == TurnPhase.THINKING -> "Translating..."
                                    sessionActive -> "Listening — tap to translate now"
                                    else -> "Tap to speak"
                                },
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF1E293B),
                                ),
                            )
                            Text(
                                text = if (sessionActive) {
                                    // "Finished speaking? Press — it will translate immediately."
                                    "बोलकर पूरा हो गया? दबाएँ — तुरंत अनुवाद होगा"
                                } else {
                                    "हिंदी में बोलिए"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B),
                                ),
                            )

                            Spacer(Modifier.height(8.dp))

                            Text(
                                // Was a hardcoded "00:00 / 30:00" that never moved and described
                                // nothing. The mic now stays open until tapped, so a teacher needs
                                // to see both that it is still recording and how long is left
                                // before it closes itself — and the right-hand number is the real
                                // MAX_SESSION_MS rather than decoration.
                                text = "%s / %s".format(
                                    formatMmSs(sessionElapsedMs),
                                    formatMmSs(MAX_SESSION_MS),
                                ),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8),
                                ),
                            )
                        }
                    }

                    // Student Card (Green)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // Top Row: Avatar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier.size(36.dp).background(Color(0xFFBBF7D0), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("👥", fontSize = 16.sp)
                                }
                                Column {
                                    Text(
                                        "Students (Class)",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color(0xFF1E293B),
                                        ),
                                    )
                                    Text(
                                        "Hearing in ${language.englishName}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 11.5.sp,
                                            color = Color(0xFF64748B),
                                        ),
                                    )
                                }
                            }

                            Spacer(Modifier.height(18.dp))

                            // Soundwave + Speaker button + Soundwave
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                SoundwaveVisualizer(
                                    color = Color(0xFF2EAF3B),
                                    isActive = speakingAudio,
                                    modifier = Modifier.size(width = 56.dp, height = 36.dp),
                                )

                                Spacer(Modifier.width(8.dp))

                                ConcentricCircleButton(
                                    icon = Icons.Filled.PlayArrow,
                                    primaryColor = Color(0xFF2EAF3B),
                                    isPulsing = speakingAudio,
                                    // Replays THIS turn's audio. Was a phrasebook lookup on the
                                    // Hindi transcript, which found nothing for a T1 translation
                                    // and played nothing at all.
                                    onClick = { replayTranslation() },
                                    modifier = Modifier.size(92.dp),
                                )

                                Spacer(Modifier.width(8.dp))

                                SoundwaveVisualizer(
                                    color = Color(0xFF2EAF3B),
                                    isActive = speakingAudio,
                                    modifier = Modifier.size(width = 56.dp, height = 36.dp),
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            val outcomeText = result?.outcome?.displayText()
                                ?: "Translation will play here"

                            Text(
                                text = OlChikiFont.annotate(outcomeText),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF1E293B),
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "अनुवाद यहाँ सुनाया जाएगा",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B),
                                ),
                            )

                            result?.outcome?.provenance?.let { prov ->
                                Spacer(Modifier.height(6.dp))
                                ProvenanceChip(prov)
                            }
                        }
                    }
                }

                // Heard (Hindi) | Translated (target) — the two-part transcript box
                HeardAndTranslatedBox(
                    result = result,
                    phase = phase,
                    language = language,
                    speaking = speakingAudio,
                    onReplay = { replayTranslation() },
                )

                // Input Panel: Type & Translate / Quick Phrases
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Tabs row: Type & Translate | Quick Phrases
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Column(
                                    modifier = Modifier.clickable { inputTab = 0 },
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("⌨ ", fontSize = 13.sp)
                                        Text(
                                            "Type & Translate",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = if (inputTab == 0) FontWeight.Bold else FontWeight.Medium,
                                                color = if (inputTab == 0) Color(0xFF1E293B) else Color(0xFF64748B),
                                            ),
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    if (inputTab == 0) {
                                        Box(Modifier.width(115.dp).height(2.5.dp).background(Color(0xFF2EAF3B), RoundedCornerShape(2.dp)))
                                    }
                                }

                                Column(
                                    modifier = Modifier.clickable { inputTab = 1 },
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("💬 ", fontSize = 13.sp)
                                        Text(
                                            "Quick Phrases",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = if (inputTab == 1) FontWeight.Bold else FontWeight.Medium,
                                                color = if (inputTab == 1) Color(0xFF1E293B) else Color(0xFF64748B),
                                            ),
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    if (inputTab == 1) {
                                        Box(Modifier.width(95.dp).height(2.5.dp).background(Color(0xFF2EAF3B), RoundedCornerShape(2.dp)))
                                    }
                                }
                            }

                            // Clear button
                            Row(
                                modifier = Modifier.clickable { typedHindi = "" },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(Icons.Filled.Clear, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(14.dp))
                                Text("Clear", fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Text input field with Translate button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFAFAFA), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = typedHindi,
                                    onValueChange = { if (it.length <= 500) typedHindi = it },
                                    placeholder = { Text("Type in Hindi...", color = Color(0xFF94A3B8), fontSize = 14.sp) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "${typedHindi.length}/500",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, color = Color(0xFF94A3B8)),
                                    )

                                    Button(
                                        onClick = { translateTyped(typedHindi) },
                                        enabled = typedHindi.isNotBlank() && phase == TurnPhase.IDLE,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2EAF3B)),
                                        shape = RoundedCornerShape(10.dp),
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, Modifier.size(14.dp), tint = Color.White)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Translate", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Try these quick phrase suggestions
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "Try these:",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF475569),
                                ),
                            )
                            // दोहराओ and बहुत अच्छा used to sit here. Both are now deliberately
                            // unreachable from Hindi: GATITOS publishes ᱫᱦᱨᱟ and ᱵᱦᱟᱜᱮ for them,
                            // neither of which has any stem in a 126,745-word Ol Chiki list, so
                            // both were unmapped rather than repaired (see santali-hindi-keys.tsv).
                            // Suggesting them would have walked a teacher straight onto the weakest
                            // path in the app. बैठ जाओ and खड़े हो जाओ replace them because they
                            // resolve to attested corpus content, ᱫᱩᱨᱩᱵ and ᱛᱮᱜᱚ.
                            // नमस्ते leads, because greeting the class is the first thing a teacher
                            // says and it was the first thing tried in the field — where it missed
                            // T0 and came back as machine output. It now resolves to ᱡᱚᱦᱟᱨ (johar)
                            // from GATITOS, so the most likely opening utterance lands on the
                            // strongest path rather than the weakest.
                            listOf("नमस्ते", "बैठ जाओ", "खड़े हो जाओ", "सुनो ध्यान से", "फिर से बोलो", "अब लिखो").forEach { phrase ->
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFF1F5F9), RoundedCornerShape(10.dp))
                                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                                        .clickable {
                                            typedHindi = phrase
                                            translateTyped(phrase)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                ) {
                                    Text(phrase, fontSize = 11.sp, color = Color(0xFF1E293B))
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(Color(0xFFF1F5F9), CircleShape)
                                    .clickable { /* add quick phrase */ },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("+", fontSize = 14.sp, color = Color(0xFF475569))
                            }
                        }
                    }
                }
            }

            // Right Column: History / Recordings (Width ~35%)
            Box(
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxHeight()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                    .padding(14.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // History / Recordings Tab
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                    ) {
                        Column(
                            modifier = Modifier.clickable { historyTab = 0 },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("📋 ", fontSize = 13.sp)
                                Text(
                                    "History",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (historyTab == 0) FontWeight.Bold else FontWeight.Medium,
                                        color = if (historyTab == 0) Color(0xFF1E293B) else Color(0xFF64748B),
                                    ),
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            if (historyTab == 0) {
                                Box(Modifier.width(60.dp).height(2.5.dp).background(Color(0xFF2EAF3B), RoundedCornerShape(2.dp)))
                            }
                        }

                        Column(
                            modifier = Modifier.clickable { historyTab = 1 },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🎙 ", fontSize = 13.sp)
                                Text(
                                    "Recordings",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (historyTab == 1) FontWeight.Bold else FontWeight.Medium,
                                        color = if (historyTab == 1) Color(0xFF1E293B) else Color(0xFF64748B),
                                    ),
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            if (historyTab == 1) {
                                Box(Modifier.width(75.dp).height(2.5.dp).background(Color(0xFF2EAF3B), RoundedCornerShape(2.dp)))
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Search input. A real TextField now — this was a Text, so the state it wrote
                    // into could never become non-empty and the filter was decoration.
                    OutlinedTextField(
                        value = historySearch,
                        onValueChange = { historySearch = it },
                        placeholder = {
                            Text(
                                "Search previous chats...",
                                fontSize = 11.5.sp,
                                color = Color(0xFF94A3B8),
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF2EAF3B),
                            unfocusedBorderColor = Color(0xFFE2E8F0),
                        ),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    )

                    Spacer(Modifier.height(10.dp))

                    val rows = if (historyTab == 0) historyRows else recordingRows

                    if (rows.isEmpty()) {
                        // An empty state rather than an empty box. Before, five fabricated rows made
                        // this look populated on a tablet that had never been used.
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = when {
                                    historySearch.isNotBlank() -> "Nothing matches \u201C$historySearch\u201D"
                                    historyTab == 1 -> "No recordings yet.\nSpoken turns are saved here."
                                    else -> "No translations yet.\nTap the mic to start."
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF94A3B8),
                                ),
                            )
                        }
                    } else {
                        // Grouped by day off the stored timestamp, so "Today" means today rather than
                        // a hardcoded header over hardcoded rows.
                        val grouped = rows.groupBy { dayLabelFor(it.createdAtMs) }
                        // A plain Column, NOT a LazyColumn.
                        //
                        // This was `LazyColumn(Modifier.weight(1f, fill = false))`, and it rendered at
                        // zero height — the history rows were invisible even when the list was the
                        // five hardcoded ones, which is why nobody noticed the data was fake. The
                        // whole pane sits inside `HomeScreen`'s `verticalScroll`, so the parent has
                        // unbounded height, and `weight` inside an unbounded parent resolves to
                        // nothing. A lazy list is the wrong tool here anyway: the query is capped at
                        // 50 rows and nesting a scroller inside a scroller fights the gesture.
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            grouped.forEach { (day, dayRows) ->
                                Text(
                                    day,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B),
                                    ),
                                )
                                dayRows.forEach { row ->
                                    if (historyTab == 0) {
                                        HistoryRow(
                                            row = row,
                                            onPlay = {
                                                // Prefer the saved output clip; fall back to the
                                                // phrasebook only when this turn saved no audio.
                                                if (row.outputAudio != null) {
                                                    playSavedAudio(row, teacherSide = false)
                                                } else {
                                                    playPhraseAudio(row.hiText)
                                                }
                                            },
                                        )
                                    } else {
                                        RecordingRow(
                                            row = row,
                                            isPlaying = playingRecording == row.id,
                                            hasTeacher = row.teacherAudio != null,
                                            hasOutput = row.outputAudio != null,
                                            onPlayTeacher = { playSavedAudio(row, teacherSide = true) },
                                            onPlayOutput = { playSavedAudio(row, teacherSide = false) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Honest count instead of a link that did nothing.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (historyTab == 0) {
                                "${historyRows.size} saved ${if (historyRows.size == 1) "turn" else "turns"}"
                            } else {
                                "${recordingRows.size} ${if (recordingRows.size == 1) "recording" else "recordings"}"
                            },
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFF1E293B)),
                        )
                    }
                }
            }
        }

        // 4. Bottom Footer Bar
        FooterTipBar(
            tipText = "Keep your sentences short and simple for better translation.",
            actionText = "Aligned with NIPUN Bharat FLN outcomes →",
        )
    }
}

/**
 * The single place that turns a [TurnOutcome] into the string to show.
 *
 * Lifted out of the student card so the card and the transcript box cannot drift apart when a
 * branch is added to [TurnOutcome]. Note which field each branch carries: `MachineAudio` holds the
 * NATIVE script (Ol Chiki for Santali — what the class sees), while `TextOnly` holds Devanagari,
 * because on that rung the teacher reads it aloud themselves.
 */
private fun TurnOutcome.displayText(): String = when (this) {
    is TurnOutcome.VerifiedAudio -> targetText
    is TurnOutcome.CorpusAudio -> targetText
    is TurnOutcome.ApproximateAudio -> targetText
    is TurnOutcome.MachineAudio -> targetText
    is TurnOutcome.ComposedAudio -> targetText
    is TurnOutcome.TextOnly -> devanagariText
    is TurnOutcome.Unavailable -> reason.explain()
}

/**
 * The English a corpus row was translated from, where there is one.
 *
 * Shown under the translation so a teacher can see whether the quoted line actually fits what they
 * said. Null for every other rung — nothing else has a source sentence to disclose.
 */
private fun TurnOutcome.corpusSourceNote(): String? = when (this) {
    is TurnOutcome.CorpusAudio -> buildString {
        srcEn?.let { append("translated from \u201C").append(it).append('\u201D') }
        src?.let {
            if (isNotEmpty()) append(" \u00B7 ")
            append(it)
        }
    }.ifBlank { null }

    else -> null
}

/**
 * True when this turn left behind audio that can be played again with no model work.
 *
 * `MachineAudio` still holds its PCM, and `RenderingAudioPlayer` caches a rendered pack ref, so
 * replaying either costs nothing — no ASR, no translation, no synthesis. The two false branches are
 * the rungs that by definition produced no audio: on `TextOnly` the teacher reads the Devanagari
 * aloud themselves, and `Unavailable` never got that far.
 */
private fun TurnOutcome.hasReplayableAudio(): Boolean = when (this) {
    is TurnOutcome.VerifiedAudio,
    is TurnOutcome.CorpusAudio,
    is TurnOutcome.ApproximateAudio,
    is TurnOutcome.MachineAudio,
    is TurnOutcome.ComposedAudio,
    -> true

    is TurnOutcome.TextOnly,
    is TurnOutcome.Unavailable,
    -> false
}

/**
 * Plays the audio this turn already produced.
 *
 * Deliberately re-uses the outcome rather than the Hindi transcript. The student card's play button
 * used to look the transcript up in the phrasebook, which meant a T1 machine translation — the
 * common case for Santali — found no entry and played nothing, silently.
 */
private fun AudioPlayer.replay(outcome: TurnOutcome): Boolean = when (outcome) {
    // Prefer the clip where one exists. A T0 row with no pack audio was synthesised, and its
    // audioRef is the SYNTHESISED sentinel which names no file — playing it would fail.
    is TurnOutcome.VerifiedAudio -> outcome.clip?.let { play(it) } ?: play(outcome.audioRef)
    is TurnOutcome.CorpusAudio -> outcome.clip?.let { play(it) } ?: play(outcome.audioRef)
    is TurnOutcome.ApproximateAudio -> outcome.clip?.let { play(it) } ?: play(outcome.audioRef)
    is TurnOutcome.MachineAudio -> play(outcome.clip)
    is TurnOutcome.ComposedAudio -> play(outcome.clip)
    is TurnOutcome.TextOnly, is TurnOutcome.Unavailable -> false
}

/**
 * A named reason, never a bare "failed".
 *
 * "No translation available" told the teacher nothing actionable. Each rung has a different fix —
 * speak louder, sideload a pack, shorten the sentence — and the whole point of [DegradeReason] is
 * that the outcome knows which.
 */
private fun DegradeReason.explain(): String = when (this) {
    DegradeReason.NO_SPEECH_RECOGNISED -> "Nothing was recognised — speak a little louder"
    // Two different situations reach this rung and the old wording fitted neither well. For
    // Santali it means T0 missed *and* the MT model could not answer; for Mundari and Ho it means
    // no MT model exists at all and none is coming — they are absent from IndicTrans2 and
    // NLLB-200 for not being scheduled languages (V11), so "on this tablet" wrongly implied a
    // pack would fix it. What is true in both cases is that the phrasebook is the way through,
    // and that is on this screen, so the message names it.
    DegradeReason.NO_TRANSLATION_AVAILABLE ->
        "Not in the phrasebook, and no translation model for this language — " +
            "try a Quick Phrase below"
    DegradeReason.BUDGET_EXHAUSTED -> "Took too long — try a shorter sentence"
    DegradeReason.SYNTHESIS_FAILED -> "Translated, but the voice could not speak it"
    DegradeReason.AUDIO_ASSET_MISSING -> "Verified phrase found, but its audio is missing"
}

/**
 * Heard-vs-translated box: the Hindi the recogniser produced on the left, the target text on the
 * right.
 *
 * Deliberately full width rather than tucked into the student card, which ellipsises at two lines.
 * Ol Chiki runs long and this is the one place the teacher can check what the class is actually
 * being shown.
 *
 * Both halves populate at the same instant. The batch recogniser emits no partials, so there is no
 * progressive fill to design for — during a turn both sides show the same pending state.
 */
@Composable
private fun HeardAndTranslatedBox(
    result: LiveTurnEngine.TurnResult?,
    phase: TurnPhase,
    language: TargetLanguage,
    speaking: Boolean,
    onReplay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pending = phase == TurnPhase.LISTENING || phase == TurnPhase.THINKING

    val heard = when {
        phase == TurnPhase.LISTENING -> "Listening…"
        phase == TurnPhase.THINKING -> "Working it out…"
        else -> result?.transcript?.takeIf { it.isNotBlank() }
    }
    val translated = when {
        pending -> null
        else -> result?.outcome?.displayText()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "What the tablet heard, and what it will say",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = Color(0xFF334155),
                    ),
                )
                // Timings are only meaningful once a turn has actually run.
                result?.takeIf { !pending }?.let { res ->
                    Text(
                        if (res.typed) "typed · ${res.totalMs} ms"
                        else "heard ${res.asrMs} ms · total ${res.totalMs} ms",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8),
                        ),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TranscriptHalf(
                    modifier = Modifier.weight(1f),
                    accent = Color(0xFFF97316),
                    heading = "Heard · Hindi",
                    body = heard,
                    placeholder = "Speak or type, and the Hindi appears here",
                )

                Box(
                    Modifier
                        .width(1.dp)
                        .height(72.dp)
                        .background(Color(0xFFE2E8F0)),
                )

                TranscriptHalf(
                    modifier = Modifier.weight(1f),
                    accent = Color(0xFF2EAF3B),
                    heading = "Translated · ${language.englishName}",
                    body = translated,
                    placeholder = "The ${language.englishName} text appears here",
                    // ASR output carries no provenance — it is not translated content. Only the
                    // right half gets a chip.
                    provenance = result?.outcome?.provenance?.takeIf { !pending },
                )
            }

            // Where a corpus row came from, and the English it was translated from. This is the
            // disclosure that makes CORPUS provenance honest rather than decorative: a teacher can
            // see the quoted line was written for a different sentence than the one they said.
            result?.outcome?.corpusSourceNote()?.takeIf { !pending }?.let { note ->
                Spacer(Modifier.height(8.dp))
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 14.sp,
                        color = Color(0xFF17456B),
                    ),
                )
            }

            // Replay. Shown only once a turn has produced audio, because a button that cannot do
            // anything is worse than no button in front of a class.
            val outcome = result?.outcome
            if (!pending && outcome != null) {
                Spacer(Modifier.height(12.dp))
                if (outcome.hasReplayableAudio()) {
                    ReplayButton(speaking = speaking, onClick = onReplay)
                } else {
                    // The TextOnly rung on purpose has no audio: the teacher reads it aloud. Say
                    // that, rather than offering a dead button.
                    Text(
                        "No audio for this one — read the text above aloud to the class",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8),
                        ),
                    )
                }
            }
        }
    }
}

/**
 * "Play again" — replays the audio this turn already made.
 *
 * Sized past [minTouchTarget] because it gets pressed mid-lesson, often in a hurry, and it is the
 * one control a teacher reaches for when a child at the back says they did not catch it.
 */
@Composable
private fun ReplayButton(speaking: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .background(
                if (speaking) Color(0xFFDCFCE7) else Color(0xFF2EAF3B),
                RoundedCornerShape(12.dp),
            )
            .border(
                1.dp,
                if (speaking) Color(0xFF86EFAC) else Color(0xFF259B32),
                RoundedCornerShape(12.dp),
            )
            .clickable(enabled = !speaking, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Core icon set only — material-icons-extended is not a dependency and pulling it in for
        // two glyphs is not worth the APK.
        Icon(
            imageVector = if (speaking) Icons.Filled.PlayArrow else Icons.Filled.Refresh,
            contentDescription = if (speaking) "Playing the translation" else "Play the translation again",
            tint = if (speaking) Color(0xFF16A34A) else Color.White,
            modifier = Modifier.size(20.dp),
        )
        Column {
            Text(
                if (speaking) "Playing…" else "Play again",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (speaking) Color(0xFF16A34A) else Color.White,
                ),
            )
            Text(
                "फिर से सुनाओ",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    color = if (speaking) Color(0xFF16A34A) else Color(0xFFDCFCE7),
                ),
            )
        }
    }
}

@Composable
private fun TranscriptHalf(
    accent: Color,
    heading: String,
    body: String?,
    placeholder: String,
    modifier: Modifier = Modifier,
    provenance: Provenance? = null,
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(8.dp).background(accent, CircleShape))
            Text(
                heading,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                ),
            )
        }

        Spacer(Modifier.height(6.dp))

        Text(
            // annotate() styles only the Ol Chiki runs with the bundled face. Applying that family
            // to the whole string would tofu the Devanagari, which this box shows on the TextOnly
            // rung — the font carries 53 codepoints and no Devanagari at all.
            text = OlChikiFont.annotate(body ?: placeholder),
            style = MaterialTheme.typography.bodyLarge.copy(
                // 16 sp floor: this is the one box on the screen a teacher has to actually read.
                fontSize = 16.sp,
                fontWeight = if (body != null) FontWeight.SemiBold else FontWeight.Normal,
                color = if (body != null) Color(0xFF1E293B) else Color(0xFF94A3B8),
            ),
            // No maxLines. The student card already ellipsises at two, and being able to read the
            // whole line is the reason this box exists.
        )

        provenance?.let {
            Spacer(Modifier.height(8.dp))
            ProvenanceChip(it)
        }
    }
}

/** `HH:mm` for a stored wall-clock timestamp. */
private fun clockLabelFor(atMs: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(atMs))

/**
 * "Today" / "Yesterday" / a date, from the stored timestamp.
 *
 * Computed rather than stored as a boolean: the old `ChatHistoryItem.isToday` was fixed at
 * construction, so a row created yesterday would still have claimed "Today" after midnight.
 */
private fun dayLabelFor(atMs: Long): String {
    val row = Calendar.getInstance().apply { timeInMillis = atMs }
    val now = Calendar.getInstance()
    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    if (sameDay(row, now)) return "Today"
    now.add(Calendar.DAY_OF_YEAR, -1)
    if (sameDay(row, now)) return "Yesterday"
    return SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(atMs))
}

/**
 * One saved turn in the History list.
 *
 * Shows the provenance, which the fabricated version could not: every old row was untagged, so a
 * teacher scrolling back had no way to tell a reviewed phrase from a machine guess. The target text
 * goes through [OlChikiFont.annotate] because for Santali it is Ol Chiki, and the bundled face carries
 * no Devanagari — applying it to the whole string would tofu a Mundari row.
 */
@Composable
private fun HistoryRow(row: TurnEntity, onPlay: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(Color(0xFFDCFCE7), CircleShape)
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Play this translation again",
                    tint = Color(0xFF16A34A),
                    modifier = Modifier.size(16.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    row.hiText.ifBlank { "(nothing recognised)" },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        color = Color(0xFF1E293B),
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = OlChikiFont.annotate(
                        row.targetNative.ifBlank {
                            row.degradeReason?.let { reasonLabel(it) } ?: "—"
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                clockLabelFor(row.createdAtMs),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = Color(0xFF94A3B8)),
            )
            row.provenanceOrNull?.let {
                Spacer(Modifier.height(2.dp))
                ProvenanceDot(it)
            }
        }
    }
}

/**
 * One recording, with separate playback for the two sides of a turn.
 *
 * Two buttons rather than one because they answer different questions: "what did I say" checks the
 * microphone and the ASR, "what did the class hear" checks the translation and the voice. A single
 * button would force a teacher to guess which half was wrong.
 */
@Composable
private fun RecordingRow(
    row: TurnEntity,
    isPlaying: Boolean,
    hasTeacher: Boolean,
    hasOutput: Boolean,
    onPlayTeacher: () -> Unit,
    onPlayOutput: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isPlaying) Color(0xFFF0FDF4) else Color.White,
                RoundedCornerShape(10.dp),
            )
            .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                row.hiText.ifBlank { "(nothing recognised)" },
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = Color(0xFF1E293B),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                clockLabelFor(row.createdAtMs),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = Color(0xFF94A3B8)),
            )
        }

        if (row.targetNative.isNotBlank()) {
            Text(
                text = OlChikiFont.annotate(row.targetNative),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Color(0xFF64748B)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(6.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // Each button is present only when its file is, so no button can fail to play.
            if (hasTeacher) {
                RecordingChip(
                    label = if (row.typed) "Typed" else "You",
                    enabled = true,
                    onClick = onPlayTeacher,
                )
            }
            if (hasOutput) {
                RecordingChip(label = "Class", enabled = true, onClick = onPlayOutput)
            }
            if (isPlaying) {
                Text(
                    "Playing…",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        color = Color(0xFF16A34A),
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
        }
    }
}

@Composable
private fun RecordingChip(label: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .background(Color(0xFFDCFCE7), RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = "Play the $label side of this turn",
            tint = Color(0xFF16A34A),
            modifier = Modifier.size(12.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF166534),
            ),
        )
    }
}

/**
 * Compact provenance marker for a list row.
 *
 * A dot plus its initial, not a dot alone: §4.5 requires a glyph and a word as well as a hue, and a
 * history list has no room for the full [ProvenanceChip]. The letter carries the meaning if the
 * colour cannot.
 */
@Composable
private fun ProvenanceDot(provenance: Provenance) {
    val (color, letter) = when (provenance) {
        Provenance.VERIFIED -> BolmitraColors.Verified to "V"
        Provenance.CORPUS -> BolmitraColors.Corpus to "C"
        Provenance.APPROXIMATE -> BolmitraColors.Approximate to "A"
        Provenance.MACHINE -> BolmitraColors.Unavailable to "M"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(
            letter,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                color = color,
            ),
        )
    }
}

/** Short, teacher-facing text for a stored degrade reason. */
private fun reasonLabel(name: String): String = when (name) {
    "NO_SPEECH_RECOGNISED" -> "nothing recognised"
    "NO_TRANSLATION_AVAILABLE" -> "no translation available"
    "BUDGET_EXHAUSTED" -> "ran out of time"
    "AUDIO_ASSET_MISSING" -> "text only — no audio"
    "SYNTHESIS_FAILED" -> "text only — voice failed"
    else -> name.lowercase().replace('_', ' ')
}
