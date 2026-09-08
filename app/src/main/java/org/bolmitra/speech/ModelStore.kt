package org.bolmitra.speech

import android.content.Context
import java.io.File

/**
 * Locates model files on device — ARCHITECTURE.md §6.3 (model manager), §6.8.2 (packs).
 *
 * Models are **not** in the APK. §5.4 budgets the APK at 40–60 MB without them, and §6.3's
 * manager fetches a tier-matched variant set at runtime. In production they arrive as signed
 * packs over USB or Wi-Fi Direct from a block office (§4.8.5) — never over the internet, which
 * would break R6 at the worst moment.
 *
 * For development they are pushed with `adb push` into app-specific external storage, which
 * needs no runtime permission and survives reinstalls:
 *
 *     adb push models/. /sdcard/Android/data/org.bolmitra/files/models/
 *
 * `ponytail:` No signature verification here yet. That is deliberate sequencing, not an
 * oversight — §4.7 makes pack integrity a trust boundary and Ed25519 verification belongs with
 * the pack importer, which does not exist. The ceiling is real: today any file at the right path
 * is loaded on faith. Nothing ships to a school until the importer lands.
 */
class ModelStore(private val context: Context) {

    /** Prefers external app-specific dir (adb-pushable), falls back to internal. */
    private val root: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "models")

    val asrStreamingModel: File get() = File(root, "asr-streaming/model.int8.onnx")
    val asrStreamingTokens: File get() = File(root, "asr-streaming/tokens.txt")
    val asrBatchModel: File get() = File(root, "asr-batch/model.int8.onnx")
    val asrBatchTokens: File get() = File(root, "asr-batch/tokens.txt")
    val vadModel: File get() = File(root, "vad/silero_vad.onnx")

    /**
     * Voice files for a given target language.
     *
     * These were hardcoded to `tts-unr/` when there was one target. They are keyed on
     * [TargetLanguage.voiceDir] now so a second language is a directory rather than a code change —
     * which matters because the languages differ in what they *have*, not in how they are located.
     */
    fun ttsModel(language: TargetLanguage): File = File(root, "${language.voiceDir}/model.onnx")

    fun ttsTokens(language: TargetLanguage): File = File(root, "${language.voiceDir}/tokens.txt")

    /**
     * T1 machine-translation files for a given target language.
     *
     * Keyed on [TargetLanguage.mtDir], which is null wherever no model exists, so these return
     * null rather than pointing at a directory that can never be filled. Three graphs because
     * that is how the seq2seq export is shaped: [mtEncoder] runs once per utterance, [mtDecoder]
     * produces the first token and the initial KV cache, and [mtDecoderWithPast] runs every step
     * after that.
     */
    private fun mtFile(language: TargetLanguage, name: String): File? =
        language.mtDir?.let { File(root, "$it/$name") }

    fun mtEncoder(language: TargetLanguage): File? = mtFile(language, "encoder.onnx")

    /**
     * The fused decoder — both the no-cache and with-past paths in one graph.
     *
     * Replaces the two separate decoder graphs the export ships. Those held the same weights
     * twice, 194 MB and 185 MB, and loading both cost 184 MB of duplicate resident memory for no
     * behavioural gain: the fused graph reproduces all 11 eval sentences token for token. Built by
     * tools/mt-merge-decoder.py.
     */
    fun mtDecoderMerged(language: TargetLanguage): File? =
        mtFile(language, "decoder_merged.onnx")

    /**
     * The flattened SentencePiece table the Kotlin tokenizer reads.
     *
     * Built off-device by tools/export-spm-table.py from [mtSrcSpm] and the dictionaries, because
     * there is no SentencePiece for Android and no protobuf parser here to read a .model with.
     * One file replaces all three at runtime: piece, merge score, source id, target id.
     */
    fun mtSpmTable(language: TargetLanguage): File? = mtFile(language, "spm.tsv")

    /**
     * The original SentencePiece models. Staged for provenance and for regenerating [mtSpmTable];
     * nothing on device reads them. Source and target sides are byte-identical for the indic→indic
     * checkpoint — one shared piece table, two direction-specific dictionaries.
     */
    fun mtSrcSpm(language: TargetLanguage): File? = mtFile(language, "src.model")

    fun mtTgtSpm(language: TargetLanguage): File? = mtFile(language, "tgt.model")

    fun mtSrcDict(language: TargetLanguage): File? = mtFile(language, "dict.SRC.json")

    fun mtTgtDict(language: TargetLanguage): File? = mtFile(language, "dict.TGT.json")

    /**
     * Precomputed token ids used by the latency spike, standing in for a tokenizer that does not
     * exist on Android yet. Not part of the shipped pack.
     */
    fun mtFixture(language: TargetLanguage): File? = mtFile(language, "fixture.json")

    /**
     * True when the three graphs needed to run a translation are all present.
     *
     * Deliberately does NOT require the SentencePiece or dict files: the spike runs from
     * precomputed ids and must not be blocked by files it never opens. Tighten this when the
     * Kotlin tokenizer lands and those files become load-bearing.
     */
    fun hasMtGraphs(language: TargetLanguage): Boolean =
        mtEncoder(language)?.isFile == true && mtDecoderMerged(language)?.isFile == true

    /** Mundari, kept for the diagnostics screen's fixed inventory. */
    val ttsModel: File get() = ttsModel(TargetLanguage.MUNDARI)
    val ttsTokens: File get() = ttsTokens(TargetLanguage.MUNDARI)

    val rootPath: String get() = root.absolutePath

    /** What is actually present, for the Phase 0 screen to report honestly. */
    fun inventory(): List<ModelFile> = listOf(
        ModelFile("ASR streaming (option A)", asrStreamingModel),
        ModelFile("ASR streaming tokens", asrStreamingTokens),
        ModelFile("ASR batch (option B)", asrBatchModel),
        ModelFile("ASR batch tokens", asrBatchTokens),
        ModelFile("Silero VAD", vadModel),
        ModelFile("Mundari TTS (Odia script)", ttsModel),
        ModelFile("Mundari TTS tokens", ttsTokens),
    ) + mtInventory(TargetLanguage.SANTALI)

    /** MT rows, listed separately because they only exist for languages that have a model. */
    private fun mtInventory(language: TargetLanguage): List<ModelFile> {
        val n = language.englishName
        return listOfNotNull(
            mtEncoder(language)?.let { ModelFile("$n MT encoder", it) },
            mtDecoderMerged(language)?.let { ModelFile("$n MT decoder (fused)", it) },
            mtSrcSpm(language)?.let { ModelFile("$n MT SentencePiece (src)", it) },
            mtTgtSpm(language)?.let { ModelFile("$n MT SentencePiece (tgt)", it) },
        )
    }

    fun hasStreamingAsr(): Boolean = asrStreamingModel.isFile && asrStreamingTokens.isFile
    fun hasBatchAsr(): Boolean = asrBatchModel.isFile && asrBatchTokens.isFile
    fun hasVad(): Boolean = vadModel.isFile

    fun hasTts(language: TargetLanguage): Boolean =
        ttsModel(language).isFile && ttsTokens(language).isFile

    fun hasTts(): Boolean = hasTts(TargetLanguage.MUNDARI)
}

data class ModelFile(val label: String, val file: File) {
    val present: Boolean get() = file.isFile
    val sizeMb: Double get() = if (present) file.length() / (1024.0 * 1024.0) else 0.0
}
