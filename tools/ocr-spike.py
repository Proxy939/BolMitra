# -*- coding: utf-8 -*-
"""
Measure Santali (Ol Chiki) OCR character error rate. A spike, run BEFORE building any OCR screen.

Answer: **do not build it.** Best measured CER is 0.439 on the easier of two handwriting styles,
0.907 on the other. See `latest research.md` #ocr-spike-result. This script exists so that
conclusion is one command away from being re-checked rather than taken on trust.

    python tools/ocr-spike.py                 # fetches to E:/research/ocr, then measures
    python tools/ocr-spike.py --cache DIR
    python tools/ocr-spike.py --skip-fetch

Needs a Tesseract binary. Everything else is fetched.

### Why the ground truth is not mine

`ofdn/OpenSpeaks-Before-AI` ships `Annotated Image Text.csv` next to its handwriting images. That
is load-bearing: transcribing Ol Chiki myself to build a reference would be asserting Santali
strings, which the project invariants forbid. The dataset authors annotated it; this only scores.

The 91-image printed set has **no** annotation file, so CER is not computable on it. It gets a
liveness check only, which is reported as such and is not evidence of accuracy.
"""

import argparse
import csv
import io
import json
import os
import statistics
import subprocess
import sys
import urllib.parse
import urllib.request

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

REPO_API = "https://api.github.com/repos/ofdn/OpenSpeaks-Before-AI/git/trees/main?recursive=1"
REPO_RAW = "https://raw.githubusercontent.com/ofdn/OpenSpeaks-Before-AI/main/"
HW_DIR = "OCR Image datasets/Santali Handwriting Data for OCR"

# Two independently trained models. Hembram is the same author as the CC0 dictionaries already in
# the glossary, i.e. a native Santali speaker, which makes the result below genuinely surprising.
MODELS = {
    "sat_hembram": "https://raw.githubusercontent.com/Prasanta-Hembram/Santali-tessdata/master/"
                   "tessdata%20for%20v%203.05/sat.traineddata",
    "sat_indicocr": "https://raw.githubusercontent.com/indic-ocr/tessdata/master/sat/sat.traineddata",
}

IMAGE_SETS = {
    "chapa": HW_DIR + "/Ol Chiki (Chapa) Handwriting",
    "usara": HW_DIR + "/Ol Chiki (Usara) Handwriting",
    "printed": "OCR Image datasets/Santali Printed Data for OCR (Chapa and Usara Mixed)",
}

# 7 single text line, 8 single word, 6 uniform block. Reported for ALL of them: psm is the one knob
# that legitimately changes the answer on single-word images, and choosing it afterwards would be
# selecting the flattering number.
PSMS = [7, 8, 6]

OL_LO, OL_HI = 0x1C50, 0x1C7F

TESS_CANDIDATES = [
    r"C:\Program Files\Tesseract-OCR\tesseract.exe",
    r"C:\Program Files (x86)\Tesseract-OCR\tesseract.exe",
    "tesseract",
]


def find_tesseract():
    for c in TESS_CANDIDATES:
        try:
            subprocess.run([c, "--version"], capture_output=True, timeout=30)
            return c
        except (OSError, subprocess.SubprocessError):
            continue
    return None


def fetch(url, dest):
    if os.path.isfile(dest) and os.path.getsize(dest) > 0:
        return
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    req = urllib.request.Request(url, headers={"User-Agent": "bolmitra-ocr-spike"})
    with urllib.request.urlopen(req, timeout=120) as r:
        data = r.read()
    with open(dest, "wb") as f:
        f.write(data)


def do_fetch(cache):
    for name, url in MODELS.items():
        td = os.path.join(cache, "td_" + name.split("_")[1])
        fetch(url, os.path.join(td, "sat.traineddata"))
        print("  model %-14s -> %s" % (name, td))

    req = urllib.request.Request(REPO_API, headers={"User-Agent": "bolmitra-ocr-spike"})
    with urllib.request.urlopen(req, timeout=120) as r:
        tree = json.loads(r.read())["tree"]

    for name, prefix in IMAGE_SETS.items():
        outdir = os.path.join(cache, "img_" + name)
        pngs = [e["path"] for e in tree
                if e["type"] == "blob" and e["path"].startswith(prefix)
                and e["path"].lower().endswith(".png")]
        for p in pngs:
            fetch(REPO_RAW + urllib.parse.quote(p), os.path.join(outdir, os.path.basename(p)))
        print("  images %-8s %d -> %s" % (name, len(pngs), outdir))

    for name, sub in (("chapa", "Ol Chiki (Chapa) Handwriting"),
                      ("usara", "Ol Chiki (Usara) Handwriting")):
        fetch(REPO_RAW + urllib.parse.quote("%s/%s/Annotated Image Text.csv" % (HW_DIR, sub)),
              os.path.join(cache, "gt_%s.csv" % name))
        print("  ground truth %s" % name)


