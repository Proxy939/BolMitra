"""Step 1 of the Santali roadmap, revised: is the int8 ONNX IndicTrans2 320M good enough to ship?

WHY THIS IS NOT THE CTRANSLATE2 SCRIPT THE PLAN ASKED FOR
---------------------------------------------------------
The plan said "convert IndicTrans2 320M to CTranslate2 int8". That path is blocked at two
independent points, either of which alone kills it:

  1. Conversion. ct2-transformers-converter only handles an explicit allow-list of
     architectures (BART, M2M100, MarianMT, MBART, NLLB, T5, Whisper, ...). IndicTrans is not
     on it. IndicTrans2 ships its own modeling_indictrans.py via trust_remote_code, so there is
     nothing for the converter to recognise. Supporting it means hand-writing a CT2 model spec.

  2. Runtime. CTranslate2 has no Android build target at all, and no Java/Kotlin bindings —
     its API is C++/Python. Getting it on the tablet means cross-compiling the C++ library with
     the NDK *and* writing a JNI layer. The one upstream issue asking for Android
     (OpenNMT/CTranslate2#1683, Apr 2024) has zero replies, no label and no PR.

Meanwhile ONNX Runtime is *already in the APK*, shipped inside the sherpa-onnx AAR that runs
the ASR and the VITS voice. So ONNX is not the fallback; it is the shorter path. And the export
already exists: TigreGotico/indictrans2-indic-indic-dist-320M-onnx (MIT, opset 17) is exactly
ai4bharat/indictrans2-indic-indic-dist-320M with encoder / decoder / decoder_with_past graphs
and an int8 subfolder at 519 MB. Nothing to convert.

WHAT THIS SCRIPT DECIDES
------------------------
Two things, both of which gate step 3 (wiring T1 into LiveTurnEngine):

  QUALITY. The publisher measured int8 at 60% exact match against fp32 — but on 10 sentences
  across Hindi/Tamil/Bengali/Marathi/Malayalam, *none of them Santali*. sat_Olck is the lowest-
  resource tag in the model and the one with the least training signal, so it is the most
  likely to be wrecked by dynamic quantisation. 60% on Tamil tells us nothing about it. We
  re-measure on our own 11 classroom sentences, against the PyTorch original as reference.

  LATENCY. R3 gives the whole turn 3s. ASR is measured at 241ms and TTS at roughly 400ms, so MT
  has about 2s of room. This times the int8 graphs on CPU at a fixed thread count, for beam=5
  and greedy, because beam width is the one latency lever we control for free.

  Read the latency numbers as an OPTIMISTIC BOUND, not a prediction. A desktop x86 core is
  several times a budget tablet's Cortex-A55, so a number that only just fits here will not fit
  there. The device is the only real judge; this decides whether it is worth building the APK.

Usage:
    python tools/verify-onnx-santali.py                 # int8 vs PyTorch, beam=5 and greedy
    python tools/verify-onnx-santali.py --fp32          # also check the fp32 graphs (2.05 GB)
    python tools/verify-onnx-santali.py --threads 4     # CPU threads for the latency estimate

Set HF_HOME=E:\\hf-cache first. C: has no room for these downloads.
"""

import argparse
import difflib
import importlib.util
import io
import os
import pathlib
import sys
import time

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
else:  # pragma: no cover
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

ONNX_REPO = "TigreGotico/indictrans2-indic-indic-dist-320M-onnx"
TORCH_REPO = "ai4bharat/indictrans2-indic-indic-dist-320M"

# The phrase list, language tags and degeneration metric live in the existing gate script. Import
# them rather than copy them, so the two reports cannot drift apart. The filename is hyphenated
# and therefore not importable by name, hence the explicit spec.
_here = pathlib.Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("evalsat", _here / "eval-hindi-santali.py")
evalsat = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(evalsat)

PHRASES, SRC, TGT = evalsat.PHRASES, evalsat.SRC, evalsat.TGT
degeneration_score = evalsat.degeneration_score

