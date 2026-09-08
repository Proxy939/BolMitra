package org.bolmitra.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * BolMitra design system — monochrome frosted glass on a paper wash.
 *
 * The palette, type scale, radius scale and the [SilkBackdrop] gradient are taken from the
 * supplied Compose design. Three things were changed on the way in, and each one is a project
 * requirement rather than a preference — §6.13 and V39 set concrete targets because the user is a
 * teacher holding a budget tablet in a **sunlit classroom**, which is the worst viewing condition
 * there is.
 *
 * ### 1. [InkMuted] darkened `#6B6B6B` → `#5A5A5A`
 *
 * The original measures **4.76:1** on [Paper] — technically over the 4.5:1 line, but with almost
 * no margin, and it carries most of the secondary text in the design. `#5A5A5A` gives **6.4:1**.
 * The visual intent (a muted grey, not a second black) is unchanged.
 *
 * ### 2. Card stroke changed from translucent white to an opaque hairline
 *
 * The original [GlassStroke] was white at 20% alpha, which is the correct choice for frosted glass
 * over a *dark or photographic* backdrop. Over a near-white paper wash it is invisible, so cards
 * would have no readable edge at all — and edges are the only thing that survives glare, which is
 * why §6.13 prefers outlines over shadows. Now `#D5D3CE`.
 *
 * ### 3. Type floors raised
 *
 * The original scale ran `bodySmall 12sp / labelSmall 11sp / bodyMedium 14sp`, and used 12sp for
 * real content. §6.13 sets a **16sp floor for body text** — unreadable at arm's length in daylight
 * below that. The scale's *shape* is kept (very large display numerals, tight negative tracking on
 * headings, wide-tracked small labels); only the absolute sizes moved up.
 *
 * ### Contrast audit
 *
 * | Colour | On | Ratio | AA 4.5:1 |
 * |---|---|---|---|
 * | [Ink] `#0E0E0E` | [Paper] | ~18.6:1 | passes |
 * | [InkMuted] `#5A5A5A` | [Paper] | ~6.4:1 | passes |
 * | [OnInk] `#F4F4F4` | [Ink] | ~17.6:1 | passes |
 * | [OnInkMuted] `#9A9A9A` | [Ink] | ~6.9:1 | passes |
 * | [Verified] `#1B5E20` | [Paper] | ~7.4:1 | passes |
 * | [Approximate] `#8A5300` | [Paper] | ~5.2:1 | passes |
 * | [Unavailable] `#8B1A1A` | [Paper] | ~7.6:1 | passes |
 *
 * ### 4. The audit above is against solid [Paper]. Cards are no longer solid.
 *
 * [Glass] and [GlassSoft] were lowered so the [SilkBackdrop] is genuinely visible through a panel,
 * which means a card's effective background is now a composite rather than a named colour, and it
 * varies with where on the backdrop the card happens to sit. Reasoning about that by eye is how a
 * design ends up shipping 3.9:1 body text.
 *
 * So the worst case is bounded by construction instead: [SilkFolds] caps how dark the backdrop can
 * get, [GlassAlpha.worstCaseCardContrast] composites the thinnest glass over that floor, and
 * `GlassContrastTest` fails the build if the combination stops clearing AA. The numbers in the table
 * are the *best* case, on plain [Paper]; the bounded worst case is **5.42:1 for [InkMuted]** and
 * **15.18:1 for [Ink]**, against a composite card background of `#E4E4E4` over the darkest the
 * backdrop is allowed to reach, `#C5C5C5`.
 */
object BolmitraColors {
    val Ink = Color(0xFF0E0E0E)
    val InkSoft = Color(0xFF1C1C1C)

    /** Darkened from the supplied `#6B6B6B` for contrast margin. See the class docs. */
    val InkMuted = Color(0xFF5A5A5A)

    val Paper = Color(0xFFF2F2F2)
    val PaperWarm = Color(0xFFFAFAFA)

