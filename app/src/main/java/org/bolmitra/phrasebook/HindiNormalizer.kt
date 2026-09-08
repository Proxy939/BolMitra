package org.bolmitra.phrasebook

import java.text.Normalizer

/**
 * Produces `hi_normalized` for T0 exact matching — ARCHITECTURE.md §6.3 lookup step 1:
 * "lowercased, punctuation stripped, digits to words, honorific variants folded,
 * Devanagari NFC-normalized."
 *
 * This runs on the ≤3 s critical path, so it is deliberately allocation-light string work
 * with no regex compilation per call and no model inference.
 *
 * **Why this matters more than it looks.** §4.3 measured Hindi ASR at 0.328 WER. Roughly one
 * word in three arrives wrong, so exact matching alone would miss most turns. Normalisation
 * is what converts near-misses into exact hits before fuzzy matching has to run at all —
 * every phrase it rescues moves a turn from Path B (~1.3–2.1 s) to Path A (~620–920 ms).
 */
object HindiNormalizer {

    /** U+0966..U+096F — Devanagari digits ०१२३४५६७८९. */
    private const val DEVANAGARI_ZERO = '\u0966'

    /**
     * Hindi number words, 0–20 plus round tens.
     *
     * `ponytail:` Bounded on purpose. Hindi numerals 21–99 are irregular (इकतीस, बत्तीस …)
     * and would need a 100-entry table for little gain, because **numbers are the slot-fill
     * mechanism's job** (§6.3 step 3) — `पेज {N} खोलो` handles arbitrary values without
     * spelling any of them out. This table exists only so a stored phrase that spells a small
     * number matches speech that used a digit, and vice versa. Ceiling: values above 20 that
     * are not round tens pass through as digits. Upgrade path: full 0–999 table keyed off the
     * §6.16 numeracy lakshya ranges if field data shows it is needed.
     */
    private val numberWords: Map<Int, String> = mapOf(
        0 to "शून्य", 1 to "एक", 2 to "दो", 3 to "तीन", 4 to "चार",
        5 to "पाँच", 6 to "छह", 7 to "सात", 8 to "आठ", 9 to "नौ",
        10 to "दस", 11 to "ग्यारह", 12 to "बारह", 13 to "तेरह", 14 to "चौदह",
        15 to "पंद्रह", 16 to "सोलह", 17 to "सत्रह", 18 to "अठारह", 19 to "उन्नीस",
        20 to "बीस", 30 to "तीस", 40 to "चालीस", 50 to "पचास",
        60 to "साठ", 70 to "सत्तर", 80 to "अस्सी", 90 to "नब्बे", 100 to "सौ",
    )

    /**
     * Politeness and filler tokens dropped before matching.
     *
     * `ponytail:` A token list, not morphology. Hindi imperatives inflect for politeness —
     * करो / करिए / कीजिए are the same instruction at three registers — and folding those
     * properly needs a stemmer this project has no reason to own. Ceiling: only free-standing
     * politeness words are folded, not verb endings. Upgrade path: a suffix table for the
     * imperative forms that actually appear in the seeded phrasebook, driven by field data
     * rather than guessed at now.
     */
    private val foldedTokens = setOf("जी", "ज़रा", "जरा", "please", "प्लीज़", "प्लीज")

    /**
     * True for Devanagari vowel signs, virama, anusvara, candrabindu and nukta.
     *
     * **This predicate is load-bearing.** Combining marks are Unicode categories `Mn`/`Mc`,
     * and `Char.isLetter()` is FALSE for all of them. Treating them as punctuation destroys
     * every matra: `किताब` becomes `क त ब`, which then collides with unrelated words and
     * silently converts fuzzy matches into false exact hits. Caught by
     * `PhrasebookTest.strips danda and collapses whitespace`.
     */
    private fun Char.isCombiningMark(): Boolean = when (category) {
        CharCategory.NON_SPACING_MARK,        // ि ु ् ं ँ ़
        CharCategory.COMBINING_SPACING_MARK,  // ा ी ो े ौ
        CharCategory.ENCLOSING_MARK -> true
        else -> false
    }

    /** ZWNJ / ZWJ control conjunct rendering but carry no matching signal. */
    private fun Char.isJoinControl(): Boolean = this == '\u200C' || this == '\u200D'

    /**
     * Normalises a template while preserving its `{n}` slot marker.
     *
     * Needed because `{` and `}` are punctuation, so a plain [normalize] call would erase the
     * marker and the template could never match. Splitting on the marker, normalising each
     * side, and rejoining keeps both properties.
     */
    fun normalizeTemplate(template: String): String =
        template.split(SlotFill.MARKER)
            .joinToString(SlotFill.MARKER) { normalize(it) }

    /**
     * Normalises Hindi text for storage in `hi_normalized` and for query-time comparison.
     * The same function must be used on both sides or exact matching silently degrades.
     */
    fun normalize(input: String): String {
        // NFC first: Devanagari has canonically equivalent forms (notably nukta composites),
        // and comparing un-normalised strings can fail on visually identical text.
        val nfc = Normalizer.normalize(input, Normalizer.Form.NFC)

        val sb = StringBuilder(nfc.length)
        var digitRun = StringBuilder()

        fun flushDigits() {
            if (digitRun.isEmpty()) return
            val n = digitRun.toString().toIntOrNull()
            val word = n?.let { numberWords[it] }
            // Unmapped numbers survive as digits so slot fill can still claim them.
            sb.append(word ?: digitRun.toString())
            digitRun = StringBuilder()
        }

        for (ch in nfc) {
            val asDigit = when {
                ch in '0'..'9' -> ch - '0'
                ch in DEVANAGARI_ZERO..(DEVANAGARI_ZERO + 9) -> ch - DEVANAGARI_ZERO
                else -> null
            }
            when {
                asDigit != null -> digitRun.append(asDigit)
                // Combining marks must stay attached to the preceding letter, so they are
                // appended WITHOUT flushing the digit run boundary logic around them.
                ch.isLetter() || ch.isCombiningMark() -> {
                    flushDigits()
                    sb.append(ch.lowercaseChar())
                }
                // Join controls carry no matching signal — drop, do not separate.
                ch.isJoinControl() -> Unit
                // Everything else — whitespace, danda U+0964, double danda U+0965, ASCII
                // punctuation — collapses to a single separator.
                else -> { flushDigits(); sb.append(' ') }
            }
        }
        flushDigits()

        return sb.toString()
            .split(' ')
            .filter { it.isNotEmpty() && it !in foldedTokens }
            .joinToString(" ")
    }
}
