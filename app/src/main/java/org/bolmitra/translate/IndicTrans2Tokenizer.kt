package org.bolmitra.translate

import java.io.File
import java.text.Normalizer

/**
 * Text ↔ token ids for IndicTrans2, without SentencePiece.
 *
 * ### Why this is hand-written
 *
 * [IndicTrans2Decoder] consumes token ids and produces token ids. Nothing on Android can produce
 * them: there is no SentencePiece build for the platform (its JNI wrapper's own tracker rules
 * Android out), and NDK-building SentencePiece plus a protobuf parser is far more work than the
 * algorithm itself. So `tools/export-spm-table.py` flattens the model's tables into one TSV, and
 * this class implements the encoder against it.
 *
 * ### It is BPE, not unigram
 *
 * Stated plainly because the obvious assumption is wrong: `trainer_spec.model_type` is **BPE**.
 * A unigram Viterbi — maximise summed piece score over all segmentations — would produce different
 * splits that look entirely reasonable and are not what the model was trained on. The algorithm is:
 *
 * 1. normalise, collapse whitespace, escape spaces to [SPACE] and prepend one
 * 2. explode into one symbol per **code point**
 * 3. repeatedly merge the adjacent pair whose concatenation is in the merge vocabulary and has the
 *    best score, until no adjacent pair is mergeable
 * 4. map surviving pieces to vocabulary ids, `<unk>` where absent
 *
 * The scores in this model are exactly `-(sp_id - 5)`, i.e. negative rank, which is how
 * SentencePiece stores BPE merge priority. Nothing here depends on them being log-probabilities.
 *
 * ### Two id spaces, which must not be conflated
 *
 * `src.model` holds 128000 pieces under its own internal ids; the fairseq dictionaries map piece
 * *strings* to the model's 122706/122672-entry vocabulary. Those are different numbering schemes.
 * The route is piece-string → vocabulary id. Using SentencePiece's own ids would index the wrong
 * embedding rows and yield fluent output about something unrelated.
 *
 * ### The language tags are not text
 *
 * `hin_Deva` (id 8) and `sat_Olck` (id 29925) are whole vocabulary entries. Running them through
 * BPE shreds them into `▁hin, _, D, e, va, …`. They are prepended as ids, and the table marks them
 * as non-mergeable so no ordinary text can ever merge into them.
 *
 * ### Do not feed this `HindiNormalizer` output
 *
 * [org.bolmitra.phrasebook.HindiNormalizer] is built for phrasebook matching and is actively wrong
 * as MT input: it strips danda, spells digits as Hindi words and drops honorifics. IndicTrans2 is
 * trained on sentence-terminated text with real numerals. Pass the raw ASR transcript.
 *
 * ### Fidelity, and its one known ceiling
 *
 * `IndicTrans2TokenizerTest` checks this against SentencePiece's own output over a deliberately
 * adversarial corpus, because the failure mode here is silent: the model card is explicit that bad
 * preprocessing yields fluent text in the *wrong language* rather than an error.
 *
 * `ponytail:` the normaliser is NFKC plus whitespace collapse, not SentencePiece's `nmt_nfkc`,
 * whose 237 KB precompiled charsmap differs from NFKC for a handful of C0 control characters.
 * Speech recognition does not emit control characters, so the gap is unreachable from the app's
 * only text source. Upgrade path if it ever matters: export the charsmap alongside the table and
 * apply it as a trie.
 */
