"""Export the SentencePiece piece table so Kotlin can tokenise without SentencePiece.

WHY
---
`IndicTrans2Decoder` takes token ids and returns token ids. On desktop the ids came from the HF
tokenizer; on the tablet there is nothing to produce them. There is no SentencePiece for Android
(sentencepiece-jni's tracker rules it out) and NDK-building it plus a protobuf parser is far more
work than the algorithm. So the model's tables are flattened here, once, into a file Kotlin can
read with a split on tab.

IT IS BPE, NOT UNIGRAM
----------------------
Worth stating loudly because the plan assumed otherwise: `trainer_spec.model_type` is **BPE**.
A unigram Viterbi — maximise the summed score over all segmentations — is the wrong algorithm and
would produce different, silently-wrong splits. SentencePiece BPE instead:

    1. normalise, escape spaces to U+2581, prepend a dummy U+2581
    2. explode into one symbol per character
    3. repeatedly merge the adjacent pair whose concatenation is in the vocab and has the best
       score, until no adjacent pair is in the vocab
    4. map each surviving piece to a vocabulary id, <unk> if absent

The `score` column is what makes step 3 deterministic. In this model the scores are exactly
-(sp_id - 5), i.e. negative rank, which is how SentencePiece stores BPE merge priority: lower rank
merges first. Nothing in the algorithm depends on them being true log-probabilities.

TWO ID SPACES, DO NOT CONFLATE THEM
-----------------------------------
`src.model` holds 128000 pieces under its OWN internal ids. `dict.SRC.json` / `dict.TGT.json` map
piece STRING to the model's 122706/122672-entry vocabulary. Those id spaces are different, so the
route is piece-string -> dict, never sp_id -> model. Using the SentencePiece ids would index the
wrong embedding rows and produce fluent output about something else.

src.model and tgt.model are byte-identical — one shared piece table, two direction-specific dicts —
so both id columns come from the same piece list.

OUTPUTS
-------
    models/mt-hi-sat/spm.tsv              piece \t score \t srcId \t tgtId   (-1 when absent)
    app/src/test/resources/spm-parity.tsv corpus with expected pieces and ids, for the Kotlin test

The parity corpus is committed; spm.tsv is not, because models/ is gitignored. That is why the
Kotlin test suite has two halves — see IndicTrans2TokenizerTest.

Usage:
    $env:HF_HOME="E:\\hf-cache"
    .venv-it2\\Scripts\\python.exe tools/export-spm-table.py
"""

import json
import pathlib
import sys

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

import sentencepiece as spm

ROOT = pathlib.Path(__file__).resolve().parent.parent
MODEL_DIR = ROOT / "models" / "mt-hi-sat"
TABLE = MODEL_DIR / "spm.tsv"
PARITY = ROOT / "app" / "src" / "test" / "resources" / "spm-parity.tsv"

SRC_TAG = "hin_Deva"
TGT_TAG = "sat_Olck"

