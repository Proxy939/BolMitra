package org.bolmitra.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.bolmitra.device.DeviceSpec
import org.bolmitra.device.DeviceTier
import org.bolmitra.ui.theme.BolmitraColors
import org.bolmitra.ui.theme.Dimens
import org.bolmitra.ui.theme.Radius
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Landing screen — the "Voyager" poster layout, rebuilt in Compose with BolMitra's content.
 *
 * Spacing, type hierarchy and the collage are the reference's: hairline nav bar across the top, a
 * two-line ultra-condensed display headline with a ghost outline behind it and a grey extrude under
 * it, monospace body copy beneath, an attribution line below that, and organic black starfield
 * blobs anchoring all four corners. The reference's diagonal light streak is gone — the shared
 * [org.bolmitra.ui.theme.SilkBackdrop] now supplies both the page's modelling and its top-left light
 * source, and the streak drawn over it looked like a seam rather than a beam.
 *
 * **The hero block is centred, where the reference has it flush left.** That is a deliberate
 * departure, and it also fixes a legibility fault: flush left, the body copy's first words ran
 * across the bottom-left blob, and `Ink` text on a `#0B0B0B` blob is unreadable. Three things move
 * together to make centring work — see the hero `Column`, [CentreOrigin] and the `textAlign` on
 * the shared headline style.
 *
 * ### Content substitutions
 *
 * | Reference | BolMitra |
 * |---|---|
 * | `VOYAGER` wordmark | `BOLMITRA` |
 * | `NEWS · OBSERVING · RESOURCES & EDUCATION · COMMUNITY · ABOUT US` | the app's five real destinations, and they navigate rather than sitting dead |
 * | `EXPLORE` / `THE SPACE` | `SPEAK HINDI` / `HEAR MUNDARI` — kept to two lines with the longer one second, so the type block has the reference's shape |
 * | affiliate-programs filler copy | what the app actually does, at the same two-line length |
 * | `James Singleton` | the language pair and region, occupying the same attribution slot |
 *
 * ### Two honest gaps, both about assets rather than layout
 *
 * **1. The six ink illustrations are missing.** The astronaut, alien, UFO, Saturn, moon and
 * telescope were PNGs in the original `Landing launch/` folder, which has been deleted; the
 * replacement folder ships no images at all, only a favicon. They cannot be regenerated here. The
 * blobs, the light streak and the type carry the composition in the meantime, and
 * [SpaceIllustrations] is the single place to drop them in — see its docs.
 *
 * **2. The display face is Roboto Condensed Black, not the reference's compressed grotesque.**
 * Requested through [DeviceFontFamilyName] so it uses the platform font and adds nothing to an APK
 * that already carries a 464 MB model payload (§5.4), then squeezed with a horizontal scale to
 * approach the reference's width. It is close, not identical. Bundling an OFL display face such as
 * Anton or Archivo Black would match exactly and cost ~50 KB — worth doing, and the same font
 * bundling §6.13 already needs for Devanagari in PDF worksheets (V20).
 */
