package org.bolmitra

import org.bolmitra.speech.validateCharacterTokens
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Guards the one input that took the whole process down.
 *
 * sherpa-onnx calls `exit(-1)` on a malformed `tokens.txt`, so there is no exception to assert on
 * once the file reaches JNI. The only way to have a test at all is to reject the file first, and
 * these cases are the ones sherpa's own `ReadTokens` dies on.
 */
class SherpaTokensTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun write(name: String, text: String): File =
        tmp.newFile(name).apply { writeBytes(text.toByteArray()) }

    /** Shape of a real MMS file: the space symbol is a line carrying only its id. */
    private val valid = "\u0b39 0\n\u0b2d 1\n 2\n\u0b15 3\n"

    @Test
    fun `accepts an LF file including the id-only space line`() {
        validateCharacterTokens(write("ok.txt", valid))
    }

    @Test
    fun `rejects CRLF because sherpa would exit on the space line`() {
        val e = runCatching { validateCharacterTokens(write("crlf.txt", valid.replace("\n", "\r\n"))) }
            .exceptionOrNull()
        assertTrue("expected a CR complaint, got $e", e?.message?.contains("CR") == true)
    }

    @Test
    fun `rejects a multi-code-point symbol`() {
        val e = runCatching { validateCharacterTokens(write("multi.txt", "\u0b39 0\n\u0b15\u0b3f 1\n")) }
            .exceptionOrNull()
        assertTrue("expected a code-point complaint, got $e", e?.message?.contains("code point") == true)
    }

    @Test
    fun `rejects a duplicated code point`() {
        val e = runCatching { validateCharacterTokens(write("dup.txt", "\u0b39 0\n\u0b39 1\n")) }
            .exceptionOrNull()
        assertTrue("expected a duplicate complaint, got $e", e?.message?.contains("duplicat") == true)
    }

    @Test
    fun `rejects a non-numeric id`() {
        val e = runCatching { validateCharacterTokens(write("badid.txt", "\u0b39 zero\n")) }
            .exceptionOrNull()
        assertTrue("expected an id complaint, got $e", e?.message?.contains("non-numeric") == true)
    }

    @Test
    fun `rejects a missing file`() {
        try {
            validateCharacterTokens(File(tmp.root, "absent.txt"))
            fail("expected a missing-file failure")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("missing"))
        }
    }

    /**
     * The shipped artifact itself, when present. Skipped rather than failed when it is not:
     * models are fetched, not committed (§6.3), so a clean clone has nothing to check.
     */
    @Test
    fun `the converted Mundari tokens file is acceptable to sherpa`() {
        val f = File("../models/tts-unr/tokens.txt")
        if (!f.isFile) return
        validateCharacterTokens(f)
    }
}
