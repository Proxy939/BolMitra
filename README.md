# BolMitra &nbsp;·&nbsp; बोलमित्र

> **Bol** (speak) + **Mitra** (friend) — the companion that lets a Hindi-medium teacher speak the child's language.

An **offline, on-device Android assistant** that bridges the language gap between Hindi-trained teachers and tribal-language-speaking children in Jharkhand's primary schools — in real time, with zero cloud dependency.

---

## The Problem This Solves

| Fact | Source |
|---|---|
| ~**61%** of Jharkhand students are taught in a language that is **not their mother tongue** | ASER 2011 |
| Scheduled Tribes are **26.21%** of Jharkhand's population — 8.6 million people | 2011 Census |
| PALASH's MTB-MLE programme covers **5 tribal languages** across **1,000+ schools** — but **5,000+ remain unreached** | JEPC / TOI |
| The dominant barrier is teacher supply, not method | TLP Survey 2025 |

A Hindi-medium teacher stands in front of thirty Mundari-speaking six-year-olds and **cannot say "open your book to page four"** in a language the children understand. BolMitra fixes that — without retraining the teacher, replacing the tablet, or connecting to the internet.

---

## Feature Overview

### 🎙️ Live Voice-to-Voice Translation

- ✅ **Push-to-talk interface** — teacher taps once, speaks Hindi, taps to stop; no mode switching, no menus mid-lesson
- ✅ **Offline Hindi ASR** — IndicConformer int8 via sherpa-onnx, 16 kHz mono, `VOICE_RECOGNITION` audio source with 4× min-buffer for budget hardware reliability
- ✅ **Phrasebook-first dispatch** — curated verified phrases go out instantly without touching the neural model; T0 path is sub-200 ms
- ✅ **T1 neural MT fallback** — IndicTrans2 distilled 320M int8, open-domain Hindi → Santali, runs entirely on-device
- ✅ **VITS speech synthesis** — Mundari voice (`mms-tts-unr`), 27.51 h corpus, Devanagari input
- ✅ **End-to-end pipeline live for Hindi → Santali** — speak Hindi, hear Santali, open-domain
- ✅ **15-second auto-stop** — mic closes itself so a tablet set down goes quiet; never captures classroom chatter unattended
- ✅ **Provenance label on every output** — `VERIFIED / CORPUS / APPROXIMATE / MACHINE` — the teacher always knows the confidence level

### 📖 Phrasebook & Glossary

- ✅ **5,151-term Santali glossary** ingested from CC0/CC-BY corpora — every entry cites its source; disagreements recorded, not resolved
- ✅ **FTS4 full-text search** with `unicode61` tokenizer — correct for Android's platform SQLite (`FTS5` is absent from API 28–34, a hard-won discovery)
- ✅ **HindiNormalizer** — nukta canonicalisation, schwa deletion, Nukta↔Anusvara normalisation, ZWJ stripping before any match
- ✅ **PhraseMatcher** — exact, prefix, and trigram-scored fuzzy search against the FTS4 candidate set
- ✅ **ClassroomPacks** — signed language packs delivered by USB or Wi-Fi Direct; no CDN, no server
- ✅ **Phrasebook Browser** — full-screen UI with category browse, search, audio playback, and correction flow
- ✅ **WordComposer** — builds phrase variants for akshara-level FLN exercises

### 📄 Bilingual Worksheet Generator

- ✅ **NIPUN Bharat aligned** — 33 real Class 1–2 learning outcomes transcribed and cited; ceiling targets corroborated across two government documents
- ✅ **Akshara-difficulty ladder** — Balvatika → Grade 3 literacy progression keyed on aksharas (syllable units), not English phonics — the distinction is research-backed (V41)
- ✅ **Numeracy seed** — addition/subtraction within grade-appropriate ranges (Grade 1: sum ≤ 20; Grade 2: ±99; Grade 3: ±999)
- ✅ **Interleaved item ordering** — adjacent items never share a solution strategy; confirmed 61% vs 38% retention advantage (Rohrer et al. 2020, d ≈ 0.83)
- ✅ **Picture bank using the device's own emoji font** — 84/84 glyphs verified against the test device, `Paint.hasGlyph` guard with letter-tile fallback; +0.1 MB total; downloads nothing
- ✅ **QR code on every sheet** — carries the worksheet spec, not a URL; another tablet regenerates the exact sheet offline with no file transfer required
- ✅ **Topic-triggered generation** — what the teacher said in the lesson picks the topic; the sheet is solvable by the class that just heard it
- ✅ **Lakshya tagging** — each item tagged with its NIPUN Bharat outcome position; cited on the sheet, not asserted

