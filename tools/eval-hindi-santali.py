"""Step 1 of the Santali roadmap: does Hindi -> Santali translation actually work?

This is a GATE, not a feature. Everything downstream — ONNX export, int8 quantisation, a new
TTS runtime on Android — is wasted effort if IndicTrans2's Santali output is unusable. So the
cheapest possible check runs first, on a desktop, before any of it.

What it does: translates the app's real classroom phrases from DemoSeed (hin_Deva -> sat_Olck)
and prints them side by side, plus a round-trip back to Hindi as a sanity signal.

Why the round trip matters: none of us reads Ol Chiki, so raw output is unreviewable. Santali
-> Hindi back-translation is a weak proxy — it can look fine while the forward pass is wrong,
and it shares the model's biases — but it catches the catastrophic cases: empty output, copied
input, or drift into an unrelated sentence. A Santali speaker still has to sign off before any
of this goes in front of children (the same rule DEMO_STRINGS_VERIFIED enforces for Mundari).

Usage:
    python tools/eval-hindi-santali.py                  # 320M distilled, fast
    python tools/eval-hindi-santali.py --model 1B       # full 1B, better quality
    python tools/eval-hindi-santali.py --no-roundtrip   # forward pass only

Model licence: IndicTrans2 is MIT. Unlike NLLB (CC-BY-NC) it is usable in a deployed product.
"""

import argparse
import io
import sys
import time

# The Windows console defaults to cp1252, which cannot encode Devanagari or Ol Chiki — printing a
# result raises UnicodeEncodeError after the model has already done the work. Reconfigure before
# anything is written. Results also go to a UTF-8 file, because a terminal that cannot render Ol
# Chiki turns real output into boxes and makes it unreviewable.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
else:  # pragma: no cover - older interpreters
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

MODELS = {
    "320M": "ai4bharat/indictrans2-indic-indic-dist-320M",
    "1B": "ai4bharat/indictrans2-indic-indic-1B",
}

SRC = "hin_Deva"
TGT = "sat_Olck"

# The eleven phrases from DemoSeed, minus the two templates whose {n} placeholder is not a
# sentence. These are what the app actually has to say.
PHRASES = [
    "किताब खोलो",
    "किताब बंद करो",
    "ताली बजाओ",
    "ध्यान से सुनो",
    "अपना नाम बताओ",
    "मेरे साथ बोलो",
    "बैठ जाओ",
    "खड़े हो जाओ",
    "पानी पीना है",
    # Two open-domain sentences, because the requirement is "whatever the user speaks".
    # The phrasebook cannot cover these; only real MT can.
    "आज हम गिनती सीखेंगे",
    "कल छुट्टी है इसलिए स्कूल बंद रहेगा",
]


def degeneration_score(text: str) -> float:
    """Fraction of the output taken up by its single most repeated token.

    A clean short sentence scores well under 0.4. The loops seen on the 320M — one token fifty
    times over — score above 0.8. This exists because the automated checks that passed the first
    run (Ol Chiki present, non-empty, not copied) all said the loops were fine.
    """
    toks = text.split()
    if len(toks) < 6:
        return 0.0
    return max(toks.count(t) for t in set(toks)) / len(toks)


