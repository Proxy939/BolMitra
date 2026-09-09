"""Build the shipped Santali glossary from the CC0 / CC-BY sources.

Produces `app/src/main/assets/santali-glossary.tsv`, which feeds two consumers on device:

  * T0 phrasebook rows at `Provenance.CORPUS` (a T0 hit means T1 never runs)
  * the glossary used to bias T1's decoding toward attested Santali forms

WHAT GOES IN, AND WHAT IS DELIBERATELY LEFT OUT
-----------------------------------------------
In:
    Hembram category CSVs        248 rows   classroom vocabulary, CC0
    Hembram numerals             101 rows   CC0 -- AUTHORITATIVE, see the numerals note below
    Hembram Glossary eng-sat     463 rows   CC0
    Hembram Glossary sat-eng   1,278 rows   CC0, columns reversed
    Hembram Open dictionary    1,529 rows   CC0, carries category and part of speech
    Hembram T General             60 rows   CC0, the only register-appropriate PARALLEL SENTENCES
    GATITOS en_sat             4,001 rows   CC-BY-4.0, attribution owed

Out, on purpose:
    NT (5k / 1.9k / 0.6k)    ~7,500 rows  Scripture, into a government primary school, in an
                                          archaic register that pulls AWAY from classroom speech.
                                          NT General is also monolingual, so it cannot train or
                                          evaluate translation at all.
    Name Translation             556 rows  Biblical proper names.
    Plants Scientific Name                 Latin binomials; no classroom use.

THE NUMERALS ARE NOT A PREFERENCE
---------------------------------
GATITOS publishes the SAME Santali string for six, seven and eight, and appears to confuse twelve
with twenty. This app teaches counting. So Hembram wins on every numeral unconditionally, and the
builder asserts it rather than trusting precedence ordering to get there.

DISAGREEMENT IS RECORDED, NOT RESOLVED
--------------------------------------
Three of four head-to-head pairs between these sources disagree (`yes`, `translate`, `original`
differ; only `no` matches). There is no single authoritative Santali lexicon, and the research is
explicit that a disagreement cannot be settled by majority vote or by picking the larger dataset.
So when sources differ the builder keeps ALL forms as variants and marks which source each came
from. The consumer decides; the data does not pretend to have decided.

NORMALISATION MUST MATCH THE DEVICE
-----------------------------------
`normalise_ol_chiki` below is a deliberate port of `OlChikiNormalizer.normalize` in Kotlin. If the
two drift, exact-match lookup silently degrades to fuzzy, which is the failure `pack_version`
exists to detect. Two rules, and the second is a rule about NOT acting:

  * ASCII '.' directly after an Ol Chiki letter becomes U+1C79 GAAHLAA TTUDDAAG.
  * ASCII '-' is REPORTED AND LEFT ALONE. U+1C7C PHAARKAA is a voicing switch, not a hyphen --
    rewriting it turns menag-a into a different word.

Usage:
    python tools/build-santali-glossary.py                    # uses the cache, fetches what is missing
    python tools/build-santali-glossary.py --cache E:/research
    python tools/build-santali-glossary.py --report-only      # no asset written
"""

import argparse
import csv
import io
import json
import os
import pathlib
import re
import sys
import urllib.request

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

OL_LO, OL_HI = 0x1C50, 0x1C7F
OL_LETTER_HI = 0x1C77          # letters and digits; marks start at U+1C78
GAAHLAA_TTUDDAAG = "\u1C79"

HEMBRAM = "Prasanta-Hembram/Translation-work-list-for-Santali-language"
HEMBRAM_RAW = "https://raw.githubusercontent.com/%s/master/" % HEMBRAM
GATITOS_URL = "https://huggingface.co/datasets/google/smol/resolve/main/gatitos/en_sat.jsonl"

