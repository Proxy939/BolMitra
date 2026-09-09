"""Turns the supplied Hindi->Santali classroom packs into a shippable asset.

Produces `app/src/main/assets/santali-classroom-packs.tsv`, loaded by
`phrasebook/ClassroomPacks.kt`.

WHY THIS IS A SEPARATE ASSET FROM THE GLOSSARY
==============================================
`santali-glossary.tsv` is documented as CC0/CC-BY corpus data and every row in it serves at
`Provenance.CORPUS` - verbatim from a published, citable source. This file is neither: it was
supplied for this app, it cites no corpus, and no named speaker has signed it off. Mixing the two
would force the glossary to carry a provenance column and would quietly weaken a file whose whole
claim is that its contents are quotable.

Keeping them apart also makes precedence explicit and checkable. `LiveTurnEngine` concatenates
sources and `PhraseMatcher`'s exact rung takes the FIRST match, so the order is
`DemoSeed` -> `SantaliGlossary` -> `ClassroomPacks`, and these rows can only ever fill a gap. That
matters concretely: the uploaded file disagrees with the shipped corpus on 10 normalised keys,
including the two that Revision 27 established as working. See `--report` output.

WHAT IS REJECTED, AND WHY EACH RULE EXISTS
==========================================
Every gate below fired on real rows in the supplied file.

  latin      A Santali cell containing Latin letters is a generation artifact. Four rows had them,
             including `( u line ...  )` and a bare `Permission`. Spoken to a class, the voice
             reads the English word.
  brackets   A parenthetical gloss is an editorial note, not speech. Thirteen rows had them. The
             glossary pipeline already rejects this class - see the existing test
             `no Hindi-reachable target carries list punctuation`.
  no_olchiki A row with no Ol Chiki at all cannot be what it claims to be.
  combining  A word may not begin with a combining mark; that indicates a truncated cluster.
  dup        The same Hindi mapping to two different Santali strings inside one file is an
             ambiguity about what a class hears. First occurrence wins, deterministically, and the
             loser is reported rather than dropped silently.

A `/` in the Hindi cell offers alternative wordings ("shant raho / chup raho"). Each becomes its
own lookup key, which is the same aliasing `santali-hindi-keys.tsv` uses.

The Ol Chiki MUCAAD (U+1C7E) is kept. It is Santali's own full stop, not punctuation to strip, and
the glossary builder already repairs ASCII periods into it.
"""
import argparse
import collections
import io
import os
import pathlib
import re
import sys
import unicodedata

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

OL_LO, OL_HI = 0x1C50, 0x1C7F
MUCAAD = "\u1C7E"
DANDA, DOUBLE_DANDA = "\u0964", "\u0965"

# Mirrors HindiNormalizer.foldedTokens. Kept in sync by a test on the Kotlin side.
FOLDED = {"जी", "ज़रा", "जरा", "please", "प्लीज़", "प्लीज"}


def norm_hi(s):
    """Approximates HindiNormalizer, only for collision detection in this tool."""
    s = unicodedata.normalize("NFC", s)
    s = s.replace(DANDA, " ").replace(DOUBLE_DANDA, " ")
    s = re.sub(r"[?!,.;:\"'()\[\]]", " ", s)
    return " ".join(t for t in s.split() if t and t not in FOLDED).lower()


def unescape_markdown(s):
    r"""Removes the backslash escapes the source markdown carries.

    The supplied file is markdown, so `!` is written `\!` and `_` is written `\_`. Eleven Santali
    cells shipped with those backslashes still in them - `ᱟᱹᱰᱤ ᱱᱟᱯᱟᱭ\!` and
    `ᱟᱢᱟᱜ ᱚᱲᱟᱜ ᱠᱟᱹᱢᱤ ᱫᱚ \_\_\_ ᱠᱟᱱᱟ᱾` - which is text a voice would try to read.
    """
    return re.sub(r"\\([!_*()\[\]\-.#+])", r"\1", s)


