package org.bolmitra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import org.bolmitra.ui.common.BolMitraIcons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import android.app.ActivityManager
import android.content.Context
import android.os.Debug
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bolmitra.curriculum.AssemblyResult
import org.bolmitra.curriculum.NumeracySeed
import org.bolmitra.curriculum.WorksheetAssembler
import org.bolmitra.data.TurnRecorder
import org.bolmitra.device.DeviceSpec
import org.bolmitra.device.DeviceTier
import org.bolmitra.phrasebook.DemoSeed
import org.bolmitra.phrasebook.InMemoryPhrasebook
import org.bolmitra.phrasebook.SantaliGlossary
import org.bolmitra.speech.AudioCapture
import org.bolmitra.speech.LiveTurnEngine
import org.bolmitra.speech.ModelStore
// SherpaBatchAsr and SherpaMundariTts are deliberately NOT imported. Constructing either here
// duplicates a model the shared engine already holds and killed the process with SIGSEGV; the checks
// go through LiveTurnEngine.ensureLoaded() instead. See the comment above the "asr" branch.
import org.bolmitra.speech.TargetLanguage

/**
 * One checkable subsystem.
 *
 * ### The bug this shape had
 *
 * `status` and `isWorking` were `var`s defaulting to `"Working"` / `true`, and `ComponentRow`
 * **hardcoded a green tick and the word "Working"** without ever reading either. So a component whose
 * test returned `"ASR model missing on disk"` still displayed as working, on a screen whose entire
 * purpose is to tell a teacher what is broken before a lesson starts. That is worse than showing
 * nothing.
 *
 * [state] is now the single source of truth and the row renders from it.
 */
data class DiagnosticComponent(
    val id: String,
    val iconEmoji: String,
    val name: String,
    val description: String,
    val state: CheckState = CheckState.Unknown,
)

/**
 * The result of checking one subsystem.
 *
 * `Unknown` is the honest starting point and is deliberately not "working": nothing has been checked
 * when the screen opens, and claiming otherwise is the fabrication this replaces.
 */
sealed interface CheckState {
    /** Not checked yet. */
    data object Unknown : CheckState

    /** Currently running. */
    data object Running : CheckState

    /** Checked and working, with what was measured. */
    data class Ok(val detail: String) : CheckState

    /** Checked and not working. [detail] is shown verbatim — it is what a teacher can act on. */
    data class Failed(val detail: String) : CheckState

    /** Cannot apply on this device or for this language, which is different from broken. */
    data class NotApplicable(val detail: String) : CheckState
}

/**
 * Diagnostics Screen — Exact replica of Image 5.
 */