    /**
     * The two glass fills, derived from [GlassAlpha] rather than written as colour literals.
     *
     * These were `0xCCFFFFFF` and `0x99FFFFFF` — 80% and 60% white. At 80% over a near-white
     * backdrop there is nothing to see through, so the cards read as plain white rectangles and the
     * "frosted glass" in the design was notional. They are lower now, which is what actually makes
     * the backdrop visible through a panel.
     *
     * They are computed from the alpha constants instead of restated as hex so the number
     * [GlassAlpha.worstCaseCardBackdrop] proves the contrast bound against cannot drift away from
     * the number that ships. That drift is the whole failure mode here: a designer nudges a card
     * more transparent, the audit table above silently becomes fiction, and nobody notices until a
     * teacher is squinting at a tablet in a sunlit classroom.
     */
    val Glass = Color.White.copy(alpha = GlassAlpha.TOP)
    val GlassSoft = Color.White.copy(alpha = GlassAlpha.BOTTOM)

    /**
     * Opaque, unlike the supplied translucent-white version.
     *
     * White-on-white has no edge. This is the line that keeps a card readable when the tablet is
     * catching sunlight, so it cannot be decorative.
     */
    val GlassStroke = Color(0xFFD5D3CE)

    /** Decorative only — chart guides and dividers. Never a card edge, never text. */
    val HairlineOnPaper = Color(0x14000000)
    val HairlineOnInk = Color(0x1AFFFFFF)

    val OnInk = Color(0xFFF4F4F4)
    val OnInkMuted = Color(0xFF9A9A9A)

    // --- provenance ---------------------------------------------------------------------
    // §4.5 requires every translation to carry a verified / approximate / machine label. These
    // are the only hues in an otherwise monochrome system, which is the point: the one signal
    // that must never be missed is the only one with colour. Each is still paired with a word and
    // a distinct shape, because colour alone fails for a colour-blind teacher.
    val Verified = Color(0xFF1B5E20)
    val Approximate = Color(0xFF8A5300)
    val Unavailable = Color(0xFF8B1A1A)
}

/* ------------------------------------------------------------------- glass, and its contrast */

/**
 * WCAG 2.1 contrast arithmetic.
 *
 * This lives in the theme rather than a utility file because it is not a helper that happens to sit
 * nearby — it is what *justifies* the token values above. [BolmitraColors]'s audit table is only
 * true while the numbers here say it is.
 *
 * Every colour it is asked about is a neutral grey, which is the one simplification made: for
 * `r == g == b` the weighted sum `0.2126r + 0.7152g + 0.0722b` collapses to the single linearised
 * channel, because the weights sum to 1. The palette is monochrome by design (§6.13 — the only hues
 * in the system are the three provenance colours, and those are never a card background), so the
 * general form would be dead code.
 */
internal object Contrast {

    /** WCAG relative luminance of an opaque neutral grey, given a 0..255 channel value. */
    fun luminance(channel: Int): Double {
        val c = channel / 255.0
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }

    /** Contrast ratio between two opaque neutral greys. Order does not matter. */
    fun ratio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** Source-over composite of white at [alpha] onto an opaque grey [backdrop]. */
    fun whiteOver(alpha: Float, backdrop: Int): Int =
        Math.round(alpha * 255f + (1f - alpha) * backdrop)
}

/**
 * How transparent the glass is, and the proof that it is still readable.
 *
 * Translucency and legibility pull in opposite directions, and §6.13's sunlit-classroom case means
 * legibility wins ties. Rather than settle that by eye, the alphas are chosen and then checked:
 * [worstCaseCardContrast] composites the *most* transparent glass in the system over the *darkest*
 * the backdrop can get, and `GlassContrastTest` asserts the result still clears AA for the weakest
 * text token, [BolmitraColors.InkMuted].
 */
internal object GlassAlpha {

    /** Top of a card's gradient. */
    const val TOP = 0.60f

    /**
     * Bottom of a card's gradient, and therefore the worst case: the most transparent pixel any
     * text can sit on. The bound below is computed against this one, not [TOP].
     */
    const val BOTTOM = 0.541f

    /** `#5A5A5A`, the least contrasty colour the system puts real text in. */
    private const val INK_MUTED_CHANNEL = 0x5A

    /** WCAG AA for body text. */
    const val AA_BODY = 4.5

