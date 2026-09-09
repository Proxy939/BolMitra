package org.bolmitra.phrasebook

import android.content.Context
import org.bolmitra.speech.TargetLanguage

/**
 * Read-only view of the phrasebook, for the Phrasebook screen to browse.
 *
 * ### Why this exists
 *
 * The screen used to render **eight hand-written `PhraseItemModel`s whose Mundari was invented** —
 * `बेसो`, `दाड़ा`, `सेंते रे सुतु`, `जोहा!` and their example sentences. None came from a corpus or a
 * speaker, none carried a [Provenance], and the screen displayed them with the same authority as
 * anything else. That is precisely the harm the provenance system exists to prevent, and it is the
 * first invariant in the project: never invent or "improve" a tribal-language string.
 *
 * Everything here comes from [DemoSeed] or [SantaliGlossary]. Nothing is authored in this file.
 *
 * ### Read-only, deliberately
 *
 * This does no matching and makes no provenance decisions. [PhraseMatcher] owns the lookup ladder and
 * [Phrase.provenance] is decided where a row is built; browsing must not be able to change either, so
 * a row's level is copied straight across.
 */
object PhrasebookBrowser {

    /** One browsable entry. A projection of [Phrase] plus the glossary's extra columns. */
    data class Row(
        val id: Long,
        /** The Hindi a teacher would say. */
        val hiText: String,
        /** English gloss, where the source has one. Null for seeded rows. */
        val english: String?,
        /** What the class SEES — Ol Chiki for Santali, Devanagari for Mundari. */
        val targetNative: String,
        /** What the voice is given. */
        val targetDeva: String,
        val provenance: Provenance,
        /** Which corpus, for a [Provenance.CORPUS] row. */
        val src: String?,
        /** `word` / `sentence` / `numeral`, from the glossary. Null for seeded rows. */
        val kind: String?,
        /** Part of speech where a source supplied one. */
        val pos: String?,
        /** Other sources' forms for the same term. Never merged — the disagreement is the point. */
        val variants: List<String>,
        val audioRef: String?,
        val lakshyaCode: String?,
        /**
         * True when the target text is a `DemoSeed` placeholder rather than content.
         *
         * Needed because those rows carry `Provenance.VERIFIED` — the default on [Phrase] — while
         * `DemoSeed.DEMO_STRINGS_VERIFIED` is false and every string reads `[unr-N अनुवाद-लंबित]`. The
         * provenance is not changed here: it is what the live path really uses, and rewriting it for
         * display would put the browse screen and the lookup ladder into disagreement. Instead the
         * screen states both facts — the level the app assigns, and that this particular string is a
         * placeholder awaiting a speaker.
         */
        val isPlaceholder: Boolean = false,
    ) {
        /** True when no human has reviewed this string for this classroom. */
        val needsReview: Boolean get() = provenance != Provenance.VERIFIED || isPlaceholder
    }

    /** A filter over [Row]s, with the count it actually matches. */
    data class Category(
        val name: String,
        val count: Int,
        val emoji: String,
        /** Null for "everything". */
        val kind: String? = null,
    )

    /**
     * Every browsable row for [language].
     *
     * Seeded rows first, then corpus rows, mirroring the order `LiveTurnEngine` builds T0 in — a
     * reviewed row outranks a quoted one, and the browse list should present them the same way it
     * would match them.
     *
     * Santali produces one row per Hindi **alias**, which is how `SantaliGlossary` expands the asset,
     * so the count here is larger than the 373 Hindi-keyed terms.
     */
    fun rows(context: Context, language: TargetLanguage): List<Row> {
        val seeded = DemoSeed.phrasesFor(language).map { it.toRow(kind = null, pos = null) }

        // Terms rather than Phrases for the glossary, because Term carries the English gloss, the
        // part of speech and the cross-source variants — all of which a browsing screen wants and
        // none of which survives into a Phrase.
        val corpus = if (language == TargetLanguage.SANTALI) {
            var id = 1_000_000L
            SantaliGlossary.load(context).flatMap { term ->
                if (term.devanagari.isBlank()) return@flatMap emptyList()
                term.hindiKeys.map { key ->
                    Row(
                        id = id++,
                        hiText = key,
                        english = term.english,
                        targetNative = term.santali,
                        targetDeva = term.devanagari,
                        provenance = Provenance.CORPUS,
                        src = term.src,
                        kind = term.kind,
                        pos = term.pos,
                        variants = term.variants,
                        audioRef = null,
                        lakshyaCode = null,
                    )
                }
            }
        } else {
            emptyList()
        }

        return seeded + corpus
    }

    private fun Phrase.toRow(kind: String?, pos: String?) = Row(
        id = id,
        hiText = hiText,
        english = srcEn,
        targetNative = targetTextNative,
        targetDeva = targetTextDeva,
        provenance = provenance,
        src = src,
        kind = kind,
        pos = pos,
        variants = emptyList(),
        audioRef = audioRef,
        lakshyaCode = lakshyaCode,
        isPlaceholder = isPlaceholderText(targetTextNative),
    )

