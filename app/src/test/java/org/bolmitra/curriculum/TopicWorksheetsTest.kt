package org.bolmitra.curriculum

import org.bolmitra.phrasebook.Provenance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks on topic detection and per-topic sheet generation.
 *
 * The properties worth protecting, all of them things a green build cannot see:
 *
 *  - **Items on a sheet differ.** The bug this replaced produced eight items and four distinct sums,
 *    because a spec carries one radical choice for every item.
 *  - **The NIPUN ceilings hold.** Grade 1 arithmetic must stay inside operands of 9 and sums of 20.
 *    Widening a constant to make sheets look fuller is a curriculum change in disguise.
 *  - **Nothing is invented.** A missing glossary word must leave a blank and be reported, never
 *    filled, and a Hindi-only line must not claim a provenance.
 *  - **The picture agrees with the words.** One binding drives all of them, and this pins it.
 */
class TopicWorksheetsTest {

    private val santali = mapOf(
        "cow" to TargetWord("cow", "ᱜᱟᱨᱩ", Provenance.CORPUS, "Hembram/Glossary"),
        "goat" to TargetWord("goat", "ᱢᱮᱨᱚᱢ", Provenance.CORPUS, "Hembram/Glossary"),
    )

    // -----------------------------------------------------------------------------------------
    // Topic detection
    // -----------------------------------------------------------------------------------------

    @Test
    fun `an arithmetic instruction is detected as numbers`() {
        val match = TopicDetector.detect("आज हम गिनती सीखेंगे")
        assertNotNull(match)
        assertEquals(WorksheetTopic.NUMBERS, match!!.topic)
        assertTrue(match.viaKeyword)
    }

    @Test
    fun `nouns choose the pictures while the verb chooses the topic`() {
        // "Count five mangoes" is arithmetic illustrated with fruit, not a fruit-naming lesson.
        val match = TopicDetector.detect("पाँच आम गिनो")
        assertNotNull(match)
        assertEquals(WorksheetTopic.NUMBERS, match!!.topic)
        assertEquals(PictureBank.Category.FOOD, match.pictureCategory)
    }

    @Test
    fun `a topic word alone is enough`() {
        assertEquals(
            WorksheetTopic.ANIMALS,
            TopicDetector.detect("आज जानवरों के नाम सीखते हैं")?.topic,
        )
    }

    @Test
    fun `inflected forms still match`() {
        // जानवरों must reach जानवर, फलों must reach फल.
        assertEquals(WorksheetTopic.ANIMALS, TopicDetector.detect("जानवरों")?.topic)
        assertEquals(WorksheetTopic.FRUITS, TopicDetector.detect("फलों के नाम")?.topic)
    }

    @Test
    fun `a topic is inferred from nouns when no topic word is present`() {
        val match = TopicDetector.detect("गाय और बकरी")
        assertNotNull(match)
        assertEquals(WorksheetTopic.ANIMALS, match!!.topic)
        assertTrue("should be inferred, not keyword-driven", !match.viaKeyword)
    }

    @Test
    fun `nothing recognisable returns null rather than a default topic`() {
        // Silently defaulting to arithmetic would put an unrequested sheet in front of a class.
        assertNull(TopicDetector.detect(""))
        assertNull(TopicDetector.detect("   "))
        assertNull(TopicDetector.detect("कल छुट्टी रहेगी"))
    }

    @Test
    fun `detection reports the evidence for its guess`() {
        val match = TopicDetector.detect("रंग")
        assertNotNull(match)
        assertTrue(match!!.evidence.isNotEmpty())
    }

    // -----------------------------------------------------------------------------------------
    // Generation
    // -----------------------------------------------------------------------------------------

    @Test
    fun `every topic and grade produces a usable sheet`() {
        for (topic in WorksheetTopic.entries) {
            for (band in listOf(GradeBand.GRADE_1, GradeBand.GRADE_2)) {
                val result = TopicWorksheets.build(topic, band, seed = 7, vocabulary = santali)
                assertTrue(
                    "$topic/$band produced $result",
                    result is SheetResult.Ready,
                )
                val sheet = (result as SheetResult.Ready).sheet
                assertTrue("$topic/$band too few items", sheet.items.size >= 4)
            }
        }
    }

    @Test
    fun `the pool passes its own self-check`() {
        assertEquals(emptyList<String>(), TopicWorksheets.validate())
    }

    @Test
    fun `items on one sheet are not all identical`() {
        // The regression this exists for: one radical choice per spec meant every addition item
        // from a model read "2 + 1 = ___".
        val sheet = ready(WorksheetTopic.NUMBERS, GradeBand.GRADE_1)
        val prompts = sheet.items.map { it.hiText }
        assertEquals("every item on the sheet should differ", prompts.size, prompts.distinct().size)
    }

