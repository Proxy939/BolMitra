package org.bolmitra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.bolmitra.phrasebook.Provenance
import org.bolmitra.speech.TargetLanguage
import org.bolmitra.ui.common.KidsBannerArt
import org.bolmitra.ui.common.OlChikiFont
import org.bolmitra.ui.theme.Approximate
import org.bolmitra.ui.theme.BolmitraColors
import org.bolmitra.ui.theme.Corpus
import org.bolmitra.ui.theme.Radius
import org.bolmitra.ui.theme.Unavailable
import org.bolmitra.ui.theme.Verified

/**
 * Top illustrated header banner card matching the reference screens.
 */
@Composable
fun BannerHeaderCard(
    icon: ImageVector,
    title: String,
    subtitleEn: String,
    subtitleHi: String,
    badgeLines: List<String>,
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFFE8F7ED),
                        Color(0xFFF1FAF4),
                        Color(0xFFF6FBF7),
                    ),
                ),
                RoundedCornerShape(16.dp),
            )
            .border(1.dp, Color(0xFFD6EFE0), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Icon in white circle badge
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(Color.White, CircleShape)
                    .border(1.dp, Color(0xFFD6EFE0), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF2EAF3B),
                    modifier = Modifier.size(26.dp),
                )
            }

            Spacer(Modifier.width(14.dp))

            // Title and bilingual subtitles
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = Color(0xFF14381C),
                    ),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitleEn,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.5.sp,
                        color = Color(0xFF374151),
                    ),
                )
                Text(
                    text = subtitleHi,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.5.sp,
                        color = Color(0xFF4B5563),
                    ),
                )
            }

            // Kids Banner Illustration on the right
            KidsBannerArt(
                badgeTextLines = badgeLines,
                modifier = Modifier.size(width = 240.dp, height = 75.dp),
            )

            Spacer(Modifier.width(12.dp))

            // Offline Mode Pill & Settings Gear
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Offline Pill
                Row(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(14.dp))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(7.dp).background(Color(0xFF22C55E), CircleShape))
                    Column {
                        Text(
                            "Offline Mode",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp,
                                color = Color(0xFF1E293B),
                            ),
                        )
                        Text(
                            "All features work without internet",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.5.sp,
                                color = Color(0xFF64748B),
                            ),
                        )
                    }
                }

                // Settings button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.White.copy(alpha = 0.85f), CircleShape)
                        .border(1.dp, Color(0xFFE2E8F0), CircleShape)
                        .clickable(onClick = onSettingsClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = Color(0xFF475569),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/**
 * Standard language selector bar matching the reference screens.
 */
@Composable
fun LanguageSelectorBar(
    currentLanguage: TargetLanguage,
    onSelectLanguage: (TargetLanguage) -> Unit,
    label: String = "Target Language (Child's Language)",
    trailingContent: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = Color(0xFF334155),
            ),
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Mundari
            LanguagePill(
                title = "Mundari",
                nativeScript = "मुंडारी",
                isSelected = currentLanguage == TargetLanguage.MUNDARI,
                dotColor = Color(0xFF2EAF3B),
                onClick = { onSelectLanguage(TargetLanguage.MUNDARI) },
            )

            Spacer(Modifier.width(8.dp))

            // Santali
            LanguagePill(
                title = "Santali",
                nativeScript = "ᱥᱟᱱᱛᱟᱲᱤ",
                isSelected = currentLanguage == TargetLanguage.SANTALI,
                dotColor = Color(0xFFF97316),
                onClick = { onSelectLanguage(TargetLanguage.SANTALI) },
            )

            Spacer(Modifier.width(8.dp))

            // Ho
            LanguagePill(
                title = "Ho",
                nativeScript = "हो",
                isSelected = currentLanguage == TargetLanguage.HO,
                dotColor = Color(0xFFF97316),
                onClick = { onSelectLanguage(TargetLanguage.HO) },
            )

            Spacer(Modifier.width(12.dp))

            trailingContent()
        }
    }
}

@Composable
private fun LanguagePill(
    title: String,
    nativeScript: String,
    isSelected: Boolean,
    dotColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .background(
                if (isSelected) Color(0xFF2EAF3B) else Color.White,
                RoundedCornerShape(12.dp),
            )
            .border(
                1.dp,
                if (isSelected) Color(0xFF259B32) else Color(0xFFE2E8F0),
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier.size(16.dp).background(Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color(0xFF2EAF3B),
                    modifier = Modifier.size(12.dp),
                )
            }
        } else {
            Box(Modifier.size(8.dp).background(dotColor, CircleShape))
        }

        Column {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (isSelected) Color.White else Color(0xFF1E293B),
                ),
            )
            Text(
                // Santali's endonym is Ol Chiki, so it goes through the bundled face like every
                // other Ol Chiki string. Mundari and Ho are Devanagari and get no span.
                OlChikiFont.annotate(nativeScript),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    color = if (isSelected) Color.White.copy(alpha = 0.9f) else Color(0xFF64748B),
                ),
            )
        }
    }
}

/**
 * Bottom tip footer bar.
 */
@Composable
fun FooterTipBar(
    tipText: String,
    actionText: String = "Aligned with NIPUN Bharat FLN outcomes →",
    onActionClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false),
        ) {
            Text("💡 ", fontSize = 14.sp)
            Text(
                text = "Tip: ",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    color = Color(0xFF1E293B),
                ),
            )
            Text(
                text = tipText,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.5.sp,
                    color = Color(0xFF475569),
                ),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = onActionClick),
        ) {
            Text("📖 ", fontSize = 13.sp)
            Text(
                text = actionText,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp,
                    color = Color(0xFF1E293B),
                ),
            )
        }
    }
}

/**
 * Provenance chip — §4.5's requirement that a teacher always knows whether to trust a line.
 */
@Composable
fun ProvenanceChip(provenance: Provenance, modifier: Modifier = Modifier) {
    val (bg, label, glyph) = when (provenance) {
        Provenance.VERIFIED -> Triple(Verified, "Verified", "\u2713")
        // A quotation mark, because that is exactly the claim: this string is quoted from a
        // published source, not reviewed for this classroom. Distinct glyph, word and hue from
        // "Approximate", which is a claim about our matcher rather than about the text's origin.
        Provenance.CORPUS -> Triple(Corpus, "From corpus", "\u201C")
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
