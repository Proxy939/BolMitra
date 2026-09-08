package org.bolmitra.curriculum

/**
 * NIPUN Bharat lakshya taxonomy — ARCHITECTURE.md §6.3.
 *
 * The point of modelling this explicitly, rather than putting a grade number on a worksheet, is
 * stated in the document: it makes the alignment claim **queryable rather than asserted**. The
 * app can report coverage per lakshya and flag gaps, which is the difference between "NIPUN
 * aligned" as a slide bullet and as something an officer can check.
 *
 * Codes and targets come from the official NIPUN Bharat FLN material (§6.3, V42).
 */

/** Grade bands this project covers. §6.3: Balvatika through Grade 3. */
enum class GradeBand(val label: String) {
    BALVATIKA("Balvatika"),
    GRADE_1("Grade 1"),
    GRADE_2("Grade 2"),
    GRADE_3("Grade 3"),
}

/**
 * NIPUN Bharat's literacy taxonomy has **nine components** (V42), usable as the tagging spine.
 * Numeracy is modelled alongside it rather than inside it.
 */
enum class Domain { LITERACY, NUMERACY }

/**
 * One learning outcome.
 *
 * [languageRole] exists because of a finding that is easy to miss and expensive to get wrong.
 * V42: NCF sets **L1 fluency but only L2 *beginning* literacy** (CG-10 vs CG-11). So an L1
 * lakshya and an L2 lakshya are not the same target with different words — a mother-tongue
 * worksheet and a Hindi worksheet cannot come from one generator with the vocabulary swapped.
 */
data class Lakshya(
    val code: String,
    val gradeBand: GradeBand,
    val domain: Domain,
    val description: String,
    val languageRole: LanguageRole,
    /** Numeric bounds where the outcome specifies them, e.g. "count to 20". */
    val range: LakshyaRange? = null,
    /** Structural limits on generated items, e.g. akshara count or sentence length. */
    val limits: ItemLimits = ItemLimits(),
)

enum class LanguageRole {
    /** Mother tongue. Fluency is the target. */
    L1,

    /** Hindi as a second language. Beginning literacy only at this stage. */
    L2,

    /** Applies to both, e.g. most numeracy outcomes. */
    LANGUAGE_NEUTRAL,
}

/** Inclusive numeric bounds for a numeracy outcome. */
data class LakshyaRange(val min: Int, val max: Int) {
    init {
        require(min <= max) { "invalid range $min..$max" }
    }

    operator fun contains(value: Int): Boolean = value in min..max
}

/**
 * Structural limits a generated item must respect.
 *
 * These come from V42's sourced targets, and one of them corrects a mistake worth naming. The
 * difficulty ladder is **akshara**-based, not phoneme-based (V41): in akshara scripts, akshara
 * knowledge — not phoneme awareness — is the main predictor of word reading, and phoneme
 * awareness is largely a *product* of instruction rather than a prerequisite. An English-style
 * CVC to CCVC phonics ladder does not transfer.
 */
data class ItemLimits(
    /** Max aksharas per word. Balvatika targets words of 2–3 aksharas (V42). */
    val maxAksharas: Int? = null,
    /** Max matras per word. ASER's own word-selection uses 2 aksharas with 1–2 matras (V42). */
    val maxMatras: Int? = null,
    /** Whether samyuktakshara (conjuncts) are permitted — the top rung of the ladder. */
    val allowConjuncts: Boolean = false,
    /** Max words per sentence. Grade 1 targets 4–5 simple words (V42). */
    val maxWordsPerSentence: Int? = null,
)

/**
 * Lookup over a loaded taxonomy.
 *
 * `ponytail:` Backed by a plain list. The ceiling is O(n) scans, which is irrelevant at the real
 * size of this data — the FLN taxonomy for four grade bands is tens of entries, not thousands.
 * Upgrade path: index by code if it ever loads from something larger than a bundled JSON file.
 */
class LakshyaCatalogue(private val entries: List<Lakshya>) {

    val size: Int get() = entries.size

    fun byCode(code: String): Lakshya? = entries.firstOrNull { it.code == code }

    fun forBand(band: GradeBand): List<Lakshya> = entries.filter { it.gradeBand == band }

    fun forBand(band: GradeBand, domain: Domain): List<Lakshya> =
        entries.filter { it.gradeBand == band && it.domain == domain }

    /**
     * Codes in this catalogue that nothing in [coveredCodes] addresses.
     *
     * This is the query that turns the alignment claim into a checkable fact, and it doubles as
     * the content-authoring backlog — §6.16 notes the same gap message tells the prep station
     * exactly which lakshya needs more items.
     */
    fun gaps(coveredCodes: Set<String>): List<Lakshya> =
        entries.filter { it.code !in coveredCodes }

    fun coverageRatio(coveredCodes: Set<String>): Double =
        if (entries.isEmpty()) 0.0
        else entries.count { it.code in coveredCodes }.toDouble() / entries.size
}
