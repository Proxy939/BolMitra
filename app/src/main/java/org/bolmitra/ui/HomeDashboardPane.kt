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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import org.bolmitra.ui.common.BolMitraIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.bolmitra.device.DeviceSpec
import org.bolmitra.device.DeviceTier
import org.bolmitra.phrasebook.DemoSeed
import org.bolmitra.speech.ModelStore

/**
 * Home Dashboard Screen — Exact replica of Image 2.
 */
@Composable
fun HomeDashboardPane(
    spec: DeviceSpec,
    tier: DeviceTier,
    store: ModelStore,
    wide: Boolean,
    micGranted: Boolean,
    onNavigate: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val inventory = remember { store.inventory() }
    val presentModels = remember(inventory) { inventory.count { it.present } }
    val phrases = remember { DemoSeed.phrases }
    val verifiedCount = remember(phrases) { phrases.count { it.verifiedBy != null } }
    val lakshyaCount = remember(phrases) { phrases.mapNotNull { it.lakshyaCode }.distinct().size }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 1. Top Greeting Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: Hindi greeting
            Column {
                Text(
                    text = "नमस्ते, शिक्षक जी",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 30.sp,
                        color = Color(0xFF14381C),
                    ),
                )
                Text(
                    text = "आज की कक्षा के लिए तैयार हैं?",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        color = Color(0xFF4B5563),
                    ),
                )
            }

            // Center-Right: Quote Card
            Box(
                modifier = Modifier
                    .background(Color(0xFFF1F8F1), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFD6EFE0), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("📖", fontSize = 18.sp)
                    Column {
                        Text(
                            "मातृभाषा में सीखना, उज्जवल भविष्य की नींव है।",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.5.sp,
                                color = Color(0xFF1E293B),
                            ),
                        )
                        Text(
                            "हर बच्चे की भाषा, हर बच्चे का अधिकार।",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                color = Color(0xFF64748B),
                            ),
                        )
                    }
                }
            }

            // Far Right: Offline Pill & Settings Gear
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .background(Color.White, RoundedCornerShape(14.dp))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(8.dp).background(Color(0xFF22C55E), CircleShape))
                    Column {
                        Text(
                            "Offline",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color(0xFF1E293B),
                            ),
                        )
                        Text(
                            "pack demo-v0",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                color = Color(0xFF64748B),
                            ),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFFF97316), CircleShape)
                        .clickable { onNavigate(Destination.DIAGNOSTICS) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        // 2. Row of Two Cards: Big Green Hero ("Start Live Class") + Today's Teaching
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Big Green Hero Card (weight 1.8f)
            Box(
                modifier = Modifier
                    .weight(1.8f)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF22C55E),
                                Color(0xFF16A34A),
                            ),
                        ),
                        RoundedCornerShape(18.dp),
                    )
                    .padding(20.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            // Sub-pill
                            Box(
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.20f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    "हिंदी → मुंडारी",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                    ),
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Start Live Class",
                                style = MaterialTheme.typography.displayMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 28.sp,
                                    color = Color.White,
                                ),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Speak Hindi • Hear Mundari",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 15.sp,
                                    color = Color.White.copy(alpha = 0.92f),
                                ),
                            )
                            Text(
                                "बोलिए हिंदी में, बच्चे सुनेंगे मुंडारी में",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 13.5.sp,
                                    color = Color.White.copy(alpha = 0.95f),
                                ),
                            )
                        }

                        // Glowing white circular mic button
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { onNavigate(Destination.LIVE) },
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .background(Color.White.copy(alpha = 0.25f), CircleShape)
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .background(Color.White, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        BolMitraIcons.Mic,
                                        contentDescription = "Start Live Class",
                                        tint = Color(0xFF16A34A),
                                        modifier = Modifier.size(30.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Tap to speak",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                ),
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    // Bottom Pills Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        HeroFeaturePill("📶", "Offline ready", "Works without internet", Modifier.weight(1f))
                        HeroFeaturePill("📖", "Phrasebook-first", "Fast & reliable", Modifier.weight(1f))
                        HeroFeaturePill("🛡", "Teacher-friendly", "Built for primary schools", Modifier.weight(1f))
                    }
                }
            }

            // Today's Teaching (NIPUN Bharat) Card (weight 1.0f)
            Box(
                modifier = Modifier
                    .weight(1.0f)
                    .background(Color.White, RoundedCornerShape(18.dp))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(18.dp))
                    .padding(18.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("🎯", fontSize = 16.sp)
                            Text(
                                "Today's Teaching (NIPUN Bharat)",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF1E293B),
                                ),
                            )
                        }
                        Text(
                            "demo-v0",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                color = Color(0xFF2563EB),
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // Two Metrics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "11",
                                style = MaterialTheme.typography.displayMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 32.sp,
                                    color = Color(0xFF0F172A),
                                ),
                            )
                            Text(
                                "Hindi phrases",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = Color(0xFF1E293B)),
                            )
                            Text(
                                "ready to speak",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Color(0xFF64748B)),
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "6",
                                style = MaterialTheme.typography.displayMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 32.sp,
                                    color = Color(0xFF0F172A),
                                ),
                            )
                            Text(
                                "NIPUN lakshya",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, color = Color(0xFF1E293B)),
                            )
                            Text(
                                "codes covered",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Color(0xFF64748B)),
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // FLN Outcomes banner
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("📊", fontSize = 16.sp)
                        Column {
                            Text(
                                "FLN-aligned content for early grades",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF1E293B)),
                            )
                            Text(
                                "Classroom instructions, numbers, stories and more.",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, color = Color(0xFF64748B)),
                            )
                        }
                    }
                }
            }
        }

        // 3. Quick Actions + Teaching Readiness + Offline Content Pack
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Left Column: Quick Actions + Offline Content Pack (weight 1.8f)
            Column(
                modifier = Modifier.weight(1.8f),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Quick Actions
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Quick Actions",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF1E293B),
                        ),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Live class card
                        QuickActionCard(
                            emoji = "🎙",
                            iconBg = Color(0xFFDCFCE7),
                            title = "Live class",
                            subtitle = "Speak Hindi, hear Mundari, Santali or Ho",
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigate(Destination.LIVE) },
                        )
                        // Worksheets card
                        QuickActionCard(
                            emoji = "📄",
                            iconBg = Color(0xFFFFEDD5),
                            title = "Worksheets",
                            subtitle = "Bilingual sheets from NIPUN codes",
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigate(Destination.WORKSHEETS) },
                        )
                        // Phrasebook card
                        QuickActionCard(
                            emoji = "📖",
                            iconBg = Color(0xFFDCFCE7),
                            title = "Phrasebook",
                            subtitle = "11 classroom phrases",
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigate(Destination.PHRASEBOOK) },
                        )
                    }
                }

                // Offline Content Pack Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text("🗄", fontSize = 16.sp)
                                Text(
                                    "Offline Content Pack",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B)),
                                )
                            }
                            Text("demo-v0", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, color = Color(0xFF64748B)))
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Stats
                            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                Column {
                                    Text("11/11", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF0F172A)))
                                    Text("Model files", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Color(0xFF64748B)))
                                }
                                Column {
                                    Text("781 MB", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF0F172A)))
                                    Text("On storage", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Color(0xFF64748B)))
                                }
                                Column {
                                    Text("roomy", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF0F172A)))
                                    Text("%.1f GiB • %d cores".format(spec.totalRamGiB, spec.cpuCores), style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Color(0xFF64748B)))
                                }
                            }

                            // Available Offline Status
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFF1F8F1), RoundedCornerShape(10.dp))
                                    .border(1.dp, Color(0xFFD6EFE0), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Box(
                                        modifier = Modifier.size(16.dp).background(Color(0xFF16A34A), CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(Icons.Filled.Check, null, Modifier.size(12.dp), tint = Color.White)
                                    }
                                    Column {
                                        Text("Available offline", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF166534)))
                                        Text("Ready to use", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = Color(0xFF16A34A)))
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Text("Last synced: available offline", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = Color(0xFF94A3B8)))
                    }
                }
            }

            // Right Column: Teaching Readiness (5/6) (weight 1.0f)
            Box(
                modifier = Modifier
                    .weight(1.0f)
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Teaching Readiness",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B)),
                        )
                        Text(
                            "5/6",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF475569)),
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    ReadinessItem("Hindi ASR model present", isReady = true)
                    ReadinessItem("Mundari voice present", isReady = true)
                    ReadinessItem("Phrasebook lookup working", isReady = true)
                    ReadinessItem("Content pack loaded", isReady = true)
                    ReadinessItem("Native speaker review\n0 verified, 11 awaiting review", isReady = false, isWarning = true, tag = "Review needed")
                    ReadinessItem("Device ready (storage, memory)", isReady = true)

                    Spacer(Modifier.height(10.dp))

                    // Need help? Print phrase sheet
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFAFAFA), RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("🧩", fontSize = 14.sp)
                            Column {
                                Text("Need help?", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF1E293B)))
                                Text("If voice is not clear or a phrase is missing, use printable materials.", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = Color(0xFF64748B)))
                            }
                        }

                        // Print button
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFFFEDD5), RoundedCornerShape(8.dp))
                                .clickable { onNavigate(Destination.PHRASEBOOK) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("📄 Print phrase sheet", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.5.sp, color = Color(0xFFC2410C)))
                                Text("PDF / QR worksheet", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, color = Color(0xFFEA580C)))
                            }
                        }
                    }
                }
            }
        }

        // 4. Footer Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Info, null, Modifier.size(14.dp), tint = Color(0xFF475569))
                Text(
                    "Translation labels: ",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, color = Color(0xFF475569)),
                )
                LegendDot(Color(0xFF16A34A), "Verified (by native speaker)")
                LegendDot(Color(0xFFF59E0B), "Machine (AI translated)")
                LegendDot(Color(0xFFDC2626), "Approximate (needs review)")
            }

            Text(
                "Phrasebook-first path is the safest and fastest for classroom use.",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, color = Color(0xFF64748B)),
            )
        }
    }
}