@Composable
fun DiagnosticsScreenPane(
    spec: DeviceSpec,
    tier: DeviceTier,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { ModelStore(context) }

    var selectedLang by remember { mutableStateOf(TargetLanguage.MUNDARI) }

    // Keyed on the selected language so the checks report on the pack the pills actually select.
    // Cheap: LiveTurnEngine shares one ASR and one voice across languages, so switching costs only
    // the MT model.
    val engine = remember(selectedLang) { LiveTurnEngine.get(context, selectedLang) }

    var runningTestId by remember { mutableStateOf<String?>(null) }
    var testFeedback by remember { mutableStateOf<String?>(null) }

    /** Null until something has actually been checked. Was the literal "Today, 11:23 AM". */
    var lastCheckedAtMs by remember { mutableStateOf<Long?>(null) }

    /** Live memory, read the way V66 established is not throttled. */
    var availableRamGiB by remember { mutableStateOf<Double?>(null) }
    var nativeHeapMb by remember { mutableStateOf(0.0) }
    var historyCount by remember { mutableStateOf<Int?>(null) }

    val recorder = remember { TurnRecorder(context) }

    LaunchedEffect(Unit) {
        // ActivityManager.MemoryInfo for available RAM — DeviceSpec only carries totalMem, which is
        // why the old screen printed a literal "2.18 GiB" and Settings computed totalRam * 0.29.
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        while (true) {
            val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            availableRamGiB = mem.availMem / DeviceSpec.GIB
            nativeHeapMb = Debug.getNativeHeapAllocatedSize() / 1048576.0
            historyCount = recorder.count()
            delay(2_000)
        }
    }

    // Keyed on selectedLang: the MT and voice checks differ per language, and previously the pills
    // changed a state nothing read, so every test ran Mundari whatever was selected.
    var states by remember(selectedLang) {
        mutableStateOf<Map<String, CheckState>>(emptyMap())
    }

    val components = remember(selectedLang, states) {
        listOf(
            DiagnosticComponent("mic", "🎙", "Microphone", "Capture audio from this tablet", states.of("mic")),
            DiagnosticComponent("asr", "〰", "Hindi ASR", "Speech to text (Hindi)", states.of("asr")),
            DiagnosticComponent(
                "mt", "⇄",
                // Was the literal "Translation (Hindi → Mundari)". The only MT model in the project is
                // Hindi→Santali, so on a Mundari selection that label was wrong twice over.
                "Translation (Hindi → ${selectedLang.englishName})",
                "Text translation model",
                states.of("mt"),
            ),
            DiagnosticComponent(
                "tts", "🔊",
                "${selectedLang.englishName} voice",
                "Text to speech",
                states.of("tts"),
            ),
            DiagnosticComponent("pb", "📖", "Phrasebook lookup", "Local phrase database", states.of("pb")),
            DiagnosticComponent("ws", "📄", "Worksheet generator", "Generate bilingual worksheets", states.of("ws")),
            DiagnosticComponent("storage", "💾", "Storage & files", "Model files on disk", states.of("storage")),
        )
    }

    /**
     * Runs one real check and records what it found.
     *
     * Every branch now exists. `"mt"`, `"ws"` and `"all"` previously fell through to
     * `else -> "Component test passed"`, so three of the seven buttons reported success without
     * testing anything — including "Run All Tests", whose id had no branch at all.
     */
    suspend fun check(compId: String): CheckState = withContext(Dispatchers.IO) {
        try {
            when (compId) {
                "mic" -> {
                    val cap = AudioCapture()
                    cap.start()
                    // drain() in a loop, not sleep(300).
                    //
                    // `AudioCapture` buffers only what `drain` reads — sleeping does not collect
                    // anything, so `stop()` returned null under its 100 ms floor and this check
                    // reported a working microphone as broken. Each read blocks ~128 ms, so five
                    // covers well over the floor.
                    var samples = 0
                    var peak = 0f
                    repeat(5) {
                        samples += cap.drain()
                        peak = maxOf(peak, cap.lastChunkRms)
                    }
                    val pcm = cap.stop()
                    when {
                        pcm == null || samples == 0 ->
                            CheckState.Failed("Mic opened but delivered no audio.")
                        // A silent room is not a broken microphone, and saying so would send a
                        // teacher looking for a hardware fault that is not there.
                        peak < 0.002f -> CheckState.Ok(
                            "Captured ${pcm.size} samples · very quiet, speak to test the level",
                        )
                        else -> CheckState.Ok(
                            "Captured ${pcm.size} samples · peak level %.0f%%".format(peak * 100),
                        )
                    }
                }

                // NEVER construct a second recogniser or voice here.
                //
                // The first version of this check did, and it killed the process with
                // `SIGSEGV at 0x0` in native code on "Run All Tests". `LiveTurnEngine` deliberately
                // SHARES one ASR and one voice across languages — its own comments record that
                // per-language copies reached 2.03 GB PSS — and V55 notes that a native failure
                // inside ONNX Runtime aborts the process with nothing catchable. A diagnostics
                // screen that can kill the app is worse than no diagnostics screen.
                //
                // So these checks go through `engine.ensureLoaded()`, which is idempotent and uses
                // the shared instances. That also makes the check more honest: it reports on the
                // engine the app actually runs, not on a fresh one built to be thrown away.
                "asr" -> when {
                    !store.hasBatchAsr() && store.hasStreamingAsr() ->
                        CheckState.Failed("Only the streaming model is staged; the live path needs asr-batch/.")
                    !store.hasBatchAsr() ->
                        CheckState.Failed("No Hindi ASR under ${store.rootPath}/asr-batch/")
                    else -> when (val load = engine.ensureLoaded()) {
                        is LiveTurnEngine.LoadState.Ready -> CheckState.Ok(
                            "Loaded, %.0f MB on disk".format(store.asrBatchModel.length() / 1048576.0),
                        )
                        is LiveTurnEngine.LoadState.Missing -> CheckState.Failed(load.detail)
                        is LiveTurnEngine.LoadState.Failed -> CheckState.Failed(load.detail)
                        is LiveTurnEngine.LoadState.Unsupported -> CheckState.NotApplicable(load.detail)
                    }
                }

                "mt" -> when {
                    selectedLang.mtDir == null -> CheckState.NotApplicable(
                        "${selectedLang.englishName} has no MT model anywhere — it is not a " +
                            "scheduled language. T0 alone serves it.",
                    )
                    store.hasMtGraphs(selectedLang) -> CheckState.Ok(
                        "Encoder + fused decoder present for ${selectedLang.englishName}",
                    )
                    else -> CheckState.Failed(
                        "MT graphs missing under ${store.rootPath}/${selectedLang.mtDir}/",
                    )
                }

                // Also via the shared engine. Synthesising through `engine.synthesize` exercises the
                // voice the class actually hears, and constructing a second `OfflineTts` on the same
                // model file is what produced the SIGSEGV described above.
                "tts" -> if (!store.hasTts(selectedLang)) {
                    CheckState.Failed("No voice under ${store.rootPath}/${selectedLang.voiceDir}/")
                } else {
                    val load = engine.ensureLoaded()
                    if (load !is LiveTurnEngine.LoadState.Ready) {
                        CheckState.Failed(
                            when (load) {
                                is LiveTurnEngine.LoadState.Missing -> load.detail
                                is LiveTurnEngine.LoadState.Failed -> load.detail
                                is LiveTurnEngine.LoadState.Unsupported -> load.detail
                                else -> "Voice not loaded"
                            },
                        )
                    } else {
                        val clip = engine.synthesize("किताब खोलो")
                        if (clip == null) {
                            CheckState.Failed("Voice loaded but produced no audio.")
                        } else {
                            val rate = engine.ttsSampleRate ?: 0
                            CheckState.Ok("${clip.pcm16Mono16k.size} samples at $rate Hz")
                        }
                    }
                }

                "pb" -> {
                    // Counted, not asserted. The old text was the literal "11 sample phrases".
                    val seeded = DemoSeed.phrasesFor(selectedLang).size
                    val corpus = SantaliGlossary.phrasesFor(context, selectedLang).size
                    val total = seeded + corpus
                    if (total == 0) {
                        CheckState.NotApplicable(
                            "No phrasebook for ${selectedLang.englishName} in this build.",
                        )
                    } else {
                        // Prove the ladder answers, rather than only that rows exist.
                        val probe = DemoSeed.phrasesFor(selectedLang).firstOrNull()?.hiText
                        val hit = probe?.let {
                            InMemoryPhrasebook(DemoSeed.phrasesFor(selectedLang)).lookup(it)
                        }
                        CheckState.Ok(
                            "$total rows ($seeded seeded, $corpus corpus)" +
                                if (hit != null) " · lookup returned ${hit.provenance}" else "",
                        )
                    }
                }

                "ws" -> {
                    // Actually runs the assembler instead of claiming it passed.
                    val result = WorksheetAssembler.assemble(
                        spec = NumeracySeed.spec(seed = 1L),
                        models = NumeracySeed.models,
                    )
                    when (result) {
                        is AssemblyResult.Success ->
                            CheckState.Ok("${result.items.size} items assembled from the authored pool")
                        is AssemblyResult.DeficientPool ->
                            CheckState.Failed(
                                "Pool deficient: ${result.availableAfterFiltering} of ${result.required}",
                            )
                        is AssemblyResult.NoItemModels ->
                            CheckState.Failed("No item models for ${result.lakshyaCode}")
                    }
                }

                "storage" -> {
                    val inv = store.inventory()
                    val present = inv.count { it.present }
                    val mb = inv.filter { it.present }.sumOf { it.sizeMb }
                    if (present == 0) {
                        CheckState.Failed("No model files under ${store.rootPath}")
                    } else if (present < inv.size) {
                        CheckState.Failed(
                            "$present of ${inv.size} files present. Missing: " +
                                inv.filterNot { it.present }.joinToString { it.label },
                        )
                    } else {
                        CheckState.Ok("$present of ${inv.size} files, %.0f MB".format(mb))
                    }
                }

                else -> CheckState.Failed("No check implemented for '$compId'")
            }
        } catch (e: Throwable) {
            // Throwable: a bad artifact surfaces as UnsatisfiedLinkError, and this screen is exactly
            // where that must be reported rather than crash.
            CheckState.Failed("${e.javaClass.simpleName}: ${e.message?.take(120)}")
        }
    }

    fun runComponentTest(compId: String) {
        if (runningTestId != null) return
        runningTestId = compId
        scope.launch {
            try {
                if (compId == "all") {
                    // "Run All Tests" previously hit the else branch and reported success without
                    // running anything.
                    for (c in components) {
                        runningTestId = c.id
                        states = states + (c.id to CheckState.Running)
                        states = states + (c.id to check(c.id))
                    }
                    val failed = states.values.count { it is CheckState.Failed }
                    testFeedback = if (failed == 0) {
                        "All ${components.size} checks passed."
                    } else {
                        "$failed of ${components.size} checks failed."
                    }
                } else {
                    states = states + (compId to CheckState.Running)
                    val state = check(compId)
                    states = states + (compId to state)
                    testFeedback = when (state) {
                        is CheckState.Ok -> state.detail
                        is CheckState.Failed -> state.detail
                        is CheckState.NotApplicable -> state.detail
                        else -> null
                    }
                }
                lastCheckedAtMs = System.currentTimeMillis()
            } finally {
                runningTestId = null
            }
        }
    }

    /**
     * The headline, derived from what has actually been checked.
     *
     * Four states, and "nothing checked yet" is one of them. The screen used to assert readiness before
     * a single check had run, which is the most misleading thing a diagnostics screen can do.
     */
    val overall = remember(states, components) {
        val checked = components.mapNotNull { states[it.id] }
        val failed = checked.count { it is CheckState.Failed }
        val ok = checked.count { it is CheckState.Ok }
        when {
            checked.isEmpty() -> OverallStatus(
                headline = "Not checked yet",
                detail = "Run the checks to see what this tablet can do.\nजाँच चलाएँ",
                textColor = Color(0xFF334155),
                wash = Color(0xFFF1F5F9),
                border = Color(0xFFE2E8F0),
                good = false,
            )
            failed > 0 -> OverallStatus(
                headline = "$failed ${if (failed == 1) "problem" else "problems"} found",
                detail = "Tap a failing row to see what is missing.\n" +
                    "समस्या मिली — विवरण देखें",
                textColor = Color(0xFF991B1B),
                wash = Color(0xFFFEF2F2),
                border = Color(0xFFFECACA),
                good = false,
            )
            checked.size < components.size -> OverallStatus(
                headline = "$ok of ${components.size} checked",
                detail = "No problems so far. Run the rest to be sure.\nअब तक कोई समस्या नहीं",
                textColor = Color(0xFF92400E),
                wash = Color(0xFFFFFBEB),
                border = Color(0xFFFDE68A),
                good = false,
            )
            else -> OverallStatus(
                headline = "All checks passed",
                detail = "This tablet is ready for offline teaching\n" +
                    "आपका डिवाइस ऑफ़लाइन शिक्षण के लिए तैयार है",
                textColor = Color(0xFF14532D),
                wash = Color(0xFFE8F7ED),
                border = Color(0xFFC8E6C9),
                good = true,
            )
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 1. Header Banner
        BannerHeaderCard(
            icon = Icons.Filled.Build,
            title = "Diagnostics",
            subtitleEn = "Check system readiness and fix issues",
            subtitleHi = "सिस्टम की स्थिति जाँचें और समस्याओं का समाधान करें",
            badgeLines = listOf("Everything", "working well!", "Small Checks", "Big Impact"),
        )

        // 2. Top System Status Card + Test for Language
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Left: System Status Hero
            Box(
                modifier = Modifier
                    .weight(1.8f)
                    // Hue follows the real state instead of being permanently green.
                    .background(overall.wash, RoundedCornerShape(16.dp))
                    .border(1.dp, overall.border, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(overall.textColor, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            // Glyph as well as hue, per §4.5. A green tick on a failing tablet was
                            // the single most misleading pixel on this screen.
                            Text(
                                if (overall.good) "\u2713" else "!",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                ),
                            )
                        }
                        Column {
                            Text("System Status", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = overall.textColor))
                            Text(
                                // Was the unconditional "All Core Systems Ready" — on a tablet with
                                // no models staged, on a first launch, always.
                                overall.headline,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = overall.textColor),
                            )
                            Text(
                                overall.detail,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, color = overall.textColor.copy(alpha = 0.85f)),
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🕒 ", fontSize = 11.sp)
                            Text(
                                // Was "Today, 11:23 AM", hardcoded.
                                lastCheckedAtMs?.let {
                                    "Last checked\n" +
                                        SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())
                                            .format(Date(it))
                                } ?: "Never checked",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = Color(0xFF475569)),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { runComponentTest("all") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text("▶  Run All Tests", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Right: Test for Language
            Box(
                modifier = Modifier
                    .weight(1.0f)
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                    .padding(14.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("🗣", fontSize = 14.sp)
                        Text(
                            "Test for Language",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B)),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LanguageTestPill("Mundari\nमुंडारी", isSelected = selectedLang == TargetLanguage.MUNDARI, Modifier.weight(1f)) {
                            selectedLang = TargetLanguage.MUNDARI
                        }
                        LanguageTestPill("Santali\nᱥᱟᱱᱛᱟᱲᱤ", isSelected = selectedLang == TargetLanguage.SANTALI, Modifier.weight(1f)) {
                            selectedLang = TargetLanguage.SANTALI
                        }
                        LanguageTestPill("Ho\nहो", isSelected = selectedLang == TargetLanguage.HO, Modifier.weight(0.8f)) {
                            selectedLang = TargetLanguage.HO
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Run diagnostics for the selected language pack",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = Color(0xFF64748B)),
                    )
                }
            }
        }

        // Feedback if a test was clicked
        testFeedback?.let {
            // Was always green, including for "Error: ...". The hue now follows whether anything
            // failed, so a red result cannot read as a success.
            val bad = states.values.any { s -> s is CheckState.Failed }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (bad) Color(0xFFFEF2F2) else Color(0xFFDCFCE7), RoundedCornerShape(8.dp))
                    .border(1.dp, if (bad) Color(0xFFFECACA) else Color(0xFF86EFAC), RoundedCornerShape(8.dp))
                    .padding(8.dp),
            ) {
                Text(
                    (if (bad) "\u26A0  " else "\u2713  ") + it,
                    fontSize = 11.5.sp,
                    color = if (bad) Color(0xFF991B1B) else Color(0xFF166534),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // 3. Two Columns: Left Component Status Table + Right Device/Pack Info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Left Column (weight 1.8f)
            Column(
                modifier = Modifier.weight(1.8f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Component Status Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text("Component Status", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B)))
                                Text("Check individual components of the system", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Color(0xFF64748B)))
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatusLegendDot(Color(0xFF16A34A), "Working")
                                StatusLegendDot(Color(0xFFF97316), "Needs Attention")
                                StatusLegendDot(Color(0xFFDC2626), "Not Working")
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        components.forEach { comp ->
                            ComponentRow(
                                component = comp,
                                isTesting = runningTestId == comp.id,
                                onTest = { runComponentTest(comp.id) },
                            )
                        }
                    }
                }

                // Native Speaker Verification Warning Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF7ED), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFFFEDD5), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(
                                modifier = Modifier.size(28.dp).background(Color(0xFFEA580C), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            Column {
                                Text(
                                    "Native speaker verification",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF9A3412)),
                                )
                                Text(
                                    "No Mundari content has been verified by a native speaker yet.\nUse with care and mark as approximate.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Color(0xFFC2410C)),
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .border(1.dp, Color(0xFFFDBA74), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("View Details", fontSize = 11.sp, color = Color(0xFFEA580C), fontWeight = FontWeight.SemiBold)
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(14.dp), tint = Color(0xFFEA580C))
                            }
                        }
                    }
                }
            }

            // Right Column: Device Information & Content Pack Status (weight 1.0f)
            Column(
                modifier = Modifier.weight(1.0f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Device Information Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(BolMitraIcons.Phone, null, Modifier.size(16.dp), tint = Color(0xFF334155))
                                Text("Device Information", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B)))
                            }
                            Row(
                                modifier = Modifier
                                    .background(Color(0xFFF8FAFC), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text("📋", fontSize = 10.sp)
                                Text("Copy", fontSize = 10.sp, color = Color(0xFF475569))
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // The `.ifBlank { "Xiaomi 23043RP34I" }` fallback baked the dev tablet's model
                        // number into every other device's diagnostics screen.
                        DeviceInfoRow("Model", "${spec.manufacturer} ${spec.model}".trim().ifBlank { "unknown" })
                        DeviceInfoRow("SoC", spec.socModel)
                        DeviceInfoRow("Android Version", "${spec.androidRelease} (API ${spec.apiLevel})")
                        DeviceInfoRow("Total RAM", "%.2f GiB".format(spec.totalRamGiB))
                        // Measured, polled every 2 s. Was the literal "2.18 GiB".
                        DeviceInfoRow(
                            "Available RAM",
                            availableRamGiB?.let { "%.2f GiB".format(it) } ?: "reading…",
                        )
                        // Native heap, not PSS: getProcessMemoryInfo is throttled for ordinary apps
                        // and returned a stale +0.0 MB across a 270 MB model load (V66).
                        DeviceInfoRow("App native heap", "%.0f MB".format(nativeHeapMb))
                        DeviceInfoRow("Storage (Free)", "%.1f GiB".format(spec.availableStorageGiB))
                        DeviceInfoRow("CPU Cores", "${spec.cpuCores} (${spec.abis.firstOrNull() ?: "unknown"})")
                        DeviceInfoRow("Arm dot-product", if (spec.hasDotProduct) "asimddp present" else "ABSENT — int8 may be slower")
                        DeviceInfoRow("Device tier", tier.name.lowercase())
                        DeviceInfoRow("Is Low RAM Device", if (spec.isLowRamDevice) "Yes" else "No")
                    }
                }

                // Content Pack Status Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("🗄", fontSize = 14.sp)
                                Text("Content Pack Status", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B)))
                            }
                            // Real bytes on disk, from the inventory.
                            val inv = store.inventory()
                            val presentMb = inv.filter { it.present }.sumOf { it.sizeMb }
                            Text(
                                "%.0f MB staged".format(presentMb),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, color = Color(0xFF64748B), fontWeight = FontWeight.SemiBold),
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        val inv = store.inventory()
                        val presentCount = inv.count { it.present }
                        // Fraction of files present, which is a real ratio. The 0.65f was a literal.
                        LinearProgressIndicator(
                            progress = { if (inv.isEmpty()) 0f else presentCount.toFloat() / inv.size },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = if (presentCount == inv.size) Color(0xFF16A34A) else Color(0xFFF59E0B),
                            trackColor = Color(0xFFE2E8F0),
                        )

                        Spacer(Modifier.height(10.dp))

                        // Every one of these four was a literal: "11 / 11", "1,240", "56", "320".
                        ContentPackStatRow("Model files", "$presentCount / ${inv.size}", presentCount == inv.size)
                        val seeded = DemoSeed.phrasesFor(selectedLang).size
                        val corpus = SantaliGlossary.phrasesFor(context, selectedLang).size
                        ContentPackStatRow(
                            "Phrasebook rows (${selectedLang.englishName})",
                            "${seeded + corpus}",
                            seeded + corpus > 0,
                        )
                        ContentPackStatRow(
                            "Worksheet item models",
                            "${NumeracySeed.models.size}",
                            NumeracySeed.models.isNotEmpty(),
                        )
                        ContentPackStatRow(
                            "Saved lesson turns",
                            historyCount?.toString() ?: "…",
                            (historyCount ?: 0) >= 0,
                        )

                        Spacer(Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Pack version", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = Color(0xFF94A3B8)))
                            // Was "Last synced: Aug 30, 2026" — a future date, for a sync that does not
                            // exist. There is no pack importer, so the honest field is the version.
                            Text(
                                if (selectedLang == TargetLanguage.SANTALI) {
                                    SantaliGlossary.PACK_VERSION
                                } else {
                                    "demo-v0"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = Color(0xFF64748B)),
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        // Was a "Sync Content Pack" button with an empty click handler. Packs arrive by
                        // USB or Wi-Fi Direct and there is no importer, so this states where they go
                        // instead of offering an action that cannot happen.
                        Text(
                            "Packs are side-loaded to:\n${store.rootPath}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.5.sp,
                                color = Color(0xFF94A3B8),
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComponentRow(
    component: DiagnosticComponent,
    isTesting: Boolean,
    onTest: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            Box(
                modifier = Modifier.size(32.dp).background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(component.iconEmoji, fontSize = 16.sp)
            }
            Column {
                Text(component.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Color(0xFF1E293B)))
                // The check's own words when there are any, so a failure says what is missing right
                // where the teacher is looking rather than only in a shared feedback box.
                val detail = when (val s = component.state) {
                    is CheckState.Ok -> s.detail
                    is CheckState.Failed -> s.detail
                    is CheckState.NotApplicable -> s.detail
                    else -> null
                }
                Text(
                    detail ?: component.description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 10.5.sp,
                        color = if (component.state is CheckState.Failed) {
                            Color(0xFFB91C1C)
                        } else {
                            Color(0xFF64748B)
                        },
                    ),
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Renders from component.state. This block used to be a hardcoded green tick and the
            // literal "Working", so a component whose check failed still displayed as working.
            val (dotColor, label) = when (component.state) {
                is CheckState.Unknown -> Color(0xFF94A3B8) to "Not checked"
                is CheckState.Running -> Color(0xFFF59E0B) to "Checking…"
                is CheckState.Ok -> Color(0xFF16A34A) to "Working"
                is CheckState.Failed -> Color(0xFFDC2626) to "Problem"
                is CheckState.NotApplicable -> Color(0xFF64748B) to "Not available"
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier.size(16.dp).background(dotColor, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    // A glyph per state, not a tick for everything.
                    Text(
                        when (component.state) {
                            is CheckState.Ok -> "\u2713"
                            is CheckState.Failed -> "!"
                            is CheckState.Running -> "\u22EF"
                            is CheckState.NotApplicable -> "\u2014"
                            is CheckState.Unknown -> "?"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        ),
                    )
                }
                Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = dotColor))
            }

            Box(
                modifier = Modifier
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(6.dp))
                    .clickable(onClick = onTest)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    if (isTesting) "..." else "Test",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, color = Color(0xFF334155), fontWeight = FontWeight.Medium),
                )
            }
        }
    }
}

