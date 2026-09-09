package org.bolmitra.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks on the font-scale bounds, which are the one setting that can make the app unusable.
 *
 * `Settings` itself needs a `Context`, so these test the constants and the offered steps rather than
 * the store. That is where the hazard is: a step outside the clamp would silently snap to a different
 * value than the label promised, and a bound that ignored §6.13's 16 sp floor would let a teacher
 * shrink the one box they have to read across a classroom.
 */
class SettingsFontScaleTest {

    @Test
    fun `every offered step lies inside the clamp`() {
        for ((label, value) in Settings.FONT_SCALE_STEPS) {
            assertTrue(
                "step '$label' ($value) is below MIN_FONT_SCALE ${Settings.MIN_FONT_SCALE}",
                value >= Settings.MIN_FONT_SCALE,
            )
            assertTrue(
                "step '$label' ($value) is above MAX_FONT_SCALE ${Settings.MAX_FONT_SCALE}",
                value <= Settings.MAX_FONT_SCALE,
            )
        }
    }

    /**
     * The steps must span the whole range, or the outer bounds are unreachable from the UI and the
     * clamp is guarding a value nothing can set.
     */
    @Test
    fun `the steps reach both bounds`() {
        val values = Settings.FONT_SCALE_STEPS.map { it.second }
        assertEquals(Settings.MIN_FONT_SCALE, values.min())
        assertEquals(Settings.MAX_FONT_SCALE, values.max())
    }

    @Test
    fun `the steps are distinct and ascending`() {
        val values = Settings.FONT_SCALE_STEPS.map { it.second }
        assertEquals("duplicate scales offered", values.size, values.distinct().size)
        assertEquals("steps are not in ascending order", values.sorted(), values)
    }

    /** 1.0 must be offered, or there is no way back to the platform default. */
    @Test
    fun `an unscaled option exists`() {
        assertTrue(
            "no 1.0 step offered",
            Settings.FONT_SCALE_STEPS.any { kotlin.math.abs(it.second - 1.0f) < 0.001f },
        )
    }

    /**
     * The lower bound must not undercut §6.13's 16 sp floor on the transcript box.
     *
     * 16 sp is the size a teacher reads the translation at. At 0.85 that is 13.6 sp, which is the
     * documented limit; anything smaller breaches a legibility requirement rather than a preference.
     */
    @Test
    fun `the lower bound keeps the transcript box legible`() {
        val transcriptSp = 16f
        assertTrue(
            "min scale ${Settings.MIN_FONT_SCALE} takes the 16 sp transcript below 13 sp",
            transcriptSp * Settings.MIN_FONT_SCALE >= 13f,
        )
    }

    @Test
    fun `every step has a non-blank label`() {
        assertTrue(Settings.FONT_SCALE_STEPS.all { it.first.isNotBlank() })
    }

    @Test
    fun `the default startup screen is a real destination name`() {
        // Guards against a typo that would silently fall back to HOME forever.
        assertEquals("HOME", Settings.DEFAULT_STARTUP)
    }
}
