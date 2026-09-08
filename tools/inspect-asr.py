"""Dumps the ONNX metadata sherpa-onnx reads to configure a NeMo CTC model.

Why this exists: SherpaEngines.kt notes the streaming export "leaves normalize_type EMPTY",
and the app returns null transcripts for real speech. sherpa-onnx reads feature
normalisation, vocab size and subsampling from the model's metadata_props, not from the
Kotlin config. If normalize_type is absent the encoder is fed unnormalised features and CTC
collapses to all-blank, which surfaces as empty text -> null -> NO_SPEECH_RECOGNISED.

Run: python tools/inspect-asr.py
"""

import sys
from pathlib import Path

import onnx

MODELS = Path(__file__).resolve().parent.parent / "models"

TARGETS = [
    ("streaming (option A)", MODELS / "asr-streaming" / "model.int8.onnx",
     MODELS / "asr-streaming" / "tokens.txt"),
    ("batch (option B)", MODELS / "asr-batch" / "model.int8.onnx",
     MODELS / "asr-batch" / "tokens.txt"),
]


def dump(label: str, model_path: Path, tokens_path: Path) -> None:
    print("=" * 72)
    print(f"{label}\n  {model_path.name}  ({model_path.stat().st_size / 1e6:.1f} MB)")
    print("=" * 72)

    # load_external_data=False keeps this cheap: we only want the graph header.
    m = onnx.load(str(model_path), load_external_data=False)

    props = {p.key: p.value for p in m.metadata_props}
    if not props:
        print("  metadata_props: EMPTY  <-- sherpa has nothing to configure from")
    else:
        for k in sorted(props):
            v = props[k]
            flag = ""
            if k == "normalize_type" and v.strip() == "":
                flag = "   <-- EMPTY. features will NOT be normalised."
            print(f"  {k:24} = {v!r}{flag}")

    if "normalize_type" not in props:
        print("  normalize_type           = <ABSENT>   <-- key not present at all")

    print(f"\n  inputs:")
    for i in m.graph.input:
        dims = [d.dim_param or d.dim_value for d in i.type.tensor_type.shape.dim]
        print(f"    {i.name:20} {dims}")
    print(f"  outputs:")
    for o in m.graph.output:
        dims = [d.dim_param or d.dim_value for d in o.type.tensor_type.shape.dim]
        print(f"    {o.name:20} {dims}")

    n_tokens = sum(1 for line in tokens_path.read_text(encoding="utf-8").splitlines() if line)
    print(f"\n  tokens.txt entries: {n_tokens}")
    print()


if __name__ == "__main__":
    if not MODELS.is_dir():
        sys.exit(f"no models dir at {MODELS}")
    for label, model, tokens in TARGETS:
        if model.is_file():
            dump(label, model, tokens)
        else:
            print(f"{label}: MISSING at {model}\n")
