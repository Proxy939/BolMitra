package org.bolmitra.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The astronaut's placement is the one part of [FloatingAstronaut] that is arithmetic rather than
 * drawing, and it is the part that silently breaks: nudge [AstronautSlot.WIDTH_OF_HEIGHT] up and the
 * figure's boots leave the bottom of the screen on a wide display, which nobody notices on the 16:10
 * reference tablet.
 *
 * Plain JUnit on purpose. Checking this through a Compose UI test would mean adding
 * `compose-ui-test-junit4` and an instrumented run to verify four multiplications.
 */
class AstronautSlotTest {

    /** Aspect ratios (width ÷ height) the screen can realistically be handed, landscape. */
    private val aspects = mapOf(
        "4:3 tablet" to 4f / 3f,
        "16:10 reference tablet" to 1.6f,
        "16:9" to 16f / 9f,
        "3:2 Chromebook" to 1.5f,
        "21:9 ultrawide window" to 21f / 9f,
        "unfolded foldable, near square" to 1.1f,
    )

    @Test
    fun `figure stays inside the viewport at every plausible aspect ratio`() {
        val height = 1000f
        aspects.forEach { (name, aspect) ->
            val width = height * aspect
            val (boxW, boxH) = AstronautSlot.size(width, height)

            val right = width * AstronautSlot.LEFT + boxW
            val bottom = height * AstronautSlot.TOP + boxH

            assertTrue(
                "$name: right edge $right overflows width $width",
                right <= width,
            )
            assertTrue(
                "$name: bottom edge $bottom overflows height $height",
                bottom <= height,
            )
        }
    }

    @Test
    fun `figure is large enough to read as an illustration`() {
        val height = 1000f
        aspects.forEach { (name, aspect) ->
            val (boxW, _) = AstronautSlot.size(height * aspect, height)
            // Below about a tenth of the viewport height it is a smudge, not an astronaut.
            assertTrue("$name: width $boxW is too small to be legible", boxW >= height * 0.10f)
        }
    }

    @Test
    fun `size is driven by whichever axis is tighter`() {
        // Tall-and-narrow: width is the binding constraint.
        val (narrow, _) = AstronautSlot.size(1000f, 4000f)
        assertTrue(narrow == 1000f * AstronautSlot.WIDTH_OF_WIDTH)

        // Wide-and-short: height is the binding constraint. This is the case a width-only rule got
        // wrong, so it is the reason the `minOf` exists.
        val (wide, _) = AstronautSlot.size(4000f, 1000f)
        assertTrue(wide == 1000f * AstronautSlot.WIDTH_OF_HEIGHT)
    }

    @Test
    fun `drawing keeps its aspect ratio`() {
        val (boxW, boxH) = AstronautSlot.size(1600f, 1000f)
        assertTrue(boxH == boxW * AstronautSlot.ASPECT)
    }
}