# Source precedence, highest first. Hembram is CC0, authored by a named Santali Wikipedian who is
# actively maintaining it; GATITOS is machine-assisted and has documented defects.
PRECEDENCE = ["Hembram/Numerals", "Hembram/Sentences", "Hembram/Category", "Hembram/OpenDict",
              "Hembram/Glossary", "GATITOS"]


# --------------------------------------------------------------------------------------------
# normalisation -- keep in lockstep with OlChikiNormalizer.kt
# --------------------------------------------------------------------------------------------

def is_ol_chiki(ch):
    return OL_LO <= ord(ch) <= OL_HI


def is_ol_chiki_letter(ch):
    return OL_LO <= ord(ch) <= OL_LETTER_HI


def normalise_ol_chiki(raw):
    """Returns (text, periods_repaired, hyphens_found)."""
    if not raw:
        return raw, 0, 0
    out = []
    repaired = 0
    hyphens = 0
    for i, ch in enumerate(raw):
        if ch == "." and i > 0 and is_ol_chiki_letter(raw[i - 1]):
            out.append(GAAHLAA_TTUDDAAG)
            repaired += 1
        elif ch == "-" and i > 0 and is_ol_chiki_letter(raw[i - 1]):
            hyphens += 1
            out.append(ch)
        else:
            out.append(ch)
    return "".join(out), repaired, hyphens


def rejection_reason(target):
    """Mirrors OlChikiNormalizer.rejectionReason."""
    if not target or not target.strip():
        return "blank"
    if not any(is_ol_chiki(c) for c in target):
        return "no Ol Chiki character present"
    return None


# Punctuation that means "this cell holds more than one word" in a printed glossary, never
# punctuation that belongs to a Santali word. Verified against sat.dic: a 126,745-entry Ol Chiki
# wordlist contains no entry with a comma or a parenthesis inside it.
SYNONYM_SPLIT = re.compile(r"\s*[,/;]\s*|\s*\(\s*|\s*\)\s*")


def split_synonyms(raw):
    """
    Splits a source cell that lists alternatives into (primary, [others]).

    A lexicographer writing `ᱜᱷᱩᱴᱟᱜ, ᱜᱚᱱᱴᱷᱮ` for *knee* means two words. Left joined, this becomes
    ONE speakable string, so a class asked for "knee" hears both synonyms run together with the
    comma read aloud by the voice. The parenthesised form is the same defect wearing brackets:
    `ᱤᱨᱤᱞ ᱠᱩᱲᱤ (ᱥᱟᱞᱤ)` is a headword plus a gloss, not a phrase.

    The extra forms are not discarded -- they go into the same `variants` column that already
    carries cross-source disagreement, which is exactly the right shape for "the sources offer
    more than one word here and this data is not entitled to choose".
    """
    parts = [p.strip() for p in SYNONYM_SPLIT.split(raw or "")]
    parts = [p for p in parts if p and any(is_ol_chiki(c) for c in p)]
    if not parts:
        return raw, []
    return parts[0], parts[1:]


# --------------------------------------------------------------------------------------------
# fetching
# --------------------------------------------------------------------------------------------

def fetch(url, dest):
    if dest.is_file() and dest.stat().st_size > 0:
        return dest
    dest.parent.mkdir(parents=True, exist_ok=True)
    print("  fetching %s" % url)
    req = urllib.request.Request(url, headers={"User-Agent": "bolmitra-glossary-builder"})
    with urllib.request.urlopen(req, timeout=60) as r, open(dest, "wb") as f:
        f.write(r.read())
    return dest


def read_csv_rows(path):
    """CC0 CSVs from this repo are UTF-8 WITH BOM; utf-8-sig handles both."""
    raw = path.read_bytes()
    enc = "utf-8-sig" if raw[:3] == b"\xef\xbb\xbf" else "utf-8"
    text = raw.decode(enc, "replace")
    delim = "\t" if path.suffix.lower() == ".tsv" else ","
    return [r for r in csv.reader([l for l in text.splitlines() if l.strip()], delimiter=delim)]


