"""Dump the ONNX graph I/O contract for the three IndicTrans2 int8 graphs.

This is scaffolding for the Kotlin port, not a test. ONNX Runtime's Java API binds tensors by
NAME, so the Kotlin decoder has to know every input name, its rank, its dtype and which dimensions
are dynamic. Getting one KV-cache name wrong produces either a shape error or, worse, a silently
wrong tensor, and discovering that through logcat on a tablet is the slow way round.

Run this once, read the output, then write the Kotlin against it.
"""

import io
import sys

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

import onnxruntime as ort
from huggingface_hub import hf_hub_download

REPO = "TigreGotico/indictrans2-indic-indic-dist-320M-onnx"
GRAPHS = ["encoder_model.onnx", "decoder_model.onnx", "decoder_with_past_model.onnx"]


def main() -> int:
    for g in GRAPHS:
        path = hf_hub_download(REPO, filename=f"int8/{g}")
        so = ort.SessionOptions()
        so.log_severity_level = 3
        sess = ort.InferenceSession(path, so, providers=["CPUExecutionProvider"])

        print("=" * 78)
        print(g)
        print("=" * 78)
        for kind, meta in (("INPUT", sess.get_inputs()), ("OUTPUT", sess.get_outputs())):
            print(f"  {kind}S ({len(meta)})")
            for m in meta:
                print(f"    {m.name:44} {str(m.type):26} {m.shape}")
        print()
    return 0


if __name__ == "__main__":
    sys.exit(main())
