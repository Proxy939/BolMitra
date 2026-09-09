package org.bolmitra.phrasebook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks on the Phrasebook screen's browsing logic.
 *
 * The screen this replaced showed eight invented Mundari strings with fabricated category counts. The
 * two properties worth pinning are therefore: a category count must equal what its own filter returns
 * (otherwise a teacher taps a category and lands on an empty list), and a row's provenance must be
 * carried across unchanged (otherwise corpus text is presented as reviewed).
 */
class PhrasebookBrowserTest {

    private fun row(
        id: Long,
        hi: String,
        kind: String?,
        provenance: Provenance,
        english: String? = null,
        target: String = "\u1C6B\u1C69",
        variants: List<String> = emptyList(),
    ) = PhrasebookBrowser.Row(
        id = id,
        hiText = hi,
        english = english,
        targetNative = target,
        targetDeva = "x",
        provenance = provenance,
        src = if (provenance == Provenance.CORPUS) "GATITOS" else null,
        kind = kind,
        pos = null,
        variants = variants,
        audioRef = null,
        lakshyaCode = null,
    )

    private val sample = listOf(
        row(1, "एक", "numeral", Provenance.CORPUS, english = "one"),
        row(2, "दो", "numeral", Provenance.CORPUS, english = "two"),
        row(3, "किताब", "word", Provenance.CORPUS, english = "Book"),
        row(4, "पानी", "word", Provenance.CORPUS, english = "water"),
        row(5, "किताब खोलो", null, Provenance.VERIFIED),
    )

    @Test
    fun `the shipped self-check passes`() {
        assertEquals(emptyList<String>(), PhrasebookBrowser.validate())
    }

    /**
     * The property the fabricated version broke: thirteen categories claiming 203 members over a
     * list of eight rows.
     */
    @Test
    fun `every category count equals what its filter returns`() {
        val cats = PhrasebookBrowser.categories(sample)
        assertTrue("no categories derived", cats.isNotEmpty())
        for (c in cats) {
            assertEquals(
                "category '${c.name}' claims ${c.count}",
                c.count,
                PhrasebookBrowser.filter(sample, c, "").size,
            )
        }
    }

    @Test
    fun `all phrases counts every row`() {
        assertEquals(sample.size, PhrasebookBrowser.categories(sample).first().count)
    }

    /** A category with no members would be a dead end for a teacher who tapped it. */
    @Test
    fun `empty categories are not offered`() {
        val onlyNumerals = listOf(row(1, "एक", "numeral", Provenance.CORPUS))
        val cats = PhrasebookBrowser.categories(onlyNumerals)
        assertTrue(cats.none { it.count == 0 })
        assertTrue("Words should not appear", cats.none { it.name == "Words" })
    }

    /** Seeded rows carry no `kind`; they must still be reachable rather than hidden. */
    @Test
    fun `rows with no kind get their own reachable category`() {
        val cats = PhrasebookBrowser.categories(sample)
        val classroom = cats.singleOrNull { it.name == "Classroom phrases" }
        assertTrue("seeded rows had no category", classroom != null)
        assertEquals(1, classroom!!.count)
        assertEquals(
            listOf(5L),
            PhrasebookBrowser.filter(sample, classroom, "").map { it.id },
        )
    }

    @Test
    fun `search matches hindi`() {
        assertEquals(2, PhrasebookBrowser.filter(sample, null, "किताब").size)
    }

    @Test
    fun `search matches the english gloss case-insensitively`() {
        assertEquals(1, PhrasebookBrowser.filter(sample, null, "book").size)
        assertEquals(1, PhrasebookBrowser.filter(sample, null, "BOOK").size)
    }

    @Test
    fun `search matches the target script`() {
        val rows = listOf(row(1, "क", "word", Provenance.CORPUS, target = "\u1C5B\u1C6E\u1C5C\u1C5A"))
        assertEquals(1, PhrasebookBrowser.filter(rows, null, "\u1C5B\u1C6E").size)
    }

    @Test
    fun `search and category compose`() {
        val cats = PhrasebookBrowser.categories(sample)
        val words = cats.single { it.name == "Words" }
        // किताब is a word; किताब खोलो is a seeded classroom phrase, so it must be excluded here.
        val hits = PhrasebookBrowser.filter(sample, words, "किताब")
        assertEquals(listOf(3L), hits.map { it.id })
    }

    @Test
    fun `a query with no matches returns nothing rather than everything`() {
        assertTrue(PhrasebookBrowser.filter(sample, null, "zzzzz").isEmpty())
    }

    /**
     * Provenance is copied, never re-derived. If browsing could relabel a row, the screen would be
     * back to presenting corpus strings as reviewed — which is what it did before, by showing them
     * with no label at all.
     */
    @Test
    fun `provenance is carried through unchanged`() {
        val corpus = sample.first { it.provenance == Provenance.CORPUS }
        val verified = sample.first { it.provenance == Provenance.VERIFIED }
        assertTrue("a corpus row must be flagged for review", corpus.needsReview)
        assertFalse("a verified row must not be flagged", verified.needsReview)
        assertEquals("GATITOS", corpus.src)
    }

    /**
     * The contradiction this catches: `DemoSeed` rows carry `Provenance.VERIFIED` (the default on
     * `Phrase`) while every target string is `[unr-N अनुवाद-लंबित]` and `DEMO_STRINGS_VERIFIED` is
     * false. Showing a "Verified" chip beside a placeholder is exactly the false claim §4.5 exists to
     * prevent, so a placeholder must always read as needing review.
     */
    @Test
    fun `a placeholder is flagged for review even when marked verified`() {
        val placeholder = row(1, "किताब खोलो", null, Provenance.VERIFIED)
            .copy(targetNative = "[unr-1 अनुवाद-लंबित]", isPlaceholder = true)
        assertTrue("a VERIFIED placeholder must still need review", placeholder.needsReview)
    }

    @Test
    fun `placeholder detection matches DemoSeed's markers and nothing else`() {
        assertTrue(PhrasebookBrowser.isPlaceholderText("[unr-7 अनुवाद-लंबित]"))
        assertTrue(PhrasebookBrowser.isPlaceholderText("[deva-2 अनुवाद-लंबित]"))
        // Real content must never be called a placeholder — that would flag the whole glossary.
        assertFalse(PhrasebookBrowser.isPlaceholderText("\u1C6B\u1C69\u1C68\u1C69\u1C75"))
        assertFalse(PhrasebookBrowser.isPlaceholderText("किताब"))
        assertFalse(PhrasebookBrowser.isPlaceholderText(""))
    }

    @Test
    fun `variants are preserved rather than merged`() {
        val rows = listOf(
            row(1, "अच्छा", "word", Provenance.CORPUS, variants = listOf("\u1C75\u1C77|GATITOS")),
        )
        assertEquals(1, rows.single().variants.size)
        assertEquals("\u1C75\u1C77|GATITOS", rows.single().variants.single())
    }

    @Test
    fun `an empty phrasebook yields only the All category`() {
        val cats = PhrasebookBrowser.categories(emptyList())
        assertEquals(1, cats.size)
        assertEquals(0, cats.single().count)
    }
}
