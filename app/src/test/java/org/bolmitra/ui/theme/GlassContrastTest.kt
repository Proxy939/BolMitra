package org.bolmitra.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the one thing the glassmorphism pass put at risk.
 *
 * Card fills went from 80% white to ~54–60% so the [SilkBackdrop] shows through them. That is the
 * point of the look, and it also means a card's effective background is a composite that depends on
 * the backdrop underneath — so the contrast audit in [BolmitraColors]'s docs is no longer something
 * a person can read off a table.
 *
 * The failure this prevents is quiet and plausible: someone makes the glass "a bit more see-through"
 * for a screenshot, or deepens a fold for drama, and body text in the app lands under AA. Nothing
 * looks broken on a bright desk monitor. It breaks in a sunlit classroom, which per §6.13 is the
 * condition the whole design is for.
 *
 * These are pure arithmetic on the token values, so they run as plain JUnit with no Compose or
 * Android dependency.
 */
class GlassContrastTest {

    private val inkMuted = 0x5A
    private val ink = 0x0E
    private val paper = 0xF2

    @Test
    fun `luminance and ratio agree with known WCAG values`() {
        // Black on white is the textbook 21:1.
        assertEquals(21.0, Contrast.ratio(0x00, 0xFF), 0.05)
        // A colour against itself is 1:1.
        assertEquals(1.0, Contrast.ratio(inkMuted, inkMuted), 1e-9)
        // Order must not matter.
        assertEquals(Contrast.ratio(ink, paper), Contrast.ratio(paper, ink), 1e-9)
        // Mid grey #767676 on white is the well-known 4.54:1 boundary case.
        assertTrue(Contrast.ratio(0x76, 0xFF) in 4.5..4.6)
    }

    @Test
    fun `white composite is bounded by its endpoints`() {
        assertEquals(0xFF, Contrast.whiteOver(1f, 0x00))
        assertEquals(0x40, Contrast.whiteOver(0f, 0x40))
        // Halfway between mid grey and white: 0.5*255 + 0.5*128 = 191.5, rounding up to 192.
        assertEquals(0xC0, Contrast.whiteOver(0.5f, 0x80))
    }

    @Test
    fun `backdrop cannot get dark enough to matter`() {
        val darkest = SilkFolds.darkestValue()
        // Folds only ever darken toward GREY, never past it.
        assertTrue(
            "darkest $darkest passed the fold grey ${SilkFolds.GREY}",
            darkest > SilkFolds.GREY,
        )
        assertTrue("darkest $darkest is lighter than the base", darkest < SilkFolds.BASE)
    }

    /** The assertion that actually matters. */
    @Test
    fun `body text still clears AA on the thinnest glass over the darkest backdrop`() {
        val ratio = GlassAlpha.worstCaseCardContrast()
        assertTrue(
            "InkMuted on worst-case glass is %.2f:1, under AA's %.1f:1. Either lift " .format(ratio, GlassAlpha.AA_BODY) +
                "GlassAlpha.BOTTOM, or lighten SilkFolds (fewer LAYERS, lighter GREY, or lower " +
                "PEAK_ALPHA).",
            ratio >= GlassAlpha.AA_BODY,
        )
    }

    @Test
    fun `primary text has far more headroom than the AA floor`() {
        val bg = GlassAlpha.worstCaseCardBackdrop()
        // Ink is the workhorse for headings and values; it should not be near the line at all.
        assertTrue(Contrast.ratio(ink, bg) >= 10.0)
    }

    /**
     * The landing screen's glass nav bar crosses a starfield blob, so its labels sit on glass over
     * `#0B0B0B` rather than over silk. Only [BolmitraColors.Ink] survives that; this pins both
     * halves of the fact, so the "use Ink here" rule in the nav has a reason attached that fails
     * loudly if the glass alpha ever moves.
     */
    @Test
    fun `over a starfield blob only Ink is legible on glass`() {
        val overBlob = Contrast.whiteOver(GlassAlpha.BOTTOM, GlassAlpha.BLOB)

        assertTrue(
            "Ink on glass over a blob is ${Contrast.ratio(ink, overBlob)}:1, under AA",
            Contrast.ratio(ink, overBlob) >= GlassAlpha.AA_BODY,
        )
        assertTrue(
            "InkMuted has become legible over a blob — if that is intended, the comment on " +
                "GlassAlpha.BLOB and the landing nav's colour choice both need revisiting",
            Contrast.ratio(inkMuted, overBlob) < GlassAlpha.AA_BODY,
        )
    }

    @Test
    fun `glass is actually translucent enough to see the backdrop through`() {
        // The other half of the trade. A previous revision sat at 0.80 and 0.60 effective, which is
        // why the "frosted" cards rendered as plain white; a test that only defended contrast would
        // happily let someone quietly restore that and call it fixed.
        assertTrue("top glass is too opaque to be glass", GlassAlpha.TOP <= 0.68f)
        assertTrue("bottom glass is too opaque to be glass", GlassAlpha.BOTTOM <= 0.60f)
        // And not so thin that the composite stops being a light surface at all.
        assertTrue(GlassAlpha.BOTTOM >= 0.40f)
    }
}
