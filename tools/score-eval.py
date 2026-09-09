"""Score the Hindi -> Santali eval, so quality stops being a hand judgement.

Reads the side-by-side output that `eval-hindi-santali.py` already produces and turns it into a
single repeatable number, plus a per-sentence table naming which sentences fail.

WHY THIS EXISTS
---------------
The quality claim on record was "about 5 of 11 acceptable", counted by eye. That cannot be
compared across changes, cannot be regressed against, and cannot tell you whether a glossary or a
different decode setting helped or hurt. Every improvement from here has to beat a number.

WHAT IT MEASURES, AND THE HONEST LIMITS
---------------------------------------
Nobody on this project reads Ol Chiki, so the forward output is unreviewable directly. What IS
reviewable is the round trip: Hindi -> Santali -> Hindi. This scores the returned Hindi against the
original.

That is a WEAK proxy and the limits are real:

  * It shares the model's biases. The same weights make both hops, so a systematic
    misunderstanding can round-trip cleanly and still be wrong.
  * A high score does not prove the Santali is good Santali. Only a speaker proves that.
  * A low score does not always mean a bad translation. Hindi has many ways to say the same
    thing, and the back hop may legitimately pick a different one.

What it IS reliably good at is exactly our known failure mode: WRONG WORD CHOICE. When the model
translates "sit down" as "go", the round trip comes back with a visibly different Hindi verb, and
the score drops. Verified on the two logged failures:

    baith jao      -> [wrong stem]      -> "chale jao"        (go)
    khade ho jao   -> [wrong stem]      -> "sthapit karen"    (install)

Both are caught. So this is a usable regression gate for lexical selection, which is the thing the
research says to fix, while being useless as a claim about fluency. Do not quote it as a quality
figure to anyone outside the project.

METRICS
-------
Two, because they fail differently and disagreeing is informative:

  token F1     word overlap. Harsh on short sentences; one wrong word out of three is a big drop.
  chrF2        character-bigram F1. The standard choice for morphologically rich languages, and it
               gives partial credit for a nearly-right inflection where token F1 gives none.

`ponytail:` chrF2 is hand-rolled rather than pulling in sacrebleu. It is ~15 lines, has no
dependency, and the numbers only ever get compared against each other. The ceiling: these are NOT
comparable to published chrF++ figures, so they must never appear in a paper or a slide. If that is
ever needed, install sacrebleu and use it instead of this.

Usage:
    python tools/score-eval.py                                  # score the committed output
    python tools/score-eval.py --input tools/santali-eval-output.txt
    python tools/score-eval.py --baseline tools/eval-baseline.json --check
"""

import argparse
import io
import json
import pathlib
import re
import sys

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

# A sentence is "acceptable" at or above this token F1. Chosen to reproduce the hand count of
# roughly 5 of 11 that the project already recorded, so the number is anchored to a judgement a
# human actually made rather than picked to flatter a result.
ACCEPT_TOKEN_F1 = 0.50

DEVANAGARI = re.compile(r"[\u0900-\u097F]")
# Danda, double danda and Latin sentence punctuation. The back translation almost always appends a
# danda that the input did not have, and scoring that as a difference measures nothing.
PUNCT = re.compile(r"[\u0964\u0965.,?!;:\u2014\-]")


def normalise(text):
    """Strip punctuation and collapse whitespace. Nothing script-specific."""
    return " ".join(PUNCT.sub(" ", text).split())


def _f1(a_items, b_items):
    """Multiset F1 over two sequences."""
    if not a_items or not b_items:
        return 0.0
    remaining = list(b_items)
    common = 0
    for item in a_items:
        if item in remaining:
            common += 1
            remaining.remove(item)
    if common == 0:
        return 0.0
    precision = common / len(b_items)
    recall = common / len(a_items)
    return 2 * precision * recall / (precision + recall)


def token_f1(reference, hypothesis):
    return _f1(normalise(reference).split(), normalise(hypothesis).split())


def chrf2(reference, hypothesis):
    """Character-bigram F1, spaces removed."""
    def bigrams(text):
        flat = normalise(text).replace(" ", "")
        return [flat[i:i + 2] for i in range(len(flat) - 1)]

    return _f1(bigrams(reference), bigrams(hypothesis))


def parse_eval_output(text):
    """Pulls (hindi, santali, back) triples out of the eval script's printed blocks.

    Tolerant of the separator length and of the mojibake case: if the file was written through a
    cp437 console it is double-encoded, and repairing it here is cheaper than re-running the model.
    """
    if not DEVANAGARI.search(text):
        for encoding in ("cp437", "cp1252", "cp850"):
            try:
                repaired = text.encode(encoding).decode("utf-8")
            except (UnicodeEncodeError, UnicodeDecodeError):
                continue
            if DEVANAGARI.search(repaired):
                text = repaired
                break

    rows = []
    for block in re.split(r"-{20,}", text):
        hi = re.search(r"^\s*hi\s+(.+)$", block, re.M)
        sat = re.search(r"^\s*sat\s+(.+)$", block, re.M)
        back = re.search(r"^\s*back\s+(.+)$", block, re.M)
        if hi and sat and back:
            rows.append(
                {
                    "hindi": hi.group(1).strip(),
                    "santali": sat.group(1).strip(),
                    "back": back.group(1).strip(),
                }
            )
    return rows