# --------------------------------------------------------------------------------------------
# entry model
# --------------------------------------------------------------------------------------------

class Entry:
    __slots__ = ("en", "sat", "src", "kind", "pos", "flags")

    def __init__(self, en, sat, src, kind, pos="", flags=""):
        self.en = en
        self.sat = sat
        self.src = src
        self.kind = kind
        self.pos = pos
        self.flags = flags


CATEGORY_FILES = {
    "Animal.csv": "animal",
    "Birds.csv": "bird",
    "Body Parts.csv": "body",
    "Colour.csv": "colour",
    "Days and Months.csv": "calendar",
    "Eatables.csv": "food",
    "Relation.csv": "family",
    "Time.csv": "time",
    "Vegetable.csv": "vegetable",
}


def load_hembram(cache, stats):
    entries = []

    for name, topic in CATEGORY_FILES.items():
        path = fetch(HEMBRAM_RAW + "Single%20Word/Categories/" + name.replace(" ", "%20"),
                     cache / "ph" / name)
        for row in read_csv_rows(path):
            if len(row) >= 2:
                entries.append(Entry(row[0].strip(), row[1].strip(),
                                     "Hembram/Category", "word", topic))
        stats["files"].append((name, "Hembram/Category"))

    # Numerals. Authoritative -- see the module docstring.
    path = fetch(HEMBRAM_RAW + "Single%20Word/Categories/Numbers%20upto%20100%20in%20words.csv",
                 cache / "ph" / "Numbers upto 100 in words.csv")
    for row in read_csv_rows(path):
        if len(row) >= 2:
            entries.append(Entry(row[0].strip(), row[1].strip(),
                                 "Hembram/Numerals", "numeral", "num"))
    stats["files"].append(("Numbers upto 100 in words.csv", "Hembram/Numerals"))

    # eng-sat glossary: English, Ol Chiki
    path = fetch(HEMBRAM_RAW + "Glossary/Glossary%20(eng-sat).csv",
                 cache / "ph" / "Glossary (eng-sat).csv")
    for row in read_csv_rows(path):
        if len(row) >= 2:
            entries.append(Entry(row[0].strip(), row[1].strip(), "Hembram/Glossary", "word"))
    stats["files"].append(("Glossary (eng-sat).csv", "Hembram/Glossary"))

    # sat-eng glossary: Ol Chiki, English, Santali gloss -- columns REVERSED
    path = fetch(HEMBRAM_RAW + "Glossary/Glossary%20(sat-eng).csv",
                 cache / "ph" / "Glossary (sat-eng).csv")
    for row in read_csv_rows(path):
        if len(row) >= 2:
            entries.append(Entry(row[1].strip(), row[0].strip(), "Hembram/Glossary", "word"))
    stats["files"].append(("Glossary (sat-eng).csv", "Hembram/Glossary"))

    # Open dictionary: Category, Santali, English, POS, notes
    path = fetch(HEMBRAM_RAW + "Santali%20Open%20dictionary/Santali%20Open%20dictionary.csv",
                 cache / "ph" / "Santali Open dictionary.csv")
    for row in read_csv_rows(path):
        if len(row) >= 4:
            entries.append(Entry(row[2].strip(), row[1].strip(), "Hembram/OpenDict", "word",
                                 pos=row[3].strip().lower()))
    stats["files"].append(("Santali Open dictionary.csv", "Hembram/OpenDict"))

    # Parallel sentences -- the only register-appropriate ones in the whole survey.
    for fname, url_part in [("T General (0.06k).csv", "Sentence/T%20General%20(0.06k).csv"),
                            ("T Very Basic.csv", "Sentence/T%20Very%20Basic.csv")]:
        path = fetch(HEMBRAM_RAW + url_part, cache / "ph" / fname)
        for row in read_csv_rows(path):
            if len(row) >= 2:
                entries.append(Entry(row[1].strip(), row[0].strip(),
                                     "Hembram/Sentences", "sentence"))
        stats["files"].append((fname, "Hembram/Sentences"))

    return entries