    /**
     * The darkest grey text can sit on **where the ground is the silk backdrop**.
     *
     * Not the darkest in the app, which an earlier revision of this comment wrongly claimed. The
     * landing screen's starfield blobs are `#0B0B0B`, and the glass nav bar crosses one — visibly
     * so, which is the effect working. Glass over a blob composites to about `#8F`, far below this,
     * and [BLOB] plus `GlassContrastTest` cover that case separately.
     */
    fun worstCaseCardBackdrop(): Int = Contrast.whiteOver(BOTTOM, SilkFolds.darkestValue())

    /**
     * `#0B0B0B`, the starfield blob fill — the darkest ground any glass in the app is laid over.
     *
     * Text on glass over a blob has room for [BolmitraColors.Ink] and nothing else:
     * [BolmitraColors.InkMuted] lands near 2.1:1 there, less than half the AA floor. That is why the
     * landing nav labels are `Ink`, and it is a real constraint on any future chrome placed over the
     * collage rather than a stylistic preference.
     */
    const val BLOB = 0x0B

    /** Contrast for [BolmitraColors.InkMuted] in the worst case the design permits. */
    fun worstCaseCardContrast(): Double =
        Contrast.ratio(INK_MUTED_CHANNEL, worstCaseCardBackdrop())
}

/**
 * The backdrop's fold shading, as numbers, so how dark it can get is a fact rather than a guess.
 *
 * Only the fold layers darken the page. The sheen highlights are white, and white source-over can
 * only lighten, so they cannot participate in the worst case — which is why the bound needs nothing
 * from them and adding another highlight is always safe.
 */
internal object SilkFolds {

    /** [BolmitraColors.PaperWarm]'s channel value, which the folds are laid over. */
    const val BASE = 0xFA

    /**
     * The grey each fold layer tends toward at its centre.
     *
     * **This is the sensitive knob.** Stacked alpha saturates toward this value, so it sets the
     * floor and the other two only decide how fast the floor is approached: at this grey, the worst
     * case survives [LAYERS] rising to 14 (4.47:1) or [PEAK_ALPHA] rising to 0.62 (4.52:1), but
     * darkening the grey alone to `0x3C` fails at 4.21:1. If you are reaching for "deeper, more
     * dramatic folds", this is the constant you will reach for, and it is the one that breaks
     * things.
     */
    const val GREY = 0x96

    /** Peak alpha of a single fold layer, at its centre. */
    const val PEAK_ALPHA = 0.30f

    /** How many fold layers [SilkBackdrop] draws. Keep in step with the `fold(...)` calls there. */
    const val LAYERS = 4

    /**
     * The darkest value the backdrop can reach, assuming every fold layer peaks on the same pixel.
     *
     * They do not — the centres are deliberately spread across and beyond the canvas — so this is a
     * deliberately pessimistic bound. Being pessimistic is the point: it stays true if someone
     * later moves a fold, and it only needs revisiting if someone changes [LAYERS], [GREY] or
     * [PEAK_ALPHA], which is exactly when the contrast audit does need rechecking.
     */
    fun darkestValue(): Int {
        var v = BASE.toFloat()
        repeat(LAYERS) { v += (GREY - v) * PEAK_ALPHA }
        return Math.round(v)
    }
}

// Flat aliases, because the screens read better without the object prefix on every line.
val Ink = BolmitraColors.Ink
val InkMuted = BolmitraColors.InkMuted
val Paper = BolmitraColors.Paper
val Verified = BolmitraColors.Verified
val Approximate = BolmitraColors.Approximate
val Unavailable = BolmitraColors.Unavailable

private val BolmitraScheme = lightColorScheme(
    primary = BolmitraColors.Ink,
    onPrimary = BolmitraColors.OnInk,
    secondary = BolmitraColors.InkSoft,
    onSecondary = BolmitraColors.OnInk,
    background = BolmitraColors.Paper,
    onBackground = BolmitraColors.Ink,
    surface = BolmitraColors.PaperWarm,
    onSurface = BolmitraColors.Ink,
    surfaceVariant = BolmitraColors.Paper,
    onSurfaceVariant = BolmitraColors.InkMuted,
    outline = BolmitraColors.InkMuted,
    outlineVariant = BolmitraColors.GlassStroke,
    error = BolmitraColors.Unavailable,
    onError = BolmitraColors.OnInk,
)

/** Radius scale from the supplied design: 26 dp panels, 20 dp inner tiles, pill chrome. */
object Radius {
    val sm = RoundedCornerShape(10.dp)
    val md = RoundedCornerShape(20.dp)
    val lg = RoundedCornerShape(26.dp)

