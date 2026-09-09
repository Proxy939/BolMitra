package org.bolmitra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.bolmitra.device.DeviceSpec
import org.bolmitra.device.DeviceTier

/**
 * Settings screen — exact replica of the reference design.
 *
 * 3-column layout:
 *   Left   — App Preferences + Data & Privacy
 *   Middle — Quick Settings + Notifications
 *   Right  — Device Status + Content Pack + Help & Support + Reset
 *
 * All toggles are local state; persistence is out of scope for the UI-only replica
 * task (backend untouched per task constraint).
 */
@Composable
fun SettingsScreenPane(
    spec: DeviceSpec,
    tier: DeviceTier,
    wide: Boolean = true,
    modifier: Modifier = Modifier,
) {
    /* ---------------------------------------------------------------- local state */
    var selectedTab by remember { mutableStateOf(0) }

    // App Preferences toggles
    var showTips         by remember { mutableStateOf(true) }
    var autoSave         by remember { mutableStateOf(true) }
    var hapticFeedback   by remember { mutableStateOf(false) }
    // Data & Privacy
    var storeHistory     by remember { mutableStateOf(true) }
    // Quick Settings
    var downloadWifiOnly by remember { mutableStateOf(true) }
    // Notifications
    var contentPackUp    by remember { mutableStateOf(true) }
    var newWorksheets    by remember { mutableStateOf(true) }
    var systemAlerts     by remember { mutableStateOf(true) }
    var tipsTeaching     by remember { mutableStateOf(false) }

    /* ------------------------------------------------------------------- layout */
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {

        /* Banner header */
        BannerHeaderCard(
            icon = Icons.Filled.Settings,
            title = "Settings",
            subtitleEn = "Customize your experience for better teaching",
            subtitleHi = "अपनी आवश्यकताओं के अनुसार सेटिंग्स बदलें",
            badgeLines = listOf("Your Classroom", "Your Settings", "Your Impact"),
        )

        /* Tab row */
        val tabs = listOf(
            "⚙️" to "General",
            "🌐" to "Languages",
            "🔊" to "Voice & Audio",
            "⬇️" to "Offline Content",
            "🖥️" to "Display",
            "ℹ️" to "About",
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            tabs.forEachIndexed { i, (emoji, label) ->
                val sel = i == selectedTab
                Row(
                    modifier = Modifier
                        .background(
                            if (sel) Color(0xFF2EAF3B) else Color.Transparent,
                            RoundedCornerShape(10.dp),
                        )
                        .clickable { selectedTab = i }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(emoji, fontSize = 12.sp)
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.5.sp,
                            color = if (sel) Color.White else Color(0xFF475569),
                        ),
                    )
                }
            }
        }

        /* 3-column body */
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            /* ──── LEFT PANEL ──── */
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {

                SettingsCard(
                    title = "App Preferences",
                    subtitle = "Set up the app for your teaching style",
                ) {
                    SettingsDropdownItem("App Language", "English")
                    SettingsDivider()
                    SettingsDropdownItem("Default Target Language", "Mundari (मुंडारी)")
                    SettingsDivider()
                    SettingsDropdownItem("Startup Screen", "Home")
                    SettingsDivider()
                    SettingsToggleItem("Show Tips & Hints", "Helpful guidance while using the app", showTips) { showTips = it }
                    SettingsDivider()
                    SettingsToggleItem("Auto-save Translations", "Save typed and spoken translations", autoSave) { autoSave = it }
                    SettingsDivider()
                    SettingsToggleItem("Haptic Feedback", "Vibration on button press", hapticFeedback) { hapticFeedback = it }
                }

                SettingsCard(
                    title = "Data & Privacy",
                    subtitle = "Your data stays on your device",
                ) {
                    SettingsToggleItem("Store History Locally", "Save chats, translations and recordings", storeHistory) { storeHistory = it }
                    SettingsDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Clear All History",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = Color(0xFF1E293B),
                                ),
                            )
                            Text(
                                "Delete all saved chats and recordings",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B),
                                ),
                            )
                        }
                        Row(
                            modifier = Modifier
                                .background(Color(0xFFFEF2F2), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFFECACA), RoundedCornerShape(8.dp))
                                .clickable { }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text("🗑️", fontSize = 12.sp)
                            Text(
                                "Clear",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFFDC2626),
                                ),
                            )
                        }
                    }
                }
            }

            /* ──── MIDDLE PANEL ──── */
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {

                SettingsCard(
                    title = "Quick Settings",
                    subtitle = "Frequently used options",
                    iconEmoji = "⚡",
                ) {
                    SettingsDropdownItem("Font Size", "Medium", leadingEmoji = "Aa")
                    SettingsDivider()
                    SettingsDropdownItem("Theme", "Light", leadingEmoji = "☀️")
                    SettingsDivider()
                    SettingsDropdownItem("Voice Speed", "Normal", leadingEmoji = "🎙️")
                    SettingsDivider()
                    SettingsDropdownItem("Translation Mode", "Phrasebook-first", leadingEmoji = "⇌")
                    SettingsDivider()
                    SettingsToggleItem(
                        label = "Download content on Wi-Fi only",
                        hint = "",
                        checked = downloadWifiOnly,
                        leadingEmoji = "📶",
                    ) { downloadWifiOnly = it }
                }

                SettingsCard(
                    title = "Notifications",
                    subtitle = "Stay updated about important things",
                    iconEmoji = "🔔",
                ) {
                    SettingsToggleItem("Content Pack Updates", "", contentPackUp) { contentPackUp = it }
                    SettingsDivider()
                    SettingsToggleItem("New Worksheets Available", "", newWorksheets) { newWorksheets = it }
                    SettingsDivider()
                    SettingsToggleItem("System Alerts", "", systemAlerts) { systemAlerts = it }
                    SettingsDivider()
                    SettingsToggleItem("Tips for Better Teaching", "", tipsTeaching) { tipsTeaching = it }
                }
            }

            /* ──── RIGHT PANEL ──── */
            Column(
                modifier = Modifier.width(280.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {

                /* Device Status */
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(14.dp))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                ) {
                    Column {
                        Text(
                            "📱  Device Status",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF1E293B),
                            ),
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(Modifier.size(8.dp).background(Color(0xFF22C55E), CircleShape))
                            Text(
                                "Offline Mode",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF15803D),
                                    fontSize = 11.sp,
                                ),
                            )
                            Text(
                                "All features available",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFF64748B),
                                    fontSize = 10.5.sp,
                                ),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        listOf(
                            "Model" to "${spec.manufacturer} ${spec.model}",
                            "Android Version" to "${spec.androidRelease} (API ${spec.apiLevel})",
                            "Total RAM" to "%.2f GiB".format(spec.totalRamGiB),
                            "Available RAM" to "%.2f GiB".format(spec.totalRamGiB * 0.29),
                            "Storage (Free)" to "%.1f GiB".format(spec.availableStorageGiB),
                        ).forEach { (label, value) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.5.sp,
                                        color = Color(0xFF64748B),
                                    ),
                                )
                                Text(
                                    value,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF1E293B),
                                    ),
                                )
                            }
                        }
                    }
                }

                /* Content Pack */
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(14.dp))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                Text("💾", fontSize = 16.sp)
                                Text(
                                    "Content Pack",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF1E293B),
                                    ),
                                )
                            }
                            Text(
                                "demo-v0",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFF64748B),
                                    fontSize = 10.sp,
                                ),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "11 / 11 model files",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF475569),
                                fontSize = 11.sp,
                            ),
                        )
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { 0.65f },
                            modifier = Modifier.fillMaxWidth().height(7.dp),
                            color = Color(0xFF2EAF3B),
                            trackColor = Color(0xFFE2E8F0),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "781 MB / 1.2 GB",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF64748B),
                                fontSize = 10.sp,
                            ),
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF0FDF4), RoundedCornerShape(10.dp))
                                .border(1.dp, Color(0xFFDCFCE7), RoundedCornerShape(10.dp))
                                .clickable { }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("🔄 ", fontSize = 13.sp)
                            Text(
                                "Sync Content Pack",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF166534),
                                    fontSize = 12.5.sp,
                                ),
                            )
                        }
                    }
                }

                /* Help & Support + Send Feedback */
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(14.dp))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Column {
                        SettingsSideLink(
                            emoji = "❓",
                            label = "Help & Support",
                            hint = "FAQs, user guide, troubleshooting",
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 6.dp),
                            color = Color(0xFFF1F5F9),
                        )
                        SettingsSideLink(
                            emoji = "💬",
                            label = "Send Feedback",
                            hint = "Help us improve BolMitra",
                        )
                    }
                }

                /* Reset Settings */
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF7ED), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFFED7AA), RoundedCornerShape(12.dp))
                        .clickable { }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("🔃", fontSize = 18.sp)
                    Column {
                        Text(
                            "Reset Settings",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFFC2410C),
                            ),
                        )
                        Text(
                            "Restore default settings",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                            ),
                        )
                    }
                }
            }
        }

        FooterTipBar(
            tipText = "You can change the target language anytime from the Live Class screen too.",
        )
    }
}

