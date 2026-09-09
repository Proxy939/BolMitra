package org.bolmitra.translit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ingest normalisation, exercised against the exact strings that motivated it.
 *
 * All Ol Chiki here is written as `\uXXXX` escapes on purpose. Pasting the glyphs would make the
 * test unreadable in a cp1252 console and invite an editor to normalise them, which is precisely the
 * class of damage this code exists to repair.
 */
class OlChikiNormalizerTest {

    // ᱤᱧ ᱤᱠᱟᱹ ᱠᱟᱧ ᱢᱮ — GATITOS "I'm sorry", correctly diacritised with U+1C79.
    private val sorryCorrect = "\u1C64\u1C67 \u1C64\u1C60\u1C5F\u1C79 \u1C60\u1C5F\u1C67 \u1C62\u1C6E"

    // ᱤᱠᱟ. ᱠᱟ.ᱧ ᱢᱮ — GATITOS "excuse me", the same word with ASCII periods instead.
    private val excuseBroken = "\u1C64\u1C60\u1C5F. \u1C60\u1C5F.\u1C67 \u1C62\u1C6E"

    @Test
    fun `self check passes`() {
        assertEquals(emptyList<String>(), OlChikiNormalizer.validate())
    }

    @Test
    fun `broken periods become the diacritic`() {
        val r = OlChikiNormalizer.normalize(excuseBroken)
        assertEquals(2, r.periodsRepaired)
        assertTrue("no ASCII period should survive: ${cps(r.text)}", !r.text.contains('.'))
        assertEquals(2, r.text.count { it == '\u1C79' })
    }

    /**
     * The point of the whole exercise: after repair, the shared word is spelled identically in both
     * entries. Before repair it was not, and no downstream lookup could match them.
     */
    @Test
    fun `repair makes the two GATITOS spellings of the same word agree`() {
        val repaired = OlChikiNormalizer.normalize(excuseBroken).text
        // ikaʼ  = ᱤᱠᱟᱹ  — the first word of both entries once normalised.
        val word = "\u1C64\u1C60\u1C5F\u1C79"
        assertTrue("expected '$word' in ${cps(repaired)}", repaired.contains(word))
        assertTrue("expected '$word' in ${cps(sorryCorrect)}", sorryCorrect.contains(word))
    }

    @Test
    fun `already correct text is left exactly alone`() {
        val r = OlChikiNormalizer.normalize(sorryCorrect)
        assertEquals(sorryCorrect, r.text)
        assertTrue(r.isClean)
    }

    @Test
    fun `sentence final punctuation is not eaten`() {
        // ᱫᱟᱜ. — a period after a whole word, following no letter directly? It does follow ᱜ, so it
        // WILL be repaired. That is correct behaviour and the reason MUCAAD exists for real
        // sentence ends. Assert the MUCAAD case instead, which must never be touched.
        val withMucaad = "\u1C6B\u1C5F\u1C5C\u1C7E"
        val r = OlChikiNormalizer.normalize(withMucaad)
        assertEquals(withMucaad, r.text)
        assertEquals(0, r.periodsRepaired)
    }

    @Test
    fun `latin text passes through untouched`() {
        listOf("e.g. water", "3.14", "Mr. Hembram", "sat-Olck").forEach {
            assertEquals(it, OlChikiNormalizer.normalize(it).text)
        }
    }

    /**
     * The correction that matters. U+1C7C PHAARKAA forces an ejective reading, so rewriting a
     * hyphen to it changes the word. It must be reported and preserved.
     */
    @Test
    fun `hyphen inside an Ol Chiki word is reported but never rewritten`() {
        // ᱢᱮᱱᱟᱜ-ᱟ  menag-a, where -ᱟ is the finite verb suffix.
        val menaga = "\u1C62\u1C6E\u1C71\u1C5F\u1C5C-\u1C5F"
        val r = OlChikiNormalizer.normalize(menaga)
        assertEquals("hyphen should be reported", 1, r.hyphensFound)
        assertEquals("hyphen must survive verbatim", menaga, r.text)
        assertTrue("U+1C7C must not be introduced", !r.text.contains('\u1C7C'))
        assertTrue("a reported hyphen is not clean", !r.isClean)
    }

    @Test
    fun `published GATITOS defects are rejected`() {
        assertNotNull("XXXX placeholder", OlChikiNormalizer.rejectionReason("XXXX"))
        assertNotNull("English annotation", OlChikiNormalizer.rejectionReason("till done"))
        assertNotNull("blank", OlChikiNormalizer.rejectionReason("   "))
        // ᱯᱩᱥᱠᱩᱡ — the real Santali target that shipped alongside "till done".
        assertNull(OlChikiNormalizer.rejectionReason("\u1C6F\u1C69\u1C65\u1C60\u1C69\u1C61"))
    }

    @Test
    fun `stray latin mixed into Ol Chiki is reported`() {
        // ᱦᱟ4 — a scrape artifact found in the ofdn wordlist.
        val r = OlChikiNormalizer.normalize("\u1C66\u1C5F4")
        assertTrue("digit 4 should be reported, got ${r.strayAscii}", r.strayAscii.contains('4'))
    }

    @Test
    fun `normalised output still transliterates cleanly`() {
        // The whole reason to repair: the repaired form must survive the next hop to Devanagari.
        val repaired = OlChikiNormalizer.normalize(excuseBroken).text
        val deva = OlChikiToDevanagari.transliterate(repaired)
        assertTrue(
            "unmapped after repair: ${deva.unmapped.map { "U+%04X".format(it.code) }}",
            deva.unmapped.isEmpty(),
        )
        assertTrue("expected Devanagari output", deva.devanagari.isNotBlank())
    }

    /**
     * The same string BEFORE repair leaves a stray ASCII period in the Devanagari, which is exactly
     * the leak that would reach the voice as an unknown token.
     */
    @Test
    fun `unrepaired text leaks the period into Devanagari`() {
        val deva = OlChikiToDevanagari.transliterate(excuseBroken)
        assertTrue("expected the raw period to survive into output", deva.devanagari.contains('.'))
    }

    private fun cps(s: String) = s.map { "U+%04X".format(it.code) }.joinToString(" ")
}
