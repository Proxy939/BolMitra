package org.bolmitra.curriculum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks on the picture vocabulary.
 *
 * The duplicate checks are the ones that matter. Two pictures sharing a Hindi word would let a
 * "circle the आम" item have two right answers — V40's cautionary case, which was found there by a
 * human reading the items and which nothing in a build would catch.
 */
class PictureBankTest {

    @Test
    fun `the bank passes its own self-check`() {
        assertEquals(emptyList<String>(), PictureBank.validate())
    }

    @Test
    fun `no picture is missing its Hindi word`() {
        // A blank Hindi word would print an empty prompt next to a drawing.
        PictureBank.pictures.forEach {
            assertTrue("${it.english} has no Hindi", it.hindi.isNotBlank())
        }
    }

    @Test
    fun `the requested bundles all exist and can fill a sheet`() {
        // Numbers, animals and general knowledge were the three asked for. Numbers are procedural
        // rather than a picture set, so what has to exist is the objects they count.
        listOf(
            PictureBank.Category.ANIMAL,
            PictureBank.Category.BIRD,
            PictureBank.Category.FOOD,
            PictureBank.Category.VEGETABLE,
            PictureBank.Category.BODY,
            PictureBank.Category.FAMILY,
            PictureBank.Category.NATURE,
            PictureBank.Category.SCHOOL,
            PictureBank.Category.COLOUR,
        ).forEach { category ->
            val n = PictureBank.forCategory(category).size
            assertTrue(
                "${category.posValue} has only $n pictures, below the sheet minimum",
                n >= PictureBank.MIN_PICTURES_PER_SHEET,
            )
        }
    }

    @Test
    fun `every category is usable`() {
        assertEquals(
            PictureBank.Category.entries.size,
            PictureBank.usableCategories().size,
        )
    }

    @Test
    fun `non-colour pictures carry a glyph and colours carry a swatch`() {
        PictureBank.pictures.forEach {
            if (it.category == PictureBank.Category.COLOUR) {
                assertNotNull("${it.english} needs a swatch", it.colourArgb)
                assertTrue("${it.english} should not carry a glyph", it.glyph.isEmpty())
            } else {
                assertTrue("${it.english} needs a glyph", it.glyph.isNotEmpty())
                assertNull("${it.english} should not carry a swatch", it.colourArgb)
            }
        }
    }

    @Test
    fun `no tribal-language text is authored in the picture bank`() {
        // Ol Chiki is U+1C50..U+1C7F. A Santali word typed in here would be invented content,
        // which is the one thing the project forbids outright: target words come from the glossary.
        PictureBank.pictures.forEach { picture ->
            val olChiki = picture.hindi.any { it.code in 0x1C50..0x1C7F } ||
                picture.glyph.any { it.code in 0x1C50..0x1C7F }
            assertTrue("${picture.english} contains Ol Chiki", !olChiki)
        }
    }

    @Test
    fun `lookup works by either language and is case-insensitive on the join key`() {
        assertEquals("गाय", PictureBank.byEnglish("cow")?.hindi)
        assertEquals("गाय", PictureBank.byEnglish("COW")?.hindi)
        assertEquals("cow", PictureBank.byHindi("गाय")?.english)
        assertNull(PictureBank.byEnglish("velociraptor"))
    }

    @Test
    fun `categories map onto the glossary's own pos values`() {
        // The join to SantaliGlossary depends on these strings matching its `pos` column, which the
        // measurement found to be animal/bird/food/vegetable/body/family/colour.
        listOf("animal", "bird", "food", "vegetable", "body", "family", "colour").forEach {
            assertNotNull("no category for glossary pos '$it'", PictureBank.Category.fromGlossaryPos(it))
        }
        assertNotNull(PictureBank.Category.fromGlossaryPos("ANIMAL"))
        assertNull(PictureBank.Category.fromGlossaryPos("verb"))
    }

    @Test
    fun `every glyph is a real character rather than a stray replacement`() {
        PictureBank.pictures
            .filter { it.category != PictureBank.Category.COLOUR }
            .forEach {
                assertTrue("${it.english} glyph is a replacement char", !it.glyph.contains('\uFFFD'))
                // Surrogate pairs are expected; a lone surrogate means a broken escape.
                var i = 0
                while (i < it.glyph.length) {
                    val ch = it.glyph[i]
                    if (ch.isHighSurrogate()) {
                        assertTrue(
                            "${it.english} has an unpaired high surrogate",
                            i + 1 < it.glyph.length && it.glyph[i + 1].isLowSurrogate(),
                        )
                        i += 2
                    } else {
                        assertTrue("${it.english} has an unpaired low surrogate", !ch.isLowSurrogate())
                        i++
                    }
                }
            }
    }
}
