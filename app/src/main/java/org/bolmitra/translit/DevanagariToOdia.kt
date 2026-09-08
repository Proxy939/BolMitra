package org.bolmitra.translit

import java.text.Normalizer

/**
 * Devanagari to Odia transliteration for Mundari TTS input — ARCHITECTURE.md §4.6, V63.
 *
 * ### Why this exists
 *
 * §4.6 previously said transliteration was "not needed at all for the Mundari prototype", and
 * "no transliteration on the critical path" was a stated reason for choosing Mundari over
 * Santhali. V63 falsified that: `facebook/mms-tts-unr` is trained on **Odia** orthography. All
 * 50 non-punctuation symbols in its vocabulary are Odia; it contains no Devanagari at all and
 * cannot read the script the rest of this app uses.
 *
 * Teacher-facing and printed text stays Devanagari (V7 — that is what Jharkhand textbooks use).
 * Odia exists *only* as TTS input, produced here.
 *
 * ### Where this runs
 *
 * Off-device, at T2 pack-build time, because T0 audio is pre-rendered (§6.3). It therefore
 * stays off the ≤3 s critical path. Only T1 novel-utterance synthesis needs it at runtime, and
 * that is already the looser Path B budget.
 *
 * ### The constraint that shapes the table
 *
 * The target is **not "Odia"** — it is the model's 50-symbol inventory. Odia characters outside
 * that set are as unusable as Devanagari ones, because the model has no embedding for them. So
 * [VOCAB] is embedded here and [transliterate] guarantees every character it emits is in it.
 * A test asserts that property over a corpus.
 *
 * ### `ponytail:` this table is a Unicode mapping, not linguistic advice
 *
 * The one-to-one consonant correspondences are safe — both scripts are Brahmic abugidas with
 * near-parallel layout, mostly a +0x200 codepoint offset. **The vowel folds are not safe.** The
 * inventory has no long ī/ū, no diphthongs ai/au, and no independent O, so those are folded to
 * their nearest available short vowel. That changes pronunciation. Every fold is tagged
 * [Fold.LOSSY] and reported, so a reviewer can see exactly what was approximated.
 *
 * Ceiling: these folds were chosen from Unicode tables by someone who does not speak Mundari.
 * Upgrade path: a native speaker reviews the [LOSSY_NOTES] list and corrects the choices, which
 * is Phase 0 risk #1 work and cannot be substituted with more code.
 */
object DevanagariToOdia {

    /**
     * The exact symbol inventory of `facebook/mms-tts/models/unr/vocab.txt`, read 2026-09-03.
     * Order is irrelevant here; membership is what matters. Index 33 is U+200D (ZWJ), which is
     * a genuine model symbol — note that `HindiNormalizer` deliberately *drops* ZWJ for
     * matching purposes, and that must not leak onto this path.
     */
    val VOCAB: Set<Char> = (
        "\u0B39\u0B2D\u0B19\u0B3E\u0B1C\u0B3C\u0B18\u0B0F\u0B01\u0B2B" +
            "\u0027\u0B5F\u0B15\u0B07\u0B2C\u0B16\u005F\u0B2E\u0B41\u0B32" +
            "\u0B1A\u0020\u0B17\u0B3F\u0B09\u0B20\u0B26\u0B38\u0B23\u0B37" +
            "\u0B27\u0B36\u0B24\u200D\u0B4B\u0B71\u0B25\u0B4D\u0B02\u0B47" +
            "\u0B1B\u0B06\u0B2A\u0B1E\u0B21\u0B05\u0B1D\u0B1F\u0B22\u0B28" +
            "\u0B2F\u0B40\u0B30\u0B03"
        ).toSet()

    /** Whether a mapping preserves the sound or approximates it. */
    enum class Fold { EXACT, LOSSY }

    private data class Target(val text: String, val fold: Fold)

    private fun exact(c: Char) = Target(c.toString(), Fold.EXACT)
    private fun lossy(s: String) = Target(s, Fold.LOSSY)

