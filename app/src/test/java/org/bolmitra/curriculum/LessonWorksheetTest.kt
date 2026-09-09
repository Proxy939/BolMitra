package org.bolmitra.curriculum

import org.bolmitra.data.TurnEntity
import org.bolmitra.phrasebook.Provenance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks on the sheet built from lesson history.
 *
 * The failure modes here are all "a page a teacher prints and hands to children", which no build and
 * no screenshot catches: degrade rungs printed as vocabulary, one instruction repeated ten times, or
 * machine output presented with the same authority as a reviewed phrase.
 */
class LessonWorksheetTest {

    private var seq = 0L

    private fun turn(
        hi: String,
        target: String,
        provenance: String?,
        at: Long = ++seq * 1_000L,
        src: String? = null,
    ) = TurnEntity(
        id = seq,
        createdAtMs = at,
        language = "SANTALI",
        hiText = hi,
        targetNative = target,
        targetDeva = "",
        provenance = provenance,
        src = src,
    )

    @Test
    fun `the shipped self-check passes`() {
        assertEquals(emptyList<String>(), LessonWorksheet.validate())
    }

    @Test
    fun `a sheet carries the hindi and the target for every row`() {
        val sheet = LessonWorksheet.from(
            turns = listOf(
                turn("बैठ जाओ", "\u1C6B\u1C69\u1C68\u1C69\u1C75", "CORPUS", src = "GATITOS"),
                turn("खड़े हो जाओ", "\u1C5B\u1C6E\u1C5C\u1C5A", "CORPUS", src = "GATITOS"),
            ),
            languageName = "Santali",
            nowMs = 5_000L,
        )
        assertEquals(2, sheet.rows.size)
        assertTrue(sheet.rows.all { it.hindi.isNotBlank() && it.target.isNotBlank() })
        assertEquals("GATITOS", sheet.rows.first().src)
        assertEquals(Provenance.CORPUS, sheet.rows.first().provenance)
    }

    /**
     * The degrade rungs must never reach a page.
     *
     * `Unavailable` and `TextOnly` turns are stored with a null provenance and a degrade reason,
     * because the honest history row is "we could not translate this". As a worksheet exercise it
     * would be nonsense.
     */
    @Test
    fun `turns that produced no translation are excluded`() {
        val sheet = LessonWorksheet.from(
            turns = listOf(
                turn("कुछ", "something", null),
                turn("ठीक", "\u1C5B\u1C6E\u1C5C\u1C5A", "MACHINE"),
            ),
            languageName = "Santali",
            nowMs = 1L,
        )
        assertEquals(1, sheet.rows.size)
        assertEquals("ठीक", sheet.rows.single().hindi)
    }

    @Test
    fun `a blank target never becomes a row`() {
        val sheet = LessonWorksheet.from(
            turns = listOf(turn("कुछ", "   ", "MACHINE"), turn("और", "", "CORPUS")),
            languageName = "Santali",
            nowMs = 1L,
        )
        assertTrue("blank targets produced ${sheet.rows.size} rows", sheet.isEmpty)
    }

    /**
     * A teacher says "sit down" many times in one lesson. The sheet must list it once.
     */
    @Test
    fun `repeated instructions collapse to one row`() {
        val sheet = LessonWorksheet.from(
            turns = List(6) { turn("बैठ जाओ", "\u1C6B\u1C69\u1C68\u1C69\u1C75", "CORPUS") },
            languageName = "Santali",
            nowMs = 1L,
        )
        assertEquals(1, sheet.rows.size)
    }

    /**
     * The count that drives the sheet's review warning. If it under-reports, a page of machine output
     * goes out looking checked.
     */
    @Test
    fun `machine rows are counted for the review warning`() {
        val sheet = LessonWorksheet.from(
            turns = listOf(
                turn("a", "\u1C6B\u1C69", "MACHINE"),
                turn("b", "\u1C6B\u1C6A", "MACHINE"),
                turn("c", "\u1C6B\u1C6B", "CORPUS"),
                turn("d", "\u1C6B\u1C6C", "VERIFIED"),
            ),
            languageName = "Santali",
            nowMs = 1L,
        )
        assertEquals(2, sheet.needsReviewCount)
        assertEquals(2, sheet.byProvenance[Provenance.MACHINE])
        assertEquals(1, sheet.byProvenance[Provenance.CORPUS])
        assertEquals(1, sheet.byProvenance[Provenance.VERIFIED])
    }

    /** The generation stamp and the covered window are what the user asked to see on the sheet. */
    @Test
    fun `the sheet records when it was generated and what it covers`() {
        val sheet = LessonWorksheet.from(
            turns = listOf(
                turn("a", "\u1C6B\u1C69", "CORPUS", at = 7_000L),
                turn("b", "\u1C6B\u1C6A", "CORPUS", at = 2_000L),
            ),
            languageName = "Santali",
            nowMs = 12_345L,
        )
        assertEquals(12_345L, sheet.generatedAtMs)
        assertEquals(2_000L, sheet.coversFromMs)
        assertEquals(7_000L, sheet.coversToMs)
    }

    @Test
    fun `the row limit is respected`() {
        val sheet = LessonWorksheet.from(
            turns = (1..50).map { turn("phrase $it", "\u1C6B\u1C69", "MACHINE") },
            languageName = "Santali",
            nowMs = 1L,
            limit = 8,
        )
        assertEquals(8, sheet.rows.size)
    }

    @Test
    fun `an empty history yields an empty sheet rather than a crash`() {
        val sheet = LessonWorksheet.from(emptyList(), "Santali", 1L)
        assertTrue(sheet.isEmpty)
        assertEquals(0, sheet.needsReviewCount)
        assertEquals(null, sheet.coversFromMs)
        assertNotNull(sheet.title)
    }
}
