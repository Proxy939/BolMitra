package org.bolmitra.ui.common

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import org.bolmitra.R

/**
 * The bundled Ol Chiki face, applied per script run.
 *
 * ### Why bundle it when the system already has one
 *
 * AOSP has shipped `NotoSansOlChiki-Regular.ttf` with a `<family lang="und-Olck">` fallback entry
 * since Android 7.1 — checked at five release tags — and it is present on our test tablet. So on a
 * stock build this is redundant. It is here for the build that is not stock: a budget OEM or
 * Android Go image that strips vendor fonts renders every Ol Chiki glyph as a tofu box, with **no
 * exception, no log line and no failing test**. The class sees empty rectangles and the app reports
 * success. 15.7 KB against a 90 MB APK removes that entire failure mode.
 *
 * Bundling also makes rendering deterministic. The tablet carries version 1.03; upstream is 2.003.
 * Relying on whatever the vendor shipped means the glyphs differ by device.
 *
 * We use the **hinted** build on purpose. The unhinted one is 8,564 B and has no `fpgm`/`prep`/`cvt`
 * tables at all; our target is low-DPI budget hardware, which is exactly where TrueType hinting
 * earns its bytes. Licence is **SIL OFL 1.1**, read from the font's own `name` table (nameID 13),
 * which permits bundling and embedding.
 *
 * ### The trap this class exists to avoid
 *
 * **This font contains 53 codepoints.** All 48 Ol Chiki, plus NUL, CR, space, NBSP and ₹ — and
 * nothing else. Setting it as the `fontFamily` of a `Text` that happens to hold Devanagari or Latin
 * would render *those* as tofu instead. Our own strings are mixed constantly: the `TextOnly` rung
 * carries Devanagari, and real Santali UI text looks like `google translator ᱨᱮ ᱠᱷᱩᱞᱟᱹᱭ ᱢᱮ`.
 *
 * So [annotate] styles **only the Ol Chiki runs** and leaves everything else on the default family.
 */
object OlChikiFont {

    /** The bundled face. Safe to reference outside composition. */
    val family: FontFamily = FontFamily(Font(R.font.noto_sans_ol_chiki))

    private const val BLOCK_START = 0x1C50
    private const val BLOCK_END = 0x1C7F

    /** True for any assigned Ol Chiki codepoint, letters, digits, marks and punctuation alike. */
    fun isOlChiki(ch: Char): Boolean = ch.code in BLOCK_START..BLOCK_END

    /** True if [text] contains any Ol Chiki at all. Cheap pre-check for callers. */
    fun containsOlChiki(text: String): Boolean = text.any(::isOlChiki)

    /**
     * Wraps every Ol Chiki run in the bundled family, leaving other scripts untouched.
     *
     * Returns a plain [AnnotatedString] with no spans when there is no Ol Chiki, so callers can use
     * this unconditionally without paying for it on Hindi-only text.
     */
    fun annotate(text: String): AnnotatedString {
        if (!containsOlChiki(text)) return AnnotatedString(text)

        return buildAnnotatedString {
            var i = 0
            while (i < text.length) {
                val olChiki = isOlChiki(text[i])
                var j = i
                while (j < text.length && isOlChiki(text[j]) == olChiki) j++
                val run = text.substring(i, j)
                if (olChiki) {
                    withStyle(SpanStyle(fontFamily = family)) { append(run) }
                } else {
                    append(run)
                }
                i = j
            }
        }
    }

    /**
     * Self-check on the run splitter.
     *
     * The property that fails silently: a splitter that dropped or duplicated a run would corrupt
     * displayed text while every glyph still rendered, so nothing would look broken. Asserts the
     * concatenation is lossless and that spans land only on Ol Chiki.
     */
    fun validate(): List<String> = buildList {
        // "google translator ᱨᱮ ᱠᱷᱩᱞᱟᱹᱭ ᱢᱮ" — a real mixed string from the Traduzir Santali UI.
        val mixed = "google translator \u1C68\u1C6E \u1C60\u1C77\u1C69\u1C5E\u1C5F\u1C79\u1C6D \u1C62\u1C6E"
        val annotated = annotate(mixed)
        if (annotated.text != mixed) add("annotate() altered the text: '${annotated.text}'")

        for (span in annotated.spanStyles) {
            val slice = mixed.substring(span.start, span.end)
            if (slice.any { !isOlChiki(it) }) {
                add("span [${span.start},${span.end}) covers non-Ol-Chiki: '$slice'")
            }
        }
        if (annotated.spanStyles.isEmpty()) add("mixed text produced no Ol Chiki span")

        val hindiOnly = "\u092C\u0948\u0920 \u091C\u093E\u0913"
        if (annotate(hindiOnly).spanStyles.isNotEmpty()) {
            add("Hindi-only text should get no span")
        }
        if (annotate("").text.isNotEmpty()) add("empty input should stay empty")
    }
}
