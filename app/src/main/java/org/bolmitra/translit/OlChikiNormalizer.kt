package org.bolmitra.translit

/**
 * Cleans Ol Chiki text arriving from an outside corpus, before it enters our data model.
 *
 * ### Not the same job as [OlChikiToDevanagari]'s internal mark normalisation
 *
 * That one applies CLDR's rules about the *order* of diacritics, at transliteration time, on text we
 * already trust. This one repairs **source-data damage** at ingest time, on text we do not. Keeping
 * them apart matters: a repair belongs in the pack builder where it can be recorded and reviewed,
 * not buried in the hot path where it would silently rewrite every string on every turn.
 *
 * ### The one repair, and why it is safe
 *
 * Santali's Latin orthography writes the checked/low vowel as `a.`, and several published corpora
 * carry that ASCII period straight through into Ol Chiki text where **U+1C79 GAAHLAA TTUDDAAG**
 * belongs. The evidence this is damage rather than a valid convention is internal to the data:
 * GATITOS spells the same word both ways —
 *
 * | English | Target as published | Diacritic |
 * |---|---|---|
 * | `I'm sorry` | `ᱤᱧ ᱤᱠᱟ`**`ᱹ`**` ᱠᱟᱧ ᱢᱮ` | U+1C79, correct |
 * | `excuse me` | `ᱤᱠᱟ`**`.`**` ᱠᱟ`**`.`**`ᱧ ᱢᱮ` | ASCII period, broken |
 *
 * Same word, same slots, two characters. A corpus cannot be internally inconsistent about its own
 * orthography, so one of the two is a failed script conversion. Measured across all sources:
 * `sat.dic`, a 126,745-entry wordlist assembled from Wikipedia and Wiktionary, contains **zero**
 * ASCII periods and 2,133 U+1C79. That settles which one is correct.
 *
 * ### The repair we deliberately do NOT make
 *
 * Some corpora also use an **ASCII hyphen** inside Ol Chiki words (`ᱢᱮᱱᱟᱜ-ᱟ`). It is tempting to
 * treat that the same way and map it to `ᱼ` U+1C7C, whose glyph is a short horizontal stroke.
 * **That would be a bug.** U+1C7C PHAARKAA is a *voicing switch* — see [OlChikiToDevanagari], where
 * it sets `forcePlain` and turns `ᱵ` /b/ into /pʼ/. Rewriting `ᱢᱮᱱᱟᱜ-ᱟ` (*menag-a*, with the finite
 * verb suffix `-ᱟ`) would force an ejective reading and produce a different word.
 *
 * There is also no internal-inconsistency evidence for the hyphen the way there is for the period,
 * and a hyphen is an ordinary morpheme separator. So hyphens are **reported and left alone**.
 */
object OlChikiNormalizer {

    /** OL CHIKI GAAHLAA TTUDDAAG — the checked-vowel diacritic that `.` stands in for. */
    private const val GAAHLAA_TTUDDAAG = '\u1C79'

    private const val BLOCK_START = 0x1C50
    private const val BLOCK_END = 0x1C7F

    /** Letters and digits only — the marks in U+1C78..U+1C7D are excluded on purpose. */
    private fun isOlChikiBase(ch: Char): Boolean = ch.code in BLOCK_START..0x1C77

    private fun isOlChiki(ch: Char): Boolean = ch.code in BLOCK_START..BLOCK_END

    /**
     * What a normalisation did, so the pack builder can record it rather than hide it.
     *
     * [periodsRepaired] is a count of real edits. [hyphensFound] and [strayAscii] are *reports* —
     * nothing was changed for either, and both mean a human should look at the entry.
     */
    data class Result(
        val text: String,
        val periodsRepaired: Int,
        val hyphensFound: Int,
        /** ASCII letters or digits sitting inside Ol Chiki text. Always suspicious. */
        val strayAscii: List<Char>,
    ) {
        /** True when the string needed no edits and raised no reports. */
        val isClean: Boolean
            get() = periodsRepaired == 0 && hyphensFound == 0 && strayAscii.isEmpty()
    }

