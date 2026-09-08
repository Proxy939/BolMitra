"""Fuse the two decoder graphs into one so the decoder weights are stored and loaded ONCE.

THE PROBLEM THIS SOLVES
-----------------------
The int8 export ships two decoder graphs: decoder.onnx (194 MB, first step, no cache) and
decoder_with_past.onnx (185 MB, every later step). They are the same transformer with the same
weights — the only difference is whether a KV cache comes in. Loading both means the decoder
weights sit in memory twice.

Measured on device: MT alone took native heap from 21 MB to 628 MB, and the full stack with ASR
and TTS reached 929 MB native / 1.18 GB PSS. On a 2 GB tablet that is not survivable. Roughly
185 MB of it is this duplication, and it is the only structural saving available without changing
models — the 320M indic→indic checkpoint is already the smallest one that covers sat_Olck.

HOW THE FUSION WORKS
--------------------
`optimum.onnx.merge_decoders` builds one graph wrapping both in an ONNX `If` node that branches on
a new `use_cache_branch` boolean input. Initializers common to both branches are hoisted into the
parent graph, so one copy serves both.

`strict=False` is required here: the no-cache graph has 73 outputs and the with-past graph 37,
because the latter does not re-emit the encoder KV halves. Strict mode demands equal counts.

WHY THERE ARE TWO ROUTES
------------------------
Merging the int8 graphs directly is the cheap path — the files are already local. But it only pays
off if the shared weights actually deduplicate, and they may not: the two graphs were quantized
INDEPENDENTLY, so the same underlying weight can end up with a different scale, zero-point or
initializer name in each, and non-identical initializers cannot be hoisted.

So this script measures the result rather than trusting it. If the merged file is close to the sum
of its inputs, dedup did not happen and `--fp32` is the answer: merge the fp32 graphs, where the
weights are bit-identical, then quantize the merged graph once.

Usage:
    .venv-it2\\Scripts\\python.exe tools/mt-merge-decoder.py           # try int8 merge
    .venv-it2\\Scripts\\python.exe tools/mt-merge-decoder.py --fp32    # merge fp32, then quantize
"""

import argparse
import io
import pathlib
import shutil
import sys
import time

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

REPO = "TigreGotico/indictrans2-indic-indic-dist-320M-onnx"
OUT = pathlib.Path(__file__).resolve().parent.parent / "models" / "mt-hi-sat"
WORK = pathlib.Path("E:/mt-merge-work")


def mb(path) -> float:
    return pathlib.Path(path).stat().st_size / 1024 / 1024


def report(label, merged, a, b):
    """Did the shared weights actually collapse into one copy?"""
    total = mb(a) + mb(b)
    got = mb(merged)
    saved = total - got
    print(f"\n{label}")
    print(f"  decoder            {mb(a):8.1f} MB")
    print(f"  decoder_with_past  {mb(b):8.1f} MB")
    print(f"  sum                {total:8.1f} MB")
    print(f"  merged             {got:8.1f} MB")
    print(f"  saved              {saved:8.1f} MB  ({saved / total * 100:.0f}%)")
    # A real dedup lands near the size of ONE decoder. Anything above ~85% of the sum means the
    # initializers stayed distinct and the merge bought nothing but an If node.
    deduped = got < total * 0.85
    print(f"  verdict            {'DEDUPLICATED' if deduped else 'NO DEDUP — weights stayed distinct'}")
    return deduped


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--fp32", action="store_true",
                    help="Merge the fp32 graphs then quantize the result (2.05 GB download).")
    ap.add_argument("--keep-work", action="store_true")
    args = ap.parse_args()

    from huggingface_hub import hf_hub_download
    from optimum.onnx import merge_decoders

    WORK.mkdir(parents=True, exist_ok=True)
    OUT.mkdir(parents=True, exist_ok=True)

    sub = "" if args.fp32 else "int8/"
    print(f"fetching {'fp32' if args.fp32 else 'int8'} decoder graphs…")
    dec = hf_hub_download(REPO, filename=f"{sub}decoder_model.onnx")
    past = hf_hub_download(REPO, filename=f"{sub}decoder_with_past_model.onnx")

    merged_path = WORK / ("decoder_merged_fp32.onnx" if args.fp32 else "decoder_merged_int8.onnx")
    print(f"merging (strict=False, output counts differ 73 vs 37)…")
    t0 = time.time()
    merge_decoders(decoder=dec, decoder_with_past=past, save_path=str(merged_path), strict=False)
    print(f"  merged in {time.time() - t0:.1f}s")

    deduped = report(
        "int8 merge" if not args.fp32 else "fp32 merge", merged_path, dec, past
    )

    if not args.fp32:
        if not deduped:
            print("\nThe two int8 graphs were quantized independently, so their shared weights are")
            print("not bit-identical and could not be hoisted. Re-run with --fp32 to merge before")
            print("quantizing:")
            print("    .venv-it2\\Scripts\\python.exe tools/mt-merge-decoder.py --fp32")
            return 2
        final = merged_path
    else:
        # Quantize the merged fp32 graph once, so the single shared weight copy is what gets
        # quantized and stays shared.
        from onnxruntime.quantization import QuantType, quantize_dynamic

        final = WORK / "decoder_merged_int8.onnx"
        print("\nquantizing the merged graph (dynamic int8, weights only)…")
        t0 = time.time()
        quantize_dynamic(
            model_input=str(merged_path),
            model_output=str(final),
            weight_type=QuantType.QInt8,
            # Matches the publisher's export: weight-only dynamic quantisation. Per-channel is
            # left off so this stays comparable to the int8 graphs already verified at 10/11
            # exact against PyTorch — changing two variables at once would make a regression
            # impossible to attribute.
            per_channel=False,
            extra_options={"MatMulConstBOnly": True},
        )
        print(f"  quantized in {time.time() - t0:.1f}s -> {mb(final):.1f} MB")

    dest = OUT / "decoder_merged.onnx"
    shutil.copyfile(final, dest)
    print(f"\nstaged {dest}  ({mb(dest):.1f} MB)")

    old = mb(OUT / "decoder.onnx") + mb(OUT / "decoder_with_past.onnx")
    print(f"replaces {old:.1f} MB of separate graphs — {old - mb(dest):.1f} MB less on disk")
    print("\nNext: verify parity with tools/mt-decode-raw.py --merged before trusting it, then")
    print("push and re-measure native heap on device. A smaller file is not the goal; a smaller")
    print("RESIDENT FOOTPRINT is, and only the device can confirm that.")

    if not args.keep_work:
        # The intermediate fp32 merge is ~800 MB and serves no further purpose.
        if args.fp32 and merged_path.exists():
            merged_path.unlink()
    return 0


if __name__ == "__main__":
    sys.exit(main())
