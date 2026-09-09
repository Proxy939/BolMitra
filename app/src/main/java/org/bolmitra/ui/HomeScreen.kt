package org.bolmitra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import org.bolmitra.ui.common.BolMitraIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.bolmitra.device.DeviceSpec
import org.bolmitra.device.DeviceTier
import org.bolmitra.phrasebook.DemoSeed
import org.bolmitra.speech.ModelStore
import org.bolmitra.speech.TargetLanguage
import org.bolmitra.ui.common.BolMitraEmblem
import org.bolmitra.ui.theme.Dimens

/**
 * Destinations matching the 5 main tablet screens.
 */
enum class Destination(val icon: ImageVector, val label: String) {
    HOME(Icons.Filled.Home, "Home"),
    LIVE(BolMitraIcons.Mic, "Live class"),
    WORKSHEETS(Icons.AutoMirrored.Filled.List, "Worksheets"),
    PHRASEBOOK(Icons.Filled.Search, "Phrasebook"),
    DIAGNOSTICS(Icons.Filled.Build, "Diagnostics"),
    SETTINGS(Icons.Filled.Settings, "Settings"),
}

/**
 * BolMitra Main App Shell with Sidebar Rail and full responsive tablet panes.
 */
@Composable
fun HomeScreen(
    spec: DeviceSpec,
    tier: DeviceTier,
    store: ModelStore,
    start: Destination = Destination.HOME,
    micGranted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var current by remember { mutableStateOf(start) }
    var language by remember { mutableStateOf(TargetLanguage.DEFAULT) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF6F8F5)),
    ) {
        val wide = maxWidth >= Dimens.wideBreakpoint

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Left Sidebar Rail
            if (wide) {
                SidebarRail(
                    current = current,
                    onSelect = { current = it },
                    language = language,
                    modifier = Modifier
                        .width(234.dp)
                        .fillMaxHeight(),
                )
            }

            // Main Content Area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            ) {
                when (current) {
                    Destination.HOME -> HomeDashboardPane(
                        spec = spec,
                        tier = tier,
                        store = store,
                        wide = wide,
                        micGranted = micGranted,
                        onNavigate = { current = it },
                    )
                    Destination.LIVE -> LiveClassPane(
                        wide = wide,
                        micGranted = micGranted,
                        language = language,
                        onLanguageChange = { language = it },
                    )
                    Destination.WORKSHEETS -> WorksheetsScreenPane(
                        wide = wide,
                    )
                    Destination.PHRASEBOOK -> PhrasebookScreenPane()
                    Destination.DIAGNOSTICS -> DiagnosticsScreenPane(
                        spec = spec,
                        tier = tier,
                    )
                    Destination.SETTINGS -> SettingsScreenPane(
                        spec = spec,
                        tier = tier,
                        wide = wide,
                    )
                }
            }
        }
    }
}

/**
 * Left Navigation Sidebar Rail matching the exact replica designs.
 */
@Composable
private fun SidebarRail(
    current: Destination,
    onSelect: (Destination) -> Unit,
    language: TargetLanguage,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(Color.White, RoundedCornerShape(18.dp))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(18.dp))
            .padding(14.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                // BolMitra Brand Logo + Title + Hindi Tagline
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BolMitraEmblem(Modifier.size(34.dp))
                    Column {
                        Text(
                            "BolMitra",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 21.sp,
                                color = Color(0xFF14381C),
                            ),
                        )
                        Text(
                            "बोलो • सिखाओ • साथ बढ़ो",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                color = Color(0xFF64748B),
                            ),
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Navigation Items
                Destination.entries.forEach { d ->
                    val isSelected = d == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .background(
                                if (isSelected) Color(0xFF2EAF3B) else Color.Transparent,
                                RoundedCornerShape(12.dp),
                            )
                            .clickable(role = Role.Tab) { onSelect(d) }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = d.icon,
                            contentDescription = d.label,
                            tint = if (isSelected) Color.White else Color(0xFF475569),
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = d.label,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp,
                                color = if (isSelected) Color.White else Color(0xFF334155),
                            ),
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))

                // LANGUAGE PACK section
                Text(
                    "LANGUAGE PACK",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 0.8.sp,
                        color = Color(0xFF94A3B8),
                    ),
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "हिन्दी  →  ${language.endonym}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF1E293B),
                        ),
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    language.script.displayName +
                        if (DemoSeed.phrasesFor(language).isEmpty()) " • no pack" else " • pack demo-v0",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                    ),
                )
            }

            // Bottom Motivational Quote Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF6FAF6), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFDCFCE7), RoundedCornerShape(12.dp))
                    .padding(12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (current == Destination.HOME) {
                            Text("📱", fontSize = 16.sp)
                        } else if (current == Destination.SETTINGS) {
                            Text("⚙️", fontSize = 16.sp)
                        } else {
                            BolMitraEmblem(Modifier.size(16.dp))
                        }
                    }

                    Text(
                        text = when (current) {
                            Destination.LIVE -> "“Same classroom. More voices. A brighter tomorrow.”\n— BolMitra"
                            Destination.HOME -> "All features work offline on this tablet. Content pack + voice, no internet required."
                            Destination.WORKSHEETS -> "“Every child learns better in their own language.”\n— BolMitra"
                            Destination.PHRASEBOOK -> "“Better communication brighter classrooms.”\n— BolMitra"
                            Destination.DIAGNOSTICS -> "“Check today, Teach better tomorrow.”\n— BolMitra"
                            Destination.SETTINGS -> "“Better tools for brighter classrooms.”\n— BolMitra"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp,
                            color = Color(0xFF475569),
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
            }
        }
    }
}