### 🔤 Script & Transliteration Engine

- ✅ **Ol Chiki → Devanagari** — derived from Unicode CLDR `sat_Olck → sat_FONIPA`; correctly handles positional voicing (`ᱛ → d` word-internally), aspiration digraphs (`ᱠᱷ → kh`), and voicing-switch diacritics (`ᱷ`, `ᱽ`, `ᱼ`) — all three of which a hand-built table would have got wrong
- ✅ **Bundled Noto Sans Ol Chiki** — absent on most budget OEM Android builds; bundled so Santali script always renders correctly, guaranteed
- ✅ **Devanagari → Odia** transliteration — for Ho language script output
- ✅ **OlChikiNormalizer** — Unicode normalisation and canonical form before any match or render operation
- ✅ **`OlChikiFont.annotate()`** — styles only the Ol Chiki runs in a mixed string; the face carries 53 codepoints and no Devanagari, so span-level styling is mandatory

### 🧠 On-Device ML Pipeline

- ✅ **Single ONNX Runtime** — sherpa-onnx (Apache-2.0); one runtime instance shared across ASR and TTS, no per-language engine duplication
- ✅ **RAM-tiered model loading** — `FLOOR` tier (< 3 GiB) loads batch ASR + phrasebook only; `ROOMY` tier (≥ 3 GiB) adds streaming ASR and T1 translation
- ✅ **int8 quantisation** — ASR batch model ~219 MB resident; VITS voice ~145 MB resident; T1 encoder 121 MB + fused decoder 195 MB; total measured stack **980 MB PSS**
- ✅ **Fused decoder** — encoder and decoder merged into a single ONNX graph, eliminating a model-load and an inter-session handshake
- ✅ **CPU-only inference** — `CPUExecutionProvider`; no NNAPI, no GPU delegate, no vendor accelerator — works on every arm64 SoC regardless of NPU availability
- ✅ **Model-sharing across languages** — one Hindi ASR serves every target language; Santali borrows Mundari's voice; eliminating per-language duplication dropped peak from 2.03 GB → 980 MB PSS (a measured regression, now fixed)
- ✅ **Downward-only tier override** — `FORCE_ROOMY` is honoured only if hardware already detects as ROOMY; native OOM inside ONNX Runtime aborts the process rather than throwing, and on API 28–29 there is no way to learn why the process died

### 💾 Data & Privacy Architecture

- ✅ **`android:allowBackup="false"` + explicit `dataExtractionRules`** — student recordings never reach Google Drive; Auto Backup disabled by design (V52)
- ✅ **No `INTERNET` permission in the manifest** — the app cannot phone home. There is no sync, no telemetry, no cloud inference path
- ✅ **No `CAMERA` permission** — unused dangerous permissions are not requested in an app used by children
- ✅ **Recordings in app-private storage** (`filesDir/recordings`) — never in shared media, never visible to other apps or the system gallery
- ✅ **Room database with exported schema migrations** — schema v2; `fallbackToDestructiveMigration` removed to prevent silent correction-outbox loss on update
- ✅ **Teacher correction outbox** — corrections are queued locally and become verified parallel data; the correction loop is an architectural component, not telemetry
- ✅ **`TurnRecorder`** — every voice turn saved with transcript, provenance, and recording; history is queryable for worksheet topic context

### 📱 Hardware & Device Compatibility