def translate(model, tokenizer, ip, sentences, src, tgt, device, gen):
    import torch

    batch = ip.preprocess_batch(sentences, src_lang=src, tgt_lang=tgt)
    enc = tokenizer(
        batch, truncation=True, padding="longest", return_tensors="pt", return_attention_mask=True
    ).to(device)

    with torch.no_grad():
        out = model.generate(
            **enc,
            num_return_sequences=1,
            min_length=0,
            **gen,
            # MUST stay True, and the environment must be old enough to support it.
            #
            # IndicTrans2 ships its own modeling code via trust_remote_code, written against the old
            # tuple layout for past_key_values. transformers >= ~4.50 passes an EncoderDecoderCache
            # instead, so the model's own line
            #     past_key_values[0][0].shape[2] if past_key_values is not None else 0
            # gets None and dereferences .shape on it. The obvious workaround, use_cache=False,
            # SILENTLY CORRUPTS THE OUTPUT: that same expression is how the decoder derives its
            # positional offset, so with no cache every step is treated as position 0 and generation
            # collapses into repetition loops — worst on long inputs, which is exactly the case this
            # gate exists to judge. It looks like a bad model and is actually a bad harness.
            #
            # So the fix belongs in the environment, not here: run this under transformers 4.46.x.
            # See .venv-it2, created with --system-site-packages so torch is not duplicated.
            use_cache=True,
        )

    decoded = tokenizer.batch_decode(out, skip_special_tokens=True)
    return ip.postprocess_batch(decoded, lang=tgt)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", choices=list(MODELS), default="320M")
    ap.add_argument(
        "--repo",
        default=None,
        help="Any HF repo id, overriding --model. Use this to point at a mirror of the gated "
             "AI4Bharat repo, e.g. hitman123789/indictrans2-indic-indic-dist-320M-bucket",
    )
    ap.add_argument("--no-roundtrip", action="store_true")
    ap.add_argument(
        "--sweep",
        action="store_true",
        help="Try several decoding configs on the forward pass and report which suppress "
             "degeneration. Use this to choose settings, then run without it for the full report.",
    )
    ap.add_argument("--beams", type=int, default=5)
    ap.add_argument("--no-repeat-ngram", type=int, default=3)
    ap.add_argument("--repetition-penalty", type=float, default=1.15)
    ap.add_argument("--max-length", type=int, default=64)
    args = ap.parse_args()

    try:
        import torch
        from transformers import AutoModelForSeq2SeqLM, AutoTokenizer
        from IndicTransToolkit.processor import IndicProcessor
    except ImportError as e:
        print(f"missing dependency: {e}\n  pip install torch transformers IndicTransToolkit")
        return 1

    name = args.repo or MODELS[args.model]
    device = "cuda" if torch.cuda.is_available() else "cpu"
    print(f"model  : {name}")
    print(f"device : {device}")
    print(f"pair   : {SRC} -> {TGT}\n")

    t0 = time.time()
    try:
        tokenizer = AutoTokenizer.from_pretrained(name, trust_remote_code=True)
        model = (
            AutoModelForSeq2SeqLM.from_pretrained(name, trust_remote_code=True).to(device).eval()
        )
    except OSError as e:
        # IndicTrans2 is a GATED repo. Without a token this is the first thing you hit, and the
        # raw traceback is 60 lines of huggingface_hub internals that bury the one actionable fact.
        if "gated" in str(e).lower() or "401" in str(e):
            print(f"\nThis model is GATED. Two steps, both one-off:\n")
            print(f"  1. Open https://huggingface.co/{name} while logged in and accept the terms.")
            print(f"  2. Create a read token at https://huggingface.co/settings/tokens, then:\n")
            print(f'       $env:HF_TOKEN = "hf_xxxxxxxx"      # PowerShell, this session')
            print(f"       python tools/eval-hindi-santali.py\n")
            print("IndicTrans2 itself is MIT licensed - the gate is on the download, not on use.")
            return 4
        raise
    ip = IndicProcessor(inference=True)
    print(f"loaded in {time.time() - t0:.1f}s\n")

    # The configs worth comparing. `baseline` is what the first two runs used and is included so
    # the improvement is measured, not assumed.
    #
    # max_length matters more than it looks: at 256 a loop runs for 256 tokens, so capping it to 64
    # bounds the damage even when the model does degenerate. Classroom sentences are short; 64
    # target tokens is generous for anything a teacher says in one breath.
    if args.sweep:
        configs = {
            "baseline (beams=5, len=256)":
                dict(num_beams=5, max_length=256),
            "no_repeat_ngram=3":
                dict(num_beams=5, max_length=64, no_repeat_ngram_size=3),
            "no_repeat=3 + rep_pen=1.15":
                dict(num_beams=5, max_length=64, no_repeat_ngram_size=3,
                     repetition_penalty=1.15),
            "greedy + no_repeat=3":
                dict(num_beams=1, max_length=64, no_repeat_ngram_size=3,
                     repetition_penalty=1.1),
            "beams=5 + early_stopping":
                dict(num_beams=5, max_length=64, no_repeat_ngram_size=3,
                     repetition_penalty=1.15, early_stopping=True, length_penalty=1.0),
        }
        print(f"{'config':32} {'degen':>6} {'worst':>6} {'secs':>6}")
        print("-" * 56)
        best = None
        for label, gen in configs.items():
            t0 = time.time()
            outs = translate(model, tokenizer, ip, PHRASES, SRC, TGT, device, gen)
            secs = time.time() - t0
            scores = [degeneration_score(o) for o in outs]
            n_degen = sum(1 for s in scores if s > 0.5)
            worst = max(scores)
            print(f"{label:32} {n_degen:>6} {worst:>6.2f} {secs:>6.1f}")
            if best is None or (n_degen, worst) < best[0]:
                best = ((n_degen, worst), label, gen)
        print(f"\nbest: {best[1]}  -> {best[2]}")
        print("Re-run without --sweep using those flags for the full side-by-side report.")
        return 0

    gen = dict(
        num_beams=args.beams,
        max_length=args.max_length,
        no_repeat_ngram_size=args.no_repeat_ngram,
        repetition_penalty=args.repetition_penalty,
    )
    print(f"decoding: {gen}\n")

    t0 = time.time()
    santali = translate(model, tokenizer, ip, PHRASES, SRC, TGT, device, gen)
    fwd = time.time() - t0

    back = [""] * len(santali)
    if not args.no_roundtrip:
        back = translate(model, tokenizer, ip, santali, TGT, SRC, device, gen)

    lines = ["=" * 100]
    for hi, sat, bk in zip(PHRASES, santali, back):
        lines.append(f"  hi   {hi}")
        lines.append(f"  sat  {sat}")
        if bk:
            flag = ""
            if not sat.strip():
                flag = "   <-- EMPTY OUTPUT"
            elif sat.strip() == hi.strip():
                flag = "   <-- COPIED INPUT, not translated"
            lines.append(f"  back {bk}{flag}")
        lines.append("-" * 100)

    report = "\n".join(lines)
    print(report)
    out_path = "tools/santali-eval-output.txt"
    with open(out_path, "w", encoding="utf-8") as fh:
        fh.write(f"model: {name}\npair: {SRC} -> {TGT}\n\n{report}\n")
    print(f"\nwritten to {out_path}")

    empty = sum(1 for s in santali if not s.strip())
    copied = sum(1 for s, h in zip(santali, PHRASES) if s.strip() == h.strip())
    # Ol Chiki is U+1C50..U+1C7F. If output is not in that block the model ignored tgt_lang.
    olchiki = sum(1 for s in santali if any(0x1C50 <= ord(c) <= 0x1C7F for c in s))

    print(f"\n{len(PHRASES)} sentences, forward pass {fwd:.1f}s "
          f"({fwd / len(PHRASES):.2f}s each on {device})")
    print(f"  in Ol Chiki script : {olchiki}/{len(PHRASES)}")
    print(f"  empty              : {empty}")
    print(f"  copied input       : {copied}")

    if olchiki == 0:
        print("\nVERDICT: FAIL. Nothing came back in Ol Chiki - the model is not producing Santali.")
        return 2
    if empty or copied:
        print("\nVERDICT: SUSPECT. Some outputs are empty or untranslated; inspect above.")
        return 3
    print("\nVERDICT: forward pass produces Ol Chiki for every input. A Santali speaker must now")
    print("         judge whether it is CORRECT. Script-shaped is not the same as right.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