def load_gatitos(cache, stats):
    path = fetch(GATITOS_URL, cache / "gatitos_en_sat.jsonl")
    entries = []
    with io.open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            r = json.loads(line)
            for target in r.get("trgs", []):
                entries.append(Entry(r["src"].strip(), target.strip(), "GATITOS", "word"))
    stats["files"].append(("gatitos/en_sat.jsonl", "GATITOS"))
    return entries


# --------------------------------------------------------------------------------------------
# build
# --------------------------------------------------------------------------------------------

def build(entries, hindi_keys, stats):
    """Normalise, gate, then group by English keeping every surviving variant."""
    by_en = {}
    for e in entries:
        if not e.en or not e.sat:
            stats["dropped_empty"] += 1
            continue

        sat, repaired, hyphens = normalise_ol_chiki(e.sat)
        stats["periods_repaired"] += repaired
        stats["hyphens_found"] += hyphens

        # BEFORE the reject gate: a cell listing alternatives must be split, or the joined string
        # becomes one speakable phrase. See split_synonyms.
        sat, extra_forms = split_synonyms(sat)
        if extra_forms:
            stats["synonyms_split"] += 1

        reason = rejection_reason(sat)
        if reason:
            stats["rejected"].append((e.en, e.sat, e.src, reason))
            continue

        flags = []
        if repaired:
            flags.append("repaired:%d" % repaired)
        if hyphens:
            flags.append("hyphen:%d" % hyphens)
        if extra_forms:
            flags.append("synonyms:%d" % (len(extra_forms) + 1))
        # A target with far more whitespace tokens than its source is often two variants that were
        # concatenated into one cell upstream. Flagged for review, not dropped -- Santali is more
        # analytic than English, so length alone is not proof.
        if len(sat.split()) >= len(e.en.split()) + 2:
            flags.append("longer-than-source")
            stats["flag_longer"] += 1

        key = e.en.strip().lower()
        bucket = by_en.setdefault(key, {"en": e.en.strip(), "variants": []})
        # Same Santali from the same source twice adds nothing.
        # The split-off synonyms are appended AFTER the primary with the same src. The sort below
        # is stable and ranks by src, so they cannot displace the primary; they just show up in the
        # `variants` column alongside cross-source disagreement.
        for n, form in enumerate([sat] + extra_forms):
            if not any(v.sat == form and v.src == e.src for v in bucket["variants"]):
                bucket["variants"].append(Entry(e.en.strip(), form, e.src, e.kind, e.pos,
                                                ",".join(flags) if n == 0 else "synonym-of-primary"))

    rank = {s: i for i, s in enumerate(PRECEDENCE)}
    rows = []
    for key, bucket in sorted(by_en.items()):
        variants = sorted(bucket["variants"], key=lambda v: rank.get(v.src, 99))

        # Numerals: Hembram unconditionally. Not left to precedence ordering, because GATITOS
        # publishing one string for six/seven/eight is a hazard for a counting worksheet and a
        # silent reordering of PRECEDENCE must not be able to reintroduce it.
        numeral_sources = [v for v in variants if v.src == "Hembram/Numerals"]
        if numeral_sources:
            dropped = [v for v in variants if v.src != "Hembram/Numerals"]
            if dropped:
                stats["numerals_overridden"] += len(dropped)
            variants = numeral_sources + [v for v in dropped if v.src.startswith("Hembram")]

        primary = variants[0]
        others = variants[1:]
        if others and any(v.sat != primary.sat for v in others):
            stats["disagreements"] += 1

        rows.append(
            {
                "en": bucket["en"],
                "hi": hindi_keys.get(key, ""),
                "sat": primary.sat,
                "src": primary.src,
                "kind": primary.kind,
                "pos": primary.pos,
                "flags": primary.flags,
                # Variants are kept so the consumer can see the disagreement rather than
                # inheriting a decision the data was not entitled to make.
                "variants": ";".join(
                    "%s|%s" % (v.sat, v.src) for v in others if v.sat != primary.sat
                ),
            }
        )
    return rows


