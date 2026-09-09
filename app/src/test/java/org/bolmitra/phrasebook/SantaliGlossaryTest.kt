package org.bolmitra.phrasebook

import java.io.File
import org.bolmitra.translit.OlChikiToDevanagari
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks on the shipped glossary asset and on the two properties that make it safe to serve.
 *
 * Reads `assets/santali-glossary.tsv` off disk rather than through a `Context`, because everything
 * worth asserting here — the parse, the matcher, the transliterator — is pure Kotlin. Only
 * `SantaliGlossary.load` needs Android, and it is a thin wrapper around this same parse.
 */
class SantaliGlossaryTest {

    private data class Row(
        val en: String, val hi: String, val sat: String, val src: String,
        val kind: String, val pos: String, val flags: String, val variants: String,
    ) {
        /**
         * The `hi` column is `;`-separated, because one corpus row can be reachable from several
         * Hindi phrasings. Mirrors `SantaliGlossary.Term.hindiKeys`; if the two ever disagree this
         * test file stops testing what the app actually builds.
         */
        val hiKeys: List<String>
            get() = hi.split(';').map { it.trim() }.filter { it.isNotEmpty() }
    }

    private val asset: File by lazy {
        listOf(
            File("src/main/assets/santali-glossary.tsv"),
            File("app/src/main/assets/santali-glossary.tsv"),
        ).firstOrNull { it.isFile } ?: error("glossary asset not found; run tools/build-santali-glossary.py")
    }

    private val rows: List<Row> by lazy {
        asset.readLines().drop(1).filter { it.isNotBlank() }.mapNotNull { line ->
            val f = line.split('\t', limit = 8)
            if (f.size < 8) null else Row(f[0], f[1], f[2], f[3], f[4], f[5], f[6], f[7])
        }
    }

    private val hindiKeyed: List<Row> by lazy { rows.filter { it.hi.isNotBlank() } }

    private fun byEn(en: String) = rows.firstOrNull { it.en.equals(en, ignoreCase = true) }

    /** One Phrase per Hindi alias, exactly as `SantaliGlossary.phrasesFor` emits them. */
    private fun toPhrases(r: Row, firstId: Long): List<Phrase> =
        r.hiKeys.mapIndexed { i, key -> toPhrase(r, firstId + i, key) }

    private fun toPhrase(r: Row, id: Long, key: String = r.hiKeys.first()) = Phrase(
        id = id,
        lakshyaCode = null,
        hiText = key,
        hiNormalized = HindiNormalizer.normalize(key),
        targetTextNative = r.sat,
        targetTextDeva = OlChikiToDevanagari.transliterate(r.sat).devanagari,
        audioRef = null,
        verifiedBy = null,
        packVersion = SantaliGlossary.PACK_VERSION,
        src = r.src,
        srcEn = r.en,
        provenance = Provenance.CORPUS,
    )

    // --- the asset itself ----------------------------------------------------------------

    @Test
    fun `asset parses and carries Hindi keys`() {
        assertTrue("only ${rows.size} rows", rows.size > 5_000)
        assertTrue("only ${hindiKeyed.size} Hindi-keyed", hindiKeyed.size >= 350)
    }

    @Test
    fun `every row has Ol Chiki in its target`() {
        val bad = rows.filterNot { r -> r.sat.any { it.code in 0x1C50..0x1C7F } }
        assertTrue("rows with no Ol Chiki: ${bad.take(5).map { it.en }}", bad.isEmpty())
    }

    @Test
    fun `no target carries an unrepaired ASCII period`() {
        // The ingest tool must have converted these to U+1C79. If any survive, the desktop
        // normaliser and OlChikiNormalizer have drifted apart.
        val bad = rows.filter { r ->
            r.sat.withIndex().any { (i, c) ->
                c == '.' && i > 0 && r.sat[i - 1].code in 0x1C50..0x1C77
            }
        }
        assertTrue("unrepaired periods in: ${bad.take(5).map { it.en }}", bad.isEmpty())
    }

    // --- the two eval failures the glossary exists to fix ---------------------------------

    @Test
    fun `sit and stand are present with both a Hindi key and a Santali form`() {
        for (en in listOf("sit", "stand")) {
            val r = byEn(en)
            assertNotNull("'$en' missing from the glossary", r)
            r!!
            assertTrue("'$en' has no Hindi key", r.hi.isNotBlank())
            assertTrue("'$en' has no Santali", r.sat.isNotBlank())
        }
        // The stems the research identified: sit -> durub, stand -> tego.
        assertEquals("\u1C6B\u1C69\u1C68\u1C69\u1C75", byEn("sit")!!.sat)
        assertEquals("\u1C5B\u1C6E\u1C5C\u1C5A", byEn("stand")!!.sat)
    }