@Composable
fun LandingScreen(
    spec: DeviceSpec,
    tier: DeviceTier,
    onStart: () -> Unit,
    onDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // No ground of its own: the app-wide SilkBackdrop in MainActivity is the ground. See the note
    // on the removed light streak in this file's header.
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight

        // Blobs sit behind everything, positioned as fractions of the viewport so the collage
        // holds its composition at any tablet size rather than at one hardcoded resolution.
        StarfieldBlobs(w, h)

        SpaceIllustrations(w, h)

        Column(Modifier.fillMaxSize()) {
            NavBar(onNavigate = onStart, onDiagnostics = onDiagnostics)

            // Hero block is centred in whatever height the nav leaves over, both axes.
            //
            // `weight(1f)` is what makes the vertical centring work: it claims the leftover height
            // so `Arrangement.Center` has a box to centre inside. Without it the inner Column
            // wraps its content and `Center` is a no-op.
            //
            // 0.10 w each side is not arbitrary: it leaves the headline the same 0.80 w it had
            // when the block was flush left (0.135 + 0.06 inset), so the longest line keeps its
            // slack. `HEAR MUNDARI` measures ~0.70 w before the 0.86 squeeze, and if the platform
            // lacks Roboto Condensed the fallback face is wider still — tightening this further is
            // what makes the headline wrap on some device.
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = w * 0.10f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DisplayHeadline("SPEAK HINDI", "HEAR MUNDARI", w)

                Spacer(Modifier.height(h * 0.085f))

                // No manual line break. The hard `\n` after "reaches a" was positioned for the old
                // flush-left block; centred and capped at 0.52 w it collided with the natural wrap
                // and produced "before it / reaches a / child." — an orphan line two words long.
                // Letting it wrap on its own gives an even rag at any width.
                Text(
                    "Every phrase is checked by a Mundari speaker " +
                        "before it reaches a child. Nothing you say ever leaves this tablet.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 17.sp,
                        lineHeight = 27.sp,
                    ),
                    color = BolmitraColors.Ink,
                    textAlign = TextAlign.Center,
                    // Held at 0.52 w so, centred, the copy spans 0.24–0.76 w: clear of the
                    // left blobs (to ~0.12 w) and of the right-middle blob (from 0.80 w). Widening
                    // it walks black text back onto a black blob, which is the fault this change
                    // set out to fix.
                    modifier = Modifier.widthIn(max = w * 0.52f),
                )

                Spacer(Modifier.height(h * 0.055f))

                Text(
                    "\u0939\u093F\u0928\u094D\u0926\u0940 \u2192 \u092E\u0941\u0902\u0921\u093E\u0930\u0940" +
                        "  \u00B7  Jharkhand  \u00B7  ${tier.name.lowercase()} \u00B7 %.1f GiB"
                            .format(spec.totalRamGiB),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                    ),
                    color = BolmitraColors.InkMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/* --------------------------------------------------------------------------- typography */

/**
 * The reference's compressed grotesque, approached with the platform's condensed family.
 *
 * `DeviceFontFamilyName` asks the platform for `sans-serif-condensed` (Roboto Condensed) rather
 * than shipping a font file. If a stripped OEM build lacks it, Android falls back to the default
 * family — the screen degrades to a wider headline rather than failing, which is the right failure
 * mode for a design flourish.
 */
private val CondensedBlack = FontFamily(
    Font(DeviceFontFamilyName("sans-serif-condensed"), weight = FontWeight.Black),
    Font(DeviceFontFamilyName("sans-serif-condensed"), weight = FontWeight.Bold),
)

/**
 * Two-line display headline in three stacked layers, exactly as the reference builds it:
 *
 * 1. **Ghost** — same words, outline stroke only, offset up and slightly left, sitting behind
 *    everything. In the reference this peeks out above the first line as a hollow echo.
 * 2. **Extrude** — a mid-grey solid copy offset down-right, which reads as the letters' depth.
 * 3. **Solid** — the black letters on top.
 *
 * Sizing is driven off the available width rather than a fixed sp value, so the block fills the
 * same proportion of the screen on any tablet. `scaleX` supplies the extra compression the
 * platform condensed face does not have on its own.
 */
@Composable
private fun DisplayHeadline(line1: String, line2: String, containerWidth: Dp) {
    // The reference's cap height is ~17% of viewport width. Tuned against the 2880×1800 reference
    // tablet and expressed as a ratio so it travels.
    val size = (containerWidth.value * 0.115f).sp
    val lineHeight = (containerWidth.value * 0.115f).sp
    val squeeze = 0.86f

    val solid = TextStyle(
        fontFamily = CondensedBlack,
        fontWeight = FontWeight.Black,
        fontSize = size,
        lineHeight = lineHeight,
        letterSpacing = (-0.02f).em,
        // Set on the shared style so all three layers agree. If they disagreed the ghost and the
        // extrude would drift off the solid letters on the shorter line.
        textAlign = TextAlign.Center,
    )

    Box {
        // 1. Ghost outline, up and to the left.
        Text(
            "$line1\n$line2",
            style = solid.copy(
                color = Color(0x22000000),
                drawStyle = Stroke(width = 2.5f),
            ),
            modifier = Modifier
                .offset(x = -containerWidth * 0.008f, y = -containerWidth * 0.030f)
                .graphicsLayer(scaleX = squeeze * 1.02f, transformOrigin = CentreOrigin),
        )
        // 2. Grey extrude, down and to the right.
        Text(
            "$line1\n$line2",
            style = solid.copy(color = Color(0xFF8A8A8A)),
            modifier = Modifier
                .offset(x = containerWidth * 0.0055f, y = containerWidth * 0.0055f)
                .graphicsLayer(scaleX = squeeze, transformOrigin = CentreOrigin),
        )
        // 3. Solid black.
        Text(
            "$line1\n$line2",
            style = solid.copy(color = BolmitraColors.Ink),
            modifier = Modifier.graphicsLayer(scaleX = squeeze, transformOrigin = CentreOrigin),
        )
    }
}

/**
 * Squeeze anchored to the middle, so a compressed line stays centred on the block.
 *
 * This was `TransformOrigin(0f, .5f)` while the headline was flush left — left-anchored scaling is
 * what kept it flush. Centred, a left anchor would pull the letters `(1 - squeeze) / 2` of the
 * block's width off to the left of true centre, which reads as a misalignment rather than a
 * compression.
 */
private val CentreOrigin = TransformOrigin(0.5f, 0.5f)

/* ---------------------------------------------------------------------------- nav bar */

private val navItems = listOf(
    "HOME" to Destination.HOME,
    "LIVE CLASS" to Destination.LIVE,
    "WORKSHEETS" to Destination.WORKSHEETS,
    "PHRASEBOOK" to Destination.PHRASEBOOK,
    "DIAGNOSTICS" to Destination.DIAGNOSTICS,
)

/**
 * Top nav: logo mark plus wordmark on the left, tracked caps links filling the centre-right.
 *
 * The reference's links are decorative. These are the app's real destinations and they navigate,
 * which costs nothing visually and means the row is not five dead words. Each is at least
 * [Dimens.minTouchTarget] tall (V39) — achieved with `heightIn`, so the caps stay the reference's
 * size while the target around them is legal.
 */
@Composable
private fun NavBar(onNavigate: () -> Unit, onDiagnostics: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp)
            // A floating pane of glass, which is both the design ask and the fix for a real bug:
            // bare on the paper, the last two links crossed the top-right starfield blob and were
            // Ink on #0B0B0B, i.e. invisible. The glass gives every link the same light ground.
            .background(
                Brush.verticalGradient(
                    0f to BolmitraColors.Glass,
                    1f to BolmitraColors.GlassSoft,
                ),
                Radius.pill,
            )
            .border(1.dp, BolmitraColors.GlassStroke, Radius.pill)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(30.dp).background(BolmitraColors.Ink, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "\u092C",
                style = MaterialTheme.typography.labelMedium,
                color = BolmitraColors.OnInk,
            )
        }
        Spacer(Modifier.width(9.dp))
        Text(
            "BOLMITRA",
            style = TextStyle(
                fontFamily = CondensedBlack,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                letterSpacing = 1.6.sp,
            ),
            color = BolmitraColors.Ink,
        )

        Spacer(Modifier.weight(1f))

        // 16 dp and 15 sp, down from 26 dp and 17 sp. At the old values the five labels plus the
        // wordmark overran the bar and `DIAGNOSTICS` was clipped by the right edge of the screen.
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navItems.forEach { (label, dest) ->
                Text(
                    label,
                    style = TextStyle(
                        fontFamily = CondensedBlack,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        letterSpacing = 0.8.sp,
                    ),
                    maxLines = 1,
                    color = BolmitraColors.Ink,
                    modifier = Modifier
                        .heightIn(min = Dimens.minTouchTarget)
                        .clickable(role = Role.Button) {
                            if (dest == Destination.DIAGNOSTICS) onDiagnostics() else onNavigate()
                        }
                        .padding(vertical = 14.dp),
                )
            }
        }
    }
}

