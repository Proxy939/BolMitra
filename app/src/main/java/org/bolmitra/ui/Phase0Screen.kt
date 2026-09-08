package org.bolmitra.ui

import android.os.Debug
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bolmitra.device.DeviceSpec
import org.bolmitra.device.DeviceTier
import org.bolmitra.speech.ModelStore
import org.bolmitra.speech.SherpaMundariTts
import org.bolmitra.speech.SherpaStreamingAsr
import org.bolmitra.speech.TargetLanguage
import org.bolmitra.translate.IndicTrans2Decoder
import org.bolmitra.translate.IndicTrans2MtEngine
import org.bolmitra.translate.IndicTrans2Tokenizer
import org.bolmitra.translate.Indictrans2SpikeHarness
import org.bolmitra.ui.common.DualLineChart
import org.bolmitra.ui.common.GlassCard
import org.bolmitra.ui.common.InkCard
import org.bolmitra.ui.common.PillButton
import org.bolmitra.ui.common.RowSectionHeader
import org.bolmitra.ui.theme.BolmitraColors
import org.bolmitra.ui.theme.Dimens

/**
 * Phase 0 measurement harness — ARCHITECTURE.md §5.3, §8.
 *
 * Every model-memory figure in §5.3 started as **disk size multiplied by a guess**, which is
 * exactly the reasoning V24 caught out when NLLB-600M int8 landed at ~1.9 bytes per parameter.
 * This screen replaces the arithmetic with a measurement: read memory, load a model, read again.
 * It has already corrected the document twice — V65 found the ASR estimate 28–55% *high* and the
 * synthesis budget ~50% *low*.
 *
 * `ponytail:` Deliberately a diagnostic surface, not product UI. It loads models on demand and
 * holds them, which is the opposite of §6.2.1's warm-residency rule for Live Class. Kept as a
 * normal destination rather than hidden behind a debug flag, because §11.2 requires every number
 * in this project to be traceable and the fastest way to honour that in a demo is to let anyone
 * read the raw figures off the tablet in front of them.
 */

/**
 * Native heap in MB.
 *
 * **Not PSS, on purpose (V66).** The first version of this screen called
 * `ActivityManager.getProcessMemoryInfo()` and reported 59.5 MB before *and* after loading the ASR
 * model — a delta of +0.0 — while `dumpsys meminfo` on the same process at the same moment showed
 * 132 MiB climbing to 400 MiB. Android **rate-limits that call for non-privileged apps** and hands
 * back a cached snapshot rather than failing, so the screen was not slightly wrong: it would have
 * "confirmed" that ONNX Runtime costs nothing.
 *
 * `Debug.getNativeHeapAllocatedSize()` is read from the allocator and is not throttled. It is also
 * the right slice for this question — ORT holds model weights in native allocations, not the Java
 * heap, and `dumpsys` attributes 377 MiB of the loaded state to Native Heap against 11.9 MiB of
 * Java heap. Cross-checked at ~2% agreement with `dumpsys`, where the old metric was off by two
 * orders of magnitude.
 */
private fun readNativeHeapMb(): Double =
    Debug.getNativeHeapAllocatedSize() / (1024.0 * 1024.0)

/** Java heap, shown alongside so it is visible that the model does *not* land there. */
private fun readJavaHeapMb(): Double {
    val rt = Runtime.getRuntime()
    return (rt.totalMemory() - rt.freeMemory()) / (1024.0 * 1024.0)
}

private data class LoadReport(
    val label: String,
    val beforeMb: Double,
    val afterMb: Double,
    val javaDeltaMb: Double,
    val loadMs: Long,
    val note: String,
) {
    val deltaMb: Double get() = afterMb - beforeMb
}

/**
 * Diagnostics destination: device probe, model inventory, and the memory harness.
 */
