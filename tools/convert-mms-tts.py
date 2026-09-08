#!/usr/bin/env python3
"""
Convert facebook/mms-tts VITS checkpoints to sherpa-onnx format.

    python tools/convert-mms-tts.py --lang unr

Produces  models/tts-<lang>/model.onnx  and  models/tts-<lang>/tokens.txt
which sherpa-onnx's OfflineTts loads directly.

Why this file exists (ARCHITECTURE.md V62)
------------------------------------------
sherpa-onnx publishes 642 TTS assets and only 8 are `vits-mms`, none Indic. There is no
`unr`, `hoc` or `sat` voice to download, so R2 depends on converting one ourselves. This
was not identified as a work item anywhere in the document before Revision 22.

READ THIS BEFORE USING THE OUTPUT (ARCHITECTURE.md V63)
------------------------------------------------------
**The Mundari voice speaks ODIA orthography, not Devanagari.** All 50 non-punctuation
symbols in its vocabulary are in the Odia Unicode block; there is no Devanagari in it at
all. Ho is the same. So this model CANNOT be fed the Devanagari text the rest of the app
uses — a Devanagari -> Odia transliteration step is required first.

That transliteration belongs at T2 pack-build time, not on the device, because T0 audio is
pre-rendered anyway (section 6.3). It therefore stays off the 3-second critical path.

**Licence: MMS-TTS is CC-BY-NC 4.0 (V3).** Non-commercial. A state FLN deployment is
arguably compatible, but section 4.4 requires that in writing as a Phase 0 task, and the
converted artifact inherits the same terms. Do not redistribute it without that clearance.

Implementation note
-------------------
sherpa-onnx's documented recipe builds `monotonic_align` with Cython, which needs a C
toolchain and uses `sed`. Both are awkward on Windows. It turns out to be unnecessary:
`monotonic_align.maximum_path` is called only inside `SynthesizerTrn.forward()`, the
training path. ONNX export traces `infer()`, which never touches it. So this script installs
a stub module that satisfies the import and raises if anything actually calls it.
"""

from __future__ import annotations

import argparse
import collections
import hashlib
import json
import subprocess
import sys
import types
from pathlib import Path
from urllib.request import urlopen

HF = "https://huggingface.co/facebook/mms-tts/resolve/main/models"
MMS_SPACE = "https://huggingface.co/spaces/mms-meta/MMS"

# Pinned from Git-LFS pointers. Only `unr` is pinned because it is the prototype language;
# other languages are fetched without a hash check and print a warning.
KNOWN_SHA256 = {
    "unr": {
        "G_100000.pth": (
            "254e45e7465210a02dfefd40f9e023027dad09b8e25a82b82424af9fcd8730c0",
            145496819,
        ),
    },
}

# Unicode blocks, for the script sanity check that produced V63.
BLOCKS = [
    ("DEVANAGARI", 0x0900, 0x097F),
    ("BENGALI", 0x0980, 0x09FF),
    ("ODIA", 0x0B00, 0x0B7F),
    ("OL_CHIKI", 0x1C50, 0x1C7F),
]


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def fetch(url: str, dest: Path, expect: tuple[str, int] | None) -> None:
    if dest.exists() and expect and sha256(dest) == expect[0]:
        print(f"  [cached] {dest.name}")
        return
    dest.parent.mkdir(parents=True, exist_ok=True)

    have = dest.stat().st_size if dest.exists() else 0
    print(f"  [get]    {dest.name}" + (f"  resuming from {have/1e6:.1f} MB" if have else ""))

    # curl with `-C -` rather than urlopen, so an interrupted transfer resumes instead of
    # restarting. The checkpoint is 145 MB and these are exactly the links that drop.
    rc = subprocess.run(
        ["curl", "-L", "-C", "-", "--retry", "3", "--retry-delay", "2",
         "--fail", "--silent", "--show-error", "-o", str(dest), url]
    ).returncode
    # 33 = server refused the range, which normally means the file is already complete.
    if rc == 33 and expect and dest.exists() and dest.stat().st_size == expect[1]:
        pass
    elif rc != 0:
        raise SystemExit(f"curl exit {rc} fetching {dest.name} - re-run to resume")
    if expect:
        actual = sha256(dest)
        if actual != expect[0]:
            dest.unlink()
            raise SystemExit(
                f"SHA-256 mismatch for {dest.name}\n"
                f"  expected {expect[0]}\n  actual   {actual}\n"
                "Artifact deleted. Refusing to convert an unverified checkpoint."
            )
        print("  [ok]     verified")
    else:
        print(f"  [warn]   no pinned hash for {dest.name} - not verified")


