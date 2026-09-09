package org.bolmitra.curriculum

import org.bolmitra.data.TurnEntity
import org.bolmitra.phrasebook.Provenance

/**
 * Builds a bilingual recap sheet from the turns a lesson actually produced.
 *
 * ### Why this is not `WorksheetAssembler`
 *
 * [WorksheetAssembler] composes authored [ItemModel] templates — slots, difficulty tags, interleaving
 * of solution strategies, deterministic regeneration from a seed. Its whole contract is that a sheet
 * is reproducible from `(modelId, seed)` and that rendered items are never persisted. A lesson recap
 * is the opposite kind of object: it is a record of specific things that were said at specific times,
 * and it cannot be regenerated from a seed because its content is history. Pushing turns through the
 * assembler would mean inventing `ItemModel`s at runtime and breaking the determinism its 17 tests
 * pin. So this is a separate, smaller thing, and the assembler is untouched.
 *
 * ### Provenance is printed, not dropped
 *
 * A recap sheet goes in front of children, and most rows on it will be `MACHINE` — the model's guess
 * at Hindi the phrasebook did not have. §4.5 does not stop applying because the medium changed from a
 * speaker to a page, so every row carries its level and [needsReviewCount] tells the teacher how much
 * of the sheet no human has checked. A sheet that looked authoritative would be the exact harm the
 * provenance system exists to prevent.
 */
object LessonWorksheet {

    /** One line of the sheet: what the teacher said, and what the class was shown. */
    data class Row(
        val hindi: String,
        /** Native script — Ol Chiki for Santali, Devanagari for Mundari. */
        val target: String,
        val provenance: Provenance,
        /** Which corpus, when [provenance] is [Provenance.CORPUS]. */
        val src: String? = null,
        val spokenAtMs: Long,
    ) {
        /** True when no human has reviewed this string for any purpose. */
        val isMachine: Boolean get() = provenance == Provenance.MACHINE
    }

    /**
     * A built sheet. [generatedAtMs] is stamped here rather than at display time so the sheet and the
     * timestamp shown next to it cannot disagree.
     */
    data class Sheet(
        val title: String,
        val languageName: String,
        val rows: List<Row>,
        val generatedAtMs: Long,
        /** Earliest and latest turn on the sheet, so a teacher can see which lesson it covers. */
        val coversFromMs: Long?,
        val coversToMs: Long?,
    ) {
        val isEmpty: Boolean get() = rows.isEmpty()

        /** Rows no human has reviewed. Drives the warning the sheet must carry. */
        val needsReviewCount: Int get() = rows.count { it.isMachine }

        /** How many rows came from each level, for an honest summary line. */
        val byProvenance: Map<Provenance, Int>
            get() = rows.groupingBy { it.provenance }.eachCount()
    }

    /**
     * Turns history rows into a sheet.
     *
     * Filters, in order, and each one removes something that would be wrong on a page rather than
     * merely untidy:
     *
     *  1. **No target text** — a blank right-hand column is not an exercise.
     *  2. **No provenance** — those are the degrade rungs, where the app told the teacher it could not
     *     translate. Printing "no translation available" as vocabulary would be absurd.
     *  3. **Duplicates** — a teacher repeats an instruction several times in a lesson, and the same
     *     line six times is not a worksheet. Keyed on the Hindi so the *first* (most recent, since
     *     rows arrive newest-first) survives.
     *
     * @param limit maximum rows. A page holds far fewer than the history does.
     */
    fun from(
        turns: List<TurnEntity>,
        languageName: String,
        nowMs: Long,
        limit: Int = 20,
        title: String = "Lesson recap",
    ): Sheet {
        val seen = HashSet<String>()
        val rows = turns.asSequence()
            .mapNotNull { t ->
                val provenance = t.provenance
                    ?.let { name -> runCatching { Provenance.valueOf(name) }.getOrNull() }
                    ?: return@mapNotNull null
                val hindi = t.hiText.trim()
                val target = t.targetNative.trim()
                if (hindi.isEmpty() || target.isEmpty()) return@mapNotNull null
                Row(
                    hindi = hindi,
                    target = target,
                    provenance = provenance,
                    src = t.src,
                    spokenAtMs = t.createdAtMs,
                )
            }
            .filter { seen.add(it.hindi) }
            .take(limit)
            .toList()

        return Sheet(
            title = title,
            languageName = languageName,
            rows = rows,
            generatedAtMs = nowMs,
            coversFromMs = rows.minOfOrNull { it.spokenAtMs },
            coversToMs = rows.maxOfOrNull { it.spokenAtMs },
        )
    }

    /**
     * Self-check for the two filters that fail silently.
     *
     * A sheet that kept degrade rungs would print "no translation available" as an exercise; one that
     * kept duplicates would print the same instruction ten times. Neither throws, neither shows up in
     * a build, and both are only visible once a teacher has printed the page.
     */
    fun validate(): List<String> = buildList {
        fun turn(
            hi: String,
            target: String,
            prov: String?,
            at: Long = 1_000L,
        ) = TurnEntity(
            id = 0,
            createdAtMs = at,
            language = "SANTALI",
            hiText = hi,
            targetNative = target,
            targetDeva = "",
            provenance = prov,
        )

        val sheet = from(
            turns = listOf(
                turn("बैठ जाओ", "\u1C6B\u1C69\u1C68\u1C69\u1C75", "CORPUS", at = 3_000L),
                turn("बैठ जाओ", "\u1C6B\u1C69\u1C68\u1C69\u1C75", "CORPUS", at = 2_000L),
                turn("कुछ नहीं", "", "MACHINE"),                 // no target
                turn("समय नहीं", "x", null),                      // a degrade rung
                turn("खड़े हो जाओ", "\u1C5B\u1C6E\u1C5C\u1C5A", "MACHINE", at = 1_000L),
            ),
            languageName = "Santali",
            nowMs = 9_999L,
        )

        if (sheet.rows.size != 2) add("expected 2 rows after filtering, got ${sheet.rows.size}")
        if (sheet.rows.any { it.target.isBlank() }) add("a row with no target survived")
        if (sheet.rows.count { it.hindi == "बैठ जाओ" } != 1) add("a duplicate Hindi row survived")
        if (sheet.needsReviewCount != 1) add("machine rows miscounted: ${sheet.needsReviewCount}")
        if (sheet.generatedAtMs != 9_999L) add("generation stamp was not carried")
        if (sheet.coversFromMs != 1_000L || sheet.coversToMs != 3_000L) {
            add("coverage window wrong: ${sheet.coversFromMs}..${sheet.coversToMs}")
        }
        if (from(emptyList(), "Santali", 1L).isEmpty.not()) add("an empty history produced rows")
    }
}