/* -------------------------------------------------------------------------- background */

/**
 * Paper ground plus the diagonal light streak.
 *
 * The reference has a bright wedge sweeping from the top-left corner down to the right, with a
 * darker grey triangle beneath it hugging the left edge. Both are gradients — no bitmap, so this
 * costs nothing in APK size.
 */
// drawPaperAndLightStreak() was here. It has been deleted rather than fixed, in three steps, each
// forced by the one before:
//
// 1. It opened with an opaque `drawRect(PaperWarm)` that painted over the app-wide SilkBackdrop, so
//    no change to the backdrop could ever appear on this screen.
// 2. Dropping that fill exposed its grey wedge and beam as a hard-edged triangle sitting on the
//    silk — on a device it read as a rendering artifact, a clean diagonal seam across the top-left
//    corner, not as light. It had only ever looked like a beam because it was drawn on flat paper.
// 3. The wedge and beam existed to keep the page from looking flat, and to put a light source in
//    the top-left. The silk backdrop now does both, with its own top-left highlight. So there was
//    nothing left for this function to do.
//
// Its vignette went too: it darkened by up to 5% at the edges, which is unbounded darkening beneath
// the glass nav bar's text, on top of the floor SilkFolds is supposed to establish.

/**
 * One organic blob: an irregular closed shape filled black and speckled with stars.
 *
 * These are the reference's black starfield masses. Drawn procedurally rather than as PNGs, which
 * is both cheaper and resolution-independent.
 *
 * The [seed] is fixed per blob and the geometry is derived only from it, so the shape and every
 * star position are **stable across recomposition**. A fresh `Random` per frame would make the
 * starfield shimmer on every scroll or state change, which looks like a rendering fault.
 */
