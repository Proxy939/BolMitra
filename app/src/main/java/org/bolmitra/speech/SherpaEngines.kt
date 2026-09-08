package org.bolmitra.speech

import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineNeMoCtcModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import org.bolmitra.translate.AsrEngine
import org.bolmitra.translate.AudioClip
import org.bolmitra.translate.TtsEngine
import org.bolmitra.translit.DevanagariToOdia

private const val TAG = "BolMitra/sherpa"

/** 16 kHz mono. §4.7: the ASR front-end expects it, and V48 fixes the audio path at 16 kHz. */
const val SAMPLE_RATE = 16_000

/**
 * Streaming Hindi ASR over sherpa-onnx — ARCHITECTURE.md §4.3 candidate A.
 *
 * The model is a NeMo `EncDecHybridRNNTCTCBPEModel` with only the CTC branch exported, so it
 * goes in the `neMoCtc` slot. Read from the artifact's own ONNX metadata rather than assumed;
 * that metadata also records `window_size 121 / chunk_shift 112`, matching V25, and documents
 * `att_context_size=[70, 13] (~1040 ms lookahead)` — which is the architectural source of V25's
 * 1,210 ms first-partial floor. **That floor does not improve on faster hardware.**
 *
 * > **Phase 0 trap to watch for (V25).** The published export leaves `normalize_type` EMPTY in
 * > its metadata. NeMo Conformers are normally trained with per-feature normalisation, and V25
 * > records that sherpa-onnx's online CTC path can leave `is_librosa` false, producing output
 * > that is **fluent but wrong** — plausible Hindi that does not match what was said. That
 * > failure is invisible in a WER-free smoke test, so the first real transcript must be checked
 * > against known audio, not merely observed to be non-empty.
 */
class SherpaStreamingAsr(
    modelPath: String,
    tokensPath: String,
    /** cpu4–7 are the A77s on the reference device (V64); cpu0–3 are the slow A55s. */
    numThreads: Int = 2,
) : AsrEngine, AutoCloseable {

    private val recognizer = OnlineRecognizer(
        assetManager = null,
        config = OnlineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = OnlineModelConfig(
                neMoCtc = OnlineNeMoCtcModelConfig(model = modelPath),
                tokens = tokensPath,
                numThreads = numThreads,
                modelType = "nemo_ctc",
            ),
        ),
    )

    /**
     * Transcribes one complete utterance.
     *
     * Push-to-talk means the caller already knows where the utterance ends, so endpointing is
     * not used here — the whole buffer is fed and flushed. This is the ASR tail flush §6.2.1
     * budgets at 400–700 ms.
     */
    override fun transcribe(pcm16Mono16k: ShortArray): String? {
        if (pcm16Mono16k.isEmpty()) return null
        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(pcm16Mono16k.toFloatPcm(), SAMPLE_RATE)
            stream.inputFinished()
            while (recognizer.isReady(stream)) recognizer.decode(stream)
            recognizer.getResult(stream).text.trim().ifEmpty { null }
        } catch (e: Exception) {
            Log.e(TAG, "ASR failed", e)
            null
        } finally {
            stream.release()
        }
    }

    override fun close() = recognizer.release()
}

/**
 * Mundari VITS synthesis over sherpa-onnx — §4.4.
 *
 * **This engine transliterates before synthesising, and that is not optional.** V63: the MMS
 * Mundari voice is trained on **Odia** orthography — 50 of 50 symbols in its vocabulary are
 * Odia, with no Devanagari at all. Handing it the Devanagari text the rest of the app uses would
 * produce silence or noise. So [DevanagariToOdia] runs first.
 *
 * In production this conversion happens off-device at T2 pack-build time, because T0 audio is
 * pre-rendered (§6.3) — which is why it stays off the ≤3 s critical path. This engine covers the
 * T1 novel-utterance case, already the looser Path B budget.
 */
