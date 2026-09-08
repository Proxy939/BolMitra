package org.bolmitra.phrasebook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks for the T0 lookup ladder (§6.3).
 *
 * These matter because T0 is the path that meets R3 and the path a teacher sees in front of
 * children. The two properties worth protecting above all: **a verified hit must never be
 * downgraded to approximate**, and **a miss must stay a miss** rather than quietly returning a
 * poor match with confident provenance.
 */
class PhrasebookTest {

    // --- HindiNormalizer -----------------------------------------------------------------

    @Test
    fun `strips danda and collapses whitespace`() {
        assertEquals(
            "किताब खोलो",
            HindiNormalizer.normalize("  किताब   खोलो।  "),
        )
    }

    @Test
    fun `devanagari and ascii digits both become words`() {
        // Same instruction typed with ASCII vs Devanagari digits must normalise identically.
        assertEquals(
            HindiNormalizer.normalize("पेज ५ खोलो"),
            HindiNormalizer.normalize("पेज 5 खोलो"),
        )
        assertEquals("पेज पाँच खोलो", HindiNormalizer.normalize("पेज 5 खोलो"))
    }

    @Test
    fun `spoken number matches written digit after normalisation`() {
        assertEquals(
            HindiNormalizer.normalize("तीन बार ताली बजाओ"),
            HindiNormalizer.normalize("3 बार ताली बजाओ"),
        )
    }

    @Test
    fun `unmapped numbers survive as digits for slot fill to claim`() {
        // 47 is outside the bounded table; it must not be dropped or mangled.
        assertTrue(HindiNormalizer.normalize("पेज 47 खोलो").contains("47"))
    }

    @Test
    fun `politeness tokens are folded`() {
        assertEquals(
            HindiNormalizer.normalize("किताब खोलो"),
            HindiNormalizer.normalize("किताब खोलो जी"),
        )
    }

    @Test
    fun `normalisation is idempotent`() {
        // Storage and query both call this; if it were not idempotent, re-normalising a
        // stored value would silently stop matching.
        val once = HindiNormalizer.normalize("पेज ५ खोलो।")
        assertEquals(once, HindiNormalizer.normalize(once))
    }

    // --- Dice similarity -----------------------------------------------------------------

    @Test
    fun `dice is 1 for identical and 0 for empty`() {
        assertEquals(1.0, PhraseMatcher.dice("किताब", "किताब"), 0.0001)
        assertEquals(0.0, PhraseMatcher.dice("", "किताब"), 0.0001)
    }

    @Test
    fun `dice is symmetric`() {
        val a = "किताब खोलो"
        val b = "किताब खोल"
        assertEquals(PhraseMatcher.dice(a, b), PhraseMatcher.dice(b, a), 0.0001)
    }

    @Test
    fun `near miss scores higher than unrelated phrase`() {
        val target = "किताब खोलो"
        val nearMiss = PhraseMatcher.dice("किताब खोल", target)
        val unrelated = PhraseMatcher.dice("ताली बजाओ", target)
        assertTrue("near miss $nearMiss should beat unrelated $unrelated", nearMiss > unrelated)
    }

    // --- SlotFill ------------------------------------------------------------------------

    @Test
    fun `slot fill extracts the value`() {
        assertEquals("पाँच", SlotFill.match("पेज पाँच खोलो", "पेज {n} खोलो"))
    }

    @Test
    fun `slot fill rejects a non-matching frame`() {
        assertNull(SlotFill.match("किताब खोलो", "पेज {n} खोलो"))
    }

    @Test
    fun `slot fill rejects an empty slot`() {
        assertNull(SlotFill.match("पेज खोलो", "पेज {n} खोलो"))
    }

    @Test
    fun `slot fill returns null when the template has no marker`() {
        assertNull(SlotFill.match("पेज पाँच खोलो", "पेज पाँच खोलो"))
    }

    // --- Lookup ladder -------------------------------------------------------------------

    private fun phrase(
        id: Long,
        hi: String,
        template: Boolean = false,
    ) = Phrase(
        id = id,
        lakshyaCode = "L-TEST",
        hiText = hi,
        hiNormalized = if (template) {
            HindiNormalizer.normalizeTemplate(hi)
        } else {
            HindiNormalizer.normalize(hi)
        },
        targetTextNative = "placeholder-unr",
        targetTextDeva = "placeholder-deva",
        audioRef = "a$id.wav",
        verifiedBy = "test",
        packVersion = "test-v1",
        isTemplate = template,
    )

    private val book = listOf(
        phrase(1, "किताब खोलो"),
        phrase(2, "ताली बजाओ"),
        phrase(3, "पेज {n} खोलो", template = true),
    )

    @Test
    fun `exact hit is VERIFIED with score 1`() {
        val r = PhraseMatcher.lookup("किताब खोलो।", book)
        assertNotNull(r)
        assertEquals(1L, r!!.phrase.id)
        assertEquals(Provenance.VERIFIED, r.provenance)
        assertEquals(1.0, r.score, 0.0001)
        assertNull(r.slotValue)
    }

    @Test
    fun `template hit is VERIFIED and carries the slot value`() {
        val r = PhraseMatcher.lookup("पेज 5 खोलो", book)
        assertNotNull(r)
        assertEquals(3L, r!!.phrase.id)
        assertEquals(Provenance.VERIFIED, r.provenance)
        assertEquals("पाँच", r.slotValue)
    }

    @Test
    fun `fuzzy hit is APPROXIMATE, never VERIFIED`() {
        // Deliberately degraded input, standing in for ASR error at 0.328 WER.
        val r = PhraseMatcher.lookup("किताब खोल", book, threshold = 0.5)
        assertNotNull(r)
        assertEquals(1L, r!!.phrase.id)
        assertEquals(Provenance.APPROXIMATE, r.provenance)
        assertTrue(r.score < 1.0)
    }

    @Test
    fun `unrelated input is a miss so it can route to T1`() {
        assertNull(PhraseMatcher.lookup("आज मौसम अच्छा है", book))
    }

    @Test
    fun `empty input is a miss`() {
        assertNull(PhraseMatcher.lookup("   ।  ", book))
    }

    @Test
    fun `template outranks a fuzzy match on the same input`() {
        // "पेज दस खोलो" is a clean template hit but also fuzzily resembles phrase 1.
        // Ladder order must return the VERIFIED template, not the APPROXIMATE fuzzy match.
        val r = PhraseMatcher.lookup("पेज दस खोलो", book, threshold = 0.1)
        assertNotNull(r)
        assertEquals(Provenance.VERIFIED, r!!.provenance)
        assertEquals(3L, r.phrase.id)
    }

    @Test
    fun `raising the threshold turns a weak fuzzy hit into a miss`() {
        val loose = PhraseMatcher.lookup("किताब खो", book, threshold = 0.3)
        val strict = PhraseMatcher.lookup("किताब खो", book, threshold = 0.99)
        assertNotNull(loose)
        assertNull(strict)
    }
}