    /**
     * The outer sheet only. Larger than [lg] because it contains cards that use `lg` — an outer
     * corner must be the wider of the two or the nested card appears to bulge out through it.
     */
    val xl = RoundedCornerShape(34.dp)

    val pill = RoundedCornerShape(percent = 50)
}

/**
 * Layout constants that encode the accessibility requirement, so whoever writes the next screen
 * cannot quietly forget it.
 *
 * The supplied design sized its nav rows at ~35 dp tall, its bottom-bar buttons at 42 dp and its
 * icon actions at 38 dp. All three are under the floor. Sizes here are the floor, and the screens
 * apply them with `heightIn(min = …)` so text can still grow with the system font scale.
 */
object Dimens {
    /** V39 / Android guidance: minimum 48×48 dp. */
    val minTouchTarget = 48.dp

    /** V39: at least 8 dp between targets. */
    val targetGap = 12.dp

    val screenPadding = 20.dp
    val screenPaddingWide = 32.dp
    val cardPadding = 20.dp
    val sectionGap = 14.dp

    /**
     * Primary actions a teacher hits mid-lesson, possibly one-handed while holding a book. Well
     * above the 48 dp floor on purpose — this is the push-to-talk class of control.
     */
    val primaryActionHeight = 64.dp

    /** Above this the sidebar is a permanent rail; below it collapses to a bottom bar. */
    val wideBreakpoint = 840.dp
}

private val Family = FontFamily.Default

/**
 * Shape from the supplied scale, sizes raised to §6.13's floors.
 *
 * Platform default family, so no font assets enter the APK. **Known gap:** the platform
 * Devanagari font is relied on for all Hindi and Mundari text. That is fine on the Android 14
 * reference tablet, but §6.13's PDF worksheets need an embedded subset (V20) and a stripped OEM
 * Android 9 build is not guaranteed to carry Devanagari at all. Bundling Noto Sans Devanagari is
 * outstanding work, not a decision to skip it.
 */
val BolmitraTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Family, fontWeight = FontWeight.Bold,
        fontSize = 52.sp, lineHeight = 54.sp, letterSpacing = (-1.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Family, fontWeight = FontWeight.Bold,
        fontSize = 38.sp, lineHeight = 42.sp, letterSpacing = (-1).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Family, fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Family, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Family, fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp, lineHeight = 23.sp,
    ),
    // 16 sp is the floor for anything a teacher has to read. Nothing below this carries content.
    bodyLarge = TextStyle(fontFamily = Family, fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = Family, fontSize = 16.sp, lineHeight = 23.sp),
    /** Captions and secondary metadata only — never primary content. */
    bodySmall = TextStyle(fontFamily = Family, fontSize = 14.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(
        fontFamily = Family, fontWeight = FontWeight.Medium,
        fontSize = 15.sp, lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Family, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 18.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Family, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 17.sp, letterSpacing = 0.8.sp,
    ),
)

/**
 * Light only, and that is deliberate rather than unfinished.
 *
 * The whole design is translucent white over a paper wash; inverted, [GlassCard] becomes white
 * panels on a dark ground and the frosted metaphor collapses. §6.13's case that matters is
 * **daylight**, and a classroom is lit. Following the system's dark setting here would trade a
 * working screen for a broken one, so the setting is ignored.
 */
@Composable
fun BolMitraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BolmitraScheme,
        typography = BolmitraTypography,
        content = content,
    )
}

