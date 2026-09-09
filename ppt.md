# BolMitra — slide copy

PS 26042 · six slides · no paragraphs · export to PDF before upload.

Rules applied here: one idea per line, each fact stated **once** in the whole deck,
latency phrased as a target (never as measured), nothing claimed from ARCHITECTURE.md §1.4 or §10.

---

## Slide 1 · Title page

```
BolMitra
offline mother-tongue teaching assistant for tribal-area primary schools

Problem Statement ID   26042
Title                  AI-Powered Vernacular Pedagogy and Real-Time Translation
                       Tool for Mother Tongue-Based Primary Education
Theme                  <copy exact string from the portal listing>
PS Category            Software
Team ID / Team Name    <as registered>
```

Do not gloss the name here. Slide 2 does it once.

---

## Slide 2 · Proposed solution

**THE PROBLEM**

```
~61% of Jharkhand students are taught in a language that is not their mother tongue
Scheduled Tribes are 26.21% of the state — 8.6 million people
PALASH proves the method works; teacher supply is the block
A Hindi-medium teacher cannot say "open your book to page four" in Mundari
```

**DETAILED EXPLANATION OF THE PROPOSED SOLUTION**

```
BolMitra
Offline Android app for Hindi-medium teachers in tribal schools

• Teacher taps once, speaks Hindi, taps to stop
• Class hears the mother tongue, sentence by sentence
• Verified phrasebook first, AI translation on miss
• Every lesson becomes a bilingual worksheet automatically
• Works with no internet — packs arrive by USB

Ho · Mundari · Santhali — prototype demonstrates Hindi→Santali end to end
Bol + Mitra = a friend who speaks the child's language
```

**INNOVATION & UNIQUENESS**

```
• Live speech, not a lesson library
• Verified phrasebook first, neural MT only on miss
• 5,151-term Santali glossary from CC0/CC-BY corpora, each line citing its source
• The lesson writes the worksheet — what was said becomes the sheet, timestamped
• QR carries the worksheet spec — another school regenerates it offline, on paper
• Degrades honestly: verified / from corpus / approximate / machine, down to printed paper
```

Cut from this slide: the impact block (that is slide 5), `AI Lesson Planner`
(rejected in §1.4), `SD Card / Hotspot` (slides 3–4 own distribution),
`Hindi ↔` (must be `→`; no tribal ASR exists).

---

## Slide 3 · Technical approach

**TECHNOLOGIES TO BE USED** — name the job, not just the tool

```
LANGUAGES        Kotlin (Android) · Python (tools)
UI               Jetpack Compose · Room + SQLite FTS4
SPEECH RUNTIME   sherpa-onnx — one ONNX Runtime for ASR + TTS, not separate engines
ASR (HINDI)      IndicConformer int8, offline recogniser
TTS              MMS / VITS — Mundari voice, 27.51 h corpus
TRANSLATION      T0 phrasebook + glossary → T1 IndicTrans2 distilled 320M int8
SCRIPT           Ol Chiki → Devanagari from Unicode CLDR, bundled Noto Sans Ol Chiki
HARDWARE         Android 9+ (API 28), arm64, 2 GB RAM floor
```

**KEY HIGHLIGHTS**

```
✓ 100% offline — the app holds no internet permission at all
✓ Voice-to-voice design budget ≤ 3 s, measured per utterance from end of speech
✓ Signed content packs via USB / Wi-Fi Direct
✓ 5,151 Santali terms ingested from CC0/CC-BY sources, disagreements recorded not resolved
✓ Recordings stay in app-private storage — no gallery, no upload path
```

**SYSTEM ARCHITECTURE** — one top-to-bottom diagram, fork clearly visible

```
Teacher taps the mic, speaks Hindi
  → capture, cut at each pause
  → Hindi ASR
  → T0 lookup: verified phrasebook, then cited corpus glossary
       hit  → speak that row's own text
       miss → T1 machine translation → voice
  → class hears it, sees the native script, with a provenance label
  → the turn is saved: history, recording, and the lesson's worksheet

Hindi → Santali works end to end. Mundari & Ho ship as language packs.
Teacher corrections flow back into verified packs.
```

**METHODOLOGY & PROCESS**

```
PHASE 0  MEASURE     latency + memory baseline on the real procured tablet
PHASE 1  VOICE LOOP  end-to-end Hindi→Mundari turn, phrasebook only
PHASE 2  WORKSHEETS  NIPUN-aligned worksheets, flashcards, QR
PHASE 3  NEURAL MT   compact T1 + block prep station
PHASE 4  SCALE       Santhali (Ol Chiki + transliteration), then Ho
PHASE 5  VALIDATE    native-speaker review, classroom pilot
```

**WHY THIS APPROACH**

