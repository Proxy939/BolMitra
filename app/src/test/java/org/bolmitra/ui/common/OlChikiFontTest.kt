package org.bolmitra.ui.common

import java.awt.Font
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Font reachability: every Ol Chiki codepoint the app can emit must exist in the bundled face.
 *
 * This is the check that would otherwise only be caught by looking at a screenshot. A missing glyph
 * renders as a tofu box — no exception, no log line, and a build that reports success. The project's
 * transliteration tables are already guarded this way (`validateAgainstVoice`, `validateTable`); a
 * font is the same kind of silent consumer and gets the same kind of assertion.
 *
 * Uses AWT's `canDisplay` rather than a hand-rolled `cmap` parser: it is the same question, answered
 * by code that is already correct, and AWT is available on the JVM test classpath.
 */
class OlChikiFontTest {

    /** Every assigned Ol Chiki codepoint: 10 digits, 30 letters, 6 modifiers, 2 punctuation. */
    private val allOlChiki = (0x1C50..0x1C7F).toList()

    private val fontFile: File by lazy {
        // Gradle runs unit tests with the module directory as the working directory, but be
        // tolerant: a failure to locate the file would otherwise look like a coverage failure.
        val candidates = listOf(
            File("src/main/res/font/noto_sans_ol_chiki.ttf"),
            File("app/src/main/res/font/noto_sans_ol_chiki.ttf"),
        )
        candidates.firstOrNull { it.isFile }
            ?: error("bundled font not found; looked in ${candidates.map { it.absolutePath }}")
    }

    private val awtFont: Font by lazy { Font.createFont(Font.TRUETYPE_FONT, fontFile) }

    @Test
    fun `run splitter self check passes`() {
        assertEquals(emptyList<String>(), OlChikiFont.validate())
    }

    @Test
    fun `bundled font is present and a valid TrueType file`() {
        assertTrue("font file should be non-trivial", fontFile.length() > 4_000)
        assertEquals("Noto Sans Ol Chiki", awtFont.getFamily(java.util.Locale.ROOT))
    }

    /**
     * The load-bearing assertion. All 48, not "most of them" — real corpora use the rare ones:
     * U+1C7A appears 70 times and U+1C7F five times across a 66,363-word Santali wordlist, so a
     * font missing them fails on genuine text rather than on an edge case.
     */
    @Test
    fun `bundled font covers all 48 Ol Chiki codepoints`() {
        val missing = allOlChiki.filterNot { awtFont.canDisplay(it) }
        assertTrue(
            "not displayable: ${missing.map { "U+%04X".format(it) }}",
            missing.isEmpty(),
        )
    }

    @Test
    fun `bundled font covers space so mixed runs do not break`() {
        listOf(0x0020, 0x00A0).forEach {
            assertTrue("U+%04X should be displayable".format(it), awtFont.canDisplay(it))
        }
    }

    /**
     * Documents the constraint that makes [OlChikiFont.annotate] necessary rather than optional.
     * If this ever starts failing because the font gained Devanagari coverage, the per-run styling
     * could be simplified — but until then, applying this family to Hindi text would tofu it.
     */
    @Test
    fun `bundled font does NOT cover Devanagari, which is why per-run styling exists`() {
        // क, ा, म — ordinary Hindi.
        listOf(0x0915, 0x093E, 0x092E).forEach {
            assertTrue(
                "U+%04X unexpectedly displayable; per-run styling may no longer be needed".format(it),
                !awtFont.canDisplay(it),
            )
        }
    }

    @Test
    fun `every character OlChikiToDevanagari accepts is renderable`() {
        // Closes the loop between the two guards: the transliterator's input inventory and the
        // font's coverage must be the same set, or we can display something we cannot speak, or
        // speak something we cannot display.
        val accepted = allOlChiki.filter { cp ->
            val r = org.bolmitra.translit.OlChikiToDevanagari.transliterate(cp.toChar().toString())
            r.unmapped.isEmpty()
        }
        assertTrue("transliterator should accept most of the block", accepted.size >= 40)
        val notRenderable = accepted.filterNot { awtFont.canDisplay(it) }
        assertTrue(
            "transliterator accepts but font cannot draw: ${notRenderable.map { "U+%04X".format(it) }}",
            notRenderable.isEmpty(),
        )
    }
}
