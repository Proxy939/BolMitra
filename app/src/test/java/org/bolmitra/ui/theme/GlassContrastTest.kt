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
    private val paper = 0xF2

    /**
     * `BolmitraColors.Ink`, as packed RGB rather than a grey byte.
     *
     * It became a deep green, so it can no longer be treated as a neutral — the single-channel
     * shortcut would report the luminance of `0x0A`, which is far darker than the colour actually
     * is, and every assertion below would pass for the wrong reason.
     */
    private val inkRgb = 0x112C00

    private fun inkOn(grey: Int) = Contrast.ratioHexToGrey(inkRgb, grey)

    @Test
    fun `luminance and ratio agree with known WCAG values`() {
        // Black on white is the textbook 21:1.
        assertEquals(21.0, Contrast.ratio(0x00, 0xFF), 0.05)
        // A colour against itself is 1:1.
        assertEquals(1.0, Contrast.ratio(inkMuted, inkMuted), 1e-9)
        // Order must not matter.
        assertEquals(Contrast.ratio(inkMuted, paper), Contrast.ratio(paper, inkMuted), 1e-9)
        // A neutral grey read through the RGB form must agree with the grey form.
        assertEquals(Contrast.luminance(0x5A), Contrast.luminanceHex(0x5A5A5A), 1e-12)
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
        // Green ink has less headroom here than the old near-black (~11.8:1 vs ~15.2:1), so this
        // assertion is now doing real work rather than passing by a mile.
        assertTrue("Ink on worst-case glass is ${inkOn(bg)}:1", inkOn(bg) >= 10.0)
    }

    /**
     * The landing screen's glass nav bar crosses a starfield blob, so its labels sit on glass over
     * the blob fill rather than over silk. Only [BolmitraColors.Ink] survives that; this pins both
     * halves of the fact, so the "use Ink here" rule in the nav has a reason attached that fails
     * loudly if the glass alpha ever moves.
     */
    @Test
    fun `over a starfield blob only Ink is legible on glass`() {
        // Per-channel now: the blobs are green, so compositing through the grey form would report a
        // ground far darker than the real one and this test would pass for the wrong reason.
        val overBlob = Contrast.whiteOverRgb(GlassAlpha.BOTTOM, GlassAlpha.BLOB)

        assertTrue(
            "Ink on glass over a blob is ${Contrast.ratioHex(inkRgb, overBlob)}:1, under AA. " +
                "Darken BolmitraColors.Ink — this is the constraint its doc comment describes.",
            Contrast.ratioHex(inkRgb, overBlob) >= GlassAlpha.AA_BODY,
        )
        assertTrue(
            "InkMuted has become legible over a blob — if that is intended, the comment on " +
                "GlassAlpha.BLOB and the landing nav's colour choice both need revisiting",
            Contrast.ratioHex(0x5A5A5A, overBlob) < GlassAlpha.AA_BODY,
        )
    }

    /**
     * Orange's two legitimate grounds, and the two it must stay off.
     *
     * The failure this prevents is someone reaching for the logo's orange as a general accent —
     * a rule on the paper background, an icon on a green card — where it measures below even the
     * non-text floor and quietly disappears.
     */
    @Test
    fun `Ember is legible only on dark green, in either direction`() {
        val ember = 0xF57C1F
        val leaf = 0x5CC800

        assertTrue(
            "Ember on Ink is ${Contrast.ratioHex(ember, inkRgb)}:1 — one of its two valid grounds",
            Contrast.ratioHex(ember, inkRgb) >= GlassAlpha.AA_BODY,
        )
        assertTrue(
            "Ink on Ember is ${Contrast.ratioHex(inkRgb, ember)}:1 — the other valid ground",
            Contrast.ratioHex(inkRgb, ember) >= GlassAlpha.AA_BODY,
        )
        // 3.0 is WCAG's floor for non-text graphics, which is the weakest bar orange could be held
        // to on these grounds. It fails even that, so there is no "but it is only an icon" exception.
        assertTrue(
            "Ember reached the 3:1 graphics floor on Paper. If that is intended, revisit the rule " +
                "that it is decoration-only there — the landing headline extrude relies on it.",
            Contrast.ratioHex(ember, 0xF2F2F2) < 3.0,
        )
        assertTrue(
            "Ember reached the 3:1 graphics floor on Leaf — it was 1.25:1 and unusable there",
            Contrast.ratioHex(ember, leaf) < 3.0,
        )
    }

    /**
     * Orange must never be mistaken for the amber that means "approximate".
     *
     * §4.5 requires provenance to be unambiguous, and a teacher learns the amber chip. Orange sits
     * next to it in hue, so if the two ever drift close enough to be confused, this fails — the fix
     * is to move orange or to stop using it near status, not to loosen the check.
     */
    @Test
    fun `Ember stays clear of the provenance palette in role, and is checked against amber`() {
        val ember = 0xF57C1F
        val approximate = 0x8A5300
        // They are genuinely similar — 2.35:1 — which is exactly why Ember is barred from every
        // status, badge and provenance position. This asserts the similarity so the reason is
        // recorded rather than assumed, and so a future edit cannot silently claim they are distinct.
        assertTrue(
            "Ember and Approximate have diverged. If Ember moved, the ban on using it in status " +
                "roles may be reconsidered — but only deliberately.",
            Contrast.ratioHex(ember, approximate) < 3.0,
        )
    }

    /**
     * The supplied brand green is an accent, and this pins why.
     *
     * `Leaf` (`#5CC800`) is the project's green, but it cannot carry text and cannot hold the
     * starfield's white dots. Anyone reaching for "just make it the main colour" should fail here
     * rather than discover it in a sunlit classroom, which per §6.13 is the condition that matters.
     */
    @Test
    fun `Leaf works under dark text and nowhere else`() {
        val leaf = 0x5CC800
        assertTrue(
            "Ink on Leaf is ${Contrast.ratioHex(inkRgb, leaf)}:1 — the one combination that works",
            Contrast.ratioHex(inkRgb, leaf) >= GlassAlpha.AA_BODY,
        )
        assertTrue(
            "Leaf has become legible as body text on Paper. If Leaf changed, revisit every place " +
                "the palette assumes it is a fill rather than an ink.",
            Contrast.ratioHex(leaf, 0xF2F2F2) < GlassAlpha.AA_BODY,
        )
        assertTrue(
            "OnInk on Leaf is legible now — a light-on-Leaf button would have been unreadable",
            Contrast.ratioHex(0xF4F4F4, leaf) < GlassAlpha.AA_BODY,
        )
        // White starfield dots need a dark ground. This is why the blobs are Ink and not Leaf.
        assertTrue(
            "white dots on Leaf reached AA — the blob fill choice can be revisited",
            Contrast.ratioHex(0xFFFFFF, leaf) < GlassAlpha.AA_BODY,
        )
        assertTrue(
            "white dots on Ink must stay crisp, they are what the blobs exist for",
            Contrast.ratioHex(0xFFFFFF, inkRgb) >= 10.0,
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