    /**
     * Devanagari to Odia. Consonants are the safe part; vowels are where the inventory forces
     * approximation.
     */
    private val MAP: Map<Char, Target> = buildMap {
        // --- independent vowels ---------------------------------------------------------
        put('\u0905', exact('\u0B05'))              // अ -> ଅ
        put('\u0906', exact('\u0B06'))              // आ -> ଆ
        put('\u0907', exact('\u0B07'))              // इ -> ଇ
        put('\u0908', lossy("\u0B07"))              // ई -> ଇ   no long ī in inventory
        put('\u0909', exact('\u0B09'))              // उ -> ଉ
        put('\u090A', lossy("\u0B09"))              // ऊ -> ଉ   no long ū
        put('\u090B', lossy("\u0B30\u0B3F"))        // ऋ -> ରି  vocalic r approximated
        put('\u090F', exact('\u0B0F'))              // ए -> ଏ
        put('\u0910', lossy("\u0B0F"))              // ऐ -> ଏ   no ai
        put('\u0913', lossy("\u0B09"))              // ओ -> ଉ   no independent O
        put('\u0914', lossy("\u0B09"))              // औ -> ଉ   no au

        // --- consonants: direct +0x200, all present in the inventory --------------------
        for (cp in 0x0915..0x0932) {
            // Skips the Devanagari-only slots that have no Odia counterpart.
            if (cp == 0x0929 || cp == 0x0931) continue
            val odia = (cp + 0x200).toChar()
            if (odia in VOCAB) put(cp.toChar(), exact(odia))
        }
        put('\u0933', lossy("\u0B32"))              // ळ -> ଲ   ଳ absent from inventory
        put('\u0935', Target("\u0B71", Fold.EXACT)) // व -> ୱ   NOT +0x200; U+0B35 absent
        put('\u0936', exact('\u0B36'))              // श -> ଶ
        put('\u0937', exact('\u0B37'))              // ष -> ଷ
        put('\u0938', exact('\u0B38'))              // स -> ସ
        put('\u0939', exact('\u0B39'))              // ह -> ହ

        // --- dependent vowel signs ------------------------------------------------------
        put('\u093E', exact('\u0B3E'))              // ा -> ା
        put('\u093F', exact('\u0B3F'))              // ि -> ି
        put('\u0940', exact('\u0B40'))              // ी -> ୀ
        put('\u0941', exact('\u0B41'))              // ु -> ୁ
        put('\u0942', lossy("\u0B41"))              // ू -> ୁ   no long ū matra
        put('\u0943', lossy("\u0B3F"))              // ृ -> ି   vocalic r matra approximated
        put('\u0947', exact('\u0B47'))              // े -> େ
        put('\u0948', lossy("\u0B47"))              // ै -> େ   no ai matra
        put('\u094B', exact('\u0B4B'))              // ो -> ୋ
        put('\u094C', lossy("\u0B4B"))              // ौ -> ୋ   no au matra
        put('\u094D', exact('\u0B4D'))              // ् -> ୍   virama

        // --- signs ----------------------------------------------------------------------
        put('\u0901', exact('\u0B01'))              // ँ -> ଁ
        put('\u0902', exact('\u0B02'))              // ं -> ଂ
        put('\u0903', exact('\u0B03'))              // ः -> ଃ
        put('\u093C', exact('\u0B3C'))              // ़ -> ଼

        // --- pass-through symbols already in the inventory ------------------------------
        put(' ', exact(' '))
        put('\'', exact('\''))
        put('\u200D', exact('\u200D'))              // ZWJ is a real model symbol
    }

    /** Human-readable record of every approximation, for native-speaker review. */
    val LOSSY_NOTES: List<String> = listOf(
        "ई (long ī) -> ଇ (short i): inventory has no long ī",
        "ऊ (long ū) -> ଉ (short u): inventory has no long ū",
        "ू (long ū matra) -> ୁ (short u matra)",
        "ऋ / ृ (vocalic r) -> ରି / ି: no vocalic r in inventory",
        "ऐ (ai) -> ଏ (e), ै -> େ: no ai in inventory",
        "औ (au) -> ଉ (u), ौ -> ୋ (o): no au in inventory",
        "ओ (independent O) -> ଉ (u): no independent O in inventory",
        "ळ -> ଲ: ଳ absent from inventory",
    )

    data class Result(
        /** Odia text. Every character is guaranteed to be in [VOCAB]. */
        val odia: String,
        /** Devanagari characters with no mapping. These were DROPPED — see [isClean]. */
        val unmapped: List<Char>,
        /** Characters that were approximated rather than preserved. */
        val approximated: List<Char>,
    ) {
        /** True if nothing was dropped. Approximations do not make a result unclean. */
        val isClean: Boolean get() = unmapped.isEmpty()
    }

    /**
     * Transliterates Devanagari to the model's Odia inventory.
     *
     * Unmapped characters are **dropped and reported**, never passed through. Passing them
     * through would hand the model a symbol it has no embedding for; silently dropping them
     * without reporting would hide a content bug. Digits are the common case — they are
     * unmappable because the inventory has no Odia numerals, so numbers must be spelled as
     * words before this point, which `HindiNormalizer` already does for 0–20.
     */
    fun transliterate(devanagari: String): Result {
        // NFC first. Devanagari nukta composites (U+0958..U+095F) are in Unicode's composition
        // exclusion table, so normalisation leaves them as base + nukta, which the table above
        // handles. Without this they would be unmapped.
        val nfc = Normalizer.normalize(devanagari, Normalizer.Form.NFC)

        val sb = StringBuilder(nfc.length)
        val unmapped = mutableListOf<Char>()
        val approximated = mutableListOf<Char>()

        for (ch in nfc) {
            val target = MAP[ch]
            if (target == null) {
                unmapped += ch
                continue
            }
            if (target.fold == Fold.LOSSY) approximated += ch
            sb.append(target.text)
        }
        return Result(sb.toString(), unmapped, approximated)
    }

    /**
     * Self-check that the table can only emit symbols the model knows.
     *
     * Returns offending entries; empty means the table is sound. Cheap enough to assert in a
     * test, and it is the guard that matters — a single out-of-inventory character produces
     * either silence or a wrong phoneme at synthesis time, with nothing in the logs.
     */
    fun validateTable(): List<String> = MAP.entries
        .flatMap { (src, target) ->
            target.text.filter { it !in VOCAB }.map { bad ->
                "U+%04X -> U+%04X is not in the model vocabulary".format(src.code, bad.code)
            }
        }
}