    /**
     * Repairs `.` → U+1C79 where it sits directly after an Ol Chiki letter, and reports the rest.
     *
     * The adjacency test is what keeps this from eating real punctuation: a period that ends a
     * sentence follows a space or nothing, and a period between two Latin words is not our
     * business. Only `<Ol Chiki letter>.` is rewritten.
     */
    fun normalize(raw: String): Result {
        if (raw.isEmpty()) return Result(raw, 0, 0, emptyList())

        val sb = StringBuilder(raw.length)
        var repaired = 0
        var hyphens = 0
        val stray = mutableListOf<Char>()
        var sawOlChiki = false

        for ((i, ch) in raw.withIndex()) {
            if (isOlChiki(ch)) sawOlChiki = true

            when {
                // The repair. Guarded on the PRECEDING character being an Ol Chiki letter, so
                // sentence-final periods and decimal points are untouched.
                ch == '.' && i > 0 && isOlChikiBase(raw[i - 1]) -> {
                    sb.append(GAAHLAA_TTUDDAAG)
                    repaired++
                }

                // Reported, never rewritten. See the class docs.
                ch == '-' && i > 0 && isOlChikiBase(raw[i - 1]) -> {
                    hyphens++
                    sb.append(ch)
                }

                else -> {
                    if ((ch.isLetterOrDigit()) && ch.code < 128) stray += ch
                    sb.append(ch)
                }
            }
        }

        // Latin inside a Latin-only string is not "stray"; it only matters when mixed with Ol Chiki.
        val strayReport = if (sawOlChiki) stray.distinct() else emptyList()
        return Result(sb.toString(), repaired, hyphens, strayReport)
    }

    /**
     * Whether a target string is fit to ingest at all.
     *
     * Returns a reason to reject, or null to accept. These are the two defects from the published
     * data that no orthography can excuse — a placeholder and an untranslated English annotation
     * both shipped in GATITOS as real targets:
     *
     * ```
     * {"src": "argue", "trgs": ["XXXX"], ...}
     * {"src": "fail",  "trgs": ["ᱯᱩᱥᱠᱩᱡ", "till done"], ...}
     * ```
     *
     * Ingesting either would put `XXXX` in front of a class carrying corpus provenance.
     */
    fun rejectionReason(target: String): String? = when {
        target.isBlank() -> "blank"
        target.none { isOlChiki(it) } -> "no Ol Chiki character present"
        else -> null
    }

    /**
     * Self-check for the repair rule.
     *
     * Asserts the property that fails silently in production: that we rewrite a period only when it
     * follows an Ol Chiki letter, and never anywhere else. A normaliser that ate sentence-final
     * MUCAAD or decimal points would corrupt data in a way no build and no screenshot would show.
     */
    fun validate(): List<String> = buildList {
        // dag with a checked vowel: the period must become the diacritic
        val repaired = normalize("\u1C6B\u1C5F.\u1C5C")
        if (!repaired.text.contains(GAAHLAA_TTUDDAAG)) add("period after an Ol Chiki letter was not repaired")
        if (repaired.periodsRepaired != 1) add("expected 1 repair, got ${repaired.periodsRepaired}")

        // A period after a space is ordinary punctuation and must survive untouched.
        val punctuation = normalize("\u1C6B\u1C5F\u1C5C .")
        if (punctuation.periodsRepaired != 0) add("punctuation period was rewritten")
        if (!punctuation.text.endsWith('.')) add("punctuation period was lost")

        // Latin text must pass through entirely unchanged.
        val latin = normalize("e.g. water")
        if (latin.text != "e.g. water") add("Latin text was altered: '${latin.text}'")

        // The hyphen must be reported and preserved, never rewritten.
        val hyphen = normalize("\u1C62\u1C6E\u1C71\u1C5F\u1C5C-\u1C5F")
        if (hyphen.hyphensFound != 1) add("hyphen not reported")
        if (!hyphen.text.contains('-')) add("hyphen was rewritten, which changes the word")

        if (rejectionReason("XXXX") == null) add("'XXXX' should be rejected")
        if (rejectionReason("till done") == null) add("'till done' should be rejected")
        if (rejectionReason("\u1C6B\u1C5F\u1C5C") != null) add("valid Ol Chiki was rejected")
    }
}