def install_monotonic_align_stub() -> None:
    """Satisfy `import monotonic_align` without a C build. See module docstring."""
    stub = types.ModuleType("monotonic_align")

    def maximum_path(*_args, **_kwargs):
        raise RuntimeError(
            "monotonic_align.maximum_path was called. It is only used by "
            "SynthesizerTrn.forward() during training; ONNX export should trace infer(). "
            "If you hit this, the export path changed and this stub is no longer safe."
        )

    stub.maximum_path = maximum_path  # type: ignore[attr-defined]
    sys.modules["monotonic_align"] = stub


def classify_script(symbols: list[str]) -> dict[str, int]:
    counts: dict[str, int] = collections.Counter()
    for s in symbols:
        if not s or s in (" ", "_", "'"):
            continue
        cp = ord(s[0])
        name = next((n for n, lo, hi in BLOCKS if lo <= cp <= hi), "other")
        counts[name] += 1
    return dict(counts)


def write_tokens(symbols: list[str], out: Path) -> None:
    """Mirrors sherpa-onnx's documented tokens.txt generation, including case folding.

    `newline="\\n"` is load-bearing, not tidiness. sherpa-onnx's `ReadTokens`
    (offline-tts-character-frontend.cc) encodes the space symbol as a line holding *only* an id,
    and detects that case with `iss >> sym; if (iss.eof())`. A trailing CR leaves the stream
    short of eof, so the line is parsed as `sym="21"`, `Utf8ToUtf32("21").size() != 1`, and
    sherpa calls `exit(-1)` — status 255, uncatchable, process gone. Every other line survives
    CRLF because `iss >> std::ws` eats the CR after the id is read.
    """
    upper = [s.upper() for s in symbols]
    duplicate = {t for t, c in collections.Counter(upper).items() if c > 1}
    with out.open("w", encoding="utf-8", newline="\n") as f:
        for idx, token in enumerate(symbols):
            f.write(f"{token} {idx}\n")
            if (
                token.lower() != token.upper()
                and len(token.upper()) == 1
                and token.upper() not in duplicate
            ):
                f.write(f"{token.upper()} {idx}\n")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--lang", default="unr", help="MMS language code (unr, hoc, ...)")
    ap.add_argument("--work", default="build/mms-convert", help="scratch directory")
    ap.add_argument("--out", default=None, help="output dir (default models/tts-<lang>)")
    args = ap.parse_args()

    lang = args.lang
    work = Path(args.work).resolve() / lang
    out = Path(args.out).resolve() if args.out else Path("models") / f"tts-{lang}"
    out = out.resolve()
    work.mkdir(parents=True, exist_ok=True)
    out.mkdir(parents=True, exist_ok=True)

    print(f"=== fetching {lang} checkpoint ===")
    pins = KNOWN_SHA256.get(lang, {})
    for name in ("G_100000.pth", "config.json", "vocab.txt"):
        fetch(f"{HF}/{lang}/{name}", work / name, pins.get(name))

    # --- script sanity check: the check that produced V63 -----------------------------
    symbols = [
        line.replace("\n", "")
        for line in (work / "vocab.txt").read_text(encoding="utf-8").splitlines(True)
    ]
    dist = classify_script(symbols)
    print(f"\n=== vocabulary: {len(symbols)} symbols ===")
    for name, n in sorted(dist.items(), key=lambda kv: -kv[1]):
        print(f"  {name:12} {n}")
    if dist.get("DEVANAGARI", 0) == 0:
        print(
            "\n  *** This voice does NOT read Devanagari (V63). The app's text is\n"
            "      Devanagari, so a Devanagari -> target-script transliteration is\n"
            "      REQUIRED before synthesis. Run it at T2 pack-build time. ***"
        )

    cfg = json.loads((work / "config.json").read_text(encoding="utf-8"))
    if cfg["data"]["training_files"].split(".")[-1] == "uroman":
        raise SystemExit(
            f"{lang} is a uroman model. sherpa-onnx's VITS path does not support romanised "
            "input; this needs the uroman preprocessor, which is out of scope."
        )

    # --- MMS source ------------------------------------------------------------------
    src = work / "MMS"
    if not src.exists():
        print(f"\n=== cloning MMS source ===")
        # Skip LFS: we only need the Python sources, not the space's own checkpoints.
        subprocess.run(
            ["git", "clone", "--depth", "1", MMS_SPACE, str(src)],
            check=True,
            env={**__import__("os").environ, "GIT_LFS_SKIP_SMUDGE": "1"},
        )
    sys.path.insert(0, str(src))
    sys.path.insert(0, str(src / "vits"))

    install_monotonic_align_stub()

    import torch  # noqa: E402  imported late, after sys.path is set

    # PyTorch 2.6 flipped `torch.load`'s default to weights_only=True. The MMS checkpoint is a
    # full pickle written for torch 1.13, so under 2.6+ VITS's own utils.load_checkpoint fails
    # with an UnpicklingError that says nothing useful about the cause.
    #
    # Forcing weights_only=False is normally the wrong instinct - it executes arbitrary pickle
    # payloads. It is defensible *here specifically* because the file was SHA-256 verified
    # against a hash pinned from the Git-LFS pointer before we got this far, so provenance is
    # established rather than assumed. If the pin is ever removed, remove this shim too.
    _torch_load = torch.load

    def _load_trusted(*a, **kw):
        kw.setdefault("weights_only", False)
        return _torch_load(*a, **kw)

    if tuple(int(p) for p in torch.__version__.split(".")[:2]) >= (2, 6):
        torch.load = _load_trusted  # type: ignore[assignment]

    from vits import utils  # noqa: E402
    from vits.models import SynthesizerTrn  # noqa: E402

    print(f"\n=== exporting (torch {torch.__version__}) ===")
    if not torch.__version__.startswith("1.13"):
        print(
            "  [note] sherpa-onnx's recipe pins torch 1.13.0. This is a newer torch, which\n"
            "         works but is untested upstream. If export fails oddly, try 1.13 in a venv."
        )
    hps = utils.get_hparams_from_file(str(work / "config.json"))

    net_g = SynthesizerTrn(
        len(symbols),
        hps.data.filter_length // 2 + 1,
        hps.train.segment_size // hps.data.hop_length,
        **hps.model,
    )
    net_g.cpu().eval()
    utils.load_checkpoint(str(work / "G_100000.pth"), net_g, None)

    class OnnxModel(torch.nn.Module):
        def __init__(self, m: SynthesizerTrn) -> None:
            super().__init__()
            self.model = m

        def forward(self, x, x_lengths, noise_scale, length_scale, noise_scale_w):
            # infer(), NOT forward() - this is why the monotonic_align stub is safe.
            return self.model.infer(
                x=x,
                x_lengths=x_lengths,
                noise_scale=noise_scale,
                length_scale=length_scale,
                noise_scale_w=noise_scale_w,
            )[0]

    model_path = out / "model.onnx"
    x = torch.randint(low=1, high=10, size=(50,), dtype=torch.int64).unsqueeze(0)
    with torch.no_grad():
        torch.onnx.export(
            OnnxModel(net_g),
            (
                x,
                torch.tensor([x.shape[1]], dtype=torch.int64),
                torch.tensor([1], dtype=torch.float32),
                torch.tensor([1], dtype=torch.float32),
                torch.tensor([1], dtype=torch.float32),
            ),
            str(model_path),
            opset_version=13,
            input_names=["x", "x_length", "noise_scale", "length_scale", "noise_scale_w"],
            output_names=["y"],
            dynamic_axes={"x": {0: "N", 1: "L"}, "x_length": {0: "N"}, "y": {0: "N", 2: "L"}},
        )

    # sherpa-onnx reads these from the ONNX metadata; without them it cannot configure
    # the frontend or the output sample rate.
    import onnx  # noqa: E402

    meta = {
        "model_type": "vits",
        "comment": "mms",
        "url": f"https://huggingface.co/facebook/mms-tts/tree/main/models/{lang}",
        "add_blank": int(hps.data.add_blank),
        "language": lang,
        "frontend": "characters",
        "n_speakers": int(hps.data.n_speakers),
        "sample_rate": hps.data.sampling_rate,
        # Not read by sherpa-onnx; recorded so the artifact carries its own provenance.
        "licence": "CC-BY-NC-4.0",
        "script": max(dist, key=dist.get) if dist else "unknown",
    }
    m = onnx.load(str(model_path))
    for k, v in meta.items():
        p = m.metadata_props.add()
        p.key, p.value = k, str(v)
    onnx.save(m, str(model_path))

    write_tokens(symbols, out / "tokens.txt")

    size_mb = model_path.stat().st_size / (1024 * 1024)
    print(f"\n=== done ===")
    print(f"  {model_path}  ({size_mb:.1f} MB)")
    print(f"  {out / 'tokens.txt'}")
    print(f"  sample_rate={meta['sample_rate']}  script={meta['script']}  licence=CC-BY-NC-4.0")
    print(
        "\nNext: verify with sherpa-onnx-offline-tts using TARGET-SCRIPT text, not "
        "Devanagari.\nSee ARCHITECTURE.md V63."
    )


if __name__ == "__main__":
    main()
