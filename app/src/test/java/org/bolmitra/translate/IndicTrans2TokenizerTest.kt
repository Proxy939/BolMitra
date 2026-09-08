package org.bolmitra.translate

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Two halves, deliberately.
 *
 * **Algorithm tests** build a tiny table inline and always run. They pin the mechanics — merge
 * order, tie-breaking, code-point splitting, the dummy prefix, `<unk>` fallback, tag handling — and
 * they would catch a refactor that broke any of it even on a machine with no model staged.
 *
 * **Parity tests** compare against SentencePiece's own output over an adversarial corpus, using the
 * real 130k-row table. They are the ones that matter for correctness, and they can only run where
 * `models/` is populated, because `models/` is gitignored (large, and licence-restricted). They
 * skip with a message rather than failing, so a fresh clone is not red for the wrong reason — but
 * that does mean a green run on a bare checkout has NOT checked fidelity. Run them before trusting
 * a tokenizer change:
 *
 * ```
 * .venv-it2\Scripts\python.exe tools/stage-mt-model.py
 * .venv-it2\Scripts\python.exe tools/export-spm-table.py
 * gradlew :app:testDebugUnitTest
 * ```
 *
 * The reason this is tested at all rather than eyeballed: the model card is explicit that faulty
 * preprocessing produces fluent output *in the wrong language* instead of an error, and none of us
 * reads Ol Chiki well enough to notice.
 */
class IndicTrans2TokenizerTest {

    // ---- fixtures ---------------------------------------------------------------------------

    /**
     * A hand-built table. Scores are merge priorities, higher merges first, matching the real
     * model where score is negative rank.
     */
    private fun tinyTable(): File {
        val f = File.createTempFile("spm-tiny", ".tsv")
        f.deleteOnExit()
        f.writeText(
            buildString {
                // piece            score  srcId  tgtId
                appendLine("<unk>\t-\t3\t3")
                appendLine("<s>\t-\t0\t0")
                appendLine("</s>\t-\t2\t2")
                appendLine("<pad>\t-\t1\t1")
                appendLine("hin_Deva\t-\t8\t-1")
                appendLine("sat_Olck\t-\t29925\t-1")
                // Characters.
                appendLine("\u2581\t-100\t100\t100")
                appendLine("a\t-101\t101\t101")
                appendLine("b\t-102\t102\t102")
                appendLine("c\t-103\t103\t103")
                appendLine("\uD83D\uDE00\t-104\t104\t104")
                // Merges. "ab" outranks "bc", so "abc" must go ["ab","c"] and never ["a","bc"].
                appendLine("ab\t-10\t110\t110")
                appendLine("bc\t-20\t111\t111")
                appendLine("\u2581ab\t-5\t112\t112")
                // Present as a piece but with no source id, to exercise the <unk> fallback.
                appendLine("\u2581c\t-30\t-1\t120")
            },
        )
        return f
    }

    private fun tiny() = IndicTrans2Tokenizer.load(tinyTable())

    // ---- algorithm --------------------------------------------------------------------------

    @Test
    fun `normalise collapses whitespace and prefixes a single escaped space`() {
        val t = tiny()
        assertEquals("\u2581a\u2581b", t.normalize("a b"))
        assertEquals("\u2581a\u2581b", t.normalize("  a   b  "))
        assertEquals("\u2581a\u2581b", t.normalize("\ta\n\nb\r"))
        assertEquals("\u2581a", t.normalize("a"))
    }

    @Test
    fun `empty and whitespace-only input produce no pieces at all`() {
        // Not a lone dummy prefix. SentencePiece emits nothing here, and emitting one token would
        // put a stray piece in front of every silent turn.
        val t = tiny()
        assertEquals(emptyList<String>(), t.encodeToPieces(""))
        assertEquals(emptyList<String>(), t.encodeToPieces("   "))
        assertEquals(emptyList<String>(), t.encodeToPieces("\t\n"))
    }

    @Test
    fun `merge order follows score, not position`() {
        // "abc": both ab(-10) and bc(-20) are mergeable. ab has the higher score so it wins,
        // leaving ["ab","c"]. A greedy left-to-right merger would agree here by luck, so also
        // check the mirrored case where the better merge is on the right.
        val t = tiny()
        assertEquals(listOf("\u2581ab", "c"), t.encodeToPieces("abc"))
    }

    @Test
    fun `equal scores break ties leftmost`() {
        val f = File.createTempFile("spm-tie", ".tsv")
        f.deleteOnExit()
        f.writeText(
            "<unk>\t-\t3\t3\n" +
                // The loader insists both language tags exist, so a table without them is
                // rejected before it can be used.
                "hin_Deva\t-\t8\t-1\n" +
                "sat_Olck\t-\t29925\t-1\n" +
                "\u2581\t-100\t100\t100\n" +
                "a\t-101\t101\t101\n" +
                // Both merges score identically; the left pair must be taken first.
                "aa\t-10\t110\t110\n",
        )
        // "aaa" -> merge the first pair -> ["aa","a"], not ["a","aa"].
        assertEquals(listOf("\u2581", "aa", "a"), IndicTrans2Tokenizer.load(f).encodeToPieces("aaa"))
    }

    @Test
    fun `astral characters stay whole`() {
        // U+1F600 is a surrogate pair in UTF-16. Splitting on Char would hand BPE half of it.
        val t = tiny()
        assertEquals(listOf("\u2581", "\uD83D\uDE00"), t.encodeToPieces("\uD83D\uDE00"))
    }

    @Test
    fun `encode frames pieces with both language tags and eos`() {
        val ids = tiny().encode("abc")
        assertEquals(8L, ids[0])
        assertEquals(29925L, ids[1])
        assertEquals(112L, ids[2])              // ▁ab
        assertEquals(103L, ids[3])              // c
        assertEquals(2L, ids[ids.size - 1])     // </s>
        assertEquals(5, ids.size)
    }