    @Test
    fun `grade 1 arithmetic stays inside the NIPUN ceilings`() {
        val sheet = ready(WorksheetTopic.NUMBERS, GradeBand.GRADE_1)
        sheet.items.forEach { item ->
            val a = item.bindings["a"]?.toIntOrNull()
            val b = item.bindings["b"]?.toIntOrNull()
            if (a != null && b != null) {
                assertTrue("operand $a > ceiling", a <= NipunOutcomes.GRADE_1_OPERAND_MAX)
                assertTrue("operand $b > ceiling", b <= NipunOutcomes.GRADE_1_OPERAND_MAX)
                if (item.strategy != SolutionStrategy.SUBTRACTION_NO_BORROW) {
                    assertTrue("sum ${a + b} > ceiling", a + b <= NipunOutcomes.GRADE_1_SUM_MAX)
                }
            }
        }
    }

    @Test
    fun `subtraction never goes negative`() {
        for (band in listOf(GradeBand.GRADE_1, GradeBand.GRADE_2)) {
            ready(WorksheetTopic.NUMBERS, band).items
                .filter { it.strategy == SolutionStrategy.SUBTRACTION_NO_BORROW }
                .forEach {
                    val a = it.bindings.getValue("a").toInt()
                    val b = it.bindings.getValue("b").toInt()
                    assertTrue("$a - $b is negative", a - b >= 0)
                }
        }
    }

    @Test
    fun `grade 2 multiplication only uses the tables the source names`() {
        ready(WorksheetTopic.NUMBERS, GradeBand.GRADE_2).items
            .filter { it.strategy == SolutionStrategy.MULTIPLICATION_AS_REPEATED_ADDITION }
            .forEach {
                val table = it.bindings.getValue("table").toInt()
                assertTrue("table of $table is outside 2, 3, 4", table in NipunOutcomes.GRADE_2_TABLES)
            }
    }

    @Test
    fun `division always shares out exactly`() {
        // The hazard is a dividend bound independently of the divisor: 9 sweets among 4 children.
        ready(WorksheetTopic.NUMBERS, GradeBand.GRADE_2).items
            .filter { it.strategy == SolutionStrategy.DIVISION_AS_SHARING }
            .forEach {
                val share = it.bindings.getValue("share").toInt()
                val children = it.bindings.getValue("children").toInt()
                val total = share * children
                assertEquals("$total must divide by $children", 0, total % children)
                assertEquals(share.toString(), it.answer)
                assertTrue("dividend should appear in the prompt", it.hiText.contains(total.toString()))
            }
    }

    @Test
    fun `counting items never ask for more objects than can be drawn`() {
        for (band in listOf(GradeBand.GRADE_1, GradeBand.GRADE_2)) {
            ready(WorksheetTopic.NUMBERS, band).items
                .filter { it.strategy == SolutionStrategy.COUNTING }
                .forEach {
                    val n = it.bindings.getValue("count").toInt()
                    assertTrue("$n objects is beyond the drawable cap", n <= TopicWorksheets.MAX_DRAWN_OBJECTS)
                }
        }
    }

    @Test
    fun `arithmetic answers are computed, not left blank`() {
        ready(WorksheetTopic.NUMBERS, GradeBand.GRADE_1).items
            .filter { it.strategy == SolutionStrategy.ADDITION_NO_CARRY }
            .forEach {
                val a = it.bindings.getValue("a").toInt()
                val b = it.bindings.getValue("b").toInt()
                assertEquals((a + b).toString(), it.answer)
            }
    }

    @Test
    fun `a pattern item shows a run and answers with the next term`() {
        assertEquals("2, 4, 6, 8, ______", TopicWorksheets.expandPattern("2,2"))
        assertEquals("10", TopicWorksheets.nextInPattern("2,2"))
        assertEquals("5, 10, 15, 20, ______", TopicWorksheets.expandPattern("5,5"))
        assertEquals("25", TopicWorksheets.nextInPattern("5,5"))
    }

    @Test
    fun `multiplication is shown as the repeated addition it stands for`() {
        assertEquals("3 + 3 + 3 + 3 + 3", TopicWorksheets.repeatedAddition("3", "5"))
        val item = ready(WorksheetTopic.NUMBERS, GradeBand.GRADE_2).items
            .firstOrNull { it.strategy == SolutionStrategy.MULTIPLICATION_AS_REPEATED_ADDITION }
        assertNotNull(item)
        assertTrue("prompt should show the addition", item!!.hiText.contains(" + "))
    }