/* ═══════════════════════════════════════════════ private helper composables ══ */

@Composable
private fun SettingsCard(
    title: String,
    subtitle: String,
    iconEmoji: String? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (iconEmoji != null) Text(iconEmoji, fontSize = 14.sp)
                Column {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = Color(0xFF1E293B),
                        ),
                    )
                    if (subtitle.isNotEmpty()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                            ),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SettingsDropdownItem(
    label: String,
    value: String,
    leadingEmoji: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (leadingEmoji != null) {
                Text(leadingEmoji, fontSize = 14.sp, color = Color(0xFF64748B))
            }
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    color = Color(0xFF1E293B),
                ),
            )
        }
        Row(
            modifier = Modifier
                .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                value,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = Color(0xFF334155),
                ),
            )
            Text("▾", fontSize = 10.sp, color = Color(0xFF64748B))
        }
    }
}

@Composable
private fun SettingsToggleItem(
    label: String,
    hint: String,
    checked: Boolean,
    leadingEmoji: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            if (leadingEmoji != null) Text(leadingEmoji, fontSize = 14.sp)
            Column {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        color = Color(0xFF1E293B),
                    ),
                )
                if (hint.isNotEmpty()) {
                    Text(
                        hint,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 10.5.sp,
                            color = Color(0xFF64748B),
                        ),
                    )
                }
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF2EAF3B),
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFCBD5E1),
            ),
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
}

@Composable
private fun SettingsSideLink(emoji: String, label: String, hint: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(emoji, fontSize = 16.sp)
            Column {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = Color(0xFF1E293B),
                    ),
                )
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 10.5.sp,
                        color = Color(0xFF64748B),
                    ),
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(0xFF94A3B8),
            modifier = Modifier.size(18.dp),
        )
    }
}