private fun DrawScope.drawStarfieldBlob(
    topLeft: Offset,
    blobSize: Size,
    seed: Int,
    starCount: Int,
) {
    val rng = Random(seed)
    val cx = topLeft.x + blobSize.width / 2f
    val cy = topLeft.y + blobSize.height / 2f
    val rx = blobSize.width / 2f
    val ry = blobSize.height / 2f

    // Build the outline from jittered radii around an ellipse, then smooth it by running
    // quadratic segments through the midpoints — the standard trick for a closed organic curve.
    val lobes = 9
    val pts = (0 until lobes).map { i ->
        val a = (i.toFloat() / lobes) * 2f * Math.PI.toFloat()
        val j = 0.80f + rng.nextFloat() * 0.34f
        Offset(cx + cos(a) * rx * j, cy + sin(a) * ry * j)
    }
    val path = Path()
    fun mid(a: Offset, b: Offset) = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
    var prev = mid(pts.last(), pts.first())
    path.moveTo(prev.x, prev.y)
    for (i in pts.indices) {
        val ctrl = pts[i]
        val next = mid(pts[i], pts[(i + 1) % pts.size])
        path.quadraticTo(ctrl.x, ctrl.y, next.x, next.y)
        prev = next
    }
    path.close()
    drawPath(path, Color(0xFF0B0B0B))

    // Stars, sampled inside a shrunken ellipse so none land outside the blob outline.
    repeat(starCount) {
        val a = rng.nextFloat() * 2f * Math.PI.toFloat()
        // sqrt keeps the distribution even rather than clustered at the centre.
        val r = kotlin.math.sqrt(rng.nextFloat()) * 0.78f
        val x = cx + cos(a) * rx * r
        val y = cy + sin(a) * ry * r
        val big = rng.nextFloat() > 0.90f
        val radius = if (big) 1.4.dp.toPx() + rng.nextFloat() * 1.dp.toPx() else
            0.35.dp.toPx() + rng.nextFloat() * 0.8.dp.toPx()
        val alpha = 0.30f + rng.nextFloat() * 0.70f
        drawCircle(Color.White.copy(alpha = alpha), radius, Offset(x, y))
        if (big) {
            // A faint halo on the brightest few, as in the reference's larger stars.
            drawCircle(
                Color.White.copy(alpha = alpha * 0.18f),
                radius * 3.2f,
                Offset(x, y),
            )
        }
    }
}