    /**
     * Recognises a `DemoSeed` placeholder.
     *
     * Matched on the marker `DemoSeed` actually writes rather than on `DEMO_STRINGS_VERIFIED` alone,
     * so a build that flips that flag without replacing the strings is still described correctly.
     */
    internal fun isPlaceholderText(target: String): Boolean =
        target.startsWith("[unr-") || target.startsWith("[deva-") ||
            target.contains("अनुवाद-लंबित")

    /**
     * Categories with **counted** membership, from the rows that exist.
     *
     * The previous list was thirteen fixed categories with invented counts — "Greetings 15",
     * "Animals 14", "Emergency / Help 8" — summing to 203 against a screen showing eight rows. These
     * are derived, so a category cannot claim members it does not have, and empty ones are dropped
     * rather than shown as a dead end.
     *
     * Grouped on the glossary's own `kind` column instead of invented semantic buckets. A semantic
     * taxonomy would need someone to classify 5,151 rows, and guessing it here would be the same
     * mistake in a different column.
     */
    fun categories(rows: List<Row>): List<Category> {
        val byKind = rows.groupingBy { it.kind ?: "" }.eachCount()
        return buildList {
            add(Category("All Phrases", rows.size, "\u229E", kind = null))
            KIND_LABELS.forEach { (kind, labelAndEmoji) ->
                val n = byKind[kind] ?: 0
                if (n > 0) add(Category(labelAndEmoji.first, n, labelAndEmoji.second, kind = kind))
            }
            // Seeded rows carry no kind; surfaced separately so they are reachable rather than hidden
            // behind a filter that never matches them.
            val unclassified = byKind[""] ?: 0
            if (unclassified > 0) {
                add(Category("Classroom phrases", unclassified, "\uD83D\uDC65", kind = ""))
            }
        }
    }

    private val KIND_LABELS = linkedMapOf(
        "word" to ("Words" to "\uD83D\uDD24"),
        "sentence" to ("Sentences" to "\uD83D\uDCAC"),
        "numeral" to ("Numbers" to "\uD83D\uDD22"),
    )

    /**
     * Filters by category and free text.
     *
     * Matches Hindi, the English gloss and the target script, because a teacher looking for a phrase
     * may remember any of the three. Case-folded on the English side only — Devanagari and Ol Chiki
     * have no case, and `lowercase()` on them is a no-op that would only cost time over 5,000 rows.
     */
    fun filter(rows: List<Row>, category: Category?, query: String): List<Row> {
        val q = query.trim()
        val byCategory = if (category?.kind == null) rows else rows.filter { (it.kind ?: "") == category.kind }
        if (q.isEmpty()) return byCategory
        val lower = q.lowercase()
        return byCategory.filter { r ->
            r.hiText.contains(q) ||
                r.targetNative.contains(q) ||
                r.english?.lowercase()?.contains(lower) == true
        }
    }

    /**
     * Self-check for the two properties that would silently mislead.
     *
     * A category count that does not match its filter sends a teacher to an empty list, and a row
     * whose provenance is not carried across would present corpus text as reviewed.
     */
    fun validate(): List<String> = buildList {
        fun row(id: Long, hi: String, kind: String?, prov: Provenance, en: String? = null) = Row(
            id = id, hiText = hi, english = en, targetNative = "x", targetDeva = "x",
            provenance = prov, src = null, kind = kind, pos = null, variants = emptyList(),
            audioRef = null, lakshyaCode = null,
        )

        val rows = listOf(
            row(1, "एक", "numeral", Provenance.CORPUS),
            row(2, "दो", "numeral", Provenance.CORPUS),
            row(3, "किताब", "word", Provenance.CORPUS, en = "Book"),
            row(4, "किताब खोलो", null, Provenance.VERIFIED),
        )

        val cats = categories(rows)
        if (cats.first().count != 4) add("'All' counted ${cats.first().count} of 4")
        // Every category's count must equal what its own filter returns.
        cats.forEach { c ->
            val actual = filter(rows, c, "").size
            if (actual != c.count) add("category '${c.name}' claims ${c.count}, filter gives $actual")
        }
        if (cats.any { it.count == 0 }) add("an empty category was offered")

        if (filter(rows, null, "किताब").size != 2) add("Hindi substring search missed a row")
        if (filter(rows, null, "book").size != 1) add("English gloss search is not case-folded")
        if (filter(rows, null, "zzz").isNotEmpty()) add("a search with no matches returned rows")

        val verified = rows.single { it.provenance == Provenance.VERIFIED }
        if (verified.needsReview) add("a VERIFIED row was marked as needing review")
        if (!rows.first().needsReview) add("a CORPUS row was not marked as needing review")

        // A placeholder must never read as reviewed, whatever provenance the row carries.
        if (!isPlaceholderText("[unr-3 अनुवाद-लंबित]")) add("the DemoSeed placeholder was not detected")
        if (isPlaceholderText("\u1C6B\u1C69\u1C68\u1C69\u1C75")) add("real Ol Chiki was called a placeholder")
        val placeholder = verified.copy(isPlaceholder = true)
        if (!placeholder.needsReview) add("a VERIFIED placeholder was not flagged for review")
    }
}