class SherpaMundariTts(
    modelPath: String,
    tokensPath: String,
    numThreads: Int = 2,
) : TtsEngine, AutoCloseable {

    init {
        // MUST run before OfflineTts is constructed. See validateCharacterTokens.
        validateCharacterTokens(java.io.File(tokensPath))
    }

    private val tts = OfflineTts(
        assetManager = null,
        config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = modelPath,
                    tokens = tokensPath,
                    // MMS VITS is character-based with no lexicon or espeak-ng data dir.
                    lexicon = "",
                    dataDir = "",
                ),
                numThreads = numThreads,
            ),
            // V49: sherpa's offline TTS fires its callback once per sentence batch, and the
            // default is 1, so for the short imperatives in this domain the "first chunk" IS
            // the whole waveform. There is no intra-sentence streaming to exploit.
            maxNumSentences = 1,
        ),
    )

    val sampleRate: Int get() = tts.sampleRate()

    /** Last transliteration outcome, so the caller can surface dropped characters. */
    @Volatile
    var lastTransliteration: DevanagariToOdia.Result? = null
        private set

    override fun synthesizeUtterance(text: String): AudioClip? {
        val t = DevanagariToOdia.transliterate(text)
        lastTransliteration = t
        if (t.odia.isBlank()) {
            Log.w(TAG, "nothing synthesisable after transliteration; dropped=${t.unmapped}")
            return null
        }
        if (t.unmapped.isNotEmpty()) {
            // Reported, not silently swallowed: dropped characters mean missing speech.
            Log.w(TAG, "dropped ${t.unmapped.size} unmapped char(s): ${t.unmapped}")
        }
        return try {
            val audio = tts.generate(text = t.odia, sid = 0, speed = 1.0f)
            AudioClip(audio.samples.toPcm16())
        } catch (e: Exception) {
            Log.e(TAG, "TTS failed", e)
            null
        }
    }

    override fun close() = tts.release()
}

/**
 * Rejects a `tokens.txt` that sherpa-onnx would refuse — **before** sherpa gets to see it.
 *
 * ### Why this exists
 *
 * sherpa-onnx does not throw on a malformed config. `SHERPA_ONNX_EXIT(-1)` expands to `exit(-1)`,
 * so the whole process disappears with status 255 and **no Java exception, no crash dump, and no
 * chance to recover.** `ApplicationExitInfo` reports `reason=EXIT_SELF status=255` and nothing
 * else. That is unacceptable behaviour to hand a teacher mid-lesson, so every input sherpa can
 * die on is checked on this side of the JNI boundary first.
 *
 * This bit us for real: the converter wrote CRLF line endings on Windows. `ReadTokens` in
 * `offline-tts-character-frontend.cc` encodes the space symbol as a line containing only an id,
 * and detects it with `iss >> sym; if (iss.eof())`. A trailing CR leaves the stream short of eof,
 * so that line parsed as the two-character symbol `"21"`, failed the `size() != 1` check, and
 * killed the app. Every other line tolerated CRLF, which is why it looked like a memory problem.
 *
 * The parse below mirrors sherpa's, so a file that passes here is one sherpa will accept.
 */
internal fun validateCharacterTokens(tokens: java.io.File) {
    require(tokens.isFile) { "tokens.txt missing: ${tokens.absolutePath}" }

    val seen = HashMap<Int, Int>()
    // Split on '\n' only. `readLines()` would strip a trailing CR and hide the exact defect this
    // function exists to catch — C++ `std::getline` leaves the CR in the string, so we must too.
    tokens.readText().split('\n').forEachIndexed { i, raw ->
        val lineNo = i + 1
        require(!raw.contains('\r')) {
            "tokens.txt line $lineNo has a CR. sherpa-onnx exit(-1)s on the space-symbol line " +
                "when the file is CRLF. Rewrite with LF endings."
        }
        if (raw.isEmpty()) return@forEachIndexed

        val fields = raw.split(' ').filter { it.isNotEmpty() }
        // One field means the symbol IS a space and only its id is written; that is sherpa's
        // own convention, not a defect.
        val (symbol, idText) = when (fields.size) {
            1 -> " " to fields[0]
            2 -> fields[0] to fields[1]
            else -> throw IllegalArgumentException(
                "tokens.txt line $lineNo has ${fields.size} fields, expected 1 or 2: '$raw'",
            )
        }
        val id = idText.toIntOrNull()
            ?: throw IllegalArgumentException("tokens.txt line $lineNo has a non-numeric id: '$raw'")

        // sherpa compares by UTF-32 code point and requires exactly one.
        val codePoints = symbol.codePointCount(0, symbol.length)
        require(codePoints == 1) {
            "tokens.txt line $lineNo maps $codePoints code points, sherpa requires 1: '$raw'"
        }
        val cp = symbol.codePointAt(0)
        val prior = seen.put(cp, id)
        require(prior == null) {
            "tokens.txt line $lineNo duplicates code point U+%04X (already id $prior)".format(cp)
        }
    }
    require(seen.isNotEmpty()) { "tokens.txt is empty: ${tokens.absolutePath}" }
}

