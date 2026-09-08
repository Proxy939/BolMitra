package org.bolmitra.translate

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.os.SystemClock
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Greedy seq2seq decode over the three int8 IndicTrans2 ONNX graphs — the T1 tier of §4.5.
 *
 * ### Why this class exists at all
 *
 * sherpa-onnx cannot run an encoder-decoder translation model. Listing the AAR shows 128 classes,
 * none under `ai/onnxruntime` and none for seq2seq or tokenisation, so there is no config object to
 * hand it — the MT tier needs its own ONNX Runtime. CTranslate2, which AI4Bharat's own docs point
 * at, has no Android build target and no JVM bindings, and its converter does not recognise the
 * IndicTrans architecture, so it was ruled out before any code was written.
 *
 * ### What this class deliberately does NOT do
 *
 * **It does not tokenise.** It takes token ids and returns token ids. That is not an oversight and
 * not a stub: there is no SentencePiece for Android (the JNI wrapper's own issue tracker rules it
 * out) so a tokenizer means writing unigram Viterbi in Kotlin, and that work is only worth paying
 * for if the decode loop fits inside R3 on real hardware. This class is what answers that
 * question. [Indictrans2SpikeHarness] feeds it ids precomputed by `tools/mt-decode-raw.py`.
 *
 * So this is not yet an [MtEngine]. It is the half of one whose cost was unknown.
 *
 * ### The graph contract
 *
 * Dumped by `tools/mt-graph-io.py`; 18 layers, 8 heads, 64 per head.
 *
 * ```
 * encoder.onnx           in  input_ids[B,S] i64, attention_mask[B,S] i64
 *                        out last_hidden_state[B,S,512] f32
 *
 * decoder_merged.onnx    in  encoder_attention_mask[B,S], input_ids[B,T],
 *                            encoder_hidden_states[B,S,512],
 *                            72 past_key_values.{0..17}.{decoder,encoder}.{key,value},
 *                            use_cache_branch[1] bool
 *                        out logits[B,T,122672]
 *                            + 72 present.{0..17}.{decoder,encoder}.{key,value}
 * ```
 *
 * Three traps, all silent:
 *
 * 1. **Every** past input must be bound on the priming step too, as a zero-length tensor. They
 *    are top-level graph inputs, so ONNX Runtime requires all of them regardless of which branch
 *    `use_cache_branch` selects. Same for `encoder_hidden_states` on later steps, which the
 *    with-past branch never reads.
 * 2. The encoder KV halves are constant for the whole utterance. The with-past branch only passes
 *    them through, so they are captured once at step 0 and carried forward. Re-feeding stale or
 *    zeroed encoder KV still produces fluent output; it is just unrelated to the input.
 * 3. `decoderStartTokenId` and `eosTokenId` are **the same value**, 2. Testing `token == EOS`
 *    without excluding the priming step returns an empty translation every time.
 *
 * ### Parity with the desktop reference is the whole point
 *
 * Every decoding decision here mirrors `tools/mt-decode-raw.py` — including the order the two
 * logit processors run in and the fact that both see the priming token. Divergence is a bug, and
 * [Indictrans2SpikeHarness] checks it against fixture ids rather than trusting that it looks fine.
 */