def levenshtein(a, b):
    if a == b:
        return 0
    if not a:
        return len(b)
    if not b:
        return len(a)
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]


def load_gt(cache, name):
    out = {}
    with io.open(os.path.join(cache, "gt_%s.csv" % name),
                 encoding="utf-8-sig", newline="") as f:
        for row in csv.reader(f):
            if len(row) >= 2 and row[0].strip().isdigit() and row[1].strip():
                out[row[0].strip()] = row[1].strip()
    return out


def ocr(tess, image, tessdata, psm, oem):
    try:
        p = subprocess.run(
            [tess, image, "stdout", "-l", "sat", "--tessdata-dir", tessdata,
             "--psm", str(psm), "--oem", str(oem)],
            capture_output=True, timeout=120)
    except subprocess.SubprocessError:
        return None
    if p.returncode != 0:
        return None
    return p.stdout.decode("utf-8", "replace")


def clean(s):
    """Ol Chiki only, whitespace collapsed. Tesseract appends form feeds and newlines."""
    return " ".join("".join(
        c if OL_LO <= ord(c) <= OL_HI else " " for c in (s or "")).split())


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cache", default="E:/research/ocr")
    ap.add_argument("--skip-fetch", action="store_true")
    args = ap.parse_args()
    cache = args.cache

    tess = find_tesseract()
    if not tess:
        print("no tesseract binary found; tried %s" % TESS_CANDIDATES)
        return 2
    ver = subprocess.run([tess, "--version"], capture_output=True).stdout.decode(
        "utf-8", "replace").splitlines()[0]
    print("tesseract: %s" % ver)

    if not args.skip_fetch:
        print("fetching")
        do_fetch(cache)
    print()

    tessdirs = {"sat_hembram": os.path.join(cache, "td_hembram"),
                "sat_indicocr": os.path.join(cache, "td_indicocr")}

    # Which engine mode does each model support? Research claimed legacy-only (--oem 0).
    probe = os.path.join(cache, "img_chapa", "1.png")
    oem_for = {}
    print("engine modes accepted")
    for model, td in tessdirs.items():
        modes = [oem for oem in (0, 1, 3) if ocr(tess, probe, td, 8, oem) is not None]
        print("  %-14s %s" % (model, modes or "NONE"))
        if modes:
            oem_for[model] = 0 if 0 in modes else modes[0]
    print()

    gts = {"chapa": load_gt(cache, "chapa"), "usara": load_gt(cache, "usara")}
    print("character error rate, annotated handwriting")
    print("%-14s %-4s %-7s %5s %8s %8s %8s" %
          ("model", "psm", "set", "n", "CER", "median", "exact"))
    print("-" * 66)
    best = None
    for model, td in tessdirs.items():
        if model not in oem_for:
            continue
        for psm in PSMS:
            for setname, gt in gts.items():
                cers, exact = [], 0
                for num, ref in sorted(gt.items(), key=lambda kv: int(kv[0])):
                    img = os.path.join(cache, "img_" + setname, "%s.png" % num)
                    if not os.path.isfile(img):
                        continue
                    hyp = clean(ocr(tess, img, td, psm, oem_for[model]))
                    exact += (hyp == ref)
                    cers.append(levenshtein(ref, hyp) / max(1, len(ref)))
                if not cers:
                    continue
                mean = sum(cers) / len(cers)
                print("%-14s %-4d %-7s %5d %8.3f %8.3f %8s"
                      % (model, psm, setname, len(cers), mean,
                         statistics.median(cers), "%d/%d" % (exact, len(cers))))
                if best is None or mean < best[0]:
                    best = (mean, model, psm, setname)
    print()
    if best:
        print("BEST: CER %.3f  (%s, psm %d, %s)" % best)
        print()
        print("A CER of %.2f means roughly %d character in every %d is wrong. Ol Chiki errors"
              % (best[0], round(best[0] * 10), 10))
        print("concentrate on VOWELS, which is what distinguishes one word from another, so these")
        print("are word-changing errors rather than near-misses. Not a foundation for a screen")
        print("whose output is spoken to children. See latest research.md #ocr-spike-result.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
