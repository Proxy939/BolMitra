package org.bolmitra.translit

import org.bolmitra.phrasebook.DemoSeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks for Devanagari to Odia transliteration (§4.6, V63).
 *
 * The property that matters above all others: **every emitted character must be in the model's
 * vocabulary.** A single out-of-inventory character yields silence or a wrong phoneme at
 * synthesis time and logs nothing, so it has to be caught here rather than by listening.
 */
class DevanagariToOdiaTest {

    @Test
    fun `the mapping table can only emit symbols the model knows`() {
        // The guard that makes the rest of this file trustworthy.
        val problems = DevanagariToOdia.validateTable()
        assertTrue("table emits out-of-vocabulary symbols: $problems", problems.isEmpty())
    }

    @Test
    fun `every phrase in the demo seed transliterates to in-vocabulary output`() {
        // Runs the real corpus through, which is what would actually reach the TTS model.
        for (p in DemoSeed.phrases) {
            val r = DevanagariToOdia.transliterate(p.hiText)
            val bad = r.odia.filter { it !in DevanagariToOdia.VOCAB }
            assertTrue(
                "'${p.hiText}' produced out-of-vocab chars: " +
                    bad.map { "U+%04X".format(it.code) },
                bad.isEmpty(),
            )
        }
    }

    // --- consonants: the safe part ---------------------------------------------------------

    @Test
    fun `consonants map by the plus-0x200 offset`() {
        // क ख ग -> କ ଖ ଗ
        assertEquals("\u0B15\u0B16\u0B17", DevanagariToOdia.transliterate("कखग").odia)
    }

    @Test
    fun `va maps to Odia WA, not the plus-0x200 slot`() {
        // व is U+0935; U+0935+0x200 = U+0B35 is NOT in the inventory. Must be ୱ U+0B71.
        val r = DevanagariToOdia.transliterate("व")
        assertEquals("\u0B71", r.odia)
        assertTrue(r.isClean)
    }

    @Test
    fun `a full word transliterates cleanly`() {
        // किताब -> କିତାବ
        val r = DevanagariToOdia.transliterate("किताब")
        assertEquals("\u0B15\u0B3F\u0B24\u0B3E\u0B2C", r.odia)
        assertTrue(r.isClean)
        assertTrue(r.approximated.isEmpty())
    }

    @Test
    fun `virama and conjuncts survive`() {
        // पुस्तक -> ପୁସ୍ତକ, virama preserved so the conjunct is not lost
        val r = DevanagariToOdia.transliterate("पुस्तक")
        assertTrue("virama missing", r.odia.contains('\u0B4D'))
        assertTrue(r.isClean)
    }

    @Test
    fun `nasals and signs map exactly`() {
        assertEquals("\u0B02", DevanagariToOdia.transliterate("\u0902").odia) // ं -> ଂ
        assertEquals("\u0B01", DevanagariToOdia.transliterate("\u0901").odia) // ँ -> ଁ
        assertEquals("\u0B03", DevanagariToOdia.transliterate("\u0903").odia) // ः -> ଃ
    }

    @Test
    fun `nukta composites decompose and map`() {
        // ज़ may arrive precomposed (U+095B) or as ज + nukta. NFC decomposes it because
        // Devanagari nukta forms are composition-excluded, so both must work identically.
        val precomposed = DevanagariToOdia.transliterate("\u095B")
        val decomposed = DevanagariToOdia.transliterate("\u091C\u093C")
        assertEquals(decomposed.odia, precomposed.odia)
        assertTrue(precomposed.isClean)
        assertTrue(precomposed.odia.contains('\u0B3C')) // nukta preserved
    }

    // --- the lossy folds, reported rather than hidden --------------------------------------

    @Test
    fun `long vowels fold to short and are reported as approximated`() {
        val r = DevanagariToOdia.transliterate("ई")
        assertEquals("\u0B07", r.odia)          // -> ଇ short i
        assertTrue(r.isClean)                  // nothing dropped
        assertEquals(listOf('ई'), r.approximated) // but flagged
    }

    @Test
    fun `diphthongs fold and are reported`() {
        for (ch in listOf('ऐ', 'औ', 'ओ')) {
            val r = DevanagariToOdia.transliterate(ch.toString())
            assertTrue("$ch should be flagged approximated", r.approximated.contains(ch))
            assertTrue("$ch should still emit something", r.odia.isNotEmpty())
        }
    }

    @Test
    fun `an exact mapping is NOT reported as approximated`() {
        val r = DevanagariToOdia.transliterate("कमल")
        assertTrue(r.approximated.isEmpty())
    }

    // --- unmapped input is dropped AND reported -------------------------------------------

    @Test
    fun `digits are unmapped because the inventory has no Odia numerals`() {
        // This is why numbers must be spelled as words before transliteration.
        val r = DevanagariToOdia.transliterate("पेज 5")
        assertFalse(r.isClean)
        assertTrue(r.unmapped.contains('5'))
        // The digit is dropped, never passed through to the model.
        assertFalse(r.odia.contains('5'))
    }

    @Test
    fun `latin text is dropped and reported, not passed through`() {
        val r = DevanagariToOdia.transliterate("book")
        assertEquals("", r.odia)
        assertEquals(4, r.unmapped.size)
    }

    @Test
    fun `danda is dropped and reported`() {
        // Devanagari danda U+0964 is not in the model inventory.
        val r = DevanagariToOdia.transliterate("कमल\u0964")
        assertFalse(r.isClean)
        assertTrue(r.unmapped.contains('\u0964'))
    }

    @Test
    fun `spaces and apostrophes pass through as inventory symbols`() {
        val r = DevanagariToOdia.transliterate("क म")
        assertTrue(r.isClean)
        assertTrue(r.odia.contains(' '))
    }

    @Test
    fun `empty input is clean and empty`() {
        val r = DevanagariToOdia.transliterate("")
        assertEquals("", r.odia)
        assertTrue(r.isClean)
    }

    @Test
    fun `lossy notes are documented for native-speaker review`() {
        // Not a behavioural test. It asserts the review list has not been quietly emptied,
        // because these folds are pronunciation decisions made from Unicode tables by someone
        // who does not speak Mundari.
        assertTrue(DevanagariToOdia.LOSSY_NOTES.size >= 8)
    }
}
