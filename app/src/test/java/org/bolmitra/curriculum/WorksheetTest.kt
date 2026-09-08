package org.bolmitra.curriculum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks for item generation and worksheet assembly (§6.16).
 *
 * Three properties are worth protecting here, all of them findings rather than preferences:
 *
 *  - **Determinism.** Item identity is `(modelId, seed)` and rendered sheets are never stored, so
 *    a reprint must be byte-identical. If this breaks, answer keys stop matching worksheets.
 *  - **Radical slots are never randomised** (V40). Randomising one produces an item that no
 *    longer measures its lakshya, with no visible symptom.
 *  - **Interleaving is not shuffling** (V45). Adjacent items must not share a strategy.
 */
class WorksheetTest {

    private val wordModel = ItemModel(
        modelId = "read-word-v1",
        lakshyaCode = "FLN-L-01",
        strategy = SolutionStrategy.WORD_READING,
        hiTemplate = "{word} पढ़ो",
        targetTemplate = "[unr] {word}",
        slots = listOf(
            Slot.Radical("word", listOf("कमल", "किताब", "पुस्तक")),
        ),
        answerTemplate = "{word}",
    )

    private val nameModel = ItemModel(
        modelId = "match-picture-v1",
        lakshyaCode = "FLN-L-01",
        strategy = SolutionStrategy.PICTURE_WORD_MATCH,
        hiTemplate = "{child}, {object} पर घेरा लगाओ",
        targetTemplate = "[unr] {child} {object}",
        slots = listOf(
            Slot.Incidental("child", listOf("आशा", "बिरसा", "सोमा")),
            Slot.Incidental("object", listOf("गाय", "पेड़", "घर")),
        ),
    )

    private val countModel = ItemModel(
        modelId = "count-v1",
        lakshyaCode = "FLN-L-01",
        strategy = SolutionStrategy.COUNTING,
        hiTemplate = "{n} तक गिनो",
        targetTemplate = "[unr] {n}",
        slots = listOf(Slot.Radical("n", listOf("5", "10", "20"))),
        answerTemplate = "{n}",
    )

    // --- validation ------------------------------------------------------------------------

    @Test
    fun `well-formed models validate clean`() {
        assertTrue(wordModel.validate().isEmpty())
        assertTrue(nameModel.validate().isEmpty())
    }

    @Test
    fun `undeclared slot reference is caught`() {
        val broken = wordModel.copy(hiTemplate = "{word} और {missing} पढ़ो")
        assertTrue(broken.validate().any { it.contains("missing") })
    }

    @Test
    fun `unused slot is caught because it silently consumes seed entropy`() {
        val broken = nameModel.copy(
            slots = nameModel.slots + Slot.Incidental("unused", listOf("x")),
        )
        assertTrue(broken.validate().any { it.contains("unused") })
    }

    @Test
    fun `empty slot values are caught`() {
        val broken = wordModel.copy(slots = listOf(Slot.Radical("word", emptyList())))
        assertTrue(broken.validate().any { it.contains("no values") })
    }

    // --- determinism -----------------------------------------------------------------------

    @Test
    fun `same model and seed regenerate an identical item`() {
        // The property that makes exact reprints and matching answer keys possible.
        val a = ItemGenerator.generate(nameModel, seed = 42)
        val b = ItemGenerator.generate(nameModel, seed = 42)
        assertEquals(a, b)
    }

    @Test
    fun `different seeds vary incidental slots`() {
        val seeds = (1L..40L).map { ItemGenerator.generate(nameModel, it).hiText }
        assertTrue("expected variation across seeds", seeds.distinct().size > 1)
    }

    // --- radical vs incidental (V40) -------------------------------------------------------

    @Test
    fun `radical slot is NEVER randomised - it defaults to the lowest-difficulty value`() {
        // Across many seeds the radical value must not move, or the item stops measuring its
        // lakshya. Defaulting to values.first() is a pinned choice, not a random one.
        val words = (1L..50L).map { ItemGenerator.generate(wordModel, it).answer }
        assertEquals(setOf("कमल"), words.toSet())
    }