// --- PCM conversion -----------------------------------------------------------------------
// sherpa-onnx speaks float [-1, 1]; our AudioClip is PCM16 because that is what AudioTrack
// takes and what V48 fixed the pre-rendered WAV path to. Converting at the boundary keeps one
// audio representation in the domain rather than two.

internal fun ShortArray.toFloatPcm(): FloatArray =
    FloatArray(size) { this[it] / 32768.0f }

internal fun FloatArray.toPcm16(): ShortArray = ShortArray(size) {
    // Clamp before scaling: VITS can overshoot [-1, 1] slightly and wrapping would click.
    (this[it].coerceIn(-1.0f, 1.0f) * 32767.0f).toInt().toShort()
}

/**
 * Batch Hindi ASR over sherpa-onnx — §4.3 candidate B, and **the one that actually transcribes.**
 *
 * ### Why this replaced candidate A
 *
 * Candidate A returned `null` for every real utterance on device. Dumping both artifacts' ONNX
 * `metadata_props` (see `tools/inspect-asr.py`) shows why, and it is not subtle:
 *
 * | | A: salesken FastConformer | B: AI4Bharat IndicConformer |
 * |---|---|---|
 * | `normalize_type` | `''` — **empty** | `'per_feature'` |
 * | `model_type` | `EncDecHybridRNNTCTCBPEModel` | `EncDecCTCModelBPE` |
 * | `vocab_size` | 1024 | 5633 |
 *
 * sherpa-onnx takes feature normalisation from that metadata, not from [FeatureConfig]. With the
 * key empty, A's encoder — trained on per-feature-normalised log-mel — is fed unnormalised
 * features. The CTC posteriors collapse onto the blank symbol, `getResult().text` comes back
 * empty, and [AsrEngine.transcribe]'s `ifEmpty { null }` turns that into
 * `NO_SPEECH_RECOGNISED`. The teacher speaks and the app reports silence.
 *
 * The class docs on [SherpaStreamingAsr] flagged this as a Phase 0 trap to watch for. It was not a
 * risk; it was already happening. B declares `per_feature` correctly, so it is now the default.
 *
 * ### Batch is the right shape here anyway
 *
 * A's streaming machinery bought nothing. Push-to-talk hands over a complete utterance, so
 * [SherpaStreamingAsr] already fed the whole buffer and flushed rather than endpointing — paying
 * for a streaming encoder, its 1,040 ms attention lookahead and its cache tensors to do a batch
 * job. An `OfflineRecognizer` is a plain forward pass over the utterance, and V25's 1,210 ms
 * first-partial floor stops applying because there are no partials to wait for.
 *
 * The cost is size: 197.6 MB against 174.3 MB. `tools/fetch-models.ps1` already warns that §5.3
 * had this backwards, costing B at 120 MB and calling it the lighter option.
 *
 * `ponytail:` Still no WER measurement. `per_feature` being declared means the front-end now
 * matches training, which is necessary but not sufficient — the ceiling is that "it produces
 * Hindi" is not the same as "it produces the right Hindi", and the published median WER for the
 * candidate A model was 0.328. Check the first transcripts against known audio.
 */
class SherpaBatchAsr(
    modelPath: String,
    tokensPath: String,
    numThreads: Int = 2,
) : AsrEngine, AutoCloseable {

    private val recognizer = OfflineRecognizer(
        assetManager = null,
        config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = OfflineModelConfig(
                nemo = OfflineNemoEncDecCtcModelConfig(model = modelPath),
                tokens = tokensPath,
                numThreads = numThreads,
                modelType = "nemo_ctc",
            ),
        ),
    )

    override fun transcribe(pcm16Mono16k: ShortArray): String? {
        if (pcm16Mono16k.isEmpty()) return null
        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(pcm16Mono16k.toFloatPcm(), SAMPLE_RATE)
            recognizer.decode(stream)
            val text = recognizer.getResult(stream).text.trim()
            Log.d(TAG, "batch ASR -> '${text}' (${pcm16Mono16k.size} samples)")
            text.ifEmpty { null }
        } catch (e: Exception) {
            Log.e(TAG, "batch ASR failed", e)
            null
        } finally {
            stream.release()
        }
    }

    override fun close() = recognizer.release()
}