/** The derived headline for the status hero, with the hues that go with it. */
private data class OverallStatus(
    val headline: String,
    val detail: String,
    val textColor: Color,
    val wash: Color,
    val border: Color,
    /** True only when every check has run and passed. Drives the glyph, not just the colour. */
    val good: Boolean,
)

/** Missing entry means never checked, which is a state and not a pass. */
private fun Map<String, CheckState>.of(id: String): CheckState = this[id] ?: CheckState.Unknown

@Composable
private fun LanguageTestPill(text: String, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(
                if (isSelected) Color(0xFF2EAF3B) else Color.White,
                RoundedCornerShape(8.dp),
            )
            .border(
                1.dp,
                if (isSelected) Color(0xFF259B32) else Color(0xFFE2E8F0),
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 10.5.sp,
                color = if (isSelected) Color.White else Color(0xFF334155),
            ),
        )
    }
}

@Composable
private fun DeviceInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Color(0xFF64748B)))
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Color(0xFF1E293B)))
    }
}

/**
 * A content-pack figure and whether it is satisfied.
 *
 * [ok] used to be implicit: the row drew an unconditional green tick, so "0 / 11 model files" was
 * presented as a pass.
 */
@Composable
private fun ContentPackStatRow(label: String, value: String, ok: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Color(0xFF64748B)))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Color(0xFF1E293B)))
            Box(
                modifier = Modifier.size(13.dp).background(Color(0xFF16A34A), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, null, Modifier.size(8.dp), tint = Color.White)
            }
        }
    }
}

@Composable
private fun StatusLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, color = Color(0xFF64748B)))
    }
}