@Composable
private fun HeroFeaturePill(emoji: String, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(emoji, fontSize = 14.sp)
            Column {
                Text(title, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White, fontSize = 11.sp))
                Text(subtitle, style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp))
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    emoji: String,
    iconBg: Color,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .background(Color.White, RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier.size(36.dp).background(iconBg, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(emoji, fontSize = 16.sp)
                }
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Color(0xFF1E293B)))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp, color = Color(0xFF64748B)), maxLines = 1)
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(16.dp), tint = Color(0xFF94A3B8))
        }
    }
}

@Composable
private fun ReadinessItem(
    title: String,
    isReady: Boolean,
    isWarning: Boolean = false,
    tag: String = "Ready",
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(
                        when {
                            isReady -> Color(0xFF16A34A)
                            isWarning -> Color(0xFFF97316)
                            else -> Color(0xFFEF4444)
                        },
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isReady) {
                    Icon(Icons.Filled.Check, null, Modifier.size(11.dp), tint = Color.White)
                } else {
                    Text("!", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.5.sp,
                    color = Color(0xFF334155),
                ),
            )
        }

        Text(
            tag,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = when {
                    isReady -> Color(0xFF16A34A)
                    isWarning -> Color(0xFFEA580C)
                    else -> Color(0xFFDC2626)
                },
            ),
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, color = Color(0xFF475569)))
    }
}
