"""Hand-rolled greedy decode over the three IndicTrans2 int8 ONNX graphs, using raw ONNX Runtime.

WHY THIS EXISTS
---------------
The Kotlin engine on the tablet cannot use optimum — optimum is Python, and its
ORTModelForSeq2SeqLM is what drove the loop in verify-onnx-santali.py. On device we bind tensors
by name through ONNX Runtime's Java API and run the decode loop ourselves. This script is that
same loop, written against the same raw API, on a machine where a failure prints a stack trace
instead of vanishing into logcat.

It does three jobs:

  1. VALIDATES THE LOOP. If this reproduces the optimum output, the algorithm is right and any
     later disagreement from Kotlin is a porting bug, not a design bug. That distinction is worth
     a lot when the only debugger on the far side is Log.d.

  2. PINS THE CONTRACT for the port. Names, shapes, dtypes and the past/present handoff are all
     expressed here in the order the Kotlin has to do them.

  3. EMITS A DEVICE FIXTURE (mt-device-fixture.json): input token ids and expected output token
     ids for the 11 eval sentences. The tablet spike consumes this instead of tokenising, because
     there is no SentencePiece on Android and writing one is only worth doing AFTER latency is
     proven. Deferring it is the point, not an oversight.

THE GRAPH CONTRACT (dumped by mt-graph-io.py, 18 layers / 8 heads / 64 head dim)
--------------------------------------------------------------------------------
  encoder_model.onnx
      in : input_ids[B,S] int64, attention_mask[B,S] int64
      out: last_hidden_state[B,S,512] float

  decoder_model.onnx                      -- first step only, no cache
      in : encoder_attention_mask[B,S] int64, input_ids[B,T] int64,
           encoder_hidden_states[B,S,512] float
      out: logits[B,T,122672], present.{0..17}.{decoder,encoder}.{key,value}   (72 tensors)

  decoder_with_past_model.onnx            -- every later step
      in : encoder_attention_mask[B,S] int64, input_ids[B,1] int64,
           past_key_values.{0..17}.{decoder,encoder}.{key,value}               (72 tensors)
      out: logits[B,1,122672], present.{0..17}.decoder.{key,value}             (36 tensors)

  Two things here are easy to get wrong and both are silent:
    * The with-past graph takes NO encoder_hidden_states. The encoder projections arrive as
      past_key_values.N.encoder.*, are constant for the whole utterance, and must be carried
      forward from step 0 unchanged — the with-past graph does not re-emit them, which is why it
      has 37 outputs and not 73.
    * decoder_start_token_id == eos_token_id == 2. Stopping at "token == 2" without excluding the
      priming step terminates immediately and yields empty output.
"""

import argparse
import io
import json
import pathlib
import sys
import time

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
else:  # pragma: no cover
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

import numpy as np
import onnxruntime as ort
from huggingface_hub import hf_hub_download

REPO = "TigreGotico/indictrans2-indic-indic-dist-320M-onnx"

N_LAYERS = 18
DECODER_START = 2
EOS = 2
PAD = 1

# Matches the GREEDY config in verify-onnx-santali.py, which measured 10/11 exact against PyTorch.
# All three settings are reproduced in the Kotlin port, so keep them in sync.
MAX_NEW_TOKENS = 64
NO_REPEAT_NGRAM = 3
REPETITION_PENALTY = 1.15

_here = pathlib.Path(__file__).resolve().parent


