package org.bolmitra.translit

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards against drift between [DevanagariToOdia.VOCAB] and the real converted model.
 *
 * [DevanagariToOdia] embeds the model's symbol inventory as a compile-time constant, because the
 * transliterator must be able to validate itself without a model file present. That constant is
 * a *copy*, and copies rot: regenerate the voice from a different MMS snapshot, or convert a
 * different language, and the embedded set silently stops describing reality. The transliterator
 * would then emit symbols the model has no embedding for, producing silence or wrong phonemes
 * with nothing in the logs.
 *
 * So when the converted artifact IS present, this compares them. The model lives under
 * `models/`, which is gitignored (licence-restricted, CC-BY-NC per V3), so the test **skips**
 * rather than fails when it is absent — CI without models must stay green.
 */
class VocabDriftTest {

    /** Unit tests run with the module dir as CWD, so the repo root is one level up. */
    private val tokensFile = File("../models/tts-unr/tokens.txt")

    @Test
    fun `embedded VOCAB matches the converted model tokens file`() {
        if (!tokensFile.isFile) {
            println("SKIP: ${tokensFile.path} absent - run tools/convert-mms-tts.py --lang unr")
            return
        }

        val fromModel = tokensFile.readLines()
            .filter { it.isNotEmpty() }
            // Format is "<symbol> <id>"; the symbol may itself be a space, so split from the right.
            .mapNotNull { it.substringBeforeLast(' ').firstOrNull() }
            .toSet()

        assertTrue("tokens.txt parsed to nothing", fromModel.isNotEmpty())

        val embedded = DevanagariToOdia.VOCAB
        val missingFromEmbedded = fromModel - embedded
        val extraInEmbedded = embedded - fromModel

        assertEquals(
            "symbols in the model but NOT in the embedded VOCAB: " +
                missingFromEmbedded.map { "U+%04X".format(it.code) },
            emptySet<Char>(),
            missingFromEmbedded,
        )
        assertEquals(
            "symbols in the embedded VOCAB but NOT in the model - transliteration could emit " +
                "these and the model would not know them: " +
                extraInEmbedded.map { "U+%04X".format(it.code) },
            emptySet<Char>(),
            extraInEmbedded,
        )
    }

    @Test
    fun `converted model is the Odia-script voice V63 identified`() {
        if (!tokensFile.isFile) {
            println("SKIP: model not converted yet")
            return
        }
        val odia = DevanagariToOdia.VOCAB.count { it.code in 0x0B00..0x0B7F }
        val devanagari = DevanagariToOdia.VOCAB.count { it.code in 0x0900..0x097F }
        // The finding itself, asserted: this voice cannot read the script our text uses.
        assertEquals("expected zero Devanagari in the model vocabulary", 0, devanagari)
        assertTrue("expected the Odia inventory, got $odia symbols", odia >= 45)
    }
}
