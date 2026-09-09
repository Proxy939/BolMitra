package org.bolmitra.curriculum

import org.bolmitra.phrasebook.Provenance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks on offline worksheet sharing.
 *
 * The property that makes the feature work is the round trip: a scan must regenerate the *same*
 * sheet, item for item, with no network and nothing transmitted but a few dozen characters. If
 * anything breaks determinism upstream this is what notices, because a shared worksheet would
 * quietly come back different on the receiving tablet.
 */
class WorksheetShareTest {

    private val vocab = mapOf(
        "cow" to TargetWord("cow", "ᱜᱟᱨᱩ", Provenance.CORPUS, "Hembram/Glossary"),
        "mango" to TargetWord("mango", "ᱩᱞ", Provenance.CORPUS, "Hembram/Glossary"),
    )

    private fun sheet(
        topic: WorksheetTopic = WorksheetTopic.NUMBERS,
        band: GradeBand = GradeBand.GRADE_1,
        seed: Long = 2026,
        pack: String? = "glossary-v1",
    ): Sheet = (
        TopicWorksheets.build(topic, band, seed, vocab, pack) as SheetResult.Ready
        ).sheet

    @Test
    fun `a scan regenerates the identical sheet`() {
        val original = sheet()
        val payload = WorksheetShare.payloadFor(original)

        val spec = WorksheetShare.parseSpec(payload)
        assertNotNull("the payload must carry a recoverable spec", spec)

        val rebuilt = WorksheetShare.rebuild(spec!!, vocab, "glossary-v1")
        assertTrue(rebuilt is SheetResult.Ready)
        assertEquals(original.items, (rebuilt as SheetResult.Ready).sheet.items)
    }

    @Test
    fun `the round trip holds for every topic and grade`() {
        for (topic in WorksheetTopic.entries) {
            for (band in listOf(GradeBand.GRADE_1, GradeBand.GRADE_2)) {
                val original = sheet(topic, band, seed = 31)
                val spec = WorksheetShare.parseSpec(WorksheetShare.payloadFor(original))
                assertNotNull("$topic/$band lost its spec", spec)
                assertEquals(topic, spec!!.topic)
                assertEquals(band, spec.gradeBand)
                val rebuilt = WorksheetShare.rebuild(spec, vocab, "glossary-v1")
                assertEquals(
                    "$topic/$band did not round-trip",
                    original.items,
                    (rebuilt as SheetResult.Ready).sheet.items,
                )
            }
        }
    }

    @Test
    fun `the answer key is not in the payload`() {
        // A shared sheet may be handed to a child. The key is regenerated on a teacher's tablet.
        val original = sheet(WorksheetTopic.NUMBERS, GradeBand.GRADE_2)
        val payload = WorksheetShare.payloadFor(original)
        val answers = original.items.mapNotNull { it.answer }.filter { it.length > 1 }
        answers.forEach {
            // Guarded on length: a single digit legitimately appears inside a prompt like "9 - 1".
            assertTrue("payload leaks the answer '$it'", !payload.contains("= $it"))
        }
    }

    @Test
    fun `the payload is readable text with no URL in it`() {
        val payload = WorksheetShare.payloadFor(sheet())
        assertTrue("no link belongs in an offline payload", !payload.contains("http"))
        assertTrue(payload.startsWith("BolMitra"))
        // The questions must be present for a camera-only reader.
        assertTrue(payload.contains(sheet().items.first().hiText))
    }

    @Test
    fun `the spec line survives even when the body is truncated`() {
        // This is why the spec goes second rather than last: a BolMitra scan is never the
        // truncated version, however long the sheet is.
        val original = sheet(WorksheetTopic.ANIMALS, GradeBand.GRADE_1)
        val tiny = WorksheetShare.payloadFor(original, maxChars = 140)
        assertNotNull(WorksheetShare.parseSpec(tiny))
        val rebuilt = WorksheetShare.rebuild(WorksheetShare.parseSpec(tiny)!!, vocab, "glossary-v1")
        assertEquals(original.items, (rebuilt as SheetResult.Ready).sheet.items)
    }

