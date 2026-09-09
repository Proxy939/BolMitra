package org.bolmitra.phrasebook

/**
 * The T0 lookup ladder — ARCHITECTURE.md §6.3, "cheapest first":
 *
 *   1. Exact match on `hi_normalized`            -> VERIFIED
 *   2. Template slot fill, e.g. `पेज {N} खोलो`   -> VERIFIED (numeral synthesised separately)
 *   3. Fuzzy: FTS4 candidates + Kotlin trigram   -> APPROXIMATE
 *   4. Miss                                      -> null, routes to T1
 *
 * Note the ordering difference from the document's prose, which lists fuzzy before slot fill.
 * **Slot fill is tried first here deliberately**: a template hit is *verified* content, while
 * a fuzzy hit is only approximate. Trying fuzzy first would let an approximate match beat an
 * exact template, downgrading provenance for no reason. Same four rungs, better order.
 *
 * Trigram scoring lives in Kotlin rather than SQL because V46 established that Android's
 * platform SQLite has no FTS5, so the trigram tokenizer is unavailable — FTS4 supplies the
 * candidate set and ranking happens here (§4.7).
 */
object PhraseMatcher {

    /**
     * Dice threshold for accepting a fuzzy match.
     *
     * `ponytail:` A starting value, not a measured one. This is the single most important
     * tuning knob in T0: §4.3 measured Hindi ASR at 0.328 WER, so fuzzy matching carries real
     * traffic, and this number trades false accepts (a teacher says one thing, children hear
     * another) against unnecessary T1 fallbacks. Too low is worse than too high — a wrong
     * translation delivered confidently is the failure mode this project can least afford.
     * Ceiling: unvalidated. Upgrade path: calibrate against recorded classroom audio in
     * Phase 0 and report the precision/recall curve, not a single number.
     */
    const val FUZZY_THRESHOLD = 0.72

    private const val TRIGRAM_PAD = '\u0000'

    /**
     * Runs the ladder over a candidate set. [candidates] is whatever FTS4 returned, or the
     * whole phrasebook in tests — the function is pure so it can be tested without a database.
     */
    fun lookup(
        rawHindi: String,
        candidates: List<Phrase>,
        threshold: Double = FUZZY_THRESHOLD,
    ): LookupResult? {
        val query = HindiNormalizer.normalize(rawHindi)
        if (query.isEmpty()) return null

        // 1. Exact. The row's own provenance stands — an exact hit adds no uncertainty.
        candidates.firstOrNull { !it.isTemplate && it.hiNormalized == query }
            ?.let { return LookupResult(it, it.provenance, 1.0) }

        // 2. Template slot fill. Exact in the same sense, so the row's provenance stands too.
        for (t in candidates.filter { it.isTemplate }) {
            val slot = SlotFill.match(query, t.hiNormalized)
            if (slot != null) {
                return LookupResult(t, t.provenance, 1.0, slotValue = slot)
            }
        }

        // 3. Fuzzy over non-template candidates.
        val best = candidates
            .filter { !it.isTemplate }
            .map { it to dice(query, it.hiNormalized) }
            .maxByOrNull { it.second }

        if (best != null && best.second >= threshold) {
            return LookupResult(best.first, downgradeForFuzzy(best.first.provenance), best.second)
        }

        // 4. Miss.
        return null
    }

    /**
     * Provenance for a fuzzy hit: the row's own level, capped at [Provenance.APPROXIMATE].
     *
     * Two distinct uncertainties get combined here and the cap is what keeps them honest.
     * [Provenance.VERIFIED] is a claim about the TEXT — a speaker read it. A fuzzy match adds a
     * claim about the MATCH — we are not certain this row is what the teacher said. So a fuzzy hit
     * on speaker-reviewed content is still only approximate, which is the pre-existing behaviour and
     * correct.
     *
     * What is new is the other direction: a fuzzy hit on a [Provenance.CORPUS] row must not be
     * *promoted* to APPROXIMATE, because APPROXIMATE at least implies a human wrote it for this
     * purpose. It stays CORPUS, which carries the weaker claim and the citation.
     */
    private fun downgradeForFuzzy(rowProvenance: Provenance): Provenance = when (rowProvenance) {
        Provenance.VERIFIED -> Provenance.APPROXIMATE
        Provenance.CORPUS -> Provenance.CORPUS
        Provenance.APPROXIMATE -> Provenance.APPROXIMATE
        Provenance.MACHINE -> Provenance.MACHINE
    }

    /**
     * Sørensen–Dice coefficient over character trigrams: `2|A ∩ B| / (|A| + |B|)`.
     *
     * Trigrams are counted as a multiset rather than a set. Costs three extra lines and is
     * correct for repeated trigrams, which short Hindi imperatives do produce — set-based
     * scoring would over-reward a phrase that repeats a syllable.
     */
    fun dice(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0

        val ta = trigrams(a)
        val tb = trigrams(b)
        val total = ta.values.sum() + tb.values.sum()
        if (total == 0) return 0.0

        var intersection = 0
        for ((gram, countA) in ta) {
            val countB = tb[gram] ?: continue
            intersection += minOf(countA, countB)
        }
        return 2.0 * intersection / total
    }

    /** Padded character trigrams with occurrence counts. Padding weights word edges. */
    private fun trigrams(s: String): Map<String, Int> {
        val padded = "$TRIGRAM_PAD$TRIGRAM_PAD$s$TRIGRAM_PAD$TRIGRAM_PAD"
        val out = HashMap<String, Int>(padded.length)
        for (i in 0..padded.length - 3) {
            val g = padded.substring(i, i + 3)
            out[g] = (out[g] ?: 0) + 1
        }
        return out
    }
}

/**
 * Template slot matching — §6.3 lookup step 3.
 *
 * A template is a normalised phrase containing the literal marker `{n}` (lowercased by
 * [HindiNormalizer]), e.g. `पेज {n} खोलो`. Matching extracts the slot value so the caller can
 * play verified template audio and synthesise only the numeral.
 */
object SlotFill {

    const val MARKER = "{n}"

    /**
     * Returns the slot value if [query] matches [template], else null.
     *
     * Implemented as a prefix/suffix comparison rather than a regex: templates have exactly
     * one slot, so this is cheaper, allocation-free apart from the substring, and cannot
     * misbehave on Devanagari the way a hand-written character class might.
     */
    fun match(query: String, template: String): String? {
        val marker = template.indexOf(MARKER)
        if (marker < 0) return null

        val prefix = template.substring(0, marker)
        val suffix = template.substring(marker + MARKER.length)

        if (!query.startsWith(prefix) || !query.endsWith(suffix)) return null
        // Guard against prefix and suffix overlapping on a short query.
        if (query.length < prefix.length + suffix.length) return null

        val value = query.substring(prefix.length, query.length - suffix.length).trim()
        return value.ifEmpty { null }
    }
}
