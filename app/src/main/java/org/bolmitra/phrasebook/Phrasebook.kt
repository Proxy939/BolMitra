package org.bolmitra.phrasebook

/**
 * T0 phrasebook types — ARCHITECTURE.md §6.3.
 *
 * T0 is the primary translation path, not a fallback. §4.5: every shipped phrase is
 * native-speaker verified, which is what makes it safe in front of children, and it is
 * why T0 outranks every model in the design by importance. It is also what meets R3 —
 * §6.2.1 budgets the phrasebook-hit path at ~620–920 ms against a 3 s requirement.
 */

/**
 * How a translation was produced. Surfaced in the UI so a teacher always knows whether to
 * trust it (§4.5). Never inferred at display time — it is decided at lookup and carried.
 */
enum class Provenance {
    /** Native-speaker reviewed. Exact phrasebook hit, or a verified template. */
    VERIFIED,

    /** Fuzzy phrasebook match above threshold. Usable, but the teacher should judge. */
    APPROXIMATE,

    /** Neural output from T1. No human has seen this string. */
    MACHINE,
}

/**
 * One phrasebook row. Mirrors the §6.3 schema; Room entities come later, so this stays a
 * plain data class and the matching logic below stays unit-testable without a device.
 */
data class Phrase(
    val id: Long,
    val lakshyaCode: String?,
    val hiText: String,
    /** Output of [HindiNormalizer.normalize]. Stored, not computed at query time. */
    val hiNormalized: String,
    val targetTextNative: String,
    val targetTextDeva: String,
    val audioRef: String?,
    val verifiedBy: String?,
    val packVersion: String,
    /**
     * A template carries a `{N}` slot, e.g. `पेज {N} खोलो`. Template audio plays with the
     * numeral synthesised separately, which covers combinatorial cases (page numbers,
     * counts) without storing every variant (§6.3 lookup step 3).
     */
    val isTemplate: Boolean = false,
)

/**
 * Result of a T0 lookup. `null` from the matcher means a genuine miss, which routes to T1.
 */
data class LookupResult(
    val phrase: Phrase,
    val provenance: Provenance,
    /** Dice coefficient for a fuzzy hit; 1.0 for exact and template hits. */
    val score: Double,
    /**
     * Value extracted from a `{N}` slot, if this was a template hit. The caller synthesises
     * this separately and splices it into the template audio.
     */
    val slotValue: String? = null,
)
