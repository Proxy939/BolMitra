"""Stage the int8 IndicTrans2 graphs into the on-device ModelStore layout, ready for adb push.

Copies out of the local Hugging Face cache rather than re-downloading: the files are already there
and already etag-verified by huggingface_hub, and they total ~500 MB. It resolves through
hf_hub_download so the cache's symlinks are followed — reading the snapshot paths directly gives
0-byte reparse points on Windows, which is the failure mode that previously produced silently
empty vocab files.

Target layout, following the per-language pattern ModelStore already uses for voices:

    models/mt-hi-sat/
        encoder.onnx              encoder_model.onnx
        decoder.onnx              decoder_model.onnx           first step, no cache
        decoder_with_past.onnx    decoder_with_past_model.onnx every later step
        src.model  tgt.model      SentencePiece models, source and target sides are separate
        dict.SRC.json  dict.TGT.json
        fixture.json              precomputed token ids for the latency spike

The SentencePiece and dict files are staged now even though nothing on device reads them yet —
they are 9.5 MB against a 500 MB push, and staging them here avoids a second half-hour transfer
when the Kotlin tokenizer lands.

Usage:
    $env:HF_HOME="E:\\hf-cache"
    .venv-it2\\Scripts\\python.exe tools/stage-mt-model.py
    # then, to push (slow, run it in your own terminal):
    adb push models/mt-hi-sat /sdcard/Android/data/org.bolmitra/files/models/
"""

import hashlib
import pathlib
import shutil
import sys

from huggingface_hub import hf_hub_download

REPO = "TigreGotico/indictrans2-indic-indic-dist-320M-onnx"
DEST = pathlib.Path(__file__).resolve().parent.parent / "models" / "mt-hi-sat"

# remote filename -> on-device filename
#
# The two decoder graphs are deliberately NOT here. They hold the same weights twice (194 MB +
# 185 MB) and loading both cost 184 MB of duplicate resident memory. tools/mt-merge-decoder.py
# fuses them into decoder_merged.onnx, which this script only checks for.
FILES = {
    "int8/encoder_model.onnx": "encoder.onnx",
    "model.SRC": "src.model",
    "model.TGT": "tgt.model",
    "dict.SRC.json": "dict.SRC.json",
    "dict.TGT.json": "dict.TGT.json",
}


def sha256(path: pathlib.Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def main() -> int:
    DEST.mkdir(parents=True, exist_ok=True)
    total = 0
    print(f"staging into {DEST}\n")

    for remote, local in FILES.items():
        src = pathlib.Path(hf_hub_download(REPO, filename=remote))
        dst = DEST / local
        # A 0-byte source means the cache holds a dangling symlink, not a file. Fail loudly:
        # an empty vocab or a truncated graph fails much later and much less clearly.
        size = src.stat().st_size
        if size == 0:
            print(f"  FAIL {local}: resolved source is 0 bytes ({src})")
            return 1
        if not dst.is_file() or dst.stat().st_size != size:
            shutil.copyfile(src, dst)
        total += size
        print(f"  {local:26} {size / 1024 / 1024:8.1f} MB  sha256 {sha256(dst)[:16]}…")

    # Built locally rather than downloaded: the fused decoder by tools/mt-merge-decoder.py, the
    # flattened piece table by tools/export-spm-table.py.
    for name, builder in (
        ("decoder_merged.onnx", "tools/mt-merge-decoder.py"),
        ("spm.tsv", "tools/export-spm-table.py"),
    ):
        p = DEST / name
        if not p.is_file():
            print(f"  MISSING {name} — build it first:")
            print(f"    .venv-it2\\Scripts\\python.exe {builder}")
            return 1
        total += p.stat().st_size
        print(f"  {name:26} {mb(p):8.1f} MB  (built locally by {builder})")

    # Superseded files left behind by an earlier staging run would still be pushed and would still
    # occupy the tablet, so name them rather than leaving them to rot silently.
    stale = [DEST / "decoder.onnx", DEST / "decoder_with_past.onnx"]
    present = [p for p in stale if p.is_file()]
    if present:
        print(f"\n  {len(present)} superseded file(s) still in {DEST}, "
              f"{sum(mb(p) for p in present):.1f} MB:")
        for p in present:
            print(f"    {p.name}")
        print("  Delete them locally and on the tablet — decoder_merged.onnx replaces both:")
        print("    adb shell rm /sdcard/Android/data/org.bolmitra/files/models/mt-hi-sat/"
              "decoder.onnx")
        print("    adb shell rm /sdcard/Android/data/org.bolmitra/files/models/mt-hi-sat/"
              "decoder_with_past.onnx")

    # The spike fixture is generated locally, not fetched.
    fixture = pathlib.Path(__file__).resolve().parent / "mt-device-fixture.json"
    if fixture.is_file():
        shutil.copyfile(fixture, DEST / "fixture.json")
        total += fixture.stat().st_size
        print(f"  {'fixture.json':26} {fixture.stat().st_size / 1024:8.1f} KB")
    else:
        print("  WARNING: tools/mt-device-fixture.json missing — run tools/mt-decode-raw.py first")

    print(f"\ntotal {total / 1024 / 1024:.1f} MB in {DEST}")
    print("\nPush it yourself — this is a multi-minute transfer and belongs in your own terminal:")
    print("  $adb = \"$env:LOCALAPPDATA\\Android\\Sdk\\platform-tools\\adb.exe\"")
    print(f"  & $adb push models/mt-hi-sat "
          f"/sdcard/Android/data/org.bolmitra/files/models/")
    return 0


if __name__ == "__main__":
    sys.exit(main())
