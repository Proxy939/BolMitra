package org.bolmitra.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.bolmitra.ui.theme.BolmitraColors
import org.bolmitra.ui.theme.Dimens
import org.bolmitra.ui.theme.Radius

/**
 * The frosted-panel vocabulary from the supplied design, as-is except for the card edge.
 *
 * The hairline carries the edge on its own. An earlier revision kept a low shadow alongside it on the
 * grounds that shadow reads as glass indoors while the outline survives glare outdoors — reasonable,
 * but not available: see [GlassCard] for why a translucent surface cannot have an elevation shadow.
 * [InkCard] is opaque and keeps its lift.
 */

/**
 * Frosted panel: translucent fill, visible hairline, soft lift.
 *
 * ### The container fill is `Transparent`, and the gradient is on the Card
 *
 * This once set `containerColor = Glass` *and* drew a `Glass → GlassSoft` gradient in a `Column`
 * inside it, so every pixel was glass over glass. Two ~60% layers composite to ~84%, which is why
 * the cards read as solid white, and it left the true alpha unstateable — so the bound in
 * [org.bolmitra.ui.theme.GlassAlpha] could not have been proved against it. The gradient is now the
 * only fill and it is the alpha the bound uses.
 *
 * It also has to be on the **Card**, not on an inner `Column`. A Column wraps its content, so on any
 * card stretched past its own content — every card sharing a `Row` with a taller sibling — the fill
 * stopped short and bare backdrop resumed at a hard edge. `fillMaxSize()` on that Column does not
 * help, because the Card's height derives from the Column: asking it to fill its parent is circular
 * and it just wraps again.
 *
 * ### There is no shadow, and there cannot be one
 *
 * A translucent surface cannot carry an elevation shadow on Android. The platform draws the shadow
 * as a full blurred silhouette of the outline and only punches out the interior when it knows the
 * layer is opaque; at ~55% alpha it does not, so the shadow shows *through* the panel — densest near
 * the rim, which reads as a dark band just inside every card edge, roughly `contentPadding` wide,
 * like a second inset panel.
 *
 * Measured on a device, sampling one row across a card:
 *
 * | Configuration | Rim | Middle |
 * |---|---|---|
 * | `Card(elevation = 5.dp)` | ~196 | 247 |
 * | `Modifier.shadow` before the background | ~228 | 247 |
 * | no shadow at all | 247 | 247 |
 *
 * Reordering only dilutes it, because the bleed is beneath the fill rather than over it. So the lift
 * is gone and the hairline carries the edge alone — which is what §6.13 asks for anyway, since an
 * outline survives glare where a shadow does not. This is also why glassmorphism designs generally
 * use borders rather than elevation, the reference image included.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = Radius.lg,
    contentPadding: Dp = Dimens.cardPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    0f to BolmitraColors.Glass,
                    1f to BolmitraColors.GlassSoft,
                ),
                shape,
            ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, BolmitraColors.GlassStroke),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(contentPadding), content = content)
    }
}

/**
 * The outer pane the reference design sits everything else on: one large, very thin sheet of glass
 * inset from the screen edges, with the backdrop reading around and through it.
 *
 * Thinner than a [GlassCard] by design. It is a ground for other panels rather than a panel itself,
 * so it has to stay subordinate — at card alpha the nested cards would vanish into it and the whole
 * depth ordering would collapse into one grey mass.
 *
 * It holds no text of its own, which is why its alpha is allowed below the level
 * [org.bolmitra.ui.theme.GlassAlpha] bounds. Anything that *does* carry text sits in a card on top
 * of it, and that card's own fill is what the bound covers.
 */
@Composable
fun GlassSheet(
    modifier: Modifier = Modifier,
    shape: Shape = Radius.xl,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .background(
                Brush.linearGradient(
                    0.0f to Color.White.copy(alpha = 0.34f),
                    0.5f to Color.White.copy(alpha = 0.20f),
                    1.0f to Color.White.copy(alpha = 0.30f),
                ),
                shape,
            )
            .border(1.dp, Color.White.copy(alpha = 0.55f), shape),
        content = content,
    )
}

/** Inverted emphasis panel: paper text on ink. */
@Composable
fun InkCard(
    modifier: Modifier = Modifier,
    shape: Shape = Radius.lg,
    contentPadding: Dp = Dimens.cardPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = shape,
        // The brand green, not the dark ink. This is the app's one high-emphasis card, so it is where
        // the green should be loudest. Its content must be [BolmitraColors.OnLeaf] /
        // [BolmitraColors.OnLeafMuted] — light text on this fill measures 1.96:1.
        colors = CardDefaults.cardColors(containerColor = BolmitraColors.Leaf),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/** Small wide-tracked caption used for section chrome. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = BolmitraColors.InkMuted,
        modifier = modifier,
    )
}

/**
 * Pill button, ink-filled or outlined.
 *
 * Exists because the supplied design built its buttons as bare `Row`s with `clickable` and
 * horizontal padding, which produced ~44 dp targets and no `Role.Button` semantics. Same look,
 * with the 48 dp floor and a name TalkBack can announce.
 */
@Composable
fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    filled: Boolean = true,
    enabled: Boolean = true,
) {
    val fg = when {
        !enabled -> BolmitraColors.InkMuted
        // Filled buttons are the brand green now, so their label is DARK, not light. Keeping OnInk
        // here would have put 1.96:1 text on every primary action in the app.
        filled -> BolmitraColors.OnLeaf
        else -> BolmitraColors.Ink
    }
    Row(
        modifier
            .heightIn(min = Dimens.minTouchTarget)
            .then(
                if (filled) {
                    Modifier.background(
                        if (enabled) BolmitraColors.Leaf else BolmitraColors.GlassStroke,
                        Radius.pill,
                    )
                } else {
                    Modifier.border(1.dp, BolmitraColors.InkMuted, Radius.pill)
                },
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
        if (icon != null) {
            Spacer(Modifier.width(8.dp))
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * Circular icon action at the 48 dp floor.
 *
 * [label] is mandatory and becomes the content description. The supplied design passed
 * `contentDescription = null` on interactive share, refresh and edit icons, which leaves them
 * unreachable by TalkBack — for an icon-only control the label is the only affordance there is.
 */
@Composable
fun CircleAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(Dimens.minTouchTarget)
            // Orange fill with the dark icon on it, 5.62:1. This is the only always-present control
            // in the top bar, so it is worth an accent; on glass it read as another piece of chrome.
            .background(BolmitraColors.Ember, CircleShape)
            .border(1.dp, BolmitraColors.Ink.copy(alpha = 0.25f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = BolmitraColors.Ink, modifier = Modifier.size(20.dp))
    }
}

/** Status pill for the brandbar — a filled dot plus a word, never colour alone. */
@Composable
fun StatusPill(
    text: String,
    dotColor: androidx.compose.ui.graphics.Color = BolmitraColors.Verified,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .background(BolmitraColors.Glass, Radius.pill)
            .border(1.dp, BolmitraColors.GlassStroke, Radius.pill)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(9.dp).background(dotColor, CircleShape))
        Text(text, style = MaterialTheme.typography.labelMedium, color = BolmitraColors.Ink)
    }
}

/** Section header with an optional trailing caption, as in the reference's "Last Projects" row. */
@Composable
fun RowSectionHeader(
    title: String,
    trailing: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.heightIn(min = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (trailing != null) SectionLabel(trailing)
    }
}

/** Fills leftover row space so a partially-filled grid row keeps its column widths. */
@Composable
fun RowScope.GridFiller(count: Int) {
    repeat(count) { Spacer(Modifier.weight(1f)) }
}