# Deliberately adversarial. Each line is here because it exercises something that could silently
# differ between SentencePiece's normaliser and a hand-written one, or between BPE and a
# plausible-but-wrong merge order.
CORPUS = [
    # The classroom phrases the app actually says.
    "किताब खोलो",
    "किताब बंद करो",
    "ताली बजाओ",
    "ध्यान से सुनो",
    "अपना नाम बताओ",
    "मेरे साथ बोलो",
    "बैठ जाओ",
    "खड़े हो जाओ",
    "पानी पीना है",
    "आज हम गिनती सीखेंगे",
    "कल छुट्टी है इसलिए स्कूल बंद रहेगा",
    # Sentence-final punctuation. HindiNormalizer strips danda; MT input must not.
    "ताली बजाओ ।",
    "यह क्या है? बहुत अच्छा! ठीक है।",
    "दोहरा दंड ॥",
    # Digits, both ASCII and Devanagari, which IndicTrans2 handles natively.
    "पेज 5 खोलो",
    "पेज ५ खोलो",
    "1234567890",
    "कक्षा 10 में 42 बच्चे हैं",
    # Whitespace handling: remove_extra_whitespaces and add_dummy_prefix.
    "आज  हम   गिनती सीखेंगे",
    "  leading and trailing  ",
    "\tटैब\tसे\tअलग\t",
    "एक\nनयी\nपंक्ति",
    # Nukta: composed vs decomposed forms normalise differently.
    "क़िताब ज़रा फ़ोन",
    "क\u093cिताब ज\u093cरा",
    # Zero-width joiners, which HindiNormalizer drops and this must not.
    # ZWJ (200D) is KEPT by nmt_nfkc; ZWNJ (200C) becomes a SPACE. Adjacent codepoints, opposite
    # treatment — the single most likely thing to get backwards.
    "क\u200dिताब",
    "अ\u200cब",
    "क\u200dष अ\u200cब क\u200dिताब",
    # The rest of the space-mapped set, none of which Character.isWhitespace() agrees about.
    "अ\u200bब",       # ZERO WIDTH SPACE   -> space
    "अ\ufeffब",       # BOM / ZWNBSP       -> space
    "अ\u00a0ब",       # NO-BREAK SPACE     -> space
    "अ\u202fब",       # NARROW NBSP        -> space
    "अ\u2007ब",       # FIGURE SPACE       -> space
    "अ\u3000ब",       # IDEOGRAPHIC SPACE  -> space
    "अ\u2028ब",       # LINE SEPARATOR     -> space
    # Deleted vs kept controls. U+0000 is kept, U+0001 is deleted; U+008F deleted, U+0085 kept;
    # U+000B deleted although Java calls it whitespace; U+00AD kept although most strippers drop it.
    "अ\u0000ब",
    "अ\u0001ब",
    "अ\u000bब",
    "अ\u001fब",
    "अ\u0085ब",
    "अ\u008fब",
    "अ\u00adब",
    "अ\u2060ब",       # WORD JOINER        -> kept
    # Script mixing, which is what split_by_unicode_script was trained around.
    "school जाओ",
    "COVID-19 का टीका",
    "e-mail भेजो",
    # NFKC territory.
    "ﬁne",
    "ＡＢＣ ａｂｃ",
    "①②③",
    "Ⅳ अध्याय",
    "①  ﬁ  Ａ",
    # Rare and unmappable, to exercise the <unk> path.
    "﷽",
    "😀 हँसो",
    "\u0000\u0001control",
    # Target script, for the decode direction.
    "ᱟᱨᱦᱚᱲ",
    "ᱯᱚᱛᱚᱵ ᱫᱚ ᱮᱦᱚᱵ ᱢᱮ ᱾",
    # Degenerate inputs.
    "",
    " ",
    "।",
    "अ",
    # Long, to catch anything quadratic or length-capped.
    "आज हम गिनती सीखेंगे और फिर कल छुट्टी है इसलिए स्कूल बंद रहेगा तो सोमवार को "
    "सब बच्चे अपनी किताब और कॉपी लेकर समय पर आएँ",
]