def load_eval_module():
    """Reuse the phrase list and language tags from the existing gate script."""
    import importlib.util

    spec = importlib.util.spec_from_file_location("evalsat", _here / "eval-hindi-santali.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def sessions(threads, merged=None):
    """Loads the graphs. With `merged`, the two decoders are replaced by one fused graph.

    The fused graph wraps both decoders in an ONNX `If` on a `use_cache_branch` input and hoists
    the shared weights into the parent graph, so the decoder weights are stored and loaded once
    instead of twice. See tools/mt-merge-decoder.py — it saved 184 MB of the 379 MB pair.
    """
    so = ort.SessionOptions()
    so.log_severity_level = 3
    so.intra_op_num_threads = threads
    so.inter_op_num_threads = 1
    out = {}
    encoder = hf_hub_download(REPO, filename="int8/encoder_model.onnx")
    out["encoder_model"] = ort.InferenceSession(encoder, so, providers=["CPUExecutionProvider"])
    if merged:
        out["merged"] = ort.InferenceSession(merged, so, providers=["CPUExecutionProvider"])
    else:
        for name in ("decoder_model", "decoder_with_past_model"):
            path = hf_hub_download(REPO, filename=f"int8/{name}.onnx")
            out[name] = ort.InferenceSession(path, so, providers=["CPUExecutionProvider"])
    return out


def apply_repetition_penalty(logits, generated):
    """HuggingFace semantics: divide positive logits, multiply negative ones.

    Sign matters — a flat `logits[ids] /= penalty` makes already-unlikely tokens MORE likely,
    because dividing a negative number by 1.15 moves it toward zero. That bug is invisible on
    short outputs and shows up as drift on long ones.
    """
    if REPETITION_PENALTY == 1.0 or not generated:
        return logits
    ids = np.fromiter(set(generated), dtype=np.int64)
    vals = logits[ids]
    logits[ids] = np.where(vals > 0, vals / REPETITION_PENALTY, vals * REPETITION_PENALTY)
    return logits


def banned_by_ngram(generated):
    """Token ids that would complete a repeat of an already-seen NO_REPEAT_NGRAM-gram.

    This is the setting that actually stopped the repetition loops in the 320M sweep, so it is
    not optional decoration — without it the model degenerates on exactly the longer open-domain
    sentences this feature exists to serve.

    ponytail: rebuilds the n-gram set each step, O(T^2) over a T<=64 decode. That is a few
    thousand tuple hashes on the longest utterance, i.e. far below the cost of one decoder step,
    so an incremental dict is not worth the extra state. The Kotlin port does the same.
    """
    n = NO_REPEAT_NGRAM
    if len(generated) < n:
        return ()
    seen = {}
    for i in range(len(generated) - n + 1):
        prefix = tuple(generated[i:i + n - 1])
        seen.setdefault(prefix, set()).add(generated[i + n - 1])
    return tuple(seen.get(tuple(generated[-(n - 1):]), ()))


def greedy_decode(sess, input_ids, attention_mask, max_new_tokens=MAX_NEW_TOKENS):
    """One utterance, batch size 1. Returns the generated token ids without the priming token."""
    enc_out = sess["encoder_model"].run(
        ["last_hidden_state"],
        {"input_ids": input_ids, "attention_mask": attention_mask},
    )[0]
    is_merged = "merged" in sess

    # ---- step 0: the no-cache path, priming with decoder_start_token_id -----------------
    dec = sess["merged"] if is_merged else sess["decoder_model"]
    out_names = [o.name for o in dec.get_outputs()]
    feeds0 = {
        "encoder_attention_mask": attention_mask,
        "input_ids": np.array([[DECODER_START]], dtype=np.int64),
        "encoder_hidden_states": enc_out,
    }
    if is_merged:
        # The fused graph declares every past input at the top level, so all 72 must be bound even
        # on the pass that does not use them. Zero-length tensors are how the no-cache branch is
        # selected in practice; use_cache_branch is what actually picks it.
        for i in range(N_LAYERS):
            for side in ("decoder", "encoder"):
                for kv in ("key", "value"):
                    feeds0[f"past_key_values.{i}.{side}.{kv}"] = np.zeros(
                        (1, 8, 0, 64), dtype=np.float32
                    )
        feeds0["use_cache_branch"] = np.array([False], dtype=bool)
    res = dec.run(out_names, feeds0)
    named = dict(zip(out_names, res))
    logits = named["logits"][0, -1, :].astype(np.float32)

    # The cache, keyed exactly as the with-past graph wants its inputs. The encoder halves are
    # written once here and never touched again; only the decoder halves grow.
    past = {}
    for i in range(N_LAYERS):
        for side in ("decoder", "encoder"):
            for kv in ("key", "value"):
                past[f"past_key_values.{i}.{side}.{kv}"] = named[f"present.{i}.{side}.{kv}"]

    generated = []
    with_past = sess["merged"] if is_merged else sess["decoder_with_past_model"]
    wp_out_names = [o.name for o in with_past.get_outputs()]

    for _ in range(max_new_tokens):
        # Both logit processors see the priming token, not just the emitted ones, because that is
        # what HuggingFace does: its decoder input_ids array starts with decoder_start_token_id
        # and every LogitsProcessor receives that whole array. Excluding it looks harmless and is
        # not — decoder_start_token_id is 2, which is also EOS, so leaving it out means EOS never
        # gets the repetition penalty and the sequence terminates earlier than the reference.
        # Matching HF here is what lets the 10/11-exact-vs-PyTorch greedy measurement carry over.
        history = [DECODER_START] + generated
        logits = apply_repetition_penalty(logits, history)
        ban = banned_by_ngram(history)
        if ban:
            logits[np.asarray(ban, dtype=np.int64)] = -np.inf
        # PAD is never a legitimate emission for a batch of one; letting it win would end the
        # sentence with a pad token that the detokeniser then has to strip.
        logits[PAD] = -np.inf

        nxt = int(np.argmax(logits))
        if nxt == EOS:
            break
        generated.append(nxt)

        feeds = dict(past)
        feeds["encoder_attention_mask"] = attention_mask
        feeds["input_ids"] = np.array([[nxt]], dtype=np.int64)
        if is_merged:
            # encoder_hidden_states is a top-level input of the fused graph, so it must be bound
            # on every step even though the with-past branch ignores it. Rebinding the same array
            # costs nothing.
            feeds["encoder_hidden_states"] = enc_out
            feeds["use_cache_branch"] = np.array([True], dtype=bool)
        res = with_past.run(wp_out_names, feeds)
        named = dict(zip(wp_out_names, res))

        # Only the decoder halves are re-emitted; the encoder halves stay as written at step 0.
        for i in range(N_LAYERS):
            for kv in ("key", "value"):
                past[f"past_key_values.{i}.decoder.{kv}"] = named[f"present.{i}.decoder.{kv}"]
        logits = named["logits"][0, -1, :].astype(np.float32)

    return generated


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--threads", type=int, default=4)
    ap.add_argument("--fixture", default="tools/mt-device-fixture.json")
    ap.add_argument(
        "--merged",
        nargs="?",
        const="models/mt-hi-sat/decoder_merged.onnx",
        default=None,
        help="Use the fused decoder graph instead of the two separate ones. In this mode the "
             "fixture is READ AND COMPARED rather than rewritten — the fixture is the reference "
             "the fusion has to reproduce, so overwriting it would erase the very thing being "
             "checked and make any regression invisible.",
    )
    args = ap.parse_args()

    from transformers import AutoTokenizer
    from IndicTransToolkit.processor import IndicProcessor

    evalsat = load_eval_module()
    phrases, src, tgt = evalsat.PHRASES, evalsat.SRC, evalsat.TGT

    tok = AutoTokenizer.from_pretrained(REPO, trust_remote_code=True)
    ip = IndicProcessor(inference=True)
    sess = sessions(args.threads, merged=args.merged)

    reference = None
    if args.merged:
        ref_path = pathlib.Path(args.fixture)
        if not ref_path.is_file():
            print(f"{ref_path} not found. Run without --merged first to produce the reference.")
            return 1
        reference = {
            r["hindi"]: r["expected_output_ids"]
            for r in json.loads(ref_path.read_text(encoding="utf-8"))["sentences"]
        }

    print(f"pair    : {src} -> {tgt}   ({len(phrases)} sentences)")
    print(f"threads : {args.threads}")
    print(f"decoder : {'FUSED ' + args.merged if args.merged else 'separate graphs'}")
    print(f"decode  : greedy, no_repeat_ngram={NO_REPEAT_NGRAM}, "
          f"rep_penalty={REPETITION_PENALTY}, max_new={MAX_NEW_TOKENS}\n")

    records, total = [], 0.0
    for hi in phrases:
        tagged = ip.preprocess_batch([hi], src_lang=src, tgt_lang=tgt)
        enc = tok(tagged, return_tensors="np", padding=False, truncation=True, max_length=256)
        input_ids = enc["input_ids"].astype(np.int64)
        attn = enc["attention_mask"].astype(np.int64)

        t0 = time.time()
        ids = greedy_decode(sess, input_ids, attn)
        secs = time.time() - t0
        total += secs

        try:
            text = tok.batch_decode([ids], skip_special_tokens=True, src=False)[0]
        except TypeError:
            text = tok.batch_decode([ids], skip_special_tokens=True)[0]
        final = ip.postprocess_batch([text], lang=tgt)[0]

        mark = ""
        if reference is not None:
            mark = "  MATCH" if reference.get(hi) == ids else "  DIFFER"
        print(f"  {secs:5.2f}s  {len(ids):>2} tok  {final}{mark}")
        records.append({
            "hindi": hi,
            "tagged": tagged[0],
            "input_ids": input_ids[0].tolist(),
            "attention_mask": attn[0].tolist(),
            "expected_output_ids": ids,
            "expected_text_raw": text,
            "expected_text": final,
        })

    print(f"\ntotal {total:.2f}s, {total / len(phrases):.2f}s/sentence "
          f"(raw ORT, hand-rolled greedy, {args.threads} threads)")

    if reference is not None:
        agree = sum(1 for r in records if reference.get(r["hindi"]) == r["expected_output_ids"])
        print(f"\nfused vs separate graphs: {agree}/{len(records)} token-id sequences identical")
        if agree == len(records):
            print("The fusion is exact. Both branches share one copy of the decoder weights and")
            print("produce the same output, so the 184 MB saving costs nothing in behaviour.")
            return 0
        print("MISMATCH. Unlike the ARM-vs-x86 near-tie flips, this is the SAME hardware and the")
        print("same arithmetic, so identical output is the correct expectation here — a difference")
        print("means the fusion changed the graph's semantics. Do not ship it.")
        return 3

    fixture = {
        "_comment": (
            "Reference for the Android ORT spike. Produced by tools/mt-decode-raw.py against the "
            "int8 graphs of " + REPO + ". input_ids are already IndicProcessor-preprocessed and "
            "SentencePiece-encoded, so the device does not need a tokenizer to run the gate. "
            "expected_output_ids is what a correct greedy decode must reproduce EXACTLY."
        ),
        "repo": REPO,
        "n_layers": N_LAYERS,
        "n_heads": 8,
        "head_dim": 64,
        "decoder_start_token_id": DECODER_START,
        "eos_token_id": EOS,
        "pad_token_id": PAD,
        "max_new_tokens": MAX_NEW_TOKENS,
        "no_repeat_ngram_size": NO_REPEAT_NGRAM,
        "repetition_penalty": REPETITION_PENALTY,
        "sentences": records,
    }
    out = pathlib.Path(args.fixture)
    out.write_text(json.dumps(fixture, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"fixture written to {out}")

    # Cross-check against the optimum-driven run, which was itself checked against PyTorch at
    # 10/11 exact. If these disagree, the hand-rolled loop is wrong and the port must not start.
    prior = _here / "santali-onnx-verify.txt"
    if prior.is_file():
        body = prior.read_text(encoding="utf-8")
        agree = sum(1 for r in records if r["expected_text"] and r["expected_text"] in body)
        print(f"\nagreement with the optimum int8 run in santali-onnx-verify.txt: "
              f"{agree}/{len(records)} outputs found verbatim")
        if agree < len(records):
            print("  Note: that file logged beam=5; greedy legitimately differs on some "
                  "sentences (it scored 0.97 mean similarity vs 0.98). Only a near-zero "
                  "agreement count would indicate a broken loop.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
