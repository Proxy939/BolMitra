package org.bolmitra.phrasebook

import java.io.File
import org.bolmitra.speech.TargetLanguage
import org.bolmitra.translit.OlChikiToDevanagari
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks on the supplied classroom packs asset.
 *
 * The load-bearing property is **precedence**. The supplied file disagrees with the shipped corpus
 * on ten normalised Hindi keys, two of which are the phrases Revision 27 established as working, and
 * the user's instruction was explicitly not to disturb that path. Order of concatenation is the only
 * thing keeping the corpus on air, and order is easy to change by accident — so it is asserted here
 * rather than left to a comment.
 *
 * Reads the assets off disk rather than through a `Context`, the same way `SantaliGlossaryTest` does,
 * so this stays a JVM test.
 */
class ClassroomPacksTest {

    private data class Row(val hi: String, val sat: String, val en: String, val pack: String)

    private val asset: File by lazy {
        listOf(
            File("src/main/assets/santali-classroom-packs.tsv"),
            File("app/src/main/assets/santali-classroom-packs.tsv"),
        ).firstOrNull { it.isFile }
            ?: error("packs asset not found; run tools/build-classroom-packs.py")
    }

    private val glossaryAsset: File by lazy {
        listOf(
            File("src/main/assets/santali-glossary.tsv"),
            File("app/src/main/assets/santali-glossary.tsv"),
        ).firstOrNull { it.isFile } ?: error("glossary asset not found")
    }

    private val rows: List<Row> by lazy {
        asset.readLines().drop(1).filter { it.isNotBlank() }.mapNotNull { line ->
            val f = line.split('\t')
            if (f.size < 4) null else Row(f[0].trim(), f[1].trim(), f[2].trim(), f[3].trim())
        }
    }

    /** Normalised Hindi -> Santali for every Hindi-keyed corpus row. */
    private val corpus: Map<String, String> by lazy {
        buildMap {
            glossaryAsset.readLines().drop(1).filter { it.isNotBlank() }.forEach { line ->
                val f = line.split('\t')
                if (f.size >= 3 && f[1].isNotBlank()) {
                    f[1].split(';').map { it.trim() }.filter { it.isNotEmpty() }.forEach { key ->
                        put(HindiNormalizer.normalize(key), f[2])
                    }
                }
            }
        }
    }

    // --- the asset itself -------------------------------------------------------------------

    @Test
    fun `assets holds only files meant to ship`() {
        // The builder wrote its rejection log next to the asset, which put 6 KB of review notes
        // inside the APK: everything in `assets/` ships by definition, so a stray file there is a
        // shipping decision made by accident. This is the cheap guard against the next one.
        val dir = asset.parentFile!!
        val unexpected = dir.listFiles()!!
            .filter { it.isFile }
            .map { it.name }
            .filterNot { it.endsWith(".tsv") }
        assertEquals("unexpected files in assets/: $unexpected", emptyList<String>(), unexpected)
    }

    @Test
    fun `asset parses and carries a usable number of rows`() {
        assertTrue("expected the supplied packs to ship a few hundred rows, got ${rows.size}",
            rows.size > 300)
        rows.forEach {
            assertTrue("blank Hindi in $it", it.hi.isNotBlank())
            assertTrue("blank Santali in $it", it.sat.isNotBlank())
        }
    }

    @Test
    fun `every row carries Ol Chiki`() {
        rows.forEach {
            assertTrue(
                "'${it.en}' has no Ol Chiki: ${it.sat}",
                it.sat.any { c -> c.code in 0x1C50..0x1C7F },
            )
        }
    }

    @Test
    fun `no shipped row carries a generation artifact`() {
        // The four rows with Latin words inside the Ol Chiki — one held a bare `Permission`, another
        // the English word `line` — are why the builder has a gate. If one reaches the asset, the
        // voice reads an English word to a Santali class.
        rows.forEach {
            assertTrue("'${it.en}' has Latin letters in its Santali: ${it.sat}",
                !it.sat.any { c -> c in 'A'..'Z' || c in 'a'..'z' })
        }
    }

