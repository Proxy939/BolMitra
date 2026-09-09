package org.bolmitra.curriculum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks on the transcribed NIPUN Class 1-2 lakshyas.
 *
 * A transcription is data, so most of what can go wrong is invisible to the compiler: a dropped
 * line, a repeated ordinal, a ceiling typed as 90 instead of 99. `NipunOutcomes.validate()` is the
 * self-check for exactly that, and this runs it.
 *
 * The ceilings are asserted individually as well, because they are the numbers the worksheet
 * generator is allowed to produce and they were the ones corroborated across two documents. If
 * someone widens Grade 1 addition to 99 to make sheets look fuller, that is a curriculum change
 * disguised as a constant, and it should fail here.
 */
class NipunOutcomesTest {

    @Test
    fun `the transcription passes its own self-check`() {
        assertEquals(emptyList<String>(), NipunOutcomes.validate())
    }

    @Test
    fun `outcome counts match the printed source`() {
        assertEquals(16, NipunOutcomes.forBand(GradeBand.GRADE_1).size)
        assertEquals(17, NipunOutcomes.forBand(GradeBand.GRADE_2).size)
        assertEquals(33, NipunOutcomes.all.size)
    }

    @Test
    fun `numeracy is the domain with authorable items`() {
        assertEquals(6, NipunOutcomes.numeracy(GradeBand.GRADE_1).size)
        assertEquals(8, NipunOutcomes.numeracy(GradeBand.GRADE_2).size)
    }

    @Test
    fun `codes are document positions built from the position they name`() {
        val outcome = NipunOutcomes.byCode("C1-NUM-3")
        assertNotNull(outcome)
        assertEquals(GradeBand.GRADE_1, outcome!!.gradeBand)
        assertEquals(NipunOutcomes.NipunDomain.NUMERACY, outcome.nipunDomain)
        assertEquals(3, outcome.ordinal)
    }

    @Test
    fun `no outcome carries an official-looking NIPUN code`() {
        // The framework assigns no codes at all, so anything shaped like `N-1.2` or `FLN-N-01`
        // would be an invented identifier wearing a government costume. See the class note.
        val officialLooking = Regex("""^[A-Z]{2,4}[-–]\d+\.\d+$""")
        NipunOutcomes.all.forEach {
            assertTrue(
                "${it.code} looks like an official outcome identifier",
                !officialLooking.matches(it.code),
            )
        }
    }

    @Test
    fun `grade 1 arithmetic ceilings are the ones both sources agreed on`() {
        assertEquals(9, NipunOutcomes.GRADE_1_OPERAND_MAX)
        assertEquals(20, NipunOutcomes.GRADE_1_SUM_MAX)
        assertEquals(20, NipunOutcomes.GRADE_1_COUNT_MAX)
    }

    @Test
    fun `grade 2 ceilings are the ones both sources agreed on`() {
        assertEquals(99, NipunOutcomes.GRADE_2_NUMBER_MAX)
        assertEquals(listOf(2, 3, 4), NipunOutcomes.GRADE_2_TABLES)
    }

    @Test
    fun `grade 2 is strictly harder than grade 1, in the encoded ranges`() {
        // Not a tautology: it is the check that catches a copy-paste between the two blocks.
        val g1 = NipunOutcomes.byCode("C1-NUM-1")!!.range!!
        val g2 = NipunOutcomes.byCode("C2-NUM-1")!!.range!!
        assertTrue("grade 2 counting range must exceed grade 1", g2.max > g1.max)
    }

    @Test
    fun `numeracy outcomes are language neutral and literacy outcomes are not`() {
        // A numeral is not a translation, which is what lets numeracy items ship without a
        // speaker. If a literacy outcome were marked language-neutral it would invite exactly
        // the authoring the first invariant forbids.
        NipunOutcomes.all.forEach {
            val role = it.toLakshya().languageRole
            if (it.nipunDomain == NipunOutcomes.NipunDomain.NUMERACY) {
                assertEquals(it.code, LanguageRole.LANGUAGE_NEUTRAL, role)
            } else {
                assertEquals(it.code, LanguageRole.L1, role)
            }
        }
    }

    @Test
    fun `the catalogue exposes the transcription for gap queries`() {
        val catalogue = NipunOutcomes.catalogue
        assertEquals(33, catalogue.size)
        // Nothing is authored for literacy, so a coverage query must report those as gaps rather
        // than let "NIPUN aligned" pass unchecked.
        val covered = setOf("C1-NUM-3")
        assertTrue(catalogue.gaps(covered).any { it.code == "C1-RDG-1" })
        assertTrue(catalogue.gaps(covered).none { it.code == "C1-NUM-3" })
    }

    @Test
    fun `an unknown code resolves to nothing rather than a default`() {
        assertNull(NipunOutcomes.byCode("C9-NUM-1"))
        assertNull(NipunOutcomes.byCode(""))
    }

    @Test
    fun `every outcome cites both languages`() {
        NipunOutcomes.all.forEach {
            assertTrue("${it.code} english", it.english.isNotBlank())
            assertTrue("${it.code} hindi", it.hindi.isNotBlank())
        }
    }

    @Test
    fun `the source is recorded so the claim is checkable`() {
        assertTrue(NipunOutcomes.SOURCE_URL.startsWith("https://"))
        assertTrue(NipunOutcomes.CORROBORATING_URL.startsWith("https://"))
        assertTrue(NipunOutcomes.SOURCE_URL != NipunOutcomes.CORROBORATING_URL)
    }
}
