package org.bolmitra.translate

import android.util.Log
import org.bolmitra.speech.ModelStore
import org.bolmitra.speech.TargetLanguage
import org.bolmitra.translit.OlChikiToDevanagari

/**
 * The T1 tier, assembled: Hindi speech in, Santali out — §4.5 Path B.
 *
 * Three pieces that were each proven separately now do one job:
 *
 * ```
 * transcript ──IndicTrans2Tokenizer──> ids ──IndicTrans2Decoder──> ids
 *            ──IndicTrans2Tokenizer──> Ol Chiki ──OlChikiToDevanagari──> Devanagari
 * ```
 *
 * [MtOutput.targetTextNative] carries the Ol Chiki, which is what the class is shown.
 * [MtOutput.targetTextDeva] carries the Devanagari, which is what the teacher can read *and* what
 * the voice is fed — the TTS stage transliterates Devanagari to Odia itself, so it cannot accept
 * Ol Chiki. See the note at the synthesis call in [TurnOrchestrator].
 *
 * ### Honesty, not optional
 *
 * Everything this produces is unreviewed machine output. [TurnOutcome.MachineAudio] fixes the
 * provenance to [org.bolmitra.phrasebook.Provenance.MACHINE], which the UI renders as a
 * "⚠ Machine" chip, and that is load-bearing rather than decorative: the 320M scored roughly 5 of
 * 11 acceptable on the eval set, and it fails hardest on exactly the short classroom imperatives
 * the T0 phrasebook already covers. T0 is checked first for that reason. This tier is for the
 * open-domain sentences no phrasebook can hold.
 *
 * And the voice is not Santali. It is the Mundari VITS reading Santali text, chosen because both
 * are North Munda and it is the closest phonology that will actually run — Indic Parler-TTS is the
 * only real Santali voice and sherpa-onnx cannot execute it. The picker states this on screen; it
 * must not become a comment nobody reads.
 *
 * ### Do not normalise the input
 *
 * [translate] takes the **raw ASR transcript**. The interface parameter is named `normalizedHindi`,
 * which is aspirational — `TurnOrchestrator` passes the raw transcript, and that is correct here.
 * `HindiNormalizer` strips danda, spells digits as Hindi words and drops honorifics: all right for
 * phrasebook matching, all wrong for a model trained on sentence-terminated text with real
 * numerals. IndicTrans2 does its own preprocessing inside the tokenizer.
 */
class IndicTrans2MtEngine(
    private val tokenizer: IndicTrans2Tokenizer,
    private val decoder: IndicTrans2Decoder,
    private val srcTag: String = IndicTrans2Tokenizer.HIN_DEVA,
    private val tgtTag: String = IndicTrans2Tokenizer.SAT_OLCK,
) : MtEngine, AutoCloseable {

    /** Set when the last call produced text the voice cannot fully pronounce. For diagnostics. */
    @Volatile
    var lastApproximation: String? = null
        private set

    override fun translate(normalizedHindi: String): MtOutput? {
        val hindi = normalizedHindi.trim()
        if (hindi.isEmpty()) return null

        return try {
            val ids = tokenizer.encode(hindi, srcTag, tgtTag)
            // A single unpadded sentence, so the mask is all ones and must track the ids rather
            // than any fixed length.
            val decoded = decoder.decode(ids, LongArray(ids.size) { 1L })
            if (decoded.tokenIds.isEmpty()) {
                Log.w(TAG, "empty decode for '$hindi'")
                return null
            }

            val olChiki = tokenizer.decode(decoded.tokenIds)
            if (olChiki.isBlank()) {
                Log.w(TAG, "decoded ${decoded.tokenIds.size} tokens but no text for '$hindi'")
                return null
            }

            val deva = OlChikiToDevanagari.transliterate(olChiki)
            if (deva.devanagari.isBlank()) {
                // Returning a blank here would reach the voice as silence and the teacher as an
                // empty line, with the UI still claiming a successful turn.
                Log.w(TAG, "transliteration emptied '$olChiki'")
                return null
            }
            lastApproximation = buildString {
                if (deva.unmapped.isNotEmpty()) {
                    append("dropped ${deva.unmapped.map { "U+%04X".format(it.code) }}")
                }
                if (deva.approximated.isNotEmpty()) {
                    if (isNotEmpty()) append("; ")
                    append("${deva.approximated.size} approximated")
                }
            }.ifEmpty { null }

            Log.d(
                TAG,
                "'$hindi' -> '$olChiki' (${decoded.totalMs}ms, ${decoded.steps} tokens)" +
                    (lastApproximation?.let { " [$it]" } ?: ""),
            )
            MtOutput(targetTextNative = olChiki, targetTextDeva = deva.devanagari)
        } catch (e: Throwable) {
            // Throwable, not Exception: ONNX Runtime surfaces problems as OrtException but a
            // missing or mismatched native library arrives as UnsatisfiedLinkError, and neither
            // may take the process down in front of a class. A null degrades to a named reason.
            Log.e(TAG, "translate failed", e)
            null
        }
    }

    override fun close() {
        runCatching { decoder.close() }
    }

    companion object {
        private const val TAG = "IndicTrans2Mt"

        /**
         * Builds the engine for a language, or returns null when its files are not on the tablet.
         *
         * Null rather than throwing, because a missing model is an ordinary state on a device that
         * receives packs over USB — `LiveTurnEngine` treats a null engine as "no T1" and degrades
         * to the phrasebook, which is a working app rather than a crash.
         */
        fun loadOrNull(
            store: ModelStore,
            language: TargetLanguage,
            threads: Int = IndicTrans2Decoder.DEFAULT_THREADS,
        ): IndicTrans2MtEngine? {
            val encoder = store.mtEncoder(language) ?: return null
            val decoderFile = store.mtDecoderMerged(language) ?: return null
            val table = store.mtSpmTable(language) ?: return null
            if (!encoder.isFile || !decoderFile.isFile || !table.isFile) {
                Log.i(TAG, "${language.englishName} MT files not present under ${encoder.parent}")
                return null
            }
            return try {
                val tok = IndicTrans2Tokenizer.load(table)
                Log.i(TAG, "tokenizer: ${tok.stats}")
                val dec = IndicTrans2Decoder(
                    encoderPath = encoder.absolutePath,
                    decoderMergedPath = decoderFile.absolutePath,
                    numThreads = threads,
                )
                IndicTrans2MtEngine(tok, dec)
            } catch (e: Throwable) {
                Log.e(TAG, "${language.englishName} MT load failed", e)
                null
            }
        }
    }
}