    @Test
    fun `truncation says that it truncated`() {
        val payload = WorksheetShare.payloadFor(sheet(WorksheetTopic.ANIMALS), maxChars = 200)
        assertTrue("a shortened sheet must announce it", payload.contains("…"))
    }

    @Test
    fun `a payload stays inside the QR capacity ceiling`() {
        // Devanagari and Ol Chiki are three bytes per character in UTF-8, and QR version 40 at
        // level L tops out near 2,900 bytes. The char cap is only useful if the byte count follows.
        for (topic in WorksheetTopic.entries) {
            for (band in listOf(GradeBand.GRADE_1, GradeBand.GRADE_2)) {
                val payload = WorksheetShare.payloadFor(sheet(topic, band))
                assertTrue("$topic/$band payload is ${payload.length} chars", payload.length <= 800)
                val bytes = payload.toByteArray(Charsets.UTF_8).size
                assertTrue("$topic/$band payload is $bytes bytes, past the QR ceiling", bytes <= 2900)
            }
        }
    }

    @Test
    fun `a different pack version is reported rather than silently accepted`() {
        val spec = WorksheetShare.parseSpec(WorksheetShare.payloadFor(sheet(pack = "glossary-v1")))!!
        assertTrue(spec.matchesPack("glossary-v1"))
        assertTrue("a different pack must not read as a match", !spec.matchesPack("glossary-v2"))
        // Unknown on either side is unknown, not a match.
        assertTrue(!spec.matchesPack(null))
    }

    @Test
    fun `a sheet shared without a pack version cannot claim to match one`() {
        val spec = WorksheetShare.parseSpec(WorksheetShare.payloadFor(sheet(pack = null)))!!
        assertNull(spec.packVersion)
        assertTrue(!spec.matchesPack("glossary-v1"))
    }

    @Test
    fun `malformed and hostile payloads return null instead of throwing`() {
        // A scan is untrusted input and must not be able to crash a screen in front of a class.
        listOf(
            "", "   ", "hello world", "BM1", "BM1|", "BM1|NOPE|GRADE_1|1|p",
            "BM1|NUMBERS|GRADE_9|1|p", "BM1|NUMBERS|GRADE_1|notanumber|p",
            "BM2|NUMBERS|GRADE_1|1|p", "BM1|NUMBERS|GRADE_1",
            "\u0000\u0001", "BM1|NUMBERS|GRADE_1|99999999999999999999999|p",
        ).forEach {
            assertNull("should not parse: '$it'", WorksheetShare.parseSpec(it))
        }
    }

    @Test
    fun `a spec is found even when a camera app prepends its own text`() {
        val payload = "Scanned QR code:\nsome app banner\n" + WorksheetShare.payloadFor(sheet())
        assertNotNull(WorksheetShare.parseSpec(payload))
    }

    @Test
    fun `the spec line is short enough to always fit`() {
        WorksheetTopic.entries.forEach { topic ->
            val line = WorksheetShare.specLine(sheet(topic, GradeBand.GRADE_2, Long.MAX_VALUE))
            assertTrue("spec line is ${line.length} chars: $line", line.length < 80)
        }
    }

    @Test
    fun `rebuilding with a different glossary keeps the arithmetic identical`() {
        // Arithmetic is script-neutral, so a receiver on another pack must still get the same sums.
        val original = sheet(WorksheetTopic.NUMBERS, GradeBand.GRADE_2)
        val spec = WorksheetShare.parseSpec(WorksheetShare.payloadFor(original))!!
        val rebuilt = WorksheetShare.rebuild(spec, emptyMap(), "glossary-v2") as SheetResult.Ready
        assertEquals(
            original.items.map { it.hiText },
            rebuilt.sheet.items.map { it.hiText },
        )
    }
}

/** Checks on flashcard decks. */
class FlashcardsTest {