/**
 * The five blobs, placed at the reference's fractions of the viewport.
 *
 * Left-middle (behind the alien), bottom-left (the moon), top-right (the astronaut), right-middle
 * (Saturn) and a small one bottom-right. All bleed off their nearest edge exactly as the reference
 * does, which is what makes the page feel like a cropped poster rather than a centred card.
 */
@Composable
private fun StarfieldBlobs(w: Dp, h: Dp) {
    Canvas(Modifier.fillMaxSize()) {
        val W = size.width
        val H = size.height

        // Left-middle, bleeding off the left edge.
        drawStarfieldBlob(
            topLeft = Offset(-W * 0.09f, H * 0.24f),
            blobSize = Size(W * 0.21f, H * 0.30f),
            seed = 11,
            starCount = 70,
        )
        // Bottom-left, bleeding off both the left and bottom edges.
        drawStarfieldBlob(
            topLeft = Offset(-W * 0.06f, H * 0.63f),
            blobSize = Size(W * 0.25f, H * 0.48f),
            seed = 23,
            starCount = 110,
        )
        // Top-right, behind the astronaut.
        drawStarfieldBlob(
            topLeft = Offset(W * 0.76f, -H * 0.05f),
            blobSize = Size(W * 0.22f, H * 0.36f),
            seed = 37,
            starCount = 80,
        )
        // Right-middle, behind Saturn.
        drawStarfieldBlob(
            topLeft = Offset(W * 0.80f, H * 0.46f),
            blobSize = Size(W * 0.24f, H * 0.26f),
            seed = 53,
            starCount = 60,
        )
        // Small bottom-right. Moved out from 0.71 w / 0.78 h, where it crossed the tail of the
        // centred body copy and swallowed the last few characters — Ink on #0B0B0B again.
        drawStarfieldBlob(
            topLeft = Offset(W * 0.79f, H * 0.84f),
            blobSize = Size(W * 0.075f, H * 0.13f),
            seed = 71,
            starCount = 22,
        )
    }
}

/**
 * Illustration layer — **currently empty, and that is a missing asset rather than a design choice.**
 *
 * The reference places six hand-inked drawings over the blobs: an astronaut top-right, an alien on
 * the left edge, a UFO bottom-centre-left, Saturn on the right, a cratered moon bottom-left and a
 * telescope bottom-centre. They were PNGs in the original `Landing launch/` folder. That folder has
 * been deleted and the replacement ships no images, so there is nothing to draw and nothing here
 * can invent them.
 *
 * To finish the screen, drop the six files into `app/src/main/res/drawable/` and add one `Image`
 * per slot below at the fractions already commented. The blob positions above are the anchors, so
 * each illustration lands over its own starfield the way the reference has it.
 */
@Composable
private fun SpaceIllustrations(w: Dp, h: Dp) {
    // TODO(assets): re-supply the five remaining illustrations, then place them here. The astronaut
    // no longer needs an asset — see [FloatingAstronaut].
    //   alien      x ≈ 0.00 w, y ≈ 0.28 h, width ≈ 0.075 w
    //   ufo        x ≈ 0.24 w, y ≈ 0.74 h, width ≈ 0.19 w
    //   saturn     x ≈ 0.78 w, y ≈ 0.60 h, width ≈ 0.20 w
    //   moon       x ≈ 0.02 w, y ≈ 0.68 h, width ≈ 0.13 w
    //   telescope  x ≈ 0.56 w, y ≈ 0.82 h, width ≈ 0.15 w
    Box(Modifier.fillMaxSize()) {
        FloatingAstronaut(w, h)
    }
}

/* --------------------------------------------------------------------------- astronaut */

/**
 * Placement arithmetic for the astronaut, kept as plain numbers rather than inlined into the
 * composable so it can be checked by a normal JUnit test. The project has no Compose UI test
 * dependency and this does not justify adding one.
 */
internal object AstronautSlot {
    /**
     * Left edge, as a fraction of viewport width.
     *
     * 0.815, moved right from 0.76 after seeing it on a device: `HEAR MUNDARI` reaches 0.81 w once
     * centred, so at 0.76 the astronaut's raised arm sat on top of the `I`. The headline wins that
     * argument — it is the hero, and the figure is decoration.
     */
    const val LEFT = 0.815f