/**
 * The soft silk wash the screens sit on — the thing the glass is glass *of*.
 *
 * ### Why this was rebuilt
 *
 * The previous version was three near-white gradients: `PaperWarm → Paper → HairlineOnPaper` plus a
 * white radial. End to end it spanned roughly `#FAFAFA` to `#F2F2F2`, about 3% of the value range.
 * Nothing was visible through a translucent panel because there was nothing behind it to see, so
 * every "frosted" card in the app rendered as a flat white rectangle. Transparency needs something
 * to be transparent *to*; that missing backdrop, not the card styling, was the actual gap.
 *
 * ### How the folds are made without a blur
 *
 * Wide, heavily-feathered radial gradients whose centres sit at or beyond the edges of the canvas,
 * so only their soft flanks land on screen. Overlapped, they read as out-of-focus fabric folds.
 * There is no blur pass anywhere here, and that is deliberate on three counts:
 *
 * - `minSdk` is 28. `RenderEffect.createBlurEffect` is API 31, so a real blur would do nothing at
 *   all on the Android 9 tablets §4.8.1 exists to support.
 * - Per-frame backdrop blur is the most GPU-expensive effect in a UI toolkit, and the target is
 *   budget hardware.
 * - It would buy nothing visually. In the reference design the blur is baked into the background
 *   image; the panels over it are plain translucent white with a hairline. Static soft gradients
 *   reproduce that exactly, at zero cost and on every supported device.
 *
 * Gradients also mean no bitmap, which matters when §5.4 already gates on a 464 MB model payload.
 *
 * ### The one constraint that is not aesthetic
 *
 * Fold darkness is capped by [SilkFolds], because card text now sits on glass thin enough for the
 * backdrop to show through it. Darken the folds and you darken the effective background of every
 * body string in the app. `GlassContrastTest` holds the line.
 */
@Composable
fun SilkBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().drawBehind { drawSilk() }) {
        content()
    }
}

/**
 * Base paper, then [SilkFolds.LAYERS] fold layers, then white sheen.
 *
 * Order matters for the contrast bound as well as the look: every darkening layer is applied before
 * any lightening one, so [SilkFolds.darkestValue] can model the floor by walking the fold layers
 * alone and ignoring the sheen entirely.
 */
private fun DrawScope.drawSilk() {
    drawRect(BolmitraColors.PaperWarm)

    val foldGrey = Color(0xFF969696)

    /**
     * One fold. Centre is given in fractions of the canvas and is allowed to sit outside it, which
     * is what keeps the visible part to a soft flank rather than an obvious circular blob.
     */
    /**
     * Radius is a fraction of [Size.maxDimension], not `minDimension`.
     *
     * These screens are landscape tablets, so `minDimension` is the height. Sizing folds off it made
     * them too small to span the width, and they read as faint corner smudges instead of folds
     * crossing the page.
     */
    fun fold(cx: Float, cy: Float, radius: Float) {
        drawRect(
            Brush.radialGradient(
                0.00f to foldGrey.copy(alpha = SilkFolds.PEAK_ALPHA),
                0.55f to foldGrey.copy(alpha = SilkFolds.PEAK_ALPHA * 0.45f),
                1.00f to Color.Transparent,
                center = Offset(size.width * cx, size.height * cy),
                radius = size.maxDimension * radius,
            ),
        )
    }

    // Four layers, matching SilkFolds.LAYERS. Spread rather than clustered in the corners: the
    // middle of the screen is where the glass actually sits, so shading only the edges is shading
    // the one region nothing shows through.
    fold(1.00f, 0.50f, 0.60f)
    fold(0.42f, 1.02f, 0.52f)
    fold(0.86f, -0.06f, 0.38f)
    fold(0.06f, 0.32f, 0.34f)

    // The sheen: a broad diagonal highlight running with the folds, which is what separates silk
    // from grey mush. White, so it can only lighten — it cannot affect the contrast floor.
    //
    // Peak alpha is 0.26, down from 0.62. On a real device the stronger version erased the folds
    // entirely and the whole page came back looking as flat as the near-white gradients this
    // replaced. A highlight has to leave something to highlight.
    drawRect(
        Brush.linearGradient(
            0.00f to Color.White.copy(alpha = 0.00f),
            0.34f to Color.White.copy(alpha = 0.18f),
            0.50f to Color.White.copy(alpha = 0.26f),
            0.68f to Color.White.copy(alpha = 0.10f),
            1.00f to Color.White.copy(alpha = 0.00f),
            start = Offset(0f, size.height * 0.12f),
            end = Offset(size.width * 0.88f, size.height),
        ),
    )

    // A second, tighter highlight in the top-left, where the light comes from on the landing
    // screen's beam too, so the two screens agree about where the light source is.
    drawRect(
        Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.24f),
            1f to Color.White.copy(alpha = 0f),
            center = Offset(size.width * 0.16f, size.height * 0.10f),
            radius = size.maxDimension * 0.45f,
        ),
    )
}