def ol_chiki_count(text):
    return sum(1 for ch in text if 0x1C50 <= ord(ch) <= 0x1C7F)


def score(rows):
    scored = []
    for row in rows:
        tf = token_f1(row["hindi"], row["back"])
        cf = chrf2(row["hindi"], row["back"])
        scored.append(
            {
                **row,
                "token_f1": round(tf, 4),
                "chrf2": round(cf, 4),
                "accepted": tf >= ACCEPT_TOKEN_F1,
                # Catastrophic cases the F1 alone would not name.
                "empty_santali": ol_chiki_count(row["santali"]) == 0,
                "copied_input": normalise(row["santali"]) == normalise(row["hindi"]),
            }
        )
    n = len(scored) or 1
    summary = {
        "sentences": len(scored),
        "mean_token_f1": round(sum(s["token_f1"] for s in scored) / n, 4),
        "mean_chrf2": round(sum(s["chrf2"] for s in scored) / n, 4),
        "accepted": sum(1 for s in scored if s["accepted"]),
        "empty": sum(1 for s in scored if s["empty_santali"]),
        "copied": sum(1 for s in scored if s["copied_input"]),
        "accept_threshold_token_f1": ACCEPT_TOKEN_F1,
    }
    return summary, scored


def report(summary, scored, out):
    def w(line=""):
        out.write(line + "\n")

    w("Hindi -> Santali -> Hindi round-trip score")
    w("=" * 100)
    w("PROXY METRIC. Good at catching wrong word choice; says nothing about whether the Santali")
    w("reads well. Only a Santali speaker can say that. Never quote these outside the project.")
    w("=" * 100)
    w()
    w("%-3s %-44s %7s %7s %4s  %s" % ("#", "hindi in", "tokF1", "chrF2", "ok", "hindi back"))
    w("-" * 130)
    for i, s in enumerate(scored, 1):
        flags = ""
        if s["empty_santali"]:
            flags += " [NO OL CHIKI]"
        if s["copied_input"]:
            flags += " [COPIED INPUT]"
        w(
            "%-3d %-44s %7.2f %7.2f %4s  %s%s"
            % (i, s["hindi"], s["token_f1"], s["chrf2"],
               "y" if s["accepted"] else ".", s["back"], flags)
        )
    w()
    w("mean token F1 : %.3f" % summary["mean_token_f1"])
    w("mean chrF2    : %.3f" % summary["mean_chrf2"])
    w(
        "accepted      : %d of %d  (token F1 >= %.2f)"
        % (summary["accepted"], summary["sentences"], summary["accept_threshold_token_f1"])
    )
    if summary["empty"]:
        w("EMPTY OUTPUT  : %d" % summary["empty"])
    if summary["copied"]:
        w("COPIED INPUT  : %d" % summary["copied"])


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", default="tools/santali-eval-output.txt")
    ap.add_argument("--baseline", default="tools/eval-baseline.json")
    ap.add_argument(
        "--write-baseline",
        action="store_true",
        help="Record the current scores as the baseline to compare future runs against.",
    )
    ap.add_argument(
        "--check",
        action="store_true",
        help="Exit non-zero if the mean token F1 fell below the baseline. For use in a gate.",
    )
    args = ap.parse_args()

    path = pathlib.Path(args.input)
    if not path.is_file():
        print("not found: %s" % path)
        print("Run tools/eval-hindi-santali.py first to produce it.")
        return 2

    rows = parse_eval_output(path.read_text(encoding="utf-8", errors="replace"))
    if not rows:
        print("no 'hi/sat/back' blocks found in %s" % path)
        return 2

    summary, scored = score(rows)
    report(summary, scored, sys.stdout)

    baseline_path = pathlib.Path(args.baseline)
    if args.write_baseline:
        baseline_path.write_text(
            json.dumps({"summary": summary, "sentences": scored}, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        print()
        print("baseline written to %s" % baseline_path)
        return 0

    if args.check:
        if not baseline_path.is_file():
            print()
            print("no baseline at %s; run with --write-baseline first" % baseline_path)
            return 2
        base = json.loads(baseline_path.read_text(encoding="utf-8"))["summary"]
        delta = summary["mean_token_f1"] - base["mean_token_f1"]
        print()
        print(
            "baseline mean token F1 %.3f -> now %.3f  (%+.3f)"
            % (base["mean_token_f1"], summary["mean_token_f1"], delta)
        )
        print(
            "baseline accepted %d -> now %d"
            % (base["accepted"], summary["accepted"])
        )
        # A tolerance, not zero: the same model can differ in the last decimal across machines,
        # and greedy decoding is path dependent, so an exact-equality gate would flap.
        if delta < -0.01:
            print("REGRESSION: mean token F1 dropped by more than 0.01")
            return 1
        print("no regression")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