class IndicTrans2Decoder(
    encoderPath: String,
    decoderMergedPath: String,
    private val numThreads: Int = DEFAULT_THREADS,
) : AutoCloseable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()

    private fun options() = OrtSession.SessionOptions().apply {
        // Budget tablets in this class have four usable cores; taking more than that on a
        // foreground turn starves the UI thread and the audio callback.
        setIntraOpNumThreads(numThreads)
        setInterOpNumThreads(1)
    }

    private val encoder: OrtSession = env.createSession(encoderPath, options())

    /**
     * One decoder session, not two.
     *
     * The export ships the decoder twice — once without cache support for the first step and once
     * with, 194 MB and 185 MB of the same weights. Loading both put 184 MB of duplicate weights in
     * memory, measured as part of a 628 MB native heap for MT alone. `tools/mt-merge-decoder.py`
     * fuses them into one graph whose two branches share a single copy of the weights, selected at
     * runtime by [USE_CACHE_BRANCH]. Verified byte-identical: all 11 eval sentences produce exactly
     * the same token ids through the fused graph as through the pair.
     */
    private val decoder: OrtSession = env.createSession(decoderMergedPath, options())

    /**
     * Layer count read from the graph rather than hardcoded to 18.
     *
     * A wrong count here would build a partial KV cache, and a partial cache does not throw — the
     * layers that got no past silently attend to nothing and the output degrades in a way that
     * reads as a bad model.
     */
    private val layerCount: Int = decoder.outputNames
        .count { it.matches(Regex("""present\.\d+\.decoder\.key""")) }

    /** Cache keys in a fixed order, built once. Names must match the graph inputs exactly. */
    private val pastNames: List<String> = buildList {
        for (i in 0 until layerCount) {
            for (side in listOf("decoder", "encoder")) {
                for (kv in listOf("key", "value")) add("past_key_values.$i.$side.$kv")
            }
        }
    }

    init {
        require(layerCount > 0) {
            "No present.N.decoder.key outputs in $decoderMergedPath — wrong graph?"
        }
        // The fused graph is identified by this input. Without it we would be looking at the old
        // unfused decoder.onnx, which silently ignores the cache and re-runs the full prefix every
        // step — correct output, but quadratic cost and no memory saving.
        require(decoder.inputNames.contains(USE_CACHE_BRANCH)) {
            "$decoderMergedPath has no $USE_CACHE_BRANCH input, so it is not a fused decoder. " +
                "Produce it with tools/mt-merge-decoder.py."
        }
        Log.i(TAG, "loaded: $layerCount layers, fused decoder, $numThreads threads")
    }

    data class Decoded(
        val tokenIds: IntArray,
        val encoderMs: Long,
        val decodeMs: Long,
    ) {
        val totalMs: Long get() = encoderMs + decodeMs
        val steps: Int get() = tokenIds.size

        // Data class with an array member: generated equals/hashCode compare by identity, which
        // makes them quietly useless in a test assertion.
        override fun equals(other: Any?): Boolean =
            this === other || (
                other is Decoded && tokenIds.contentEquals(other.tokenIds) &&
                    encoderMs == other.encoderMs && decodeMs == other.decodeMs
                )

        override fun hashCode(): Int =
            (tokenIds.contentHashCode() * 31 + encoderMs.hashCode()) * 31 + decodeMs.hashCode()
    }

    /**
     * Translates one already-tokenised utterance. Batch size is fixed at 1.
     *
     * @param inputIds source token ids, IndicProcessor-preprocessed and SentencePiece-encoded,
     *   including the two language tags that IndicTrans2 requires as a prefix.
     * @param attentionMask same length as [inputIds]; all ones for a single unpadded sentence.
     * @return generated target token ids, excluding the priming token and the terminating EOS.
     */
    fun decode(
        inputIds: LongArray,
        attentionMask: LongArray,
        maxNewTokens: Int = MAX_NEW_TOKENS,
    ): Decoded {
        require(inputIds.size == attentionMask.size) {
            "inputIds (${inputIds.size}) and attentionMask (${attentionMask.size}) differ"
        }
        require(inputIds.isNotEmpty()) { "inputIds is empty" }

        val srcShape = longArrayOf(1, inputIds.size.toLong())
        // Everything allocated here is closed in reverse order by this list, including on the
        // failure path. Leaking an OnnxTensor leaks native memory that no GC will reclaim, and a
        // process-scoped engine would accumulate it turn after turn.
        val scope = mutableListOf<AutoCloseable>()

        try {
            val srcIds = tensor(inputIds, srcShape).also(scope::add)
            val srcMask = tensor(attentionMask, srcShape).also(scope::add)

            val encStart = SystemClock.elapsedRealtime()
            val encResult = encoder.run(mapOf("input_ids" to srcIds, "attention_mask" to srcMask))
                .also(scope::add)
            val hidden = encResult["last_hidden_state"].get() as OnnxTensor
            val encoderMs = SystemClock.elapsedRealtime() - encStart

            val decodeStart = SystemClock.elapsedRealtime()

            // ---- step 0: no-cache branch, primed with decoderStartTokenId -------------------
            val primeIds = tensor(longArrayOf(DECODER_START.toLong()), longArrayOf(1, 1))
                .also(scope::add)
            val noCache = boolTensor(false).also(scope::add)
            val useCache = boolTensor(true).also(scope::add)

            val first = decoder.run(
                buildMap<String, OnnxTensor> {
                    put("encoder_attention_mask", srcMask)
                    put("input_ids", primeIds)
                    put("encoder_hidden_states", hidden)
                    // The fused graph declares all 72 past inputs at the top level, so every one
                    // must be bound even on the branch that does not read them. Zero-length
                    // tensors are the conventional filler; use_cache_branch is what selects the
                    // branch.
                    for (name in pastNames) put(name, emptyPast().also(scope::add))
                    put(USE_CACHE_BRANCH, noCache)
                },
            )

            var logits: FloatArray
            val past = HashMap<String, OnnxTensor>(pastNames.size)
            try {
                logits = lastStepLogits(first["logits"].get() as OnnxTensor)
                for (name in pastNames) {
                    val present = name.removePrefix("past_key_values.")
                    past[name] = copyOf(first["present.$present"].get() as OnnxTensor)
                }
            } finally {
                // Safe to release now: copyOf took ownership of its own buffers above.
                first.close()
            }

            val generated = ArrayList<Int>(maxNewTokens)
            try {
                for (step in 0 until maxNewTokens) {
                    // Order matches tools/mt-decode-raw.py exactly: repetition penalty, then the
                    // n-gram ban, then the pad veto. Both processors see the priming token, as
                    // HuggingFace's do, because their decoder input array starts with it. Leaving
                    // it out means EOS is never penalised and output stops short.
                    applyRepetitionPenalty(logits, DECODER_START, generated)
                    for (banned in bannedByNgram(DECODER_START, generated)) {
                        logits[banned] = Float.NEGATIVE_INFINITY
                    }
                    // Pad is never a legitimate emission at batch size 1.
                    logits[PAD] = Float.NEGATIVE_INFINITY

                    val next = argmax(logits)
                    if (next == EOS) break
                    generated.add(next)

                    val stepIds = tensor(longArrayOf(next.toLong()), longArrayOf(1, 1))
                    val res = try {
                        decoder.run(
                            HashMap<String, OnnxTensor>(past).apply {
                                put("encoder_attention_mask", srcMask)
                                put("input_ids", stepIds)
                                // Also a top-level input of the fused graph and so mandatory on
                                // every step, even though the with-past branch never reads it.
                                // Rebinding the same tensor costs nothing.
                                put("encoder_hidden_states", hidden)
                                put(USE_CACHE_BRANCH, useCache)
                            },
                        )
                    } finally {
                        stepIds.close()
                    }
                    try {
                        logits = lastStepLogits(res["logits"].get() as OnnxTensor)
                        // Only the decoder halves are taken. The fused graph re-emits all 72
                        // presents, but the encoder halves on the with-past branch are just a
                        // pass-through of what we fed in, so keeping the step-0 values is both
                        // cheaper and safer than trusting that pass-through.
                        for (i in 0 until layerCount) {
                            for (kv in listOf("key", "value")) {
                                val key = "past_key_values.$i.decoder.$kv"
                                val fresh = copyOf(res["present.$i.decoder.$kv"].get() as OnnxTensor)
                                past.remove(key)?.close()
                                past[key] = fresh
                            }
                        }
                    } finally {
                        res.close()
                    }
                }
            } finally {
                past.values.forEach { runCatching { it.close() } }
            }

            return Decoded(
                tokenIds = generated.toIntArray(),
                encoderMs = encoderMs,
                decodeMs = SystemClock.elapsedRealtime() - decodeStart,
            )
        } finally {
            scope.asReversed().forEach { runCatching { it.close() } }
        }
    }

    // ---- tensor helpers ---------------------------------------------------------------------

    private fun tensor(data: LongArray, shape: LongArray): OnnxTensor {
        val buf = ByteBuffer.allocateDirect(data.size * Long.SIZE_BYTES)
            .order(ByteOrder.nativeOrder()).asLongBuffer()
        buf.put(data).rewind()
        return OnnxTensor.createTensor(env, buf, shape)
    }

    private fun boolTensor(value: Boolean): OnnxTensor =
        OnnxTensor.createTensor(env, booleanArrayOf(value))

    /** Zero-length KV placeholder for the no-cache branch: [1, heads, 0, headDim]. */
    private fun emptyPast(): OnnxTensor {
        val buf = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder()).asFloatBuffer()
        return OnnxTensor.createTensor(env, buf, longArrayOf(1, N_HEADS, 0, HEAD_DIM))
    }

    /**
     * Takes an independent copy of a graph output so the owning result can be closed.
     *
     * The alternative — holding every previous result alive to keep its buffers valid — grows
     * native memory for the whole utterance and makes lifetime depend on decode length. Copying
     * is about 1 MB per step here, which is far below the cost of the step that produced it.
     */
    private fun copyOf(t: OnnxTensor): OnnxTensor {
        val src: FloatBuffer = t.floatBuffer
        val dst = ByteBuffer.allocateDirect(src.remaining() * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        dst.put(src).rewind()
        return OnnxTensor.createTensor(env, dst, t.info.shape)
    }

    /**
     * Logits for the final position only, as a flat array.
     *
     * Read through the FloatBuffer rather than `getValue()`: the latter materialises a
     * `float[1][T][122672]` Java array, which is ~490 KB of copying per step plus the object
     * graph, on the hot path of the one stage whose latency this whole exercise is measuring.
     */
    private fun lastStepLogits(t: OnnxTensor): FloatArray {
        val shape = t.info.shape
        val vocab = shape[shape.size - 1].toInt()
        val steps = shape[shape.size - 2].toInt()
        val buf = t.floatBuffer
        val out = FloatArray(vocab)
        buf.position((steps - 1) * vocab)
        buf.get(out)
        return out
    }

    // ---- logit processors, mirroring tools/mt-decode-raw.py ---------------------------------

    /**
     * HuggingFace semantics: positive logits are divided, negative ones multiplied.
     *
     * The sign test is load-bearing. A flat `logits[id] /= penalty` moves negative logits toward
     * zero, i.e. makes already-unlikely repeats MORE likely — the opposite of the intent, and
     * invisible on short outputs.
     */
    private fun applyRepetitionPenalty(logits: FloatArray, primer: Int, generated: List<Int>) {
        if (REPETITION_PENALTY == 1.0f) return
        val seen = HashSet<Int>(generated.size + 1)
        seen.add(primer)
        seen.addAll(generated)
        for (id in seen) {
            val v = logits[id]
            logits[id] = if (v > 0f) v / REPETITION_PENALTY else v * REPETITION_PENALTY
        }
    }

    /**
     * Token ids that would complete a repeat of an n-gram already in the history.
     *
     * This is the setting that stopped the repetition loops in the desktop sweep — without it the
     * 320M degenerates on precisely the longer open-domain sentences T1 exists to handle, so it is
     * not optional tuning.
     *
     * `ponytail:` rebuilds the n-gram map every step, O(T²) over T ≤ 64. That is a few thousand
     * int-pair hashes against a decoder step costing milliseconds, so an incremental map is not
     * worth the extra state. Same choice as the Python reference.
     */
    private fun bannedByNgram(primer: Int, generated: List<Int>): List<Int> {
        val n = NO_REPEAT_NGRAM
        val history = ArrayList<Int>(generated.size + 1).apply { add(primer); addAll(generated) }
        if (history.size < n) return emptyList()
        val seen = HashMap<List<Int>, MutableSet<Int>>()
        for (i in 0..history.size - n) {
            seen.getOrPut(history.subList(i, i + n - 1).toList()) { HashSet() }
                .add(history[i + n - 1])
        }
        val tail = history.subList(history.size - (n - 1), history.size).toList()
        return seen[tail]?.toList() ?: emptyList()
    }

    private fun argmax(logits: FloatArray): Int {
        var best = 0
        var bestVal = logits[0]
        for (i in 1 until logits.size) {
            if (logits[i] > bestVal) {
                bestVal = logits[i]
                best = i
            }
        }
        return best
    }

    override fun close() {
        runCatching { encoder.close() }
        runCatching { decoder.close() }
    }

    companion object {
        private const val TAG = "IndicTrans2"

        /** Selects the fused decoder's branch: false primes without a cache, true steps with one. */
        const val USE_CACHE_BRANCH = "use_cache_branch"

        /** Shapes only, for the zero-length cache placeholders. Layer count is read from the graph. */
        private const val N_HEADS = 8L
        private const val HEAD_DIM = 64L

        /** From the export's generation_config.json. Start and EOS are the same id — see docs. */
        const val DECODER_START = 2
        const val EOS = 2
        const val PAD = 1

        /** Must stay in step with tools/mt-decode-raw.py or the fixture check is meaningless. */
        const val MAX_NEW_TOKENS = 64
        const val NO_REPEAT_NGRAM = 3
        const val REPETITION_PENALTY = 1.15f

        const val DEFAULT_THREADS = 4
    }
}