# The settings the sweep in eval-hindi-santali.py landed on. no_repeat_ngram_size is the one that
# actually stopped the repetition loops; max_length=64 bounds the damage when it still degenerates.
BEAM = dict(num_beams=5, max_length=64, no_repeat_ngram_size=3, repetition_penalty=1.15)
GREEDY = dict(num_beams=1, max_length=64, no_repeat_ngram_size=3, repetition_penalty=1.15)


def translate(model, tokenizer, ip, sentences, src, tgt, gen, device="cpu"):
    """One decode path used for BOTH models.

    Deliberately not reusing evalsat.translate: the ONNX repo bundles its own
    tokenization_indictrans.py, whose decode takes a `src` flag to pick between the separate
    source and target vocabularies. If the two models were decoded by two different functions,
    a mismatch in the report could come from the harness rather than from quantisation, which
    would defeat the entire point of the comparison.
    """
    import torch

    batch = ip.preprocess_batch(sentences, src_lang=src, tgt_lang=tgt)
    enc = tokenizer(
        batch, truncation=True, padding="longest", max_length=256,
        return_tensors="pt", return_attention_mask=True,
    ).to(device)

    with torch.no_grad():
        out = model.generate(**enc, num_return_sequences=1, min_length=0, use_cache=True, **gen)

    try:
        decoded = tokenizer.batch_decode(out, skip_special_tokens=True, src=False)
    except TypeError:
        # The upstream AI4Bharat tokenizer has no `src` kwarg; target-side is its default.
        decoded = tokenizer.batch_decode(out, skip_special_tokens=True)
    return ip.postprocess_batch(decoded, lang=tgt)


def similarity(a: str, b: str) -> float:
    """Character-level ratio, so near-misses are visible instead of collapsing to pass/fail.

    Exact string match is a brutal metric for MT: one different postposition scores the same as
    complete gibberish. We cannot read Ol Chiki, so we cannot judge which of those we are looking
    at by eye. A ratio separates "quantisation nudged a word" from "quantisation destroyed it".

    ponytail: difflib, not chrF. It is stdlib and adequate for ranking int8 against fp32 on 11
    sentences. If this ever becomes a real quality claim, use sacrebleu's chrF++ instead.
    """
    return difflib.SequenceMatcher(None, a, b).ratio()


def register_indictrans_with_optimum():
    """Teach optimum the IndicTrans config field names. Four lines, and it is desktop-only.

    optimum has the same allow-list disease as CTranslate2: NormalizedConfigManager maps a
    model_type string to the attribute names it should read for layer counts and head counts,
    and `IndicTrans` is not in the table, so ORTModelForSeq2SeqLM cannot work out the KV-cache
    shapes and refuses to load. The difference from CT2 is that this one is a dictionary entry
    rather than a missing build target.

    The remapping is the same one the publisher's exporter needed: IndicTrans2 calls its width
    encoder_embed_dim, not d_model, and splits vocab_size per side. M2M100's registration
    (BartLikeNormalizedTextConfig) only remaps hidden_size and num_attention_heads, which is not
    enough here because the layer counts differ per side too.

    NOTE FOR THE ANDROID PORT: none of this ships. On the tablet we drive encoder_model.onnx,
    decoder_model.onnx and decoder_with_past_model.onnx directly through ONNX Runtime's Java
    API and write the decode loop in Kotlin, so optimum is not in the picture. This function
    exists purely so the desktop can produce a reference to check the device against.
    """
    from optimum.utils import NormalizedConfigManager, NormalizedSeq2SeqConfig

    cfg = NormalizedSeq2SeqConfig.with_args(
        hidden_size="encoder_embed_dim",
        num_attention_heads="encoder_attention_heads",
        encoder_num_layers="encoder_layers",
        decoder_num_layers="decoder_layers",
        encoder_num_attention_heads="encoder_attention_heads",
        decoder_num_attention_heads="decoder_attention_heads",
        vocab_size="vocab_size",
        eos_token_id="eos_token_id",
    )
    # config.model_type is the exact string "IndicTrans"; register the lowercase spelling too in
    # case a future optimum normalises the key before lookup.
    NormalizedConfigManager._conf["IndicTrans"] = cfg
    NormalizedConfigManager._conf["indictrans"] = cfg