def main() -> int:
    if not (MODEL_DIR / "src.model").is_file():
        print(f"missing {MODEL_DIR / 'src.model'} — run tools/stage-mt-model.py first")
        return 1

    sp = spm.SentencePieceProcessor()
    sp.Load(str(MODEL_DIR / "src.model"))
    src_dict = json.loads((MODEL_DIR / "dict.SRC.json").read_text(encoding="utf-8"))
    tgt_dict = json.loads((MODEL_DIR / "dict.TGT.json").read_text(encoding="utf-8"))

    n = sp.GetPieceSize()
    print(f"pieces in src.model      : {n}")
    print(f"entries in dict.SRC.json : {len(src_dict)}")
    print(f"entries in dict.TGT.json : {len(tgt_dict)}")

    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    mapped_src = mapped_tgt = 0
    with TABLE.open("w", encoding="utf-8", newline="\n") as fh:
        # A tab or newline inside a piece would corrupt the file irrecoverably. Whitespace is
        # escaped to U+2581 by SentencePiece so this should be impossible; assert rather than hope,
        # because the failure would look like a tokenizer bug much later.
        for i in range(n):
            p = sp.IdToPiece(i)
            if "\t" in p or "\n" in p or "\r" in p:
                print(f"FAIL: piece {i} contains a tab or newline: {p!r}")
                return 2
            s = src_dict.get(p, -1)
            t = tgt_dict.get(p, -1)
            mapped_src += s != -1
            mapped_tgt += t != -1
            fh.write(f"{p}\t{sp.GetScore(i):.0f}\t{s}\t{t}\n")

        # Entries that exist in the fairseq dictionaries but are NOT SentencePiece pieces: the
        # language tags, the specials, and a few thousand others. They are needed for their ids
        # only and must never take part in BPE merging — if "hin_Deva" were a mergeable piece,
        # text that happened to contain those characters could merge into the tag token. The "-"
        # in the score column marks them as id-only, and the Kotlin loader keeps them out of the
        # merge vocabulary.
        sp_pieces = {sp.IdToPiece(i) for i in range(n)}
        extra = sorted((set(src_dict) | set(tgt_dict)) - sp_pieces)
        for p in extra:
            if "\t" in p or "\n" in p or "\r" in p:
                print(f"FAIL: dict entry contains a tab or newline: {p!r}")
                return 2
            fh.write(f"{p}\t-\t{src_dict.get(p, -1)}\t{tgt_dict.get(p, -1)}\n")

    print(f"\nwrote {TABLE}  ({TABLE.stat().st_size / 1024 / 1024:.1f} MB)")
    print(f"  pieces with a src vocab id : {mapped_src} ({n - mapped_src} will fall back to <unk>)")
    print(f"  pieces with a tgt vocab id : {mapped_tgt}")
    print(f"  id-only entries (no merge) : {len(extra)}  e.g. {extra[:3]}")

    # The language tags are whole vocabulary entries and must NOT be run through BPE. Encoding
    # "hin_Deva sat_Olck …" shreds them into ['▁hin','_','D','e','va',…]. Record the ids so the
    # Kotlin side can assert it is prepending them rather than tokenising them.
    tags = {
        "src_tag": SRC_TAG, "tgt_tag": TGT_TAG,
        "src_tag_id": src_dict[SRC_TAG], "tgt_tag_id": src_dict[TGT_TAG],
    }
    print(f"\ntags: {SRC_TAG}={tags['src_tag_id']}, {TGT_TAG}={tags['tgt_tag_id']}")

    # TSV rather than JSON on purpose. This file is read by a JVM unit test, where Android's
    # org.json is a stub that throws "not mocked", and pulling in a JSON library just to read a
    # test fixture is not worth it. Pieces can never contain a literal space or tab — SentencePiece
    # escapes whitespace to U+2581 — so space-joining them is safe. Only the source text needs
    # escaping, since the corpus deliberately includes tabs and newlines.
    def esc(s: str) -> str:
        return (
            s.replace("\\", "\\\\").replace("\t", "\\t")
            .replace("\n", "\\n").replace("\r", "\\r")
        )

    lines = [
        "# Parity fixture for IndicTrans2TokenizerTest. Generated by tools/export-spm-table.py.",
        "# The Kotlin tokenizer must reproduce these exactly; anything else is silently wrong",
        "# output in the wrong language rather than an error.",
        "#",
        "# model_type=BPE  normalizer=nmt_nfkc",
        f"# {SRC_TAG}={tags['src_tag_id']}  {TGT_TAG}={tags['tgt_tag_id']}  "
        f"eos=2  unk={src_dict['<unk>']}",
        "#",
        "# E<TAB>text (\\t \\n \\r \\\\ escaped)<TAB>pieces joined by space<TAB>ids joined by comma",
        "# D<TAB>ids joined by comma<TAB>expected decoded text",
    ]

    n_enc = 0
    for text in CORPUS:
        pieces = sp.EncodeAsPieces(text)
        ids = (
            [src_dict[SRC_TAG], src_dict[TGT_TAG]]
            + [src_dict.get(p, src_dict["<unk>"]) for p in pieces]
            + [2]
        )
        lines.append(f"E\t{esc(text)}\t{' '.join(pieces)}\t{','.join(map(str, ids))}")
        n_enc += 1

    # Decode direction, using the real model outputs from the decode fixture so the test covers
    # the path the app will actually take rather than synthetic ids.
    n_dec = 0
    fixture = ROOT / "tools" / "mt-device-fixture.json"
    if fixture.is_file():
        for rec in json.loads(fixture.read_text(encoding="utf-8"))["sentences"]:
            ids = ",".join(map(str, rec["expected_output_ids"]))
            lines.append(f"D\t{ids}\t{esc(rec['expected_text_raw'])}")
            n_dec += 1
    else:
        print("  (tools/mt-device-fixture.json absent — no decode cases)")

    PARITY.parent.mkdir(parents=True, exist_ok=True)
    PARITY.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"wrote {PARITY}  ({PARITY.stat().st_size / 1024:.1f} KB, "
          f"{n_enc} encode + {n_dec} decode cases)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