def hindi_variants(cell):
    for part in cell.split("/"):
        cleaned = unicodedata.normalize("NFC", unescape_markdown(part)).strip()
        cleaned = cleaned.strip(DANDA).strip()
        if cleaned:
            yield cleaned


def santali_primary(cell):
    """The form a class hears, plus any alternatives the source offered.

    Nineteen Santali cells hold two translations separated by `/` - `ᱡᱚᱦᱟᱨ / ᱡᱚᱦᱟᱨ ᱜᱮ᱾` for
    नमस्ते, `ᱦᱮᱸ / ᱦᱮᱸ ᱜᱮ᱾` for हाँ. Shipped whole, the voice reads the slash and both variants
    aloud. Splitting the Hindi on `/` but not the Santali was the original oversight.

    The first form wins, which follows the glossary's own convention: it keeps disagreeing forms in a
    `variants` column and never merges them into what is spoken.
    """
    cleaned = unicodedata.normalize("NFC", unescape_markdown(cell)).strip()
    parts = [p.strip() for p in cleaned.split("/") if p.strip()]
    if not parts:
        return "", []
    return parts[0], parts[1:]


def parse(path):
    """Reads the markdown tables. Returns (pack, english, hindi_cell, santali)."""
    rows, pack = [], None
    for line in io.open(path, encoding="utf-8").read().splitlines():
        head = re.match(r"^#{2,4}\s*\**\s*(Pack\s*\d+[^*|]*)", line)
        if head:
            pack = head.group(1).strip().rstrip("*").strip()
            continue
        if not line.startswith("|"):
            continue
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) < 3:
            continue
        if cells[0].lower() == "english" or set(cells[0]) <= set(": -"):
            continue
        rows.append((pack or "(unpacked)", cells[0], cells[1], cells[2]))
    return rows