    /**
     * A teacher's first word must reach the corpus rather than the model.
     *
     * `नमस्ते` used to return machine output carrying a `MACHINE` marker, and the Santali was never
     * the problem: GATITOS already held `Hello` as ᱡᱚᱦᱟᱨ (johar), the ordinary Santali greeting,
     * with **no Hindi key**, so the T0 exact rung could not see it and the turn fell through to T1.
     * The fix was a Hindi key, not a translation.
     *
     * Pinned because the asset is a build artifact: a future run of the builder with the keys file
     * mis-edited would put a guess back in front of a class, silently and with a marker that admits
     * it only after the class has already heard it.
     */
    @Test
    fun `namaste reaches the corpus greeting rather than the model`() {
        val hello = byEn("Hello")
        assertNotNull("'Hello' missing from the glossary", hello)
        // U+1C61 U+1C5A U+1C66 U+1C5F U+1C68 — johar.
        assertEquals("\u1C61\u1C5A\u1C66\u1C5F\u1C68", hello!!.sat)
        assertTrue("नमस्ते is not a lookup key for the greeting", "नमस्ते" in hello.hiKeys)
        assertTrue("नमस्कार is not a lookup key", "नमस्कार" in hello.hiKeys)

        // Reaching it must be an EXACT hit, so the row arrives at CORPUS and not downgraded.
        val phrases = toPhrases(hello, 9_000)
        val book = InMemoryPhrasebook(phrases)
        val hit = book.lookup(HindiNormalizer.normalize("नमस्ते"))
        assertNotNull("नमस्ते does not resolve in the phrasebook", hit)
        assertEquals("\u1C61\u1C5A\u1C66\u1C5F\u1C68", hit!!.phrase.targetTextNative)
        assertEquals(Provenance.CORPUS, hit.provenance)
    }

    @Test
    fun `the defective singular greeting row is never reachable`() {
        // GATITOS spells the singular `greeting` as ᱡᱦᱟᱨ (jhar) — the ᱚ is missing — while `Hello`
        // and `greetings` both carry the correct ᱡᱚᱦᱟᱨ. Keying the broken row would put the defect
        // in a classroom, so it must stay unreachable from Hindi.
        byEn("greeting")?.let {
            assertTrue(
                "the misspelt 'greeting' row (jhar) must not carry a Hindi key",
                it.hi.isBlank(),
            )
        }
    }

    @Test
    fun `classroom courtesies resolve to corpus text`() {
        // The words a teacher reaches for straight after नमस्ते. Each was already in the corpus and
        // only needed a Hindi key; none of these Santali strings was authored here.
        val expected = mapOf(
            "welcome" to "\u1C6B\u1C5F\u1C68\u1C5F\u1C62",                     // daram
            "greetings" to "\u1C61\u1C5A\u1C66\u1C5F\u1C68",                   // johar
        )
        expected.forEach { (en, sat) ->
            val row = byEn(en)
            assertNotNull("'$en' missing from the glossary", row)
            assertEquals("'$en' target changed", sat, row!!.sat)
            assertTrue("'$en' has no Hindi key", row.hi.isNotBlank())
        }
    }

    @Test
    fun `the how-are-you sentence stays unreachable while its punctuation is unresolved`() {
        // ᱟᱢ ᱪᱮᱞᱠᱟ ᱢᱮᱱᱟᱢᱟ? is real Hembram text and exactly what the teacher typed, but it ends in
        // an ASCII question mark that the voice would read aloud — `no Hindi-reachable target
        // carries list punctuation` catches it, which is the guard doing its job. Keying it needs a
        // punctuation rule for Ol Chiki first, so this records the decision rather than leaving the
        // next person to rediscover the failure.
        byEn("How are you.")?.let {
            assertTrue(
                "keying this row needs its trailing '?' resolved first",
                it.hi.isBlank(),
            )
        }
    }

    /**
     * The GATITOS numeral defect must not have survived ingest.
     *
     * GATITOS publishes one string for six, seven AND eight. This app teaches counting, so the
     * builder overrides numerals with Hembram's unconditionally. If precedence is ever reordered,
     * this fails.
     */
    @Test
    fun `six seven and eight are three different words`() {
        val six = byEn("six")!!.sat
        val seven = byEn("seven")!!.sat
        val eight = byEn("eight")!!.sat
        assertTrue("six == seven ($six)", six != seven)
        assertTrue("seven == eight ($seven)", seven != eight)
        assertTrue("six == eight ($six)", six != eight)
    }

    @Test
    fun `twelve and twenty are not the same word`() {
        assertTrue(byEn("twelve")!!.sat != byEn("twenty")!!.sat)
    }

    // --- SAFETY: a word must not be served in place of a sentence -------------------------