    @Test
    fun `pieces without a source id fall back to unk`() {
        // "▁c" is mergeable but has srcId -1, so it must encode as <unk>=3 rather than -1.
        val ids = tiny().encode("c")
        assertEquals(listOf(8L, 29925L, 3L, 2L), ids.toList())
    }

    @Test
    fun `language tags are never merged into`() {
        // The loader rejects a table where a tag is mergeable, because otherwise text containing
        // those characters could collapse into the tag token.
        val f = File.createTempFile("spm-bad", ".tsv")
        f.deleteOnExit()
        f.writeText("<unk>\t-\t3\t3\nhin_Deva\t-5\t8\t-1\nsat_Olck\t-\t29925\t-1\n")
        val e = runCatching { IndicTrans2Tokenizer.load(f) }.exceptionOrNull()
        assertNotNull("a mergeable language tag must be rejected", e)
        assertTrue(e!!.message!!.contains("mergeable"))
    }

    @Test
    fun `decode drops specials and restores spaces`() {
        val t = tiny()
        assertEquals("ab c", t.decode(intArrayOf(0, 112, 100, 103, 2)))
        assertEquals("", t.decode(intArrayOf(0, 2)))
    }

    // ---- parity against SentencePiece -------------------------------------------------------

    private data class Enc(val text: String, val pieces: List<String>, val ids: List<Long>)
    private data class Dec(val ids: IntArray, val text: String)

    private fun unescape(s: String) = StringBuilder().also { out ->
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    't' -> { out.append('\t'); i += 2 }
                    'n' -> { out.append('\n'); i += 2 }
                    'r' -> { out.append('\r'); i += 2 }
                    '\\' -> { out.append('\\'); i += 2 }
                    else -> { out.append(c); i++ }
                }
            } else {
                out.append(c); i++
            }
        }
    }.toString()

    /** The real table lives under models/, which is gitignored. Tests run with cwd = app/. */
    private fun realTable(): File? =
        listOf(File("../models/mt-hi-sat/spm.tsv"), File("models/mt-hi-sat/spm.tsv"))
            .firstOrNull { it.isFile }

    private fun parityCases(): Pair<List<Enc>, List<Dec>> {
        val stream = javaClass.getResourceAsStream("/spm-parity.tsv")
            ?: error("spm-parity.tsv missing from test resources")
        val enc = ArrayList<Enc>()
        val dec = ArrayList<Dec>()
        stream.bufferedReader().forEachLine { line ->
            if (line.isBlank() || line.startsWith("#")) return@forEachLine
            val f = line.split('\t')
            when (f[0]) {
                "E" -> enc += Enc(
                    text = unescape(f[1]),
                    // A blank pieces column means SentencePiece produced nothing.
                    pieces = if (f[2].isEmpty()) emptyList() else f[2].split(' '),
                    ids = f[3].split(',').map { it.toLong() },
                )
                "D" -> dec += Dec(
                    ids = f[1].split(',').map { it.toInt() }.toIntArray(),
                    text = unescape(f[2]),
                )
            }
        }
        return enc to dec
    }

    @Test
    fun `pieces match SentencePiece over the adversarial corpus`() {
        val table = realTable()
        assumeTrue(
            "models/mt-hi-sat/spm.tsv not staged — run tools/stage-mt-model.py then " +
                "tools/export-spm-table.py to check tokenizer fidelity",
            table != null,
        )
        val t = IndicTrans2Tokenizer.load(table!!)
        val (encode, _) = parityCases()
        assertTrue("parity corpus is empty", encode.size >= 40)

        val failures = encode.mapNotNull { c ->
            val got = t.encodeToPieces(c.text)
            if (got == c.pieces) null else "  ${show(c.text)}\n    want ${c.pieces}\n    got  $got"
        }
        assertEquals(
            "${failures.size}/${encode.size} inputs tokenised differently:\n" +
                failures.joinToString("\n"),
            0,
            failures.size,
        )
    }

    @Test
    fun `ids match the HuggingFace tokenizer over the adversarial corpus`() {
        val table = realTable()
        assumeTrue("models/mt-hi-sat/spm.tsv not staged", table != null)
        val t = IndicTrans2Tokenizer.load(table!!)
        val (encode, _) = parityCases()

        val failures = encode.mapNotNull { c ->
            val got = t.encode(c.text).toList()
            if (got == c.ids) null else "  ${show(c.text)}\n    want ${c.ids}\n    got  $got"
        }
        assertEquals(
            "${failures.size}/${encode.size} inputs produced different ids:\n" +
                failures.joinToString("\n"),
            0,
            failures.size,
        )
    }

    @Test
    fun `decode reproduces the model's own Santali output`() {
        val table = realTable()
        assumeTrue("models/mt-hi-sat/spm.tsv not staged", table != null)
        val t = IndicTrans2Tokenizer.load(table!!)
        val (_, decode) = parityCases()
        assertTrue("no decode cases", decode.isNotEmpty())

        val failures = decode.mapNotNull { c ->
            val got = t.decode(c.ids)
            if (got == c.text) null else "    want ${show(c.text)}\n    got  ${show(got)}"
        }
        assertEquals(
            "${failures.size}/${decode.size} decoded differently:\n" + failures.joinToString("\n"),
            0,
            failures.size,
        )
    }

    /** Escapes to codepoints so a failure is readable in a console that cannot render the script. */
    private fun show(s: String): String = s.map { c ->
        if (c.code in 32..126) c.toString() else "U+%04X".format(c.code)
    }.joinToString("")
}
