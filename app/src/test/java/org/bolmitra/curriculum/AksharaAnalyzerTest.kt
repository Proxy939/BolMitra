package org.bolmitra.curriculum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks for akshara segmentation (§6.16.5).
 *
 * These matter because the whole difficulty ladder is built on the counts produced here. Getting
 * conjunct handling wrong would misgrade every conjunct word in the corpus, which means children
 * receiving items above their level with no visible symptom in the code.
 */
class AksharaAnalyzerTest {

    @Test
    fun `base aksharas with no matras`() {
        // कमल — three plain consonants, no vowel signs.
        assertEquals(listOf("क", "म", "ल"), AksharaAnalyzer.segment("कमल"))
        assertEquals(3, AksharaAnalyzer.countAksharas("कमल"))
        assertEquals(0, AksharaAnalyzer.countMatras("कमल"))
        assertEquals(AksharaRung.BASE_AKSHARA, AksharaAnalyzer.rung("कमल"))
    }

    @Test
    fun `matras attach to their consonant rather than starting a new akshara`() {
        // किताब — क + ि, त + ा, ब  => three aksharas, two matras.
        assertEquals(3, AksharaAnalyzer.countAksharas("किताब"))
        assertEquals(2, AksharaAnalyzer.countMatras("किताब"))
        assertEquals(AksharaRung.AKSHARA_WITH_MATRA, AksharaAnalyzer.rung("किताब"))
    }

    @Test
    fun `conjunct is ONE akshara, not two`() {
        // क्ष = क + virama + ष. Counting this as two would misgrade every conjunct word.
        assertEquals(1, AksharaAnalyzer.countAksharas("क्ष"))
        assertTrue(AksharaAnalyzer.hasConjunct("क्ष"))
        assertEquals(AksharaRung.SAMYUKTAKSHARA, AksharaAnalyzer.rung("क्ष"))
    }

    @Test
    fun `conjunct inside a longer word`() {
        // पुस्तक — प+ु, स+virama+त, क => three aksharas, one conjunct.
        assertEquals(3, AksharaAnalyzer.countAksharas("पुस्तक"))
        assertTrue(AksharaAnalyzer.hasConjunct("पुस्तक"))
        assertEquals(AksharaRung.SAMYUKTAKSHARA, AksharaAnalyzer.rung("पुस्तक"))
    }

    @Test
    fun `trailing virama is a halant marker, not a conjunct`() {
        assertFalse(AksharaAnalyzer.hasConjunct("क्"))
    }

    @Test
    fun `independent vowel is its own akshara`() {
        // आम — independent आ then म.
        assertEquals(listOf("आ", "म"), AksharaAnalyzer.segment("आम"))
    }

    @Test
    fun `anusvara attaches rather than splitting`() {
        // अंक — अ + anusvara, then क.
        assertEquals(2, AksharaAnalyzer.countAksharas("अंक"))
    }

    @Test
    fun `conjunct outranks matras on the ladder`() {
        // A word with both must sit on the top rung: the conjunct is what makes it hard.
        assertEquals(AksharaRung.SAMYUKTAKSHARA, AksharaAnalyzer.rung("पुस्तक"))
    }

    @Test
    fun `no devanagari yields no aksharas`() {
        assertEquals(0, AksharaAnalyzer.countAksharas("abc 123"))
        assertEquals(0, AksharaAnalyzer.countAksharas(""))
    }

    // --- limit checking, as used by item selection ----------------------------------------

    @Test
    fun `ASER-style limits admit a 2-akshara word with one matra`() {
        // V42: ASER selects common familiar words of 2 aksharas with 1-2 matras.
        val limits = ItemLimits(maxAksharas = 2, maxMatras = 2, allowConjuncts = false)
        assertTrue(AksharaAnalyzer.satisfies("पानी", limits))   // पा + नी, 2 aksharas 2 matras
        assertFalse(AksharaAnalyzer.satisfies("किताब", limits)) // 3 aksharas
    }

    @Test
    fun `conjuncts are excluded unless the lakshya allows them`() {
        val noConjuncts = ItemLimits(allowConjuncts = false)
        val withConjuncts = ItemLimits(allowConjuncts = true)
        assertFalse(AksharaAnalyzer.satisfies("पुस्तक", noConjuncts))
        assertTrue(AksharaAnalyzer.satisfies("पुस्तक", withConjuncts))
    }

    @Test
    fun `balvatika limit of 2 to 3 aksharas`() {
        // V42: Balvatika targets words of 2-3 aksharas.
        val limits = ItemLimits(maxAksharas = 3)
        assertTrue(AksharaAnalyzer.satisfies("कमल", limits))
        assertFalse(AksharaAnalyzer.satisfies("कमलपुर", limits))
    }
}