    @Test
    fun `no placeholder survives onto a sheet`() {
        // An unfilled {slot} renders literally as "{hi}" on a child's worksheet, which no compiler
        // and no green build would notice.
        for (topic in WorksheetTopic.entries) {
            for (band in listOf(GradeBand.GRADE_1, GradeBand.GRADE_2)) {
                ready(topic, band, santali).items.forEach { item ->
                    assertTrue(
                        "$topic/$band leaked a placeholder: ${item.hiText}",
                        !item.hiText.contains('{'),
                    )
                    assertTrue(
                        "$topic/$band leaked a placeholder in target: ${item.targetText}",
                        !item.targetText.contains('{'),
                    )
                    item.answer?.let {
                        assertTrue("$topic/$band leaked a placeholder in answer: $it", !it.contains('{'))
                    }
                }
            }
        }
    }

    @Test
    fun `the picture agrees with the word beside it`() {
        val sheet = ready(WorksheetTopic.ANIMALS, GradeBand.GRADE_1, santali)
        sheet.items.forEach { item ->
            val term = item.pictureTerm
            if (term != null) {
                val picture = PictureBank.byEnglish(term)
                assertNotNull("unknown picture term $term", picture)
                // Every vocabulary prompt names the pictured thing, so a mismatch is visible here.
                if (item.hiText.contains("पहला अक्षर") || item.hiText.contains("अपनी भाषा")) {
                    assertTrue(
                        "prompt does not name the pictured thing: ${item.hiText} vs ${picture!!.hindi}",
                        item.hiText.contains(picture.hindi),
                    )
                }
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Provenance and honesty
    // -----------------------------------------------------------------------------------------

    @Test
    fun `a glossary word carries its provenance onto the worksheet line`() {
        val sheet = ready(WorksheetTopic.ANIMALS, GradeBand.GRADE_1, santali)
        val withTarget = sheet.items.filter { it.targetProvenance != null }
        assertTrue("expected at least one bilingual line", withTarget.isNotEmpty())
        withTarget.forEach {
            assertEquals(Provenance.CORPUS, it.targetProvenance)
            // CORPUS is meaningless without naming the corpus.
            assertNotNull("CORPUS line with no src", it.targetSrc)
        }
    }

    @Test
    fun `a missing glossary word is left blank and reported, never invented`() {
        val sheet = ready(WorksheetTopic.NATURE, GradeBand.GRADE_1, vocab = emptyMap())
        sheet.items.forEach {
            assertNull("no vocabulary was supplied, so nothing may claim provenance", it.targetProvenance)
            assertNull(it.targetSrc)
        }
        assertTrue(
            "a Hindi-only sheet must say so",
            sheet.relaxations.any { it.constraint == "bilingual" },
        )
    }

    @Test
    fun `an empty glossary still produces a solvable Hindi sheet`() {
        // SantaliGlossary.load returns an empty list on any failure by design, and a lesson must
        // degrade to a Hindi sheet rather than to no sheet.
        val result = TopicWorksheets.build(WorksheetTopic.ANIMALS, GradeBand.GRADE_1, 3, emptyMap())
        assertTrue(result is SheetResult.Ready)
        assertEquals(0, (result as SheetResult.Ready).sheet.bilingualItemCount)
        assertTrue(result.sheet.items.all { it.hiText.isNotBlank() })
    }

    @Test
    fun `arithmetic lines claim no provenance because they contain no words`() {
        ready(WorksheetTopic.NUMBERS, GradeBand.GRADE_1, santali).items.forEach {
            assertNull("an arithmetic line has nothing to vouch for", it.targetProvenance)
        }
    }

    // -----------------------------------------------------------------------------------------
    // Determinism
    // -----------------------------------------------------------------------------------------

    @Test
    fun `a sheet is reproducible from its seed alone`() {
        val a = ready(WorksheetTopic.ANIMALS, GradeBand.GRADE_1, santali, seed = 42)
        val b = ready(WorksheetTopic.ANIMALS, GradeBand.GRADE_1, santali, seed = 42)
        assertEquals(a.items, b.items)
        assertEquals(a.titleHindi, b.titleHindi)
    }

    @Test
    fun `a different seed gives a different sheet`() {
        val a = ready(WorksheetTopic.ANIMALS, GradeBand.GRADE_1, santali, seed = 1)
        val b = ready(WorksheetTopic.ANIMALS, GradeBand.GRADE_1, santali, seed = 2)
        assertTrue("seeds must not collapse to one sheet", a.items != b.items)
    }

    @Test
    fun `a sheet records the outcomes it practises`() {
        val sheet = ready(WorksheetTopic.NUMBERS, GradeBand.GRADE_2)
        assertTrue(sheet.lakshyaCodes.isNotEmpty())
        sheet.lakshyaCodes.forEach {
            assertNotNull("$it is not a known outcome position", NipunOutcomes.byCode(it))
        }
    }

    @Test
    fun `unknown placeholders survive generation so the resolve pass can fill them`() {
        // TopicWorksheets depends on ItemGenerator leaving {hi} alone. If someone makes generate()
        // throw or blank unknown slots, every sheet here silently loses its words - so pin it.
        val model = ItemModel(
            modelId = "probe",
            lakshyaCode = "C1-NUM-1",
            strategy = SolutionStrategy.COUNTING,
            hiTemplate = "{count} {hi}",
            targetTemplate = "{count}",
            slots = listOf(Slot.Radical("count", listOf("3"))),
        )
        val item = ItemGenerator.generate(model, seed = 1)
        assertEquals("3 {hi}", item.hiText)
    }

    @Test
    fun `generation exposes the bindings it used`() {
        // The answer key is computed from these, so losing them silently breaks every key.
        ready(WorksheetTopic.NUMBERS, GradeBand.GRADE_1).items.forEach {
            assertTrue("item ${it.modelId} exposed no bindings", it.bindings.isNotEmpty())
        }
    }

    private fun ready(
        topic: WorksheetTopic,
        band: GradeBand,
        vocab: Map<String, TargetWord> = emptyMap(),
        seed: Long = 11,
    ): Sheet {
        val result = TopicWorksheets.build(topic, band, seed, vocab)
        assertTrue("$topic/$band was not buildable: $result", result is SheetResult.Ready)
        return (result as SheetResult.Ready).sheet
    }
}

/** Regression checks kept separate so the intent of each stays legible. */
class TopicWorksheetsDuplicationTest {

    private val oneWordOnly = mapOf(
        "mango" to TargetWord("mango", "ᱩᱞ", Provenance.CORPUS, "Hembram/Glossary"),
    )

    @Test
    fun `no question appears twice on a sheet`() {
        // The defect: FRUITS/Grade 2 asked "आम — इसे अपनी भाषा में लिखो" as both item 3 and item 7,
        // because the glossary covered exactly one fruit and the bilingual model had nothing else
        // to pick. Visible on the sheet, invisible to every property test that existed.
        for (topic in WorksheetTopic.entries) {
            for (band in listOf(GradeBand.GRADE_1, GradeBand.GRADE_2)) {
                val result = TopicWorksheets.build(topic, band, seed = 2026, vocabulary = oneWordOnly)
                val items = (result as SheetResult.Ready).sheet.items
                val prompts = items.map { it.hiText }
                assertEquals(
                    "$topic/$band repeats a question: " +
                        prompts.groupingBy { it }.eachCount().filterValues { it > 1 }.keys,
                    prompts.size,
                    prompts.distinct().size,
                )
            }
        }
    }

    @Test
    fun `de-duplication is deterministic, so a reprint drops the same candidates`() {
        val a = TopicWorksheets.build(WorksheetTopic.FRUITS, GradeBand.GRADE_2, 5, oneWordOnly)
        val b = TopicWorksheets.build(WorksheetTopic.FRUITS, GradeBand.GRADE_2, 5, oneWordOnly)
        assertEquals(
            (a as SheetResult.Ready).sheet.items,
            (b as SheetResult.Ready).sheet.items,
        )
    }

    @Test
    fun `a sheet still meets the minimum when candidates are rejected`() {
        val result = TopicWorksheets.build(WorksheetTopic.FRUITS, GradeBand.GRADE_2, 5, oneWordOnly)
        assertTrue(result is SheetResult.Ready)
        assertTrue((result as SheetResult.Ready).sheet.items.size >= 4)
    }

    @Test
    fun `distinct arithmetic items are not mistaken for duplicates`() {
        // De-duplicating before resolution would have collapsed these, since every counting item
        // reads "{hi} गिनो..." until the picture is filled in.
        val sheet = (TopicWorksheets.build(WorksheetTopic.NUMBERS, GradeBand.GRADE_1, 9)
            as SheetResult.Ready).sheet
        assertTrue("expected a full sheet of distinct sums", sheet.items.size >= 6)
    }

    @Test
    fun `a counting item does not print its own answer`() {
        // The target line was "{count} ______", which showed the child the number they were being
        // asked to count to.
        for (band in listOf(GradeBand.GRADE_1, GradeBand.GRADE_2)) {
            (TopicWorksheets.build(WorksheetTopic.NUMBERS, band, 4) as SheetResult.Ready)
                .sheet.items
                .filter { it.strategy == SolutionStrategy.COUNTING }
                .forEach {
                    val answer = it.answer!!
                    assertTrue(
                        "counting item leaks the answer '$answer' in its target line: ${it.targetText}",
                        !it.targetText.contains(answer),
                    )
                    assertTrue(
                        "counting item leaks the answer '$answer' in its prompt: ${it.hiText}",
                        !it.hiText.contains(answer),
                    )
                }
        }
    }
}
