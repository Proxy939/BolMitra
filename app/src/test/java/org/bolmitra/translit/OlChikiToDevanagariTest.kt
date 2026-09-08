package org.bolmitra.translit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The load-bearing test here is [every emitted character reaches the voice intact], not the
 * individual letter mappings.
 *
 * The letter values can only really be judged by a Santali speaker; what code *can* prove is that
 * the two-hop chain never hands the VITS model a symbol it has no embedding for. That failure is
 * silent — synthesis emits either nothing or a wrong phoneme, with no log line — which is the same
 * reason `DevanagariToOdia.validateTable()` exists.
 */
class OlChikiToDevanagariTest {

    /** "ᱯᱚᱛᱚᱵ ᱫᱚ ᱮᱦᱚᱵ ᱢᱮ ᱾" — the model's real output for "किताब खोलो". */
    private val realOutput = "\u1C6F\u1C5A\u1C5B\u1C5A\u1C75 \u1C6B\u1C5A " +
        "\u1C6E\u1C66\u1C5A\u1C75 \u1C62\u1C6E \u1C7E"

    @Test
    fun `every character the table can emit survives the hop to the voice`() {
        assertEquals(
            "characters would reach the VITS model that it has no embedding for",
            emptyList<String>(),
            OlChikiToDevanagari.validateAgainstVoice(),
        )
    }

    @Test
    fun `the model's own Santali output transliterates and reaches the voice`() {
        val deva = OlChikiToDevanagari.transliterate(realOutput)
        assertTrue("dropped ${deva.unmapped}", deva.isClean)

        // Chained exactly as the engine does it. Only the danda should fall out, because the voice
        // inventory has no punctuation — anything else means a real gap.
        val odia = DevanagariToOdia.transliterate(deva.devanagari)
        assertEquals(
            "unexpected characters dropped on the way to the voice: " +
                odia.unmapped.map { "U+%04X".format(it.code) },
            listOf('\u0964'),
            odia.unmapped,
        )
        assertTrue("voice input is empty", odia.odia.isNotBlank())
        for (ch in odia.odia) {
            assertTrue(
                "U+%04X is outside the voice's inventory".format(ch.code),
                ch in DevanagariToOdia.VOCAB,
            )
        }
    }

    // ---- syllable assembly, which is the part that is not a lookup ---------------------------

    @Test
    fun `consonant plus the inherent vowel is a bare consonant`() {
        // ᱠᱚ = k + ɔ. Devanagari carries /ɔ/ in the inherent vowel, so no mark is added.
        assertEquals("\u0915", OlChikiToDevanagari.transliterate("\u1C60\u1C5A").devanagari)
    }

    @Test
    fun `consonant plus a vowel becomes a matra, not an independent letter`() {
        // ᱠᱤ = k + i -> कि, not कइ.
        assertEquals("\u0915\u093F", OlChikiToDevanagari.transliterate("\u1C60\u1C64").devanagari)
    }

    @Test
    fun `a word-initial vowel uses the independent form`() {
        // ᱤ alone -> इ.
        assertEquals("\u0907", OlChikiToDevanagari.transliterate("\u1C64").devanagari)
    }

    @Test
    fun `adjacent consonants get an explicit virama`() {
        // ᱠᱥ = k + s with no vowel anywhere, so BOTH consonants are bare: क्स्. The interior virama
        // stops Devanagari reading an inherent vowel between them, and the final one stops it
        // reading an inherent vowel at the end. Two consonants, two viramas.
        assertEquals(
            "\u0915\u094D\u0938\u094D",
            OlChikiToDevanagari.transliterate("\u1C60\u1C65").devanagari,
        )
        // With a vowel between, only the closing virama remains: ᱠᱚᱥ -> कस्.
        assertEquals(
            "\u0915\u0938\u094D",
            OlChikiToDevanagari.transliterate("\u1C60\u1C5A\u1C65").devanagari,
        )
    }

    @Test
    fun `a word-final consonant gets a virama`() {
        // ᱠᱚᱥ -> कोस would be wrong; /kɔs/ ends on a bare consonant, so कस्.
        assertEquals(
            "\u0915\u0938\u094D",
            OlChikiToDevanagari.transliterate("\u1C60\u1C5A\u1C65").devanagari,
        )
    }

    @Test
    fun `a syllable is closed before a space`() {
        val r = OlChikiToDevanagari.transliterate("\u1C60\u1C5A\u1C65 \u1C60")
        assertEquals("\u0915\u0938\u094D \u0915\u094D", r.devanagari)
    }

    // ---- the CLDR rules a plain character table would have missed ----------------------------

