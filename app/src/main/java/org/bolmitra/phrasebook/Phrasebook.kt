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

    /**
     * Verbatim from a published, citable corpus. **No speaker has reviewed it.**
     *
     * The level this project was missing, and the reason it now exists: importing CC0/CC-BY Santali
     * data left every row having to claim either [VERIFIED], which would be a lie about
     * native-speaker review, or [MACHINE], which would be a lie about where the string came from and
     * would also discard the citation. Neither is acceptable when the content is spoken to children.
     *
     * A row at this level must carry both `src` (which corpus) and `srcEn` (the English the corpus
     * actually translated). `srcEn` matters more than it looks: the closest published Santali line to
     * a teacher's Hindi is often a near-miss, and showing the English it came from lets a reviewer
     * see the gap instead of having it papered over.
     *
     * Ranks below [VERIFIED] and above [MACHINE]: a human wrote it for a real purpose, but not for
     * this purpose and not for this classroom.
     */
    CORPUS,

    /** Fuzzy phrasebook match above threshold. Usable, but the teacher should judge. */
    APPROXIMATE,

    /**
     * **No human has reviewed this string for this classroom.**
     *
     * Two things arrive here. Neural output from T1 is the original case. The second is a bulk
     * translation supplied to the project with no named speaker behind it and no corpus to cite —
     * `ClassroomPacks` is exactly that, and it serves here rather than at [CORPUS] because there is
     * nothing to put in `src` that a reviewer could go and check, or at [VERIFIED] because that
     * would assert a review nobody has done.
     *
     * The wording of this comment used to be "neural output from T1", which made the level sound
     * like a property of the *pipeline*. It is a property of the *evidence*: the operative claim is
     * that no human has vouched for the string, however it got here. `src` still travels with the
     * row so its origin is traceable even when it is not citable.
     */
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
     * Where this row's content came from. **Carried, never inferred from the match rung.**
     *
     * [PhraseMatcher] used to hand back [Provenance.VERIFIED] for any exact hit, because every row
     * in the build was a hand-seeded one. The moment corpus rows exist that is a lie: an exact match
     * on a GATITOS line would have claimed native-speaker review of a string no speaker has read.
     * The rung can only ever *downgrade* this value now — see `PhraseMatcher.lookup`.
     */
    val provenance: Provenance = Provenance.VERIFIED,
    /**
     * Which corpus this row came from, e.g. `GATITOS` or `Hembram/Glossary`. Null for hand-authored
     * and speaker-verified rows. Required whenever provenance is [Provenance.CORPUS].
     */
    val src: String? = null,
    /**
     * The English the corpus line was actually translated from.
     *
     * Nongor's idea, and the sharpest one in the whole survey. The closest published Santali line to
     * "Do you need help?" may be "Can I help you?" — recording the English lets a reviewer see that
     * gap rather than discovering it in front of a class.
     */
    val srcEn: String? = null,
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