    /** Top edge, as a fraction of viewport height. */
    const val TOP = 0.40f

    const val WIDTH_OF_WIDTH = 0.165f
    const val WIDTH_OF_HEIGHT = 0.28f

    /** Height ÷ width of the figure, which is drawn in a 100 × 140 design space. */
    const val ASPECT = 1.4f

    /**
     * Box size for a [w] × [h] viewport, in whatever unit is passed in.
     *
     * Taking the **smaller** of a width-derived and a height-derived size is what keeps the figure
     * on screen at any aspect ratio. A width-only rule would overflow the bottom on anything much
     * wider than 16:9: `0.20 w × 1.4` is `0.28 w`, which is `0.65 h` at 21:9, and the slot already
     * starts `0.40 h` down. Tablets are 16:10 or 4:3, but an unfolded foldable or a resized desktop
     * window is not, and this screen is the app's first impression.
     */
    fun size(w: Float, h: Float): Pair<Float, Float> {
        val width = minOf(w * WIDTH_OF_WIDTH, h * WIDTH_OF_HEIGHT)
        return width to width * ASPECT
    }
}

/**
 * The astronaut: drawn procedurally and floated, with no image asset and no 3D engine.
 *
 * Two things were considered and rejected. A real model (SceneView/Filament plus a `.glb`) would
 * add a second native library and continuous GPU work to a decorative element, in an app already
 * managing a 464 MB model payload and sherpa-onnx's 16 KB alignment constraint. A 3D-rendered
 * still would be ~80 KB and fine, but there is no such file in the repo and the original
 * `Landing launch/` illustrations are gone. Drawing it follows what [StarfieldBlobs] and
 * [drawPaperAndLightStreak] already do here: vector, resolution-independent, zero bytes of APK.
 *
 * The depth comes from one radial gradient lit from the top-left — the same direction as the page's
 * beam — and the float from two out-of-phase drifts, a 5.2 s bob and an 8.1 s tilt. The periods are
 * deliberately not multiples of each other; matched periods make the motion read as a mechanical
 * loop rather than drifting in zero gravity.
 *
 * Both animated values are read *inside* the `graphicsLayer` lambda rather than through `by`. That
 * confines each frame's invalidation to the draw phase instead of recomposing and re-laying out the
 * whole slot sixty times a second, which matters on the low-tier tablets §5.4 targets.
 */
@Composable
private fun FloatingAstronaut(w: Dp, h: Dp) {
    val (boxW, boxH) = AstronautSlot.size(w.value, h.value)
    val slot = Modifier
        .offset(x = w * AstronautSlot.LEFT, y = h * AstronautSlot.TOP)
        .size(boxW.dp, boxH.dp)

    // Perpetual motion that a user cannot pause is exactly what WCAG 2.2.2 is about, and drifting
    // motion is a vestibular trigger. Android's global animator scale is the system-wide switch for
    // this ("Remove animations" in accessibility settings sets it to 0), so honour it and draw the
    // figure still. Not a nicety — the alternative is a screen some users cannot look at.
    val scale = LocalContext.current.animatorDurationScale()
    if (scale == 0f) {
        Canvas(slot) { drawAstronaut() }
        return
    }

    val drift = rememberInfiniteTransition(label = "astronaut")
    val bob = drift.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bob",
    )
    val tilt = drift.animateFloat(
        initialValue = -3.5f,
        targetValue = 3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(8100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "tilt",
    )

    Canvas(
        slot.graphicsLayer {
            translationY = bob.value * boxH.dp.toPx() * 0.042f
            rotationZ = tilt.value
        },
    ) {
        drawAstronaut()
    }
}

/** The system-wide animation switch. 0 means the user has asked for animations off. */
private fun Context.animatorDurationScale(): Float =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

/**
 * The figure itself, in a 100 × 140 design space so every coordinate reads as a percentage of its
 * own width and the whole thing scales with the box it is given.
 *
 * Each part is filled and then outlined in ink. The outline is structural rather than stylistic:
 * the slot straddles the edge of the right-middle blob, so the boots and torso sit on `#0B0B0B`
 * while the helmet sits on near-white paper. A white fill with no outline would leave the helmet
 * invisible against the paper, and an ink fill would lose the legs against the blob. Outlining
 * everything is what makes one drawing work over both grounds.
 */
