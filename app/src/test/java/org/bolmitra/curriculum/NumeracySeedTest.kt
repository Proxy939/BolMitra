package org.bolmitra.curriculum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The runnable check behind [NumeracySeed].
 *
 * The failure this guards against is silent by construction: a template that references a slot the
 * model never declared renders as a literal `{a}` on a child's worksheet, and nothing in a green
 * build or a screenshot review would flag it.
 */
class NumeracySeedTest {

    @Test
    fun `pool is well formed`() {
        assertEquals(emptyList<String>(), NumeracySeed.validate())
    }

    @Test
    fun `assembly succeeds and respects the item range`() {
        val result = WorksheetAssembler.assemble(
            spec = NumeracySeed.spec(seed = 7),
            models = NumeracySeed.models,
        )
        assertTrue("expected Success, got $result", result is AssemblyResult.Success)
        val items = (result as AssemblyResult.Success).items
        assertTrue("got ${items.size} items", items.size in 4..8)
    }

    @Test
    fun `no rendered item leaks an unfilled placeholder`() {
        val result = WorksheetAssembler.assemble(NumeracySeed.spec(seed = 3), NumeracySeed.models)
        val items = (result as AssemblyResult.Success).items
        items.forEach { item ->
            assertTrue("unfilled slot in hiText: ${item.hiText}", !item.hiText.contains('{'))
            assertTrue("unfilled slot in targetText: ${item.targetText}", !item.targetText.contains('{'))
        }
    }

    @Test
    fun `same seed reproduces the same sheet`() {
        val a = WorksheetAssembler.assemble(NumeracySeed.spec(seed = 42), NumeracySeed.models)
        val b = WorksheetAssembler.assemble(NumeracySeed.spec(seed = 42), NumeracySeed.models)
        assertEquals(
            (a as AssemblyResult.Success).items.map { it.hiText },
            (b as AssemblyResult.Success).items.map { it.hiText },
        )
    }

    @Test
    fun `addition items never exceed the lakshya range`() {
        val result = WorksheetAssembler.assemble(NumeracySeed.spec(seed = 11), NumeracySeed.models)
        val range = NumeracySeed.lakshya.range!!
        (result as AssemblyResult.Success).items
            .filter { it.strategy == SolutionStrategy.ADDITION_NO_CARRY }
            .forEach { item ->
                // "2 + 3 = ___" -> the two operands
                val nums = Regex("""\d+""").findAll(item.hiText).map { it.value.toInt() }.toList()
                assertTrue("expected two operands in ${item.hiText}", nums.size >= 2)
                val sum = nums[0] + nums[1]
                assertTrue("$sum outside ${range.min}..${range.max} in ${item.hiText}", sum in range)
            }
    }

    @Test
    fun `qr payload stays inside the encoder ceiling and truncates whole items`() {
        val result = WorksheetAssembler.assemble(NumeracySeed.spec(seed = 5), NumeracySeed.models)
        val items = (result as AssemblyResult.Success).items
        val payload = QrCode.payloadFor("Numbers (1 – 10)", items)

        assertTrue("payload ${payload.length} chars", payload.length <= QrCode.MAX_PAYLOAD_CHARS)
        assertTrue("payload should name the worksheet", payload.contains("Numbers"))
        // Truncation must land on an item boundary, never mid-item.
        assertTrue("payload should end with a newline", payload.endsWith("\n"))
    }

    @Test
    fun `qr payload truncates and says so when there are too many items`() {
        val one = WorksheetAssembler.assemble(NumeracySeed.spec(seed = 5), NumeracySeed.models)
        val items = (one as AssemblyResult.Success).items
        // 200 copies cannot fit; the payload must shorten and admit it rather than silently drop.
        val many = List(50) { items }.flatten()
        val payload = QrCode.payloadFor("Numbers (1 – 10)", many)

        assertTrue(payload.length <= QrCode.MAX_PAYLOAD_CHARS)
        assertTrue("should disclose truncation, got:\n$payload", payload.contains("more item"))
    }
}