    private val vocab = mapOf(
        "cow" to TargetWord("cow", "ᱜᱟᱨᱩ", Provenance.CORPUS, "Hembram/Glossary"),
        "goat" to TargetWord("goat", "ᱢᱮᱨᱚᱢ", Provenance.CORPUS, "Hembram/Glossary"),
    )

    @Test
    fun `decks pass their own self-check`() {
        assertEquals(emptyList<String>(), Flashcards.validate(vocab))
        assertEquals(emptyList<String>(), Flashcards.validate(emptyMap()))
    }

    @Test
    fun `every picture topic has a deck`() {
        val decks = Flashcards.allDecks(vocab)
        assertEquals(
            WorksheetTopic.entries.count { it.pictureCategory != null },
            decks.size,
        )
        decks.forEach { assertTrue("${it.topic} deck is empty", it.cards.isNotEmpty()) }
    }

    @Test
    fun `a card with a corpus word carries its provenance and source`() {
        val deck = Flashcards.deckFor(WorksheetTopic.ANIMALS, vocab)
        val cow = deck.cards.first { it.term == "cow" }
        assertEquals("गाय", cow.hindi)
        assertEquals("ᱜᱟᱨᱩ", cow.targetNative)
        assertEquals(Provenance.CORPUS, cow.targetProvenance)
        assertEquals("Hembram/Glossary", cow.targetSrc)
        assertTrue(cow.isBilingual)
    }

    @Test
    fun `a card without a corpus word is Hindi-only and claims nothing`() {
        val deck = Flashcards.deckFor(WorksheetTopic.ANIMALS, vocab)
        val notInCorpus = deck.cards.first { it.term == "peacock" || it.term == "bear" }
        assertNull(notInCorpus.targetNative)
        assertNull("no word means no provenance", notInCorpus.targetProvenance)
        assertNull(notInCorpus.targetSrc)
        assertTrue("the Hindi side must still be usable", notInCorpus.hindi.isNotBlank())
    }

    @Test
    fun `bilingual cards come first`() {
        val deck = Flashcards.deckFor(WorksheetTopic.ANIMALS, vocab)
        val firstMonolingual = deck.cards.indexOfFirst { !it.isBilingual }
        val lastBilingual = deck.cards.indexOfLast { it.isBilingual }
        assertTrue("bilingual cards should precede the rest", lastBilingual < firstMonolingual)
        assertEquals(2, deck.bilingualCardCount)
    }

    @Test
    fun `a deck is stable across builds and needs no seed`() {
        assertEquals(
            Flashcards.deckFor(WorksheetTopic.FRUITS, vocab).cards,
            Flashcards.deckFor(WorksheetTopic.FRUITS, vocab).cards,
        )
    }

    @Test
    fun `colour cards carry a swatch and others carry a glyph`() {
        Flashcards.deckFor(WorksheetTopic.COLOURS).cards.forEach {
            assertNotNull("${it.term} needs a swatch", it.colourArgb)
        }
        Flashcards.deckFor(WorksheetTopic.ANIMALS).cards.forEach {
            assertTrue("${it.term} needs a glyph", it.glyph.isNotEmpty())
            assertNull(it.colourArgb)
        }
    }

    @Test
    fun `an empty glossary still yields a usable Hindi deck`() {
        val deck = Flashcards.deckFor(WorksheetTopic.ANIMALS, emptyMap())
        assertEquals(0, deck.bilingualCardCount)
        assertTrue(deck.cards.isNotEmpty())
        assertTrue(deck.cards.all { it.hindi.isNotBlank() })
    }

    @Test
    fun `a limit trims the deck without reordering it`() {
        val full = Flashcards.deckFor(WorksheetTopic.ANIMALS, vocab)
        val trimmed = Flashcards.deckFor(WorksheetTopic.ANIMALS, vocab, limit = 3)
        assertEquals(3, trimmed.cards.size)
        assertEquals(full.cards.take(3), trimmed.cards)
    }
}