private fun DrawScope.drawAstronaut() {
    val u = size.width / 100f
    fun at(x: Float, y: Float) = Offset(x * u, y * u)

    val ink = Color(0xFF0B0B0B)
    val stroke = Stroke(3.2f * u)

    // Lit from the top-left, matching the page's diagonal beam so the shading agrees with the
    // background instead of contradicting it. This gradient is the entire "3D" of the thing.
    val suit = Brush.radialGradient(
        0.00f to Color(0xFFFFFFFF),
        0.55f to Color(0xFFEDEBE6),
        1.00f to Color(0xFFB4B2AC),
        center = at(34f, 24f),
        radius = 92f * u,
    )
    val hardware = Brush.radialGradient(
        0.0f to Color(0xFFD7D5D0),
        1.0f to Color(0xFF8E8C87),
        center = at(34f, 40f),
        radius = 70f * u,
    )

    fun limb(a: Offset, b: Offset, thickness: Float) {
        drawLine(ink, a, b, strokeWidth = (thickness + 6.4f) * u, cap = StrokeCap.Round)
        drawLine(suit, a, b, strokeWidth = thickness * u, cap = StrokeCap.Round)
    }

    fun panel(x: Float, y: Float, pw: Float, ph: Float, r: Float, brush: Brush) {
        val topLeft = at(x, y)
        val boxSize = Size(pw * u, ph * u)
        val corner = CornerRadius(r * u)
        drawRoundRect(brush, topLeft, boxSize, corner)
        drawRoundRect(ink, topLeft, boxSize, corner, style = stroke)
    }

    // Back to front, so each joint is covered by the part in front of it.
    //
    // Leg ends stop at y 126 / 124 rather than the design space's full 140. A round cap adds half
    // the outlined thickness — (17 + 6.4) / 2 ≈ 11.7 — beyond the point given, so an end at 132
    // puts the sole at 143.7 and the boot is drawn flattened against the bottom edge. Same reason
    // the antenna ball below sits at y 4 and not y 2.
    panel(20f, 52f, 60f, 46f, 13f, hardware)          // life-support pack
    limb(at(41f, 96f), at(33f, 126f), 17f)            // left leg
    limb(at(59f, 96f), at(69f, 124f), 17f)            // right leg
    limb(at(34f, 62f), at(12f, 88f), 14f)             // left arm, down
    limb(at(66f, 62f), at(89f, 70f), 14f)             // right arm, raised
    panel(29f, 50f, 42f, 50f, 15f, suit)              // torso
    panel(40f, 62f, 20f, 15f, 4f, hardware)           // chest control panel

    // Three indicator lights on the panel.
    repeat(3) { i ->
        drawCircle(ink, 1.5f * u, at(44f + i * 6f, 69.5f))
    }

    // Gloves, at the ends of the arms.
    listOf(at(12f, 88f), at(89f, 70f)).forEach { hand ->
        drawCircle(suit, 8.5f * u, hand)
        drawCircle(ink, 8.5f * u, hand, style = stroke)
    }

    // Helmet.
    drawCircle(suit, 25f * u, at(50f, 28f))
    drawCircle(ink, 25f * u, at(50f, 28f), style = stroke)

    // Visor: dark glass with one soft highlight, which is what reads as curvature.
    drawOval(Color(0xFF0E0E0E), at(32f, 12f), Size(36f * u, 30f * u))
    drawOval(
        Brush.radialGradient(
            0f to Color(0x99FFFFFF),
            1f to Color(0x00FFFFFF),
            center = at(41f, 20f),
            radius = 13f * u,
        ),
        at(34f, 14f),
        Size(20f * u, 15f * u),
    )

    // Antenna.
    drawLine(ink, at(32f, 12f), at(26f, 4f), strokeWidth = 2.6f * u, cap = StrokeCap.Round)
    drawCircle(ink, 3f * u, at(26f, 4f))
}