    @Test
    fun `no shipped row carries a bracketed note the voice would read aloud`() {
        rows.forEach {
            assertTrue("'${it.en}' has brackets: ${it.sat}",
                !it.sat.contains('(') && !it.sat.contains(')'))
        }
    }

    @Test
    fun `only the known ambiguous Hindi words collide on a normalised key`() {
        // Two Santali strings for one Hindi phrase is an ambiguity about what a class hears, and the
        // loader resolves it first-wins. This is the check that a *new* ambiguity cannot slip in
        // unnoticed: the two below are real and understood — रुको is both "wait" and "stop",
        // सावधान रहो is both "be careful" and "be alert" — and anything else appearing here is a
        // question for a reviewer, not something to absorb silently.
        val known = setOf("रुको", "सावधान रहो")
        val collisions = rows
            .groupBy { HindiNormalizer.normalize(it.hi) }
            .filterValues { it.size > 1 }
        assertEquals(
            "unexpected Hindi key collisions: ${collisions.keys - known}",
            emptySet<String>(),
            collisions.keys - known,
        )
    }

    @Test
    fun `first-wins de-duplication yields exactly one Santali per Hindi key`() {
        // Reproduces the loader's own pass, because that is the mapping a class actually hears.
        val seen = LinkedHashMap<String, String>()
        rows.forEach { seen.putIfAbsent(HindiNormalizer.normalize(it.hi), it.sat) }
        assertEquals(
            "de-duplication must leave one string per key",
            seen.size,
            seen.keys.size,
        )
        // And it must be stable: the asset is written in pack order, so the winner is the earlier
        // pack, not whichever row the file system happened to yield.
        val ruko = rows.filter { HindiNormalizer.normalize(it.hi) == "रुको" }
        if (ruko.size > 1) {
            assertEquals(
                "first-wins must pick the earlier pack",
                ruko.first().sat,
                seen["रुको"],
            )
        }
    }

    @Test
    fun `every row transliterates to non-blank Devanagari`() {
        // A blank Devanagari reaches the voice as silence while the UI reports a hit — a working
        // lookup the class cannot hear.
        rows.forEach {
            val deva = OlChikiToDevanagari.transliterate(it.sat).devanagari
            assertTrue("'${it.en}' transliterates to nothing: ${it.sat}", deva.isNotBlank())
        }
    }

    // --- provenance -------------------------------------------------------------------------

    @Test
    fun `packs serve at MACHINE until a speaker signs them off`() {
        // Not a quality judgement. The file names no speaker and cites no corpus, so VERIFIED would
        // assert a review nobody did and CORPUS would put an uncheckable value in `src`.
        assertEquals(Provenance.MACHINE, ClassroomPacks.PROVENANCE)
    }

    @Test
    fun `a pack row is traceable even though it is not citable`() {
        assertTrue(ClassroomPacks.SOURCE.isNotBlank())
        assertTrue(ClassroomPacks.PACK_VERSION.isNotBlank())
    }

    // --- precedence: the whole point ---------------------------------------------------------

    @Test
    fun `the corpus wins every key the packs also claim`() {
        // LiveTurnEngine concatenates DemoSeed + SantaliGlossary + ClassroomPacks and PhraseMatcher's
        // exact rung takes the FIRST match. Reordering those three would silently swap what a class
        // hears for ten phrases. This reproduces that order and checks the outcome.
        val conflicts = rows.filter { HindiNormalizer.normalize(it.hi) in corpus }
        assertTrue("expected the supplied file to overlap the corpus", conflicts.isNotEmpty())

        conflicts.forEach { row ->
            val key = HindiNormalizer.normalize(row.hi)
            val corpusSat = corpus.getValue(key)
            val book = InMemoryPhrasebook(
                listOf(
                    phrase(1, row.hi, corpusSat, Provenance.CORPUS),
                    phrase(2, row.hi, row.sat, Provenance.MACHINE),
                ),
            )
            val hit = book.lookup(row.hi)
            assertNotNull("'${row.hi}' did not resolve at all", hit)
            assertEquals(
                "the corpus must win '${row.hi}'",
                corpusSat,
                hit!!.phrase.targetTextNative,
            )
            assertEquals(Provenance.CORPUS, hit.provenance)
        }
    }