@Composable
fun DiagnosticsPane(spec: DeviceSpec, tier: DeviceTier, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap),
    ) {
        InkCard(Modifier.fillMaxWidth()) {
            Text(
                "Profile: ${tier.name.lowercase()}",
                style = MaterialTheme.typography.titleLarge,
                color = BolmitraColors.OnInk,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when (tier) {
                    DeviceTier.FLOOR ->
                        "2 GB-class budget applies (\u00A75.3): batch ASR, int8 everywhere."
                    DeviceTier.ROOMY ->
                        "Streaming ASR is affordable here (\u00A75.3 option A). The FLOOR tier " +
                            "remains entirely UNMEASURED \u2014 no 2 GB device has been tested, " +
                            "and this tablet is roughly 3\u00D7 that."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = BolmitraColors.OnInkMuted,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (spec.hasDotProduct) {
                    "asimddp present \u2014 Arm dot-product available, so int8 buys size AND " +
                        "speed on this SoC (V56 resolved for this device)."
                } else {
                    "asimddp ABSENT \u2014 int8 may be SLOWER than fp32 here. Both must be " +
                        "measured before choosing (V56)."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = BolmitraColors.OnInkMuted,
            )
        }

        GlassCard(Modifier.fillMaxWidth()) {
            RowSectionHeader("Device")
            Spacer(Modifier.height(8.dp))
            DataRow("Model", "${spec.manufacturer} ${spec.model}")
            DataRow("SoC", spec.socModel)
            DataRow("Android", "${spec.androidRelease} (API ${spec.apiLevel})")
            DataRow("Total RAM", "%.2f GiB".format(spec.totalRamGiB))
            DataRow("isLowRamDevice", spec.isLowRamDevice.toString())
            DataRow("CPU cores", spec.cpuCores.toString())
            DataRow("ABIs", spec.abis.joinToString(", "))
            DataRow("64-bit only", spec.is64BitOnly.toString())
            DataRow("Free storage", "%.1f GiB".format(spec.availableStorageGiB))
            DataRow(
                "ApplicationExitInfo",
                if (spec.apiLevel >= 30) "available" else "UNAVAILABLE (V55 ceiling)",
            )
        }

        Phase0ModelSection()
    }
}

@Composable
fun Phase0ModelSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { ModelStore(context) }
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }
    var reports by remember { mutableStateOf(listOf<LoadReport>()) }
    var currentNative by remember { mutableStateOf(readNativeHeapMb()) }

    // Held so the memory stays resident while we measure. Never released here on purpose.
    var asr by remember { mutableStateOf<SherpaStreamingAsr?>(null) }
    var tts by remember { mutableStateOf<SherpaMundariTts?>(null) }
    var mt by remember { mutableStateOf<IndicTrans2Decoder?>(null) }
    var tok by remember { mutableStateOf<IndicTrans2Tokenizer?>(null) }

    fun measure(label: String, block: suspend () -> String) {
        if (busy) return
        busy = true
        scope.launch {
            val before = readNativeHeapMb()
            val javaBefore = readJavaHeapMb()
            val start = System.currentTimeMillis()
            val note = try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: Throwable) {
                "FAILED: ${e.javaClass.simpleName}: ${e.message?.take(160)}"
            }
            val ms = System.currentTimeMillis() - start
            val after = readNativeHeapMb()
            reports = reports + LoadReport(
                label = label,
                beforeMb = before,
                afterMb = after,
                javaDeltaMb = readJavaHeapMb() - javaBefore,
                loadMs = ms,
                note = note,
            )
            currentNative = after
            busy = false
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap)) {

        GlassCard(Modifier.fillMaxWidth()) {
            RowSectionHeader("Model files", store.inventory().count { it.present }.toString() + " present")
            Spacer(Modifier.height(8.dp))
            store.inventory().forEach { m ->
                DataRow(m.label, if (m.present) "%.1f MB".format(m.sizeMb) else "MISSING")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                store.rootPath,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = BolmitraColors.InkMuted,
            )
        }

        GlassCard(Modifier.fillMaxWidth()) {
            RowSectionHeader("Resident memory", "measured, not scaled")
            Spacer(Modifier.height(8.dp))
            DataRow("Native heap now", "%.1f MB".format(currentNative))
            Spacer(Modifier.height(8.dp))
            Text(
                "Native heap rather than PSS: getProcessMemoryInfo() is throttled for ordinary " +
                    "apps and returned a stale +0.0 MB delta across a 270 MB load (V66). " +
                    "Cross-check with `adb shell dumpsys meminfo org.bolmitra`.",
                style = MaterialTheme.typography.bodySmall,
                color = BolmitraColors.InkMuted,
            )

            // The supplied design's chart, pointed at the only real series this app has: how the
            // native heap grows as models load. Drawn once there are two points to join, so it
            // never implies a trend from a single reading.
            if (reports.size >= 2) {
                Spacer(Modifier.height(8.dp))
                DualLineChart(
                    primary = reports.map { it.afterMb.toFloat() },
                    secondary = emptyList(),
                    description = "Native heap after each load step, from " +
                        "%.0f to %.0f megabytes".format(
                            reports.first().afterMb, reports.last().afterMb,
                        ),
                    modifier = Modifier.fillMaxWidth().height(110.dp),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    reports.forEachIndexed { i, _ ->
                        Text(
                            (i + 1).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = BolmitraColors.InkMuted,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.targetGap)) {
                PillButton(
                    label = "Load ASR",
                    enabled = !busy && asr == null && store.hasStreamingAsr(),
                    onClick = {
                        measure("ASR streaming (option A)") {
                            asr = SherpaStreamingAsr(
                                modelPath = store.asrStreamingModel.absolutePath,
                                tokensPath = store.asrStreamingTokens.absolutePath,
                            )
                            "loaded"
                        }
                    },
                )
                PillButton(
                    label = "Load voice",
                    enabled = !busy && tts == null && store.hasTts(),
                    onClick = {
                        measure("Mundari TTS (VITS)") {
                            val engine = SherpaMundariTts(
                                modelPath = store.ttsModel.absolutePath,
                                tokensPath = store.ttsTokens.absolutePath,
                            )
                            tts = engine
                            "loaded, sampleRate=${engine.sampleRate}"
                        }
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            PillButton(
                label = "Speak \u201C\u0915\u093F\u0924\u093E\u092C \u0916\u094B\u0932\u094B\u201D",
                filled = false,
                enabled = !busy && tts != null,
                onClick = {
                    measure("TTS synthesis") {
                        // Devanagari in; the engine transliterates to Odia first (V63).
                        val clip = tts?.synthesizeUtterance("\u0915\u093F\u0924\u093E\u092C \u0916\u094B\u0932\u094B")
                        val t = tts?.lastTransliteration
                        if (clip == null) {
                            "returned null (dropped=${t?.unmapped})"
                        } else {
                            val secs = clip.pcm16Mono16k.size / 16000.0
                            "%.2f s audio, %d samples, odia='%s'".format(
                                secs, clip.pcm16Mono16k.size, t?.odia ?: "?",
                            )
                        }
                    }
                },
            )

            // T1 MT gate. Reuses `measure` rather than adding a surface of its own, because the
            // two things worth knowing about a 519 MB int8 model are exactly what this card
            // already reports: how much resident memory it costs, and how long it takes.
            Spacer(Modifier.height(8.dp))
            PillButton(
                label = "Run MT spike (Santali)",
                filled = false,
                enabled = !busy && store.hasMtGraphs(TargetLanguage.SANTALI),
                onClick = {
                    measure("IndicTrans2 320M int8 \u2014 load + 11 sentences") {
                        val tk = tok ?: Indictrans2SpikeHarness
                            .loadTokenizer(store, TargetLanguage.SANTALI).also { tok = it }
                        val d = mt ?: Indictrans2SpikeHarness.load(
                            store = store,
                            language = TargetLanguage.SANTALI,
                            threads = IndicTrans2Decoder.DEFAULT_THREADS,
                        ).also { mt = it }
                        // Held, not closed — the native-heap delta above is only meaningful while
                        // the graphs are still resident, same reason `asr` and `tts` are kept.
                        Indictrans2SpikeHarness.verify(d, store, TargetLanguage.SANTALI, tk)
                    }
                },
            )
            // The last unproven link. The spike above proves tokenizer and decoder; the TTS button
            // proves Devanagari reaches the voice. This joins them: Hindi text in, Santali audio
            // out, through OlChikiToDevanagari and DevanagariToOdia. Everything except the
            // microphone, which needs a person to speak into it.
            Spacer(Modifier.height(8.dp))
            PillButton(
                label = "Translate + speak Santali",
                filled = false,
                enabled = !busy && store.hasMtGraphs(TargetLanguage.SANTALI) && tts != null,
                onClick = {
                    measure("Hindi \u2192 Santali \u2192 audio") {
                        val tk = tok ?: Indictrans2SpikeHarness
                            .loadTokenizer(store, TargetLanguage.SANTALI).also { tok = it }
                        val d = mt ?: Indictrans2SpikeHarness.load(
                            store, TargetLanguage.SANTALI, IndicTrans2Decoder.DEFAULT_THREADS,
                        ).also { mt = it }
                        val engine = IndicTrans2MtEngine(tk, d)
                        // Open-domain on purpose: this sentence is not in the phrasebook, so it can
                        // only come from T1. It is also the one the desktop eval round-tripped
                        // cleanly, so a bad result here points at the device, not the model.
                        val hindi = "\u0906\u091C \u0939\u092E \u0917\u093F\u0928\u0924\u0940 " +
                            "\u0938\u0940\u0916\u0947\u0902\u0917\u0947"
                        val out = engine.translate(hindi)
                            ?: return@measure "translate returned null"
                        val clip = tts?.synthesizeUtterance(out.targetTextDeva)
                        val odia = tts?.lastTransliteration
                        if (clip == null) {
                            "olck='${out.targetTextNative}' deva='${out.targetTextDeva}' " +
                                "but synthesis returned null (dropped=${odia?.unmapped})"
                        } else {
                            "%.2f s audio \u00b7 olck='%s' \u00b7 deva='%s' \u00b7 odia='%s'".format(
                                clip.pcm16Mono16k.size / 16000.0,
                                out.targetTextNative,
                                out.targetTextDeva,
                                odia?.odia ?: "?",
                            )
                        }
                    }
                },
            )
            if (tts == null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Load the voice first \u2014 Santali is spoken by the Mundari VITS.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BolmitraColors.InkMuted,
                )
            }

            if (!store.hasMtGraphs(TargetLanguage.SANTALI)) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "MT graphs not on this tablet. Stage them with tools/stage-mt-model.py, " +
                        "then adb push models/mt-hi-sat to ${store.rootPath}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BolmitraColors.InkMuted,
                )
            }

            if (busy) {
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.padding(2.dp))
                    Text("working\u2026", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        reports.forEachIndexed { i, r ->
            GlassCard(Modifier.fillMaxWidth()) {
                RowSectionHeader("${i + 1}. ${r.label}")
                Spacer(Modifier.height(8.dp))
                DataRow("native before", "%.1f MB".format(r.beforeMb))
                DataRow("native after", "%.1f MB".format(r.afterMb))
                DataRow("native delta", "%+.1f MB".format(r.deltaMb))
                DataRow("java delta", "%+.1f MB".format(r.javaDeltaMb))
                DataRow("elapsed", "${r.loadMs} ms")
                Spacer(Modifier.height(8.dp))
                Text(
                    r.note,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = BolmitraColors.InkMuted,
                )
            }
        }
    }
}