    @Test
    fun `radical slot follows the difficulty tag when supplied`() {
        val item = ItemGenerator.generate(
            wordModel,
            seed = 1,
            radicalChoices = mapOf("word" to "पुस्तक"),
        )
        assertEquals("पुस्तक", item.answer)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a radical choice outside the declared values is rejected`() {
        ItemGenerator.generate(wordModel, seed = 1, radicalChoices = mapOf("word" to "अवैध"))
    }

    // --- interleaving (V45) ----------------------------------------------------------------

    @Test
    fun `ordering separates items that share a solution strategy`() {
        val items = buildList {
            repeat(3) { add(ItemGenerator.generate(wordModel, it.toLong())) }
            repeat(3) { add(ItemGenerator.generate(nameModel, 100L + it)) }
            repeat(3) { add(ItemGenerator.generate(countModel, 200L + it)) }
        }
        val ordered = InterleaveOrderer.order(items)

        assertEquals(items.size, ordered.size)
        assertTrue(
            "adjacent items share a strategy: ${ordered.map { it.strategy }}",
            InterleaveOrderer.isFullyInterleaved(ordered),
        )
    }

    @Test
    fun `ordering is not shuffling - a blocked input becomes interleaved`() {
        // Input is deliberately fully blocked: AAABBB. A shuffle might leave runs; ordering must not.
        val items = buildList {
            repeat(3) { add(ItemGenerator.generate(wordModel, it.toLong())) }
            repeat(3) { add(ItemGenerator.generate(nameModel, 100L + it)) }
        }
        assertEquals(3, InterleaveOrderer.longestRun(items))
        val ordered = InterleaveOrderer.order(items)
        assertEquals(1, InterleaveOrderer.longestRun(ordered))
    }

    @Test
    fun `a single strategy cannot be interleaved and says so`() {
        val items = (1L..4L).map { ItemGenerator.generate(wordModel, it) }
        val ordered = InterleaveOrderer.order(items)
        assertEquals(items.size, ordered.size)
        assertTrue(!InterleaveOrderer.isFullyInterleaved(ordered))
    }

    // --- assembly and infeasibility diagnosis (V43) ----------------------------------------

    @Test
    fun `assembly succeeds and interleaves`() {
        val spec = WorksheetSpec(
            lakshyaCode = "FLN-L-01",
            minItems = 6,
            maxItems = 6,
            limits = ItemLimits(),
            seed = 7,
        )
        val result = WorksheetAssembler.assemble(spec, listOf(wordModel, nameModel, countModel))
        assertTrue(result is AssemblyResult.Success)
        val s = result as AssemblyResult.Success
        assertEquals(6, s.items.size)
        assertTrue(InterleaveOrderer.isFullyInterleaved(s.items))
    }

    @Test
    fun `assembly is reproducible from the spec seed alone`() {
        val spec = WorksheetSpec("FLN-L-01", 6, 6, ItemLimits(), seed = 99)
        val a = WorksheetAssembler.assemble(spec, listOf(wordModel, nameModel, countModel))
        val b = WorksheetAssembler.assemble(spec, listOf(wordModel, nameModel, countModel))
        assertEquals(
            (a as AssemblyResult.Success).items,
            (b as AssemblyResult.Success).items,
        )
    }

    @Test
    fun `changing the seed changes the worksheet`() {
        val models = listOf(wordModel, nameModel, countModel)
        val a = WorksheetAssembler.assemble(WorksheetSpec("FLN-L-01", 6, 6, ItemLimits(), seed = 1), models)
        val b = WorksheetAssembler.assemble(WorksheetSpec("FLN-L-01", 6, 6, ItemLimits(), seed = 2), models)
        assertNotEquals(
            (a as AssemblyResult.Success).items,
            (b as AssemblyResult.Success).items,
        )
    }

    @Test
    fun `no models for a lakshya is reported as the authoring backlog signal`() {
        val spec = WorksheetSpec("FLN-N-99", 4, 4, ItemLimits())
        val result = WorksheetAssembler.assemble(spec, listOf(wordModel))
        assertTrue(result is AssemblyResult.NoItemModels)
        assertEquals("FLN-N-99", (result as AssemblyResult.NoItemModels).lakshyaCode)
    }

    @Test
    fun `a word pool that fails structural limits is a DEFICIENT POOL, not a relaxation`() {
        // V43: structural constraints are never relaxed. The correct response is authoring
        // vocabulary, and the diagnosis must say which of the two failure kinds this is.
        val spec = WorksheetSpec(
            lakshyaCode = "FLN-L-01",
            minItems = 4,
            maxItems = 4,
            // Balvatika-style: 2 aksharas, no conjuncts.
            limits = ItemLimits(maxAksharas = 2, allowConjuncts = false),
        )
        val result = WorksheetAssembler.assemble(
            spec,
            listOf(wordModel),
            wordPool = listOf("पुस्तक", "विद्यालय"), // both too long / conjunct-bearing
        )
        assertTrue(result is AssemblyResult.DeficientPool)
        val d = result as AssemblyResult.DeficientPool
        assertEquals(0, d.availableAfterFiltering)
        assertTrue(d.diagnosis.contains("DEFICIENT POOL"))
    }

    @Test
    fun `single-strategy assembly reports the lost interleaving benefit instead of hiding it`() {
        val spec = WorksheetSpec("FLN-L-01", 4, 4, ItemLimits())
        val result = WorksheetAssembler.assemble(spec, listOf(wordModel))
        val s = result as AssemblyResult.Success
        assertTrue(s.relaxations.any { it.constraint == "interleaving" })
    }
}