def load_onnx(subfolder, threads):
    from optimum.onnxruntime import ORTModelForSeq2SeqLM
    import onnxruntime as ort

    register_indictrans_with_optimum()

    so = ort.SessionOptions()
    # Pin the thread count so the latency number means something and is comparable run to run.
    # Left at the default, ORT grabs every core on the desktop, which flatters it badly against
    # a tablet.
    so.intra_op_num_threads = threads
    so.inter_op_num_threads = 1
    return ORTModelForSeq2SeqLM.from_pretrained(
        ONNX_REPO,
        subfolder=subfolder,
        trust_remote_code=True,
        use_cache=True,
        provider="CPUExecutionProvider",
        session_options=so,
    )


def run(label, model, tokenizer, ip, gen, device="cpu"):
    t0 = time.time()
    outs = translate(model, tokenizer, ip, PHRASES, SRC, TGT, gen, device)
    secs = time.time() - t0
    print(f"  {label:28} {secs:6.2f}s total  {secs / len(PHRASES):5.2f}s/sentence")
    return outs, secs


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--threads", type=int, default=4,
                    help="ORT intra-op threads. 4 approximates a budget tablet's usable cores.")
    ap.add_argument("--fp32", action="store_true", help="Also test the fp32 graphs (2.05 GB).")
    ap.add_argument("--out", default="tools/santali-onnx-verify.txt")
    args = ap.parse_args()

    if not os.environ.get("HF_HOME"):
        print("WARNING: HF_HOME is unset. Downloads will go to C:, which has no room.")
        print('  $env:HF_HOME="E:\\hf-cache"; $env:HF_HUB_DISABLE_SYMLINKS="1"\n')

    try:
        import torch
        from transformers import AutoModelForSeq2SeqLM, AutoTokenizer
        from IndicTransToolkit.processor import IndicProcessor
    except ImportError as e:
        print(f"missing dependency: {e}")
        return 1
    try:
        import optimum  # noqa: F401
    except ImportError:
        print("missing optimum. Install WITHOUT letting it upgrade transformers:\n"
              '  .venv-it2\\Scripts\\python.exe -m pip install "optimum==1.23.3" '
              '"transformers==4.46.3"\n'
              "transformers 4.57 breaks IndicTrans2's custom past_key_values handling.")
        return 1

    ip = IndicProcessor(inference=True)
    report = []

    def say(line=""):
        print(line)
        report.append(line)

    say(f"pair    : {SRC} -> {TGT}   ({len(PHRASES)} sentences)")
    say(f"threads : {args.threads} (ORT intra-op)")
    say(f"onnx    : {ONNX_REPO}")
    say(f"torch   : {TORCH_REPO}")
    say()

    # ---- reference: the PyTorch original -------------------------------------------------
    # On CPU, with the same thread budget, so the ONNX speedup claim is measured against a
    # comparable baseline rather than against a GPU.
    torch.set_num_threads(args.threads)
    say("PyTorch fp32 (CPU) — the reference every ONNX output is scored against")
    tok_pt = AutoTokenizer.from_pretrained(TORCH_REPO, trust_remote_code=True)
    m_pt = AutoModelForSeq2SeqLM.from_pretrained(TORCH_REPO, trust_remote_code=True).eval()
    ref_beam, t_pt_beam = run("beam=5", m_pt, tok_pt, ip, BEAM)
    ref_greedy, _ = run("greedy", m_pt, tok_pt, ip, GREEDY)
    del m_pt
    say()

    # ---- the ONNX graphs -----------------------------------------------------------------
    tok_ox = AutoTokenizer.from_pretrained(ONNX_REPO, trust_remote_code=True)
    results = {}
    for sub, want in ([("int8", True)] + [("", args.fp32)]):
        if not want:
            continue
        name = sub or "fp32"
        say(f"ONNX {name}")
        try:
            m = load_onnx(sub, args.threads)
        except Exception as e:
            say(f"  FAILED to load: {type(e).__name__}: {e}")
            continue
        results[f"{name} beam=5"] = run("beam=5", m, tok_ox, ip, BEAM) + (ref_beam,)
        results[f"{name} greedy"] = run("greedy", m, tok_ox, ip, GREEDY) + (ref_greedy,)
        del m
        say()

    # ---- scoring -------------------------------------------------------------------------
    say("=" * 78)
    say(f"{'config':22} {'exact':>7} {'mean sim':>9} {'worst sim':>10} {'degen':>6} {'s/sent':>7}")
    say("-" * 78)
    verdict_rows = {}
    for label, (outs, secs, ref) in results.items():
        sims = [similarity(o, r) for o, r in zip(outs, ref)]
        exact = sum(1 for o, r in zip(outs, ref) if o.strip() == r.strip())
        degen = sum(1 for o in outs if degeneration_score(o) > 0.5)
        per = secs / len(PHRASES)
        verdict_rows[label] = (exact, sum(sims) / len(sims), min(sims), degen, per)
        say(f"{label:22} {exact:>3}/{len(PHRASES)}  {sum(sims) / len(sims):>8.2f} "
            f"{min(sims):>10.2f} {degen:>6} {per:>7.2f}")
    say()
    say(f"PyTorch CPU beam=5 for comparison: {t_pt_beam / len(PHRASES):.2f}s/sentence")
    say()

    # ---- side by side --------------------------------------------------------------------
    say("=" * 78)
    say("int8 beam=5 vs PyTorch reference, sentence by sentence")
    say("=" * 78)
    if "int8 beam=5" in results:
        outs, _, ref = results["int8 beam=5"]
        for hi, o, r in zip(PHRASES, outs, ref):
            s = similarity(o, r)
            say(f"  hi    {hi}")
            say(f"  ref   {r}")
            say(f"  int8  {o}")
            flag = ""
            if not o.strip():
                flag = "   <-- EMPTY"
            elif s < 0.5:
                flag = "   <-- DIVERGED from reference"
            say(f"  sim   {s:.2f}{flag}")
            say("-" * 78)

    out_path = pathlib.Path(args.out)
    out_path.write_text("\n".join(report) + "\n", encoding="utf-8")
    print(f"\nwritten to {out_path}")

    # ---- the actual gate -----------------------------------------------------------------
    # Two independent failure modes, reported separately, because they have different fixes:
    # bad quality means fall back to fp32 or a different quantisation; slow means cut beam width.
    say = print
    if "int8 beam=5" not in verdict_rows:
        print("\nVERDICT: int8 graphs did not run. Nothing decided.")
        return 2
    exact, mean_sim, worst_sim, degen, per = verdict_rows["int8 beam=5"]
    print()
    if mean_sim < 0.75 or degen:
        print("VERDICT: int8 QUALITY FAIL on sat_Olck. Quantisation moved the output too far from")
        print("         the reference. Options: ship fp32 (2.05 GB, likely too big), re-quantise")
        print("         per-channel, or keep the encoder fp32 and quantise only the decoder.")
        return 3
    print(f"VERDICT: int8 tracks the reference on Santali (mean sim {mean_sim:.2f}, "
          f"{exact}/{len(PHRASES)} exact).")
    print(f"         Desktop CPU cost {per:.2f}s/sentence at {args.threads} threads. This is an")
    print("         OPTIMISTIC BOUND for the tablet — a Cortex-A55 is several times slower, so")
    print("         treat anything above ~0.6s here as at risk against the 2s MT slice of R3.")
    print("         A Santali speaker still has to judge correctness; this only shows int8 did")
    print("         not make the fp32 output worse.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