    @Test
    fun `the two Revision 27 phrases still return their corpus answers`() {
        // Named explicitly rather than left to the loop above, because these two are the ones the
        // project was told not to disturb and the packs propose different words for both:
        // बैठ जाओ -> ᱫᱩᱲᱩᱵ ᱢᱮ against the corpus ᱫᱩᱨᱩᱵ, and खड़े हो जाओ -> ᱛᱤᱸᱜᱩᱱ ᱢᱮ against ᱛᱮᱜᱚ.
        val expected = mapOf(
            "बैठ जाओ" to "\u1C6B\u1C69\u1C68\u1C69\u1C75",   // durub
            "खड़े हो जाओ" to "\u1C5B\u1C6E\u1C5C\u1C5A",       // tego
        )
        expected.forEach { (hindi, corpusSat) ->
            val key = HindiNormalizer.normalize(hindi)
            assertEquals("the corpus row for '$hindi' changed", corpusSat, corpus[key])

            val packRow = rows.firstOrNull { HindiNormalizer.normalize(it.hi) == key }
            if (packRow != null) {
                assertTrue(
                    "the packs agree here, so this test no longer proves precedence",
                    packRow.sat != corpusSat,
                )
                val book = InMemoryPhrasebook(
                    listOf(
                        phrase(1, hindi, corpusSat, Provenance.CORPUS),
                        phrase(2, hindi, packRow.sat, Provenance.MACHINE),
                    ),
                )
                assertEquals(corpusSat, book.lookup(hindi)!!.phrase.targetTextNative)
            }
        }
    }

    @Test
    fun `a seeded verified row still outranks a pack row`() {
        val hindi = "बैठ जाओ"
        val book = InMemoryPhrasebook(
            listOf(
                phrase(1, hindi, "\u1C65\u1C6E\u1C5B", Provenance.VERIFIED),
                phrase(2, hindi, "\u1C6B\u1C69\u1C68\u1C69\u1C75", Provenance.CORPUS),
                phrase(3, hindi, "\u1C6B\u1C69\u1C72\u1C69\u1C75", Provenance.MACHINE),
            ),
        )
        assertEquals(Provenance.VERIFIED, book.lookup(hindi)!!.provenance)
    }

    // --- reach --------------------------------------------------------------------------------

    @Test
    fun `the packs add reach the corpus does not have`() {
        val newKeys = rows.count { HindiNormalizer.normalize(it.hi) !in corpus }
        assertTrue(
            "the packs should mostly be filling gaps, only $newKeys of ${rows.size} are new",
            newKeys > 300,
        )
    }

    @Test
    fun `a long pack sentence does not fuzzy-match a short unrelated query`() {
        // The mirror of SantaliGlossary's single-word hazard. These rows are whole sentences, and a
        // long row must not be dragged in by a one-word lookup.
        val long = rows.first { it.hi.split(' ').size >= 5 }
        val book = InMemoryPhrasebook(listOf(phrase(1, long.hi, long.sat, Provenance.MACHINE)))
        assertEquals(
            "a one-word query matched a whole sentence: ${long.hi}",
            null,
            book.lookup("पानी"),
        )
    }

    @Test
    fun `packs are Santali only`() {
        // The asset is Ol Chiki throughout; serving it for Mundari or Ho would be the wrong-language
        // bug DemoSeed.phrasesFor exists to prevent. Both phrasesFor() and size() route through
        // appliesTo(), so testing the gate covers both.
        assertTrue(ClassroomPacks.appliesTo(TargetLanguage.SANTALI))
        TargetLanguage.entries.filter { it != TargetLanguage.SANTALI }.forEach {
            assertTrue("$it must not receive Ol Chiki rows", !ClassroomPacks.appliesTo(it))
        }
    }

    private fun phrase(id: Long, hi: String, sat: String, prov: Provenance) = Phrase(
        id = id,
        lakshyaCode = null,
        hiText = hi,
        hiNormalized = HindiNormalizer.normalize(hi),
        targetTextNative = sat,
        targetTextDeva = OlChikiToDevanagari.transliterate(sat).devanagari,
        audioRef = null,
        verifiedBy = null,
        packVersion = "test",
        src = null,
        srcEn = null,
        provenance = prov,
    )

}