class IndicTrans2Tokenizer private constructor(
    /**
     * Piece → merge score, source id and target id, packed into one Long.
     *
     * One map rather than three, because the obvious version cost too much. Three
     * `HashMap<String, Int>` over ~130k pieces measured **+75.7 MB of Java heap** on device: three
     * node arrays, three sets of `HashMap.Node` objects and ~380k boxed `Integer`s for what is
     * really one record per piece. Packing them into a single map measured **+48.7 MB**, so this
     * is worth 27 MB.
     *
     * That matters because the MT tier already holds ~406 MB of native memory, and a budget
     * tablet's per-app Java heap limit is often 128–256 MB. 76 MB of it spent on a lookup table is
     * the kind of thing that turns into an OutOfMemoryError on the device nobody tested.
     *
     * `ponytail:` the remaining ~49 MB is mostly the 130k piece `String`s themselves and their
     * backing char arrays, which no amount of value packing touches. Ceiling accepted for now.
     * Upgrade path if the heap ever becomes the binding constraint: store all pieces end-to-end in
     * one `CharArray` with an offset index, or replace the map with a trie keyed on chars — both
     * are real work and neither is justified until a device actually runs out.
     *
     * Layout, low bits first: srcId+1 in 18 bits, tgtId+1 in 18, score+[SCORE_BIAS] in 19.
     * Zero means absent in every field, which is why each is stored offset by one — piece id 0 is
     * a real id (`<s>`), and a plain 0 would be indistinguishable from "not present".
     */
    private val table: HashMap<String, Long>,
    /**
     * Target vocabulary id → piece, as a flat array rather than a map.
     *
     * Ids are dense and bounded by the vocabulary size, so an array indexes directly with no
     * boxing and no nodes — about 1 MB of references into Strings the [table] already holds,
     * against roughly 12 MB for the equivalent `HashMap<Int, String>`.
     */
    private val tgtPiece: Array<String?>,
    /**
     * Load summary for the caller to log.
     *
     * Deliberately returned rather than logged here. This class touches no Android API — no
     * `android.util.Log`, no `Context` — which is what lets the whole tokenizer run in a plain JVM
     * unit test. `Log` is a throwing stub off-device, so one call would have made every parity test
     * unrunnable without loosening `unitTests.returnDefaultValues`, and that setting hides real
     * mistakes across the entire suite.
     */
    val stats: String,
) {

    /**
     * Encodes one sentence into the exact id sequence the model expects.
     *
     * @return `[srcTagId, tgtTagId, …pieces…, EOS]`
     */
    fun encode(text: String, srcTag: String = HIN_DEVA, tgtTag: String = SAT_OLCK): LongArray {
        val src = srcIdOf(srcTag)
        require(src >= 0) { "unknown source language tag '$srcTag'" }
        val tgt = srcIdOf(tgtTag)
        require(tgt >= 0) { "unknown target language tag '$tgtTag'" }
        val unk = srcIdOf(UNK)

        val pieces = encodeToPieces(text)
        val out = LongArray(pieces.size + 3)
        out[0] = src.toLong()
        out[1] = tgt.toLong()
        for (i in pieces.indices) {
            val id = srcIdOf(pieces[i])
            out[i + 2] = (if (id >= 0) id else unk).toLong()
        }
        out[out.size - 1] = EOS.toLong()
        return out
    }

    /** Source vocabulary id for a piece, or -1 if it has none. */
    private fun srcIdOf(piece: String): Int {
        val packed = table[piece] ?: return -1
        val v = (packed and SRC_MASK).toInt()
        return v - 1
    }

    /** Merge score for a piece, or null if it must never be merged into. */
    private fun scoreOf(piece: String): Int? {
        val packed = table[piece] ?: return null
        val v = ((packed ushr SCORE_SHIFT) and SCORE_MASK).toInt()
        return if (v == 0) null else v - SCORE_BIAS
    }

    /** The BPE pieces for [text], for tests and diagnostics. */
    fun encodeToPieces(text: String): List<String> {
        val normalized = normalize(text)
        // SentencePiece emits nothing at all for empty or whitespace-only input — notably NOT a
        // lone dummy prefix. Returning one would put a stray token in front of every empty turn.
        if (normalized.isEmpty()) return emptyList()
        return bpe(normalized)
    }

    /**
     * Decodes target-side ids back to text.
     *
     * Mirrors `batch_decode(skip_special_tokens=True)`: specials are dropped, [SPACE] becomes a
     * space, and the leading space from the dummy prefix is trimmed. The caller still owes the
     * equivalent of IndicProcessor's `postprocess_batch` for entity restoration.
     */
    fun decode(ids: IntArray): String {
        val sb = StringBuilder()
        for (id in ids) {
            // Bounds-checked rather than trusted: these ids come from an argmax over the decoder's
            // logits, and a graph mismatch could put one outside the vocabulary.
            if (id < 0 || id >= tgtPiece.size) continue
            val piece = tgtPiece[id] ?: continue
            if (piece in SPECIALS) continue
            sb.append(piece)
        }
        return sb.toString().replace(SPACE, ' ').trim()
    }

    // ---- normalisation ----------------------------------------------------------------------

    /**
     * NFKC, then SentencePiece's `nmt_nfkc` character rules, then whitespace collapse and space
     * escaping with a dummy prefix.
     *
     * NFKC runs first because it already folds several of these cases the same way SentencePiece
     * does — U+00A0 and U+3000 both become a plain space — and because it is what handles
     * ligatures, fullwidth forms, circled digits and nukta composition. What it does *not* do is
     * the [isDiscarded] and [isSpaceLike] sets below, which come from SentencePiece's charsmap.
     *
     * Collapsing before escaping is what makes runs of spaces, tabs and newlines behave
     * identically; escaping first would leave `▁▁▁` runs that match no piece, degrading every
     * multi-space input into single characters.
     */
    internal fun normalize(text: String): String {
        val nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC)
        val sb = StringBuilder(nfkc.length + 1)
        var pendingSpace = false
        var wrote = false
        for (ch in nfkc) {
            val cp = ch.code
            if (isDiscarded(cp)) continue
            if (isSpaceLike(cp)) {
                // Deferred rather than appended, so trailing whitespace never reaches the output
                // and interior runs collapse to one.
                if (wrote) pendingSpace = true
                continue
            }
            if (pendingSpace) {
                sb.append(SPACE)
                pendingSpace = false
            }
            sb.append(ch)
            wrote = true
        }
        if (!wrote) return ""
        return SPACE + sb.toString()
    }

    // ---- BPE --------------------------------------------------------------------------------

    private fun bpe(normalized: String): List<String> {
        // Split by CODE POINT, not by Char. Kotlin strings are UTF-16, so an emoji or any
        // astral-plane character is two chars; splitting on chars would hand BPE half a surrogate
        // pair, which matches nothing and cannot be reassembled.
        val symbols = ArrayList<String>(normalized.length)
        var i = 0
        while (i < normalized.length) {
            val cp = normalized.codePointAt(i)
            val n = Character.charCount(cp)
            symbols.add(normalized.substring(i, i + n))
            i += n
        }

        // `ponytail:` rescans every adjacent pair after each merge — O(n²) over one utterance,
        // where n is the character count of something a teacher said in one breath. That is a few
        // thousand hash lookups against a decoder step costing ~150 ms, so SentencePiece's
        // priority queue would add state for no measurable gain. Ceiling: if this is ever used on
        // paragraph-length input, switch to a heap keyed on (score, position).
        while (symbols.size > 1) {
            var bestAt = -1
            var bestScore = Int.MIN_VALUE
            for (j in 0 until symbols.size - 1) {
                val score = scoreOf(symbols[j] + symbols[j + 1]) ?: continue
                // Strictly greater, so on equal scores the leftmost pair wins — matching
                // SentencePiece, whose queue breaks ties by position.
                if (score > bestScore) {
                    bestScore = score
                    bestAt = j
                }
            }
            if (bestAt < 0) break
            symbols[bestAt] = symbols[bestAt] + symbols[bestAt + 1]
            symbols.removeAt(bestAt + 1)
        }
        return symbols
    }

    companion object {
        /** U+2581 LOWER ONE EIGHTH BLOCK — SentencePiece's escaped space. */
        const val SPACE = '\u2581'

        const val HIN_DEVA = "hin_Deva"
        const val SAT_OLCK = "sat_Olck"
        const val UNK = "<unk>"
        const val EOS = 2

        private val SPECIALS = setOf("<s>", "</s>", "<pad>", UNK)

        /** Marks a table row as id-only: present in the vocabulary but never merged into. */
        private const val NO_MERGE = "-"

        // Packed record layout. 18 + 18 + 19 = 55 bits, comfortably inside a Long. Every field is
        // stored offset so that zero unambiguously means "absent" — vocabulary id 0 is real (`<s>`)
        // and merge score 0 is real, so neither could otherwise be told apart from missing.
        private const val SRC_MASK = 0x3FFFFL          // 18 bits
        private const val TGT_SHIFT = 18
        private const val TGT_MASK = 0x3FFFFL          // 18 bits
        private const val SCORE_SHIFT = 36
        private const val SCORE_MASK = 0x7FFFFL        // 19 bits

        /**
         * Added to merge scores before packing. Scores are negative rank, running from 0 down to
         * about -128000, so this shifts them into a positive range while leaving 0 free as the
         * "not mergeable" sentinel.
         */
        private const val SCORE_BIAS = 1 shl 18

        /**
         * Characters `nmt_nfkc` deletes outright.
         *
         * Measured by probing SentencePiece, not read off its charsmap — see
         * `tools/_probechars.py` in the commit that added this. The exact membership is
         * surprising and cannot be replaced with a category test:
         *
         * - **U+0000 is KEPT** while U+0001..U+0008 are deleted. So "all C0 controls" is wrong.
         * - U+0080..U+008E and U+0090..U+009E are kept, but U+008F and U+009F are deleted.
         * - U+00AD SOFT HYPHEN is kept, though most normalisers strip it.
         *
         * `Char.isWhitespace()` cannot be used for the space set either, because Java and
         * SentencePiece disagree in both directions: Java calls U+000B and U+001C..U+001F
         * whitespace where SentencePiece deletes them, and Java says U+00A0 and U+202F are not
         * whitespace where SentencePiece maps them to a space. Using it produced two mismatches
         * against the parity corpus.
         */
        private fun isDiscarded(cp: Int): Boolean =
            cp in 0x0001..0x0008 || cp == 0x000B || cp in 0x000E..0x001F ||
                cp == 0x007F || cp == 0x008F || cp == 0x009F

        /**
         * Characters `nmt_nfkc` turns into a space.
         *
         * The consequential entry for Indic text is **U+200C ZERO WIDTH NON-JOINER, which becomes
         * a space, while U+200D ZERO WIDTH JOINER is kept**. They are adjacent codepoints that
         * look interchangeable and are not. ZWNJ appears in real Devanagari, so getting this
         * backwards would silently split words at conjunct boundaries.
         */
        private fun isSpaceLike(cp: Int): Boolean =
            cp == 0x0009 || cp == 0x000A || cp == 0x000C || cp == 0x000D || cp == 0x0020 ||
                cp == 0x00A0 || cp == 0x1680 || cp in 0x2000..0x200C ||
                cp in 0x200E..0x200F || cp == 0x2028 || cp == 0x2029 ||
                cp == 0x202F || cp == 0x205F || cp == 0x3000 || cp == 0xFEFF

        /**
         * Loads the flattened table written by `tools/export-spm-table.py`.
         *
         * Parsed by hand with `indexOf` rather than `split`: the file has ~130k rows and `split`
         * would allocate an array plus four substrings for every one of them during app start.
         */
        fun load(table: File): IndicTrans2Tokenizer {
            require(table.isFile) {
                "missing ${table.name}. Generate it with tools/export-spm-table.py and push it " +
                    "with the model pack."
            }
            val packed = HashMap<String, Long>(1 shl 18)
            // Collected first, then sized exactly, because the vocabulary bound is not known until
            // the file has been read and over-allocating a fixed 128k array wastes what this
            // packing just saved.
            val tgtIds = ArrayList<Int>(1 shl 17)
            val tgtPieces = ArrayList<String>(1 shl 17)
            var rows = 0
            var mergeable = 0
            var srcCount = 0

            table.bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (line.isEmpty()) continue
                    val t1 = line.indexOf('\t')
                    val t2 = line.indexOf('\t', t1 + 1)
                    val t3 = line.indexOf('\t', t2 + 1)
                    if (t1 < 0 || t2 < 0 || t3 < 0) {
                        throw IllegalStateException("malformed row ${rows + 1} in ${table.name}")
                    }
                    val piece = line.substring(0, t1)
                    val scoreText = line.substring(t1 + 1, t2)
                    val s = line.substring(t2 + 1, t3).toInt()
                    val g = line.substring(t3 + 1).toInt()

                    var bits = 0L
                    if (s >= 0) {
                        require(s + 1 <= SRC_MASK) { "source id $s exceeds the packed field" }
                        bits = bits or (s + 1).toLong()
                        srcCount++
                    }
                    if (g >= 0) {
                        require(g + 1 <= TGT_MASK) { "target id $g exceeds the packed field" }
                        bits = bits or ((g + 1).toLong() shl TGT_SHIFT)
                        tgtIds += g
                        tgtPieces += piece
                    }
                    if (scoreText != NO_MERGE) {
                        val biased = scoreText.toInt() + SCORE_BIAS
                        require(biased in 1..SCORE_MASK.toInt()) {
                            "merge score $scoreText is outside the packed range"
                        }
                        bits = bits or (biased.toLong() shl SCORE_SHIFT)
                        mergeable++
                    }
                    packed[piece] = bits
                    rows++
                }
            }

            val tgt = arrayOfNulls<String>((tgtIds.maxOrNull() ?: -1) + 1)
            for (i in tgtIds.indices) {
                // First writer wins: were two pieces ever to claim one target id, decoding should
                // stay deterministic rather than depend on file order.
                if (tgt[tgtIds[i]] == null) tgt[tgtIds[i]] = tgtPieces[i]
            }

            val self = IndicTrans2Tokenizer(
                packed, tgt,
                stats = "$rows rows: $mergeable mergeable, $srcCount src, ${tgtIds.size} tgt",
            )
            require(self.srcIdOf(UNK) >= 0) { "${table.name} has no $UNK entry" }
            require(self.srcIdOf(HIN_DEVA) >= 0 && self.srcIdOf(SAT_OLCK) >= 0) {
                "${table.name} is missing a language tag entry"
            }
            require(self.scoreOf(HIN_DEVA) == null) {
                "$HIN_DEVA is mergeable in ${table.name}; language tags must be id-only or text " +
                    "could merge into them"
            }
            return self
        }
    }
}