- ✅ **Android 9+ (API 28–36)** — `minSdk 28`, `targetSdk 36`, `compileSdk 37`; V36 confirmed `targetSdk` does not restrict device support
- ✅ **arm64-v8a only** — 64-bit Arm; ABI filter enforced in the build; every excluded ABI is an intentional, documented decision
- ✅ **16 KB page-size compliant** — every `LOAD` segment verified `p_align ≥ 0x4000` by `tools/check-apk-alignment.ps1`; NDK 28.2.13676358 pinned; `.so` files ship uncompressed (`useLegacyPackaging = false`) so alignment survives install
- ✅ **Microphone declared `required="false"`** — installs on a tablet with no mic; the UI handles capture being unavailable gracefully
- ✅ **No SD card required** — packs arrive by USB or Wi-Fi Direct; no removable-storage runtime path
- ✅ **No Google Play Services dependency** — sideloaded APK; no Firebase, no Play
- ✅ **`DeviceProbe`** — reads `/proc/cpuinfo` for dot-product support (`asimddp`), `StatFs` for free space, `ActivityManager` for RAM tier; conservative: unreadable file = feature absent
- ✅ **Thermal management** — `getThermalHeadroom()` (API 30+) with a three-tier fallback for API 28–29 devices that have no headroom API

### 🖥️ Screens & UI

- ✅ **Landing Screen** — language pack selection, tier detection, model-availability status badges
- ✅ **Live Class Pane** — three-column landscape tablet layout; real-time transcript, native-script display with inline Ol Chiki rendering, provenance badge per turn
- ✅ **Home Dashboard** — session summary, quick-action tiles, recent turn history
- ✅ **Phrasebook Screen** — category browse, full-text search, audio playback, correction submission flow
- ✅ **Worksheet Screen** — topic entry, grade selector, worksheet preview, QR share, print layout
- ✅ **Diagnostics Screen** — device spec, model load state, ring-buffer log, audio format report (`getFormat()` / `getClientFormat()`); no logcat needed
- ✅ **Settings Screen** — tier override (downward-only), language pack management, model path configuration
- ✅ **Phase 0 Screen** — latency and memory measurement harness; reports per-utterance pipeline timings for hardware baselining
- ✅ **Minimum text size 16 sp** for anything a teacher must read; 48×48 dp touch targets; WCAG 2.1 AA 4.5:1 contrast minimum
- ✅ **Colour-independent provenance** — shown as a glyph **and** a word as well as a hue; usable without colour vision

### 🛠️ Developer & Build Tooling

- ✅ **`tools/build-santali-glossary.py`** — ingests CC0/CC-BY corpora, deduplicates, records source citations per line
- ✅ **`tools/convert-mms-tts.py`** — converts `facebook/mms-tts-unr` checkpoint to sherpa-onnx VITS format
- ✅ **`tools/mt-merge-decoder.py`** — fuses IndicTrans2 encoder and decoder into a single ONNX graph
- ✅ **`tools/eval-hindi-santali.py`** + **`tools/score-eval.py`** — round-trip translation evaluation harness with per-sentence logging
- ✅ **`tools/verify-onnx-santali.py`** — verifies ONNX model outputs against a fixture set before any deployment
- ✅ **`tools/check-apk-alignment.ps1`** — 16 KB page-alignment hard build gate; warns (not fails) on `PT_GNU_RELRO` end, which AndroidX itself also fails
- ✅ **`tools/fetch-models.ps1`** — downloads and stages model files to the correct sideload path with integrity checking
- ✅ **`tools/inspect-asr.py`** — prints ASR model graph inputs/outputs for debugging and quantisation verification
- ✅ **`tools/ui-dump.py`** + **`tools/tap-by-text.ps1`** — uiautomator-based UI verification (required because MIUI silently drops logcat output)
- ✅ **`tools/ocr-spike.py`** — Santali handwriting OCR benchmark (best CER 0.439 on 76 annotated images; recorded as future scope, not a current feature)
- ✅ **144 unit tests** — ASR pipeline, transliteration, phrasebook matching, worksheet assembly, QR encode/decode

---

## Architecture at a Glance

```
Teacher taps mic → speaks Hindi
  └─ AudioCapture  (16 kHz mono, VOICE_RECOGNITION, 4× min-buffer)
       └─ Hindi ASR  (IndicConformer int8, sherpa-onnx)
            └─ TurnOrchestrator
                 ├─ T0  PhraseMatcher
                 │        ├─ Verified phrasebook hit → WAV playback (mmap, zero-decode latency)
                 │        └─ Corpus glossary hit     → WAV playback
                 └─ T1  IndicTrans2 320M int8
                          ├─ Tokenizer (SentencePiece)
                          ├─ Encoder (121 MB)
                          └─ Fused decoder (195 MB)
                               └─ VITS synthesis (MMS Mundari voice, ~145 MB)
                                    └─ AudioTrack playback

Provenance label on every output:  VERIFIED · CORPUS · APPROXIMATE · MACHINE
Turn saved to Room DB  →  worksheet context  →  bilingual worksheet + QR
```