    /**
     * The property [SantaliGlossary]'s docs depend on, asserted rather than assumed.
     *
     * Most glossary rows are single words. If `किताब खोलो` ("open the book") fuzzy-matched the
     * one-word row `किताब`, the class would hear "book" with the verb silently dropped — a
     * confident wrong answer, which is the failure this project can least afford.
     *
     * It is prevented by `FUZZY_THRESHOLD` being high enough that a short word cannot score against
     * a sentence containing it. **If the threshold is ever lowered, this test fails before a class
     * hears the consequence.**
     */
    @Test
    fun `a single glossary word never fuzzy-matches a sentence containing it`() {
        val cases = listOf(
            "किताब" to "किताब खोलो",
            "पानी" to "पानी पीना है",
            "नाम" to "अपना नाम बताओ",
            "बैठो" to "सब लोग बैठ जाओ",
        )
        var id = 1L
        for ((word, sentence) in cases) {
            val row = rows.firstOrNull { word in it.hiKeys } ?: continue
            // Only the single-word key is offered, which is the case under test. The `बैठ जाओ`
            // alias is a legitimate exact hit and is asserted separately below.
            val hit = PhraseMatcher.lookup(sentence, listOf(toPhrase(row, id++, word)))
            assertTrue(
                "'$sentence' matched the single word '$word' " +
                    "(score ${hit?.score}); the verb would be dropped",
                hit == null,
            )
        }
    }

    @Test
    fun `an exact match on a glossary row is CORPUS, never VERIFIED`() {
        val row = rows.first { it.hi.isNotBlank() }
        val key = row.hiKeys.first()
        val hit = PhraseMatcher.lookup(key, listOf(toPhrase(row, 1, key)))
        assertNotNull("exact match on '$key' should hit", hit)
        assertEquals(Provenance.CORPUS, hit!!.provenance)
        assertEquals(1.0, hit.score, 1e-9)
    }

    @Test
    fun `a hand-seeded VERIFIED row outranks a glossary row with the same Hindi`() {
        // Load order in LiveTurnEngine is DemoSeed first, glossary second, and PhraseMatcher's
        // exact rung takes the FIRST match. Reviewed content must win.
        val glossaryRow = rows.first { it.hi.isNotBlank() }
        val key = glossaryRow.hiKeys.first()
        val seeded = toPhrase(glossaryRow, 1, key).copy(
            provenance = Provenance.VERIFIED,
            targetTextNative = "seeded-native",
            verifiedBy = "a named speaker",
        )
        val corpus = toPhrase(glossaryRow, 2, key)

        val hit = PhraseMatcher.lookup(key, listOf(seeded, corpus))
        assertEquals(Provenance.VERIFIED, hit!!.provenance)
        assertEquals("seeded-native", hit.phrase.targetTextNative)
    }

    // --- SAFETY: everything served must be speakable ---------------------------------------

    /**
     * A row whose Ol Chiki transliterates to nothing would reach the voice as silence while the UI
     * reported a successful hit. [SantaliGlossary.phrasesFor] filters those out; this measures how
     * many there are, so the number cannot creep up unnoticed.
     */
    @Test
    fun `almost every Hindi-keyed row transliterates to non-blank Devanagari`() {
        val blank = hindiKeyed.filter {
            OlChikiToDevanagari.transliterate(it.sat).devanagari.isBlank()
        }
        assertTrue(
            "${blank.size} of ${hindiKeyed.size} Hindi-keyed rows transliterate to nothing: " +
                blank.take(5).map { it.en },
            blank.isEmpty(),
        )
    }

    @Test
    fun `no Ol Chiki survives into the Devanagari handed to the voice`() {
        // DevanagariToOdia drops Ol Chiki entirely, so a leak here becomes silence downstream.
        val leaks = hindiKeyed.filter { r ->
            OlChikiToDevanagari.transliterate(r.sat).devanagari.any { it.code in 0x1C50..0x1C7F }
        }
        assertTrue("Ol Chiki leaked for: ${leaks.take(5).map { it.en }}", leaks.isEmpty())
    }

    /**
     * A printed glossary lists alternatives inside one cell; a voice reads that cell aloud as one
     * phrase.
     *
     * Hembram's *knee* is `ᱜᱷᱩᱴᱟᱜ, ᱜᱚᱱᱴᱷᱮ` — two synonyms. Ingested whole, a class that asked for
     * "knee" hears both words run together with the comma spoken by the TTS. The parenthesised
     * variety is the same defect: `ᱤᱨᱤᱞ ᱠᱩᱲᱤ (ᱥᱟᱞᱤ)` is a headword plus a gloss.
     *
     * The ingest tool splits these and keeps the extra forms in `variants`. This asserts the
     * property on the field that is actually **spoken**, because that is where the harm is: a
     * reviewer scanning the TSV would read `ᱜᱷᱩᱴᱟᱜ, ᱜᱚᱱᱴᱷᱮ` as obviously two words and never
     * notice that the app does not.
     */
    @Test
    fun `no Hindi-reachable target carries list punctuation`() {
        val markers = charArrayOf(',', ';', '/', '(', ')', '[', ']', '=', '?', '_')
        val bad = hindiKeyed.filter { r -> r.sat.any { it in markers } }
        assertTrue(
            "these rows would be spoken with their annotation read aloud: " +
                bad.take(5).map { "${it.en} -> ${it.sat}" },
            bad.isEmpty(),
        )
    }