def load_hindi_keys(path):
    """
    en (lowercased) -> `;`-joined Hindi lookup keys, from the hand-authored classroom mapping.

    **Repeating an English word on a second line adds an ALIAS rather than replacing the first.**
    Hindi says the same instruction several ways and only one of them can be the dictionary form:
    *sit* is `बैठो`, but a teacher says `बैठ जाओ`. Both must reach the same corpus row.

    Aliases are data, deliberately, and not a rule in `HindiNormalizer`. The general rule would be
    "drop the vector verb `जाओ`", and that is measurably unsafe: it also turns `बाहर जाओ` ("go
    outside") into `बाहर`, so a class asked to go outside hears "outside". `जाओ` is only aspectual
    after a bare verb stem, and telling that from a noun needs a POS tagger this project has no
    reason to own. Listing the two constructions that actually occur costs two lines and cannot
    over-generalise -- and a Santali speaker can review them row by row.
    """
    if not path.is_file():
        return {}
    keys = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) >= 2 and parts[1].strip():
            en = parts[0].strip().lower()
            hi = parts[1].strip()
            prior = keys.get(en)
            if prior:
                if hi not in prior.split(";"):
                    keys[en] = prior + ";" + hi
            else:
                keys[en] = hi
    return keys


HEADER = ["en", "hi", "sat", "src", "kind", "pos", "flags", "variants"]