**Voice-to-voice latency budget: ≤ 3 s from end of speech.** T0 phrasebook path is sub-200 ms.

---

## Language Support

| Language | ISO | Script | Hindi → Target MT | Voice | Full Turn |
|---|---|---|---|---|---|
| **Santali** | `sat` | Ol Chiki | ✅ IndicTrans2 320M int8 | ⚠️ Borrowed — Mundari voice | ✅ End-to-end live |
| **Mundari** | `unr` | Devanagari | ❌ No model exists (not a scheduled language) | ✅ `mms-tts-unr`, 27.51 h | ✅ Phrasebook full turn |
| **Ho** | `hoc` | Devanagari | ❌ No model exists | 🔜 `mms-tts-hoc` (unconverted) | Language pack planned |

> Mundari and Ho are absent from IndicTrans2 and NLLB-200 because they are not scheduled languages of India — a policy constraint, not a modelling gap. Santali is in the Eighth Schedule.

---

## Technology Stack

| Layer | Component | Licence |
|---|---|---|
| Android runtime | Kotlin · Jetpack Compose | Apache-2.0 |
| Database | Room + SQLite FTS4 (`unicode61`) | Apache-2.0 |
| Speech runtime | sherpa-onnx (ONNX Runtime) | Apache-2.0 |
| Hindi ASR | IndicConformer int8 | AI4Bharat |
| TTS | MMS VITS (`mms-tts-unr`) | CC-BY-NC 4.0 |
| Machine translation | IndicTrans2 distilled 320M int8 | AI4Bharat |
| Santali glossary | CC0 / CC-BY corpora (source cited per line) | CC0 / CC-BY |
| Script rendering | Noto Sans Ol Chiki (bundled) | OFL |
| Build tooling | Python · PowerShell | — |
| NDK | 28.2.13676358 | Apache-2.0 |


## Model Sideload Layout

Models are **not** bundled in the APK. They are sideloaded to app-scoped external storage after install:

```
/sdcard/Android/data/org.bolmitra/files/models/
    asr-batch/       model.int8.onnx  tokens.txt        (~219 MB resident)
    asr-streaming/   model.int8.onnx  tokens.txt        (~174 MB on disk)
    tts-unr/         model.onnx       tokens.txt        (~145 MB resident)
    mt-hi-sat/       encoder.onnx     decoder_merged.onnx
                     spm.tsv          src.model  tgt.model
                     dict.SRC.json    dict.TGT.json  fixture.json   (316 MB on disk)
    vad/             silero_vad.onnx                   (staged, not yet loaded)
```

---

## Phased Delivery

| Phase | Goal | Status |
|---|---|---|
| **0 · Measure** | Latency + memory baseline on the procured tablet | 🔄 In progress |
| **1 · Voice Loop** | End-to-end Hindi → Mundari turn, phrasebook only | ✅ Live |
| **2 · Worksheets** | NIPUN-aligned bilingual worksheets, flashcards, QR | ✅ Live |
| **3 · Neural MT** | T1 IndicTrans2 + block-laptop prep station | ✅ Live (Santali) |
| **4 · Scale** | Santali Ol Chiki + transliteration pipeline | ✅ Live |
| **5 · Validate** | Native-speaker review, classroom pilot | 🔜 Planned |

---

## What Is Deliberately Not In This App

These are recorded design decisions, not features that were forgotten:

| Absent | Why |
|---|---|
| Internet permission | Manifest contains no `INTERNET`. No sync, no telemetry, no cloud inference path — ever |
| Camera permission | Unused dangerous permissions are not requested in an app used by children |
| GPU / NPU / DSP inference | CPU-only; works on every arm64 SoC regardless of accelerator |
| Google Play Services | Sideloaded APK; no Firebase, no Play dependency |
| On-device LLM lesson planner | ~1 GB at int8; breaks the 2 GB RAM floor |
| Cloud dashboard for officials | Undermines the offline-first design |
| Pronunciation scoring | No tribal-language ASR model exists; TTS ≠ recognition |
| SD card | No removable-storage runtime path; packs come by USB or Wi-Fi Direct |

