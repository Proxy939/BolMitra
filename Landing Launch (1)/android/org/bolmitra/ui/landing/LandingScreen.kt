package org.bolmitra.ui.landing

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.bolmitra.ui.common.GlassCard
import org.bolmitra.ui.common.InkCard
import org.bolmitra.ui.common.SectionLabel
import org.bolmitra.ui.theme.BolmitraColors
import org.bolmitra.ui.theme.BolmitraTheme
import org.bolmitra.ui.theme.SilkBackdrop

private data class Capability(val title: String, val body: String)

private val capabilities = listOf(
    Capability("Speak, don't type", "Streaming speech recognition runs on the device — no server, no queue."),
    Capability("Phrasebook first", "Full-text search finds a known phrase before the model is ever asked."),
    Capability("Reads it back", "On-device voices speak the result so the other person just listens."),
    Capability("Works with no signal", "Every model ships with the app. Airplane mode changes nothing."),
)

/**
 * Pre-login landing screen. Same monochrome glass system as the dashboard so
 * the transition after sign-in feels continuous.
 */
@Composable
fun LandingScreen(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SilkBackdrop(modifier = modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 840.dp
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = if (wide) 40.dp else 20.dp, vertical = 22.dp),
            ) {
                Brandbar()
                Spacer(Modifier.height(if (wide) 48.dp else 32.dp))

                if (wide) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                    ) {
                        Hero(onGetStarted, Modifier.weight(1.1f))
                        MicPanel(Modifier.weight(0.9f))
                    }
                } else {
                    Hero(onGetStarted, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(24.dp))
                    MicPanel(Modifier.fillMaxWidth())
                }

                Spacer(Modifier.height(if (wide) 44.dp else 30.dp))
                CapabilityGrid(wide)
                Spacer(Modifier.height(28.dp))
                OfflineStrip()
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun Brandbar() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(30.dp).background(BolmitraColors.Ink, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("//", style = MaterialTheme.typography.labelSmall, color = BolmitraColors.OnInk)
        }
        Spacer(Modifier.width(10.dp))
        Text("Bolmitra", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.weight(1f))
        Row(
            Modifier
                .background(BolmitraColors.Glass, RoundedCornerShape(50))
                .border(1.dp, BolmitraColors.HairlineOnPaper, RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Lock, null, tint = BolmitraColors.Ink, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text("On-device", style = MaterialTheme.typography.labelSmall, color = BolmitraColors.Ink)
        }
    }
}

@Composable
private fun Hero(onGetStarted: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        SectionLabel("Mundari · Odia · Hindi")
        Spacer(Modifier.height(14.dp))
        Text(
            "Talk across\nlanguages,\noffline.",
            style = MaterialTheme.typography.displayLarge,
            color = BolmitraColors.Ink,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            "Bolmitra listens, understands and speaks back entirely on your phone. " +
                "Nothing you say leaves the device.",
            style = MaterialTheme.typography.bodyMedium,
            color = BolmitraColors.InkMuted,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        Spacer(Modifier.height(26.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .clickable(onClick = onGetStarted)
                    .background(BolmitraColors.Ink, RoundedCornerShape(50))
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Start a conversation",
                    style = MaterialTheme.typography.labelLarge,
                    color = BolmitraColors.OnInk,
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Filled.ArrowForward,
                    null,
                    tint = BolmitraColors.OnInk,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Row(
                Modifier
                    .border(1.dp, BolmitraColors.InkMuted.copy(alpha = 0.45f), RoundedCornerShape(50))
                    .padding(horizontal = 18.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    null,
                    tint = BolmitraColors.Ink,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text("Hear a sample", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Big mic disc with a breathing halo — the listening metaphor, not decoration. */
@Composable
private fun MicPanel(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "halo")
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "halo-scale",
    )

    GlassCard(modifier = modifier, contentPadding = 24.dp) {
        Box(
            Modifier.fillMaxWidth().height(230.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(190.dp)
                    .scale(pulse)
                    .background(BolmitraColors.HairlineOnPaper, CircleShape)
            )
            Box(
                Modifier
                    .size(140.dp)
                    .scale(pulse)
                    .border(1.dp, BolmitraColors.InkMuted.copy(alpha = 0.35f), CircleShape)
            )
            Box(
                Modifier.size(96.dp).background(BolmitraColors.Ink, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "\u25CF",
                    style = MaterialTheme.typography.displayMedium,
                    color = BolmitraColors.OnInk,
                )
            }
        }
        Text(
            "Tap once. Speak naturally. Bolmitra handles the rest.",
            style = MaterialTheme.typography.bodySmall,
            color = BolmitraColors.InkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CapabilityGrid(wide: Boolean) {
    val perRow = if (wide) 2 else 1
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        capabilities.chunked(perRow).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                rowItems.forEach { item ->
                    GlassCard(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(22.dp).background(BolmitraColors.Ink, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Check,
                                    null,
                                    tint = BolmitraColors.OnInk,
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            item.body,
                            style = MaterialTheme.typography.bodySmall,
                            color = BolmitraColors.InkMuted,
                        )
                    }
                }
                if (rowItems.size < perRow) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun OfflineStrip() {
    InkCard(contentPadding = 22.dp) {
        Text(
            "No account, no upload, no waiting.",
            style = MaterialTheme.typography.headlineMedium,
            color = BolmitraColors.OnInk,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Speech models live on the device and are verified at install time. " +
                "Bolmitra keeps working in a classroom with no bars.",
            style = MaterialTheme.typography.bodySmall,
            color = BolmitraColors.OnInkMuted,
        )
    }
}

@Preview(widthDp = 1024, heightDp = 768, showBackground = true)
@Composable
private fun LandingTabletPreview() {
    BolmitraTheme { LandingScreen(onGetStarted = {}) }
}

@Preview(widthDp = 412, heightDp = 900, showBackground = true)
@Composable
private fun LandingPhonePreview() {
    BolmitraTheme { LandingScreen(onGetStarted = {}) }
}