    /**
     * Latin letters and ASCII digits in a target mean an untranslated source cell survived ingest.
     *
     * GATITOS really does publish `till done` and `XXXX` as Santali targets.
     * [org.bolmitra.translit.OlChikiNormalizer.rejectionReason] rejects a target with *no* Ol Chiki,
     * but a mixed cell passes that gate, and mixed is what a partial translation looks like.
     */
    /**
     * The two sentences the eval set got wrong now reach the right corpus row exactly.
     *
     * These are the measured failures: T1 translated `बैठ जाओ` to `ᱥᱮᱱ ᱢᱮ` ("go") and `खड़े हो जाओ`
     * to a word meaning "install", both scoring 0.00 on the round trip. They are fixed by Hindi
     * ALIAS keys, not by a rule — `जाओ` is aspectual after a bare verb stem (`बैठ जाओ` = "sit down")
     * but lexical after a noun (`बाहर जाओ` = "go outside"), and no cheap rule tells those apart.
     *
     * Asserting the exact rung matters twice over: it is the only rung that returns the corpus
     * string **verbatim**, and it is the only one that cannot silently pick a different row than the
     * one this test names.
     */
    @Test
    fun `the two failing eval sentences reach their corpus row exactly`() {
        val expected = listOf(
            // sentence           english   Ol Chiki the class must hear
            Triple("बैठ जाओ", "sit", "\u1C6B\u1C69\u1C68\u1C69\u1C75"),      // ᱫᱩᱨᱩᱵ durub
            Triple("खड़े हो जाओ", "stand", "\u1C5B\u1C6E\u1C5C\u1C5A"),      // ᱛᱮᱜᱚ  tego
        )
        // Every Hindi-keyed row, aliases expanded, exactly as the app assembles T0.
        var id = 1L
        val all = hindiKeyed.flatMap { r -> toPhrases(r, id).also { id += it.size } }

        for ((sentence, english, olChiki) in expected) {
            val hit = PhraseMatcher.lookup(HindiNormalizer.normalize(sentence), all)
            assertNotNull("'$sentence' did not match any glossary row", hit)
            assertEquals("'$sentence' matched the wrong row", 1.0, hit!!.score, 1e-9)
            assertEquals(
                "'$sentence' resolved to the wrong Santali " +
                    "(${hit.phrase.targetTextNative.map { "U+%04X".format(it.code) }})",
                olChiki,
                hit.phrase.targetTextNative,
            )
            assertEquals(english, hit.phrase.srcEn)
            // Verbatim corpus content, so it must say so and must carry its citation.
            assertEquals(Provenance.CORPUS, hit.provenance)
            assertTrue("a CORPUS row must name its source", !hit.phrase.src.isNullOrBlank())
        }
    }

    /**
     * An alias must never change WHAT is said, only whether the row is reachable.
     *
     * Every Hindi key attached to one English term points at that term's single Santali string. If
     * an alias could resolve to different content than its sibling, the aliasing mechanism would be
     * choosing words rather than choosing reachability.
     */
    @Test
    fun `all aliases of one term resolve to identical Santali`() {
        val aliased = hindiKeyed.filter { it.hiKeys.size > 1 }
        assertTrue("no aliased rows found; the alias mechanism is untested", aliased.isNotEmpty())
        for (r in aliased) {
            val phrases = toPhrases(r, 1L)
            assertEquals(r.hiKeys.size, phrases.size)
            assertEquals(
                "aliases of '${r.en}' disagree on the target",
                1,
                phrases.map { it.targetTextNative }.distinct().size,
            )
            for (key in r.hiKeys) {
                val hit = PhraseMatcher.lookup(HindiNormalizer.normalize(key), phrases)
                assertNotNull("alias '$key' of '${r.en}' does not match its own row", hit)
                assertEquals(1.0, hit!!.score, 1e-9)
            }
        }
    }

    @Test
    fun `no Hindi-reachable target mixes Latin script into Ol Chiki`() {
        val bad = hindiKeyed.filter { r ->
            r.sat.any { (it in 'a'..'z') || (it in 'A'..'Z') || (it in '0'..'9') }
        }
        assertTrue(
            "Latin leaked into speakable targets: ${bad.take(5).map { "${it.en} -> ${it.sat}" }}",
            bad.isEmpty(),
        )
    }
}
