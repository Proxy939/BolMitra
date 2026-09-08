package org.bolmitra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.bolmitra.phrasebook.Provenance
import org.bolmitra.ui.theme.Approximate
import org.bolmitra.ui.theme.BolmitraColors
import org.bolmitra.ui.theme.Radius
import org.bolmitra.ui.theme.Unavailable
import org.bolmitra.ui.theme.Verified

/**
 * The three pieces the supplied design has no equivalent for.
 *
 * Panels, tiles, buttons and charts now come from `ui/common` — that vocabulary replaced the
 * earlier `PanelCard` / `StatTile` / `ActionTile` set wholesale, and those are gone rather than
 * kept alongside as a second way to draw a card. What is left here is what the supplied design did
 * not cover, because it was designed for a generic dashboard and these are specific to translating
 * for children.
 */

/**
 * Provenance chip — §4.5's requirement that a teacher always knows whether to trust a line.
 *
 * Carries a **word and a glyph** as well as colour. This is the one signal in the app that must
 * never be ambiguous: it is the difference between native-speaker-verified content and machine
 * output in front of a class, so colour alone — which fails in glare and fails for a colour-blind
 * teacher — is not enough.
 *
 * These are also the only hues in an otherwise monochrome system. That is deliberate: the single
 * most important signal gets the only colour.
 */
@Composable
fun ProvenanceChip(provenance: Provenance, modifier: Modifier = Modifier) {
    val (bg, label, glyph) = when (provenance) {
        Provenance.VERIFIED -> Triple(Verified, "Verified", "\u2713")
        Provenance.APPROXIMATE -> Triple(Approximate, "Approximate", "\u2248")
        Provenance.MACHINE -> Triple(Unavailable, "Machine", "\u26A0")
    }
    Row(
        modifier
            .background(bg, Radius.pill)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(glyph, style = MaterialTheme.typography.labelMedium, color = Color.White)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Label/value row. Used wherever a figure needs its name next to it. */
@Composable
fun DataRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 34.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = BolmitraColors.InkMuted,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/**
 * Amber warning strip.
 *
 * Exists because of a specific standing hazard: every Mundari string in this build is a
 * placeholder (`DemoSeed.DEMO_STRINGS_VERIFIED = false`) and the voice reads Odia rather than
 * Devanagari internally (V63). Nobody should watch BolMitra speak and assume the content has been
 * reviewed.
 *
 * A visible border, not just a tint — a 12% amber wash on a near-white paper background is easy to
 * miss outdoors, and this is the one banner that must not be missed.
 */
@Composable
fun WarningBanner(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Approximate.copy(alpha = 0.10f), Radius.md)
            .border(1.dp, Approximate.copy(alpha = 0.45f), Radius.md)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(22.dp).background(Approximate, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("!", style = MaterialTheme.typography.labelMedium, color = Color.White)
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Approximate)
    }
}