def stem_report(rows, cache):
    """
    Reports glossary rows whose Santali has no support in `sat.dic`, a 126,745-word Ol Chiki list.

    ### Why this is an ingest report and NOT a runtime rejector

    The plan was to ship `sat.dic` in the APK as a validity oracle that flags implausible T1 output.
    Measured against our own 11-sentence eval set, it does not work, in three separate ways:

    1. **It passes the failure we actually have.** The wrong translation of *baith jao* is `ᱥᱮᱱ ᱢᱮ`
       ("go"). Both words are in `sat.dic`. Every word of all 11 outputs is valid except two, and
       the two rows scoring 0.00 are clean. Wrong word choice is not an invalid word.
    2. **It flags a correct answer.** `ᱛᱮᱜᱚ` (*tego*, stand) is absent, while the bare stem `ᱛᱮᱜ` and
       29 other `ᱛᱮᱜ`-forms are present. Santali is agglutinative and a corpus-derived list cannot
       hold every inflection, so absence is not evidence of error.
    3. **The source is not a lexicon.** It is a Wikipedia/Wiktionary dump: it contains place names,
       an entry that is `ᱛᱮ` + a multiplication sign, `ᱛᱮ` + danda, and underscored personal names.

    A caveat that fires on correct output and stays silent on wrong output is worse than no caveat,
    because it teaches a teacher to ignore it. The narrower job of catching blank or non-Ol-Chiki
    output is already done by `OlChikiNormalizer.rejectionReason`, in-repo and for free.

    What the list IS good for is exactly this: a one-shot desktop signal about which ingested rows
    have the weakest evidence behind them. Optional -- skipped, not fatal, when the cached file is
    absent, because `sat.dic` is 1.5 MB of un-versioned cache and not a build dependency.
    """
    dic = pathlib.Path(cache) / "ph" / "sat.dic"
    print()
    if not dic.is_file():
        print("stem support: skipped, no %s (optional)" % dic)
        return

    raw = dic.read_bytes()
    enc = "utf-8-sig" if raw[:3] == b"\xef\xbb\xbf" else "utf-8"
    vocab = set()
    for line in raw.decode(enc, "replace").splitlines()[1:]:
        line = line.strip()
        if line:
            vocab.add(line.split("/")[0])

    def supported(word):
        return any(word[:n] in vocab for n in range(len(word), 1, -1))

    weak = []
    for r in rows:
        words = [w for w in r["sat"].split() if any(is_ol_chiki(c) for c in w)]
        if words and not all(supported(w) for w in words):
            weak.append(r)

    reachable = [r for r in weak if r["hi"]]
    print("stem support against sat.dic (%d words)" % len(vocab))
    print("  rows with a word having no stem  : %d / %d" % (len(weak), len(rows)))
    print("  ...of those, reachable from Hindi: %d   <- the only ones a class can hear" % len(reachable))
    for r in sorted(reachable, key=lambda x: x["en"])[:12]:
        print("      %-20s hi=%-12s src=%-14s %s"
              % (r["en"][:20], r["hi"][:12], r["src"][:14],
                 " ".join("U+%04X" % ord(c) for c in r["sat"])))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cache", default="E:/research", help="where source downloads are kept")
    ap.add_argument("--out", default="app/src/main/assets/santali-glossary.tsv")
    ap.add_argument("--hindi-keys", default="tools/santali-hindi-keys.tsv")
    ap.add_argument("--report-only", action="store_true")
    args = ap.parse_args()

    cache = pathlib.Path(args.cache)
    stats = {
        "files": [], "dropped_empty": 0, "rejected": [], "periods_repaired": 0,
        "hyphens_found": 0, "flag_longer": 0, "disagreements": 0, "numerals_overridden": 0,
        "synonyms_split": 0,
    }

    print("sources")
    entries = load_hembram(cache, stats) + load_gatitos(cache, stats)
    print("  %d raw entries from %d files" % (len(entries), len(stats["files"])))

    hindi_keys = load_hindi_keys(pathlib.Path(args.hindi_keys))
    print("  %d hand-authored Hindi keys" % len(hindi_keys))

    rows = build(entries, hindi_keys, stats)

    print()
    print("ingest gate")
    print("  periods repaired ('.' -> U+1C79) : %d" % stats["periods_repaired"])
    print("  hyphens reported, left alone     : %d" % stats["hyphens_found"])
    print("  flagged longer-than-source       : %d" % stats["flag_longer"])
    print("  rejected outright                : %d" % len(stats["rejected"]))
    for en, sat, src, reason in stats["rejected"][:8]:
        print("      %-22s %-14s %s  (%r)" % (en[:22], src, reason, sat[:20]))
    print("  numeral variants overridden      : %d" % stats["numerals_overridden"])
    print("  multi-synonym cells split        : %d" % stats["synonyms_split"])
    print("  English keys with disagreement   : %d" % stats["disagreements"])

    stem_report(rows, cache)

    with_hi = sum(1 for r in rows if r["hi"])
    print()
    print("result")
    print("  distinct English keys            : %d" % len(rows))
    print("  with a Hindi key                 : %d  (%.1f%%)"
          % (with_hi, 100.0 * with_hi / max(1, len(rows))))
    print("  usable for Hindi lookup / bias    : %d" % with_hi)
    by_kind = {}
    for r in rows:
        by_kind[r["kind"]] = by_kind.get(r["kind"], 0) + 1
    print("  by kind                          : %s" % by_kind)

    if args.report_only:
        return 0

    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    # TSV, LF, no BOM. Same reasoning as export-spm-table.py: this is read by Kotlin and by JVM
    # unit tests, where Android's org.json is a throwing stub, and no field here can contain a tab.
    with io.open(out, "w", encoding="utf-8", newline="\n") as f:
        f.write("\t".join(HEADER) + "\n")
        for r in rows:
            for col in HEADER:
                assert "\t" not in r[col], "tab inside field %r: %r" % (col, r[col])
                assert "\n" not in r[col], "newline inside field %r" % col
            f.write("\t".join(r[col] for col in HEADER) + "\n")
    print()
    print("wrote %s  (%.1f KB)" % (out, out.stat().st_size / 1024.0))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