def gate(english, hindi, santali):
    """Returns a rejection reason, or None if the row may ship.

    Runs on the *cleaned* Santali — after unescaping and after taking the primary form — so it is
    checking what would actually be spoken rather than what the markdown happened to contain.
    """
    if not santali or not hindi:
        return "blank cell"
    if not any(OL_LO <= ord(c) <= OL_HI for c in santali):
        return "no Ol Chiki character present"
    if re.search(r"[A-Za-z]", santali):
        return "Latin letters in the Santali (generation artifact)"
    if "(" in santali or ")" in santali:
        return "bracketed editorial note the voice would read aloud"
    # These three are backstops: unescape and santali_primary should have removed them, so anything
    # reaching here means the cleaning missed a form and the row must not ship silently.
    if "\\" in santali:
        return "leftover markdown escape"
    if "/" in santali:
        return "leftover alternative form separator"
    if re.search(r"[0-9\u0966-\u096F]", santali):
        return "digits in the Santali"
    for word in santali.split():
        if word and unicodedata.combining(word[0]):
            return "a word begins with a combining mark"
    return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument(
        "--src",
        default="translation for Hindi words to Santali Langauge.md",
        help="the supplied markdown file",
    )
    ap.add_argument("--out", default="app/src/main/assets/santali-classroom-packs.tsv")
    ap.add_argument(
        "--glossary",
        default="app/src/main/assets/santali-glossary.tsv",
        help="used only to REPORT overlap; nothing is dropped because of it",
    )
    args = ap.parse_args()

    src = pathlib.Path(args.src)
    if not src.is_file():
        sys.exit("source not found: %s" % src)

    rows = parse(src)
    print("sources")
    print("  %d table rows across %d packs" % (rows and len(rows) or 0,
                                               len({r[0] for r in rows})))

    stats = collections.Counter()
    rejected = []
    kept = []                      # (hindi_key, santali, english, pack)
    seen = {}                      # normalised hindi -> (santali, english)

    for pack, english, hindi_cell, raw_santali in rows:
        # Clean BEFORE gating, so the gate judges the string that would be spoken.
        santali, alternatives = santali_primary(raw_santali)
        if alternatives:
            stats["alternative Santali forms dropped (first kept)"] += len(alternatives)
        reason = gate(english, hindi_cell, santali)
        if reason:
            stats[reason] += 1
            rejected.append((english, hindi_cell, raw_santali, reason))
            continue
        for hindi in hindi_variants(hindi_cell):
            key = norm_hi(hindi)
            if not key:
                continue
            if key in seen:
                if seen[key][0] != santali:
                    stats["duplicate Hindi with a different Santali"] += 1
                    rejected.append(
                        (english, hindi, santali,
                         "duplicate of %r which already maps to %r" % (seen[key][1], seen[key][0]))
                    )
                continue
            seen[key] = (santali, english)
            kept.append((hindi, santali, english, pack))

    print("gates")
    for reason, n in stats.most_common():
        print("  %-52s : %d" % (reason[:52], n))
    print("  %-52s : %d" % ("rows kept", len(kept)))

    # Report-only overlap with the corpus. Nothing is dropped here: precedence is enforced at
    # runtime by source order, which is checkable in a test rather than baked into an asset.
    gloss = pathlib.Path(args.glossary)
    if gloss.is_file():
        corpus = {}
        with io.open(gloss, encoding="utf-8") as fh:
            next(fh, None)
            for line in fh:
                f = line.rstrip("\n").split("\t")
                if len(f) >= 3 and f[1].strip():
                    for k in f[1].split(";"):
                        if k.strip():
                            corpus[norm_hi(k)] = f[2]
        overlap = [(h, s) for h, s, _, _ in kept if norm_hi(h) in corpus]
        differing = [(h, s, corpus[norm_hi(h)]) for h, s in overlap
                     if s.replace(MUCAAD, "").strip() != corpus[norm_hi(h)].replace(MUCAAD, "").strip()]
        print("overlap with the shipped corpus (report only)")
        print("  keys also in the corpus          : %d" % len(overlap))
        print("  ...of those, a DIFFERENT string  : %d" % len(differing))
        print("  the corpus wins every one of them at runtime: ClassroomPacks is concatenated last")
        for h, mine, theirs in differing[:12]:
            print("    hi=%r" % h)
            print("       packs  : %r" % mine)
            print("       corpus : %r  <- what a class actually hears" % theirs)

    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    with io.open(out, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("hi\tsat\ten\tpack\n")
        for hindi, santali, english, pack in kept:
            # Tabs and newlines cannot survive the format; nothing in the source has them, and a
            # silent truncation later would be worse than a loud replacement now.
            cells = [c.replace("\t", " ").replace("\n", " ") for c in (hindi, santali, english, pack)]
            fh.write("\t".join(cells) + "\n")

    print("result")
    print("  distinct Hindi keys : %d" % len(kept))
    print("  rejected rows       : %d" % len(rejected))
    print("wrote %s  (%.1f KB)" % (out, out.stat().st_size / 1024))

    # The log goes next to this script, NOT next to the asset. Writing it into
    # `app/src/main/assets/` packaged it into the APK - 6 KB of rejection notes shipped to every
    # tablet, and `assets/` is a directory where anything present is shipped by definition.
    log = pathlib.Path(__file__).with_name("_classroom-packs-rejected.txt")
    with io.open(str(log), "w", encoding="utf-8") as fh:
        fh.write("Rows rejected by build-classroom-packs.py. Not shipped; kept for review.\n\n")
        for english, hindi, santali, reason in rejected:
            fh.write("%s\n  hi  : %s\n  sat : %s\n  why : %s\n\n" % (english, hindi, santali, reason))
    print("rejection log %s (%d rows) - not shipped, kept for review" % (log, len(rejected)))


if __name__ == "__main__":
    main()
