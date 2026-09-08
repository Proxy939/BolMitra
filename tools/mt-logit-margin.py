"""Why did one sentence pick a different first token on ARM than on x86?

The device spike reproduced 10 of 11 desktop outputs exactly and disagreed on sentence 3
("ताली बजाओ"). The disagreement starts at the FIRST generated token, which rules out most of the
suspects: a KV-cache plumbing bug diverges partway through, once the cache has been mishandled for
a step or two, and a wrong n-gram or repetition rule cannot act at step 0 at all — the history is
just [decoder_start], so the n-gram processor is inactive and the penalty touches one token.

That leaves the logits themselves. If the top two candidates at step 0 are nearly tied, then int8
kernels that differ between x86 (AVX-VNNI) and AArch64 (SDOT), plus a different float accumulation
order, are enough to swap them — and greedy search is path-dependent, so one swapped token
rewrites the whole sequence. That is ordinary numerics, not a logic error.

This script measures the margin so the question is settled with a number instead of an argument.
Print the top-k at step 0 for every sentence, and flag which ones sit close enough to flip.

Usage:
    .venv-it2\\Scripts\\python.exe tools/mt-logit-margin.py
"""

import io
import pathlib
import sys

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

import importlib.util

import numpy as np

_here = pathlib.Path(__file__).resolve().parent


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, _here / filename)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


raw = load("mtraw", "mt-decode-raw.py")

# The token ids the device produced where it disagreed, from the logcat of the spike run.
DEVICE_FIRST_TOKEN = {2: 89877}


def main() -> int:
    from transformers import AutoTokenizer
    from IndicTransToolkit.processor import IndicProcessor

    evalsat = load("evalsat", "eval-hindi-santali.py")
    tok = AutoTokenizer.from_pretrained(raw.REPO, trust_remote_code=True)
    ip = IndicProcessor(inference=True)
    sess = raw.sessions(4)

    print(f"{'#':>3} {'top1':>8} {'top2':>8} {'margin':>10} {'ratio':>8}  sentence")
    print("-" * 78)

    for idx, hi in enumerate(evalsat.PHRASES):
        tagged = ip.preprocess_batch([hi], src_lang=evalsat.SRC, tgt_lang=evalsat.TGT)
        enc = tok(tagged, return_tensors="np", padding=False, truncation=True, max_length=256)
        input_ids = enc["input_ids"].astype(np.int64)
        attn = enc["attention_mask"].astype(np.int64)

        hidden = sess["encoder_model"].run(
            ["last_hidden_state"], {"input_ids": input_ids, "attention_mask": attn}
        )[0]
        dec = sess["decoder_model"]
        names = [o.name for o in dec.get_outputs()]
        res = dict(zip(names, dec.run(names, {
            "encoder_attention_mask": attn,
            "input_ids": np.array([[raw.DECODER_START]], dtype=np.int64),
            "encoder_hidden_states": hidden,
        })))
        logits = res["logits"][0, -1, :].astype(np.float32)

        # Same processors the decode loop applies at step 0.
        logits = raw.apply_repetition_penalty(logits, [raw.DECODER_START])
        logits[raw.PAD] = -np.inf

        order = np.argsort(-logits)
        t1, t2 = int(order[0]), int(order[1])
        margin = float(logits[t1] - logits[t2])
        # Margin relative to the logit scale, so "close" means close for this distribution rather
        # than close in absolute units.
        spread = float(logits[t1] - logits[order[9]])
        ratio = margin / spread if spread > 0 else float("nan")

        flag = ""
        if idx in DEVICE_FIRST_TOKEN:
            dev = DEVICE_FIRST_TOKEN[idx]
            rank = int(np.where(order == dev)[0][0])
            flag = f"  <-- device chose {dev}, desktop rank {rank + 1}"
        print(f"{idx + 1:>3} {t1:>8} {t2:>8} {margin:>10.4f} {ratio:>8.3f}  {hi}{flag}")

    print()
    print("Read it like this: a margin near zero means the two candidates are numerically")
    print("indistinguishable, so which one wins depends on kernel and accumulation order. That is")
    print("expected across architectures and is NOT evidence of a porting bug. A large margin on a")
    print("sentence the device got wrong WOULD be a real bug and needs chasing.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