---

## Repository Structure

```
app/src/main/java/org/bolmitra/
  speech/         AudioCapture · LiveTurnEngine · SherpaEngines · TrackPlayer · WavFile · WavPlayer
  translate/      IndicTrans2Decoder · IndicTrans2Tokenizer · TurnOrchestrator · Engines
  phrasebook/     Phrasebook · PhraseMatcher · SantaliGlossary · ClassroomPacks · WordComposer
  translit/       OlChikiToDevanagari · OlChikiNormalizer · DevanagariToOdia
  curriculum/     NipunOutcomes · Akshara · ItemModel · Lakshya · TopicWorksheets
                  PictureBank · QrCode · WorksheetAssembler · WorksheetShare
  data/           BolMitraDatabase · Entities · TurnDao · PhraseDao · TurnRecorder · Settings
  device/         DeviceProbe
  ui/             LandingScreen · LiveClassPane · HomeDashboardPane · PhrasebookScreenPane
                  WorksheetsScreenPane · DiagnosticsScreenPane · SettingsScreenPane · Phase0Screen

tools/
  build-santali-glossary.py    Corpus ingestion + per-line citation tracking
  convert-mms-tts.py           MMS VITS → sherpa-onnx format conversion
  mt-merge-decoder.py          ONNX encoder+decoder graph fusion
  eval-hindi-santali.py        Round-trip translation evaluation
  check-apk-alignment.ps1      16 KB page-size build gate
  fetch-models.ps1             Model download and staging
  verify-onnx-santali.py       Model fixture verification
  ocr-spike.py                 Santali handwriting OCR benchmark (future scope)

ARCHITECTURE.md                Full architecture, 77-entry claim verification table, change log
info.md                        Hardware compatibility matrix (all figures traced to source)
```

---

## References

| Paper / Resource | Relevance |
|---|---|
| [MunTTS — arXiv 2401.15579](https://arxiv.org/html/2401.15579v1) | Mundari TTS, 27.51 h Devanagari corpus, Jharkhand dialect |
| [IndicTrans2](https://github.com/AI4Bharat/IndicTrans2) | Hindi → Santali MT backbone |
| [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) | Offline ASR + TTS + VAD runtime, Apache-2.0 |
| [AdiBhashaa — arXiv 2512.04765](https://arxiv.org/abs/2512.04765) | First open MT benchmarks for Mundari and Santali |
| [MMLoSo 2025](https://openreview.net/forum?id=8HdU8QiSIa) | Shared task, 20k pairs/language; winning LoRA + reranking approach |
| [MMS — Meta JMLR 2024](https://github.com/facebookresearch/fairseq/tree/main/examples/mms) | Source of VITS checkpoints for `unr` and `hoc` |
| [PALASH / JEPC](https://www.education.economictimes.indiatimes.com/news/government-policies/need-to-include-tribal-regional-languages-in-primary-education-jharkhand-minister/126417127) | Programme context: 5 languages, 8 districts, 1,000+ schools |
| [NIPUN Bharat guidelines](https://cdnbbsr.s3waas.gov.in/s3kv024f7eaa4eb42c24d192f4be337fdd/uploads/2024/06/2024060288.pdf) | FLN learning outcome taxonomy used in worksheet alignment |
| [Unicode CLDR sat_Olck → FONIPA](https://cldr.unicode.org/) | Authoritative source for Ol Chiki transliteration rules |
| [Rohrer et al. 2020, JEP](https://doi.org/10.1037/edu0000370) | Interleaved practice: 61% vs 38%, d ≈ 0.83 — basis for item ordering |
| [Karya Hindi–Mundari dataset](https://github.com/karya-inc/dataset-hindi-mundari-translation) | Parallel corpus for Mundari MT development |

---

<sub>Problem Statement ID 26042 · Smart India Hackathon 2026 · Domain: Education Technology / Low-Resource NLP / Edge AI</sub>
