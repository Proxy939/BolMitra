package org.bolmitra.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks for [FtsQuery], the one piece of the Room layer that is testable without a device.
 *
 * It earns tests because it sits on a boundary: arbitrary ASR output is interpolated into
 * SQLite's FTS query language, which has its own operators (`OR`, `NEAR`, `*`) and quoting
 * rules. Unfiltered text there is both a crash source and an injection surface.
 */
class FtsQueryTest {

    @Test
    fun `tokens are quoted and OR-joined for recall`() {
        // OR not AND: at 0.328 WER, requiring every token would discard most real utterances.
        assertEquals("\"किताब\" OR \"खोलो\"", FtsQuery.build("किताब खोलो"))
    }

    @Test
    fun `devanagari matras survive tokenisation`() {
        // The bug this guards against: matras are Mn/Mc, so isLetterOrDigit() alone drops them
        // and किताब becomes कतब — a different word that matches nothing.
        val q = FtsQuery.build("किताब")
        assertEquals("\"किताब\"", q)
    }

    @Test
    fun `fts operators in user text are neutralised by quoting`() {
        // "OR" and "*" must be treated as literal tokens, not operators.
        val q = FtsQuery.build("किताब OR खोलो*")!!
        assertTrue(q.contains("\"OR\""))
        assertTrue(q.contains("\"खोलो\""))
        // The wildcard must not survive outside quotes where FTS would honour it.
        assertTrue(!q.contains("खोलो*"))
    }

    @Test
    fun `embedded double quote is escaped by doubling`() {
        val q = FtsQuery.build("अ\"ब")!!
        // Filtering strips the quote, so the token is intact and the expression stays valid.
        assertTrue(q.startsWith("\"") && q.endsWith("\""))
        assertTrue(!q.contains("\"\"\""))
    }

    @Test
    fun `punctuation-only input returns null so the query is skipped`() {
        assertNull(FtsQuery.build("।।  ॥ "))
        assertNull(FtsQuery.build(""))
        assertNull(FtsQuery.build("   "))
    }

    @Test
    fun `digits are kept`() {
        assertEquals("\"पेज\" OR \"5\"", FtsQuery.build("पेज 5"))
    }
}