    @Test
    fun `the four stops voice after a letter but not word-initially`() {
        // ᱛ alone is /t/ -> त. ᱚᱛ has it after a vowel, so /d/ -> द.
        assertEquals("\u0924\u094D", OlChikiToDevanagari.transliterate("\u1C5B").devanagari)
        assertEquals(
            "\u0905\u0926\u094D",
            OlChikiToDevanagari.transliterate("\u1C5A\u1C5B").devanagari,
        )
    }

    @Test
    fun `positional voicing is what makes potob come out right`() {
        // ᱯᱚᱛᱚᱵ: word-initial ᱯ stays /p/, but ᱛ and ᱵ follow letters so both voice.
        // Getting this wrong yields "potop" — recognisably the wrong word.
        assertEquals(
            "\u092A\u0926\u092C\u094D",
            OlChikiToDevanagari.transliterate("\u1C6F\u1C5A\u1C5B\u1C5A\u1C75").devanagari,
        )
    }

    @Test
    fun `the aspiration letter forms a digraph rather than a consonant of its own`() {
        // ᱠᱷ is /kʰ/ -> ख, one letter. Treating ᱷ as a consonant would give क + something.
        assertEquals("\u0916\u094D", OlChikiToDevanagari.transliterate("\u1C60\u1C77").devanagari)
    }

    @Test
    fun `ahad forces the voiced reading and phaarkaa forces the plain one`() {
        // ᱵᱽ -> /b/ -> ब even word-initially; ᱵᱼ -> /pʼ/ -> प.
        assertEquals("\u092C\u094D", OlChikiToDevanagari.transliterate("\u1C75\u1C7D").devanagari)
        assertEquals("\u092A\u094D", OlChikiToDevanagari.transliterate("\u1C75\u1C7C").devanagari)
    }

    @Test
    fun `mu tuddag nasalises the vowel it follows`() {
        // ᱟᱸ -> आँ.
        assertEquals("\u0906\u0901", OlChikiToDevanagari.transliterate("\u1C5F\u1C78").devanagari)
    }

    @Test
    fun `relaa lengthens i and u, and geminates a consonant`() {
        // ᱤᱻ -> ई (Devanagari has the long form).
        assertEquals("\u0908", OlChikiToDevanagari.transliterate("\u1C64\u1C7B").devanagari)
        // ᱞᱻ is /lː/ -> ल्ल.
        assertEquals(
            "\u0932\u094D\u0932\u094D",
            OlChikiToDevanagari.transliterate("\u1C5E\u1C7B").devanagari,
        )
    }

    @Test
    fun `decomposed mu-gahla is normalised in either order`() {
        // Real texts write ᱺ as ᱹ+ᱸ or ᱸ+ᱹ. Both must reach the same branch.
        val a = OlChikiToDevanagari.transliterate("\u1C5F\u1C79\u1C78").devanagari
        val b = OlChikiToDevanagari.transliterate("\u1C5F\u1C78\u1C79").devanagari
        val c = OlChikiToDevanagari.transliterate("\u1C5F\u1C7A").devanagari
        assertEquals(c, a)
        assertEquals(c, b)
    }

    @Test
    fun `mucaad becomes a danda`() {
        assertEquals("\u0964", OlChikiToDevanagari.transliterate("\u1C7E").devanagari)
        assertEquals("\u0965", OlChikiToDevanagari.transliterate("\u1C7F").devanagari)
    }

    @Test
    fun `digits convert but are reported as approximated because the voice cannot say them`() {
        val r = OlChikiToDevanagari.transliterate("\u1C55")   // ᱕ = 5
        assertEquals("\u096B", r.devanagari)
        assertTrue("digits must be flagged, the voice inventory has no numerals",
            r.approximated.isNotEmpty())
        // And they genuinely do fall out at the next hop, rather than being mis-spoken.
        assertTrue(DevanagariToOdia.transliterate(r.devanagari).unmapped.isNotEmpty())
    }

    @Test
    fun `spaces and foreign fragments pass through untouched`() {
        // Translation output can contain Latin or Devanagari that survived intact.
        val r = OlChikiToDevanagari.transliterate("\u1C60\u1C5A ok")
        assertEquals("\u0915 ok", r.devanagari)
        assertTrue(r.isClean)
    }

    @Test
    fun `empty input yields empty output`() {
        val r = OlChikiToDevanagari.transliterate("")
        assertEquals("", r.devanagari)
        assertTrue(r.isClean)
    }

    @Test
    fun `every lossy fold is documented for review`() {
        // The notes are the deliverable a Santali speaker actually works from, so an empty or
        // token list would defeat the point.
        assertTrue("LOSSY_NOTES is too short to be a real review list",
            OlChikiToDevanagari.LOSSY_NOTES.size >= 8)
        assertTrue(
            "the CLDR disagreement over ᱭ must stay called out — it changes words",
            OlChikiToDevanagari.LOSSY_NOTES.any { it.contains("\u1C6D") },
        )
    }
}