```
LOW-RESOURCE READY   install ~330–594 MB, fits tablets already procured
NO TRAINING NEEDED   the teacher keeps speaking Hindi
EXTENSIBLE           transliteration already covers Ol Chiki & Warang Citi
READABLE OUTPUT      Mundari uses Devanagari in Jharkhand — teacher reads it unaided
AFFORDABLE           ≈₹9,500 tablet, zero cloud inference cost
```

Also delete from this slide: the duplicate `@SIH Idea submission- Template` footer
and the three orphan numerals (`6`, `5`, `6`).

---

## Slide 4 · Feasibility and viability

**TECHNICALLY FEASIBLE** — evidence, not a repeat of slide 3

```
Mundari TTS exists — mms-tts-unr
27.51 h Mundari corpus
Hindi→Mundari corpus — Karya/MSR
Memory closes: 635 of 800 MB
```

**PRACTICALLY VIABLE**

```
Block laptop, already staffed
No CDN needed
Complements J-Guruji — it ships content, we handle live speech
```

**SCALABLE** — sequencing *is* the feasibility argument

```
Mundari — corpus + TTS + Devanagari
Santhali — MT ready, Ol Chiki pending
Ho — TTS only, corrections supply data
```

**RISKS & MITIGATION** — pair them, do not soften them

```
Reviewer capacity is the quality floor  →  5,151 cited corpus terms carry the load until a
                                            speaker signs off; every line names its source
Raw MT quality into these languages is low  →  phrasebook-first, provenance labels
CC-BY-NC on open TTS  →  school use is non-commercial; own-trained TTS = clean licence
≤3 s budget  →  measured per utterance from end of speech, not from the tap
Thirty children are also "speech"  →  the teacher opens and closes the mic; it also closes
                                        itself after 15 s so a tablet left down goes quiet
2 GB memory ceiling  →  one shared ASR and one shared voice, not one per language
```

Header line for this slide: `Top risk is native-speaker review capacity — a people
problem, with a measurement plan.` Do not restate the PS title here; slide 1 has it.

---

## Slide 5 · Impact and benefits

```
UNBLOCKS PALASH
Existing teacher, no language training

NO NEW SPEND
One block laptop, many schools
Fits tablets already bought

1,000+ REACHED, 5,000+ WAITING
Five PALASH languages, one codebase

NIPUN-QUERYABLE
Lakshya code on every phrase & worksheet
Maps to SDG 4

LEAVES A LANGUAGE RESOURCE
Corrections become verified parallel data

REACHES TABLET-LESS SCHOOLS
QR worksheets travel on paper

ROADMAP
Lakshya-wise progress reporting for the department
```

Big number for the slide: **~61%**, captioned `PALASH shows measurable FLN gains;
the block is teacher supply, not method.`

---

## Slide 6 · Research and references

**Problem evidence**

```
ASER 2011 — Jharkhand mother-tongue / school-language mismatch (source for ~61%)
PALASH / JEPC — five tribal languages, 1,000+ schools, 8 districts
NIPUN Bharat — FLN lakshya taxonomy
TLP Survey 2025 (LLF + Tata Trusts) — teacher language-use baseline
```

**Research**

```
AdiBhashaa — community-curated MT benchmark for Indian tribal languages (arXiv 2512.04765)
MMLoSo 2025 — shared task, 20,000 parallel pairs per language, 18 teams
MunTTS — Mundari TTS, 27.51 h, Devanagari, Jharkhand dialect (ComputEL-7 2024)
IndicTrans2 — 22 scheduled languages; defines the Santali-yes / Mundari-no boundary
MMS — source of the unr / hoc / sat VITS checkpoints (JMLR 2024)
sherpa-onnx — offline ASR + TTS + VAD runtime, Apache-2.0
Karya datasets — Hindi–Mundari parallel + 26,868 Mundari recordings
DIKSHA Energized Textbooks — QR-in-textbook precedent, 35 states/UTs
```

**Comparison table** — only axes no other slide claims

```
Ol Chiki on screen + Devanagari to the voice, in one pass
Provenance on every output, and a citation on every corpus line
QR spec — regenerate a sheet on a device that never had the file
Runs on a 2 GB tablet, no cloud inference
Corrections become training data
```

---

## Do not claim on any slide

`OCR` in any form. Measured against two published Santali models on 76 annotated handwriting
images: best character error rate **0.439**, worst **1.175**. The errors land on vowels, so they
change words rather than blurring them. Re-checkable with `tools/ocr-spike.py`. It is future scope,
not a feature.

Any accuracy figure for Hindi→Santali translation. The internal round-trip proxy is a development
gate, not a metric — it shares the model's own biases and says nothing about fluency.

Fix the two typos: `An fully offline` → `A fully offline`,
and `RESEARCH  AND` has a double space.

---

## Do not put on any slide

Pronunciation scoring · student read-aloud correction · live tribal→Hindi translation ·
auto-graded scanned worksheets · on-device LLM lesson planner · cloud dashboard ·
gamification. All rejected in §1.4 — each invites a question with no answer.

Lakshya-wise progress tracking may appear **only** labelled as roadmap (§10.1).
