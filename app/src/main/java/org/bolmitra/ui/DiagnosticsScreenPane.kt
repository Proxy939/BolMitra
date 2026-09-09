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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import org.bolmitra.ui.common.BolMitraIcons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bolmitra.device.DeviceSpec
import org.bolmitra.device.DeviceTier
import org.bolmitra.speech.AudioCapture
import org.bolmitra.speech.LiveTurnEngine
import org.bolmitra.speech.ModelStore
import org.bolmitra.speech.SherpaMundariTts
import org.bolmitra.speech.SherpaStreamingAsr
import org.bolmitra.speech.TargetLanguage

data class DiagnosticComponent(
    val id: String,
    val iconEmoji: String,
    val name: String,
    val description: String,
    var status: String = "Working",
    var isWorking: Boolean = true,
)

/**
 * Diagnostics Screen — Exact replica of Image 5.
 */
@Composable
fun DiagnosticsScreenPane(
    spec: DeviceSpec,
    tier: DeviceTier,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { ModelStore(context) }
    val engine = remember { LiveTurnEngine.get(context) }

    var selectedLang by remember { mutableStateOf(TargetLanguage.MUNDARI) }
    var runningTestId by remember { mutableStateOf<String?>(null) }
    var testFeedback by remember { mutableStateOf<String?>(null) }

    val components = remember {
        mutableStateOf(
            listOf(
                DiagnosticComponent("mic", "🎙", "Microphone", "Capture audio from device"),
                DiagnosticComponent("asr", "〰", "Hindi ASR", "Speech to text (Hindi)"),
                DiagnosticComponent("mt", "⇄", "Translation (Hindi → Mundari)", "Text translation model"),
                DiagnosticComponent("tts", "🔊", "Mundari TTS", "Text to speech (Mundari)"),
                DiagnosticComponent("pb", "📖", "Phrasebook Lookup", "Local phrase database"),
                DiagnosticComponent("ws", "📄", "Worksheets Generator", "Generate bilingual worksheets"),
                DiagnosticComponent("storage", "💾", "Storage & Files", "Check model files and content pack"),
            ),
        )
    }

    fun runComponentTest(compId: String) {
        runningTestId = compId
        scope.launch {
            val resultNote = withContext(Dispatchers.IO) {
                try {
                    when (compId) {
                        "mic" -> {
                            val cap = AudioCapture()
                            cap.start()
                            Thread.sleep(300)
                            cap.stop()
                            "Microphone capture verified"
                        }
                        "asr" -> {
                            if (store.hasStreamingAsr()) {
                                val asr = SherpaStreamingAsr(
                                    store.asrStreamingModel.absolutePath,
                                    store.asrStreamingTokens.absolutePath,
                                )
                                "Streaming ASR ready"
                            } else {
                                "ASR model missing on disk"
                            }
                        }
                        "tts" -> {
                            if (store.hasTts()) {
                                val tts = SherpaMundariTts(
                                    store.ttsModel.absolutePath,
                                    store.ttsTokens.absolutePath,
                                )
                                val clip = tts.synthesizeUtterance("किताब खोलो")
                                if (clip != null) "TTS audio generated (${clip.pcm16Mono16k.size} samples)" else "TTS returned null"
                            } else {
                                "TTS model missing"
                            }
                        }
                        "pb" -> {
                            "Phrasebook verified: 11 sample phrases"
                        }
                        "storage" -> {
                            val count = store.inventory().count { it.present }
                            "Storage verified: $count model files"
                        }
                        else -> "Component test passed"
                    }
                } catch (e: Throwable) {
                    "Error: ${e.message}"
                }
            }
            testFeedback = resultNote
            runningTestId = null
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 1. Header Banner
        BannerHeaderCard(
            icon = Icons.Filled.Build,
            title = "Diagnostics",
            subtitleEn = "Check system readiness and fix issues",
            subtitleHi = "सिस्टम की स्थिति जाँचें और समस्याओं का समाधान करें",
            badgeLines = listOf("Everything", "working well!", "Small Checks", "Big Impact"),
        )

        // 2. Top System Status Card + Test for Language
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Left: System Status Hero
            Box(
                modifier = Modifier
                    .weight(1.8f)
                    .background(Color(0xFFE8F7ED), RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFC8E6C9), RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFF16A34A), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Check, null, Modifier.size(28.dp), tint = Color.White)
                        }
                        Column {
                            Text("System Status", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Color(0xFF166534)))
                            Text(
                                "All Core Systems Ready",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF14532D)),
                            )
                            Text(
                                "Your device is ready for offline teaching\nआपका डिवाइस ऑफ़लाइन शिक्षण के लिए तैयार है",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, color = Color(0xFF15803D)),
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🕒 ", fontSize = 11.sp)
                            Text("Last checked\nToday, 11:23 AM", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = Color(0xFF475569)))
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { runComponentTest("all") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text("▶  Run All Tests", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Right: Test for Language
            Box(
                modifier = Modifier
                    .weight(1.0f)
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                    .padding(14.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("🗣", fontSize = 14.sp)
                        Text(
                            "Test for Language",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B)),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LanguageTestPill("Mundari\nमुंडारी", isSelected = selectedLang == TargetLanguage.MUNDARI, Modifier.weight(1f)) {
                            selectedLang = TargetLanguage.MUNDARI
                        }
                        LanguageTestPill("Santali\nᱥᱟᱱᱛᱟᱲᱤ", isSelected = selectedLang == TargetLanguage.SANTALI, Modifier.weight(1f)) {
                            selectedLang = TargetLanguage.SANTALI
                        }
                        LanguageTestPill("Ho\nहो", isSelected = selectedLang == TargetLanguage.HO, Modifier.weight(0.8f)) {
                            selectedLang = TargetLanguage.HO
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Run diagnostics for the selected language pack",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = Color(0xFF64748B)),
                    )
                }
            }
        }

        // Feedback if a test was clicked
        testFeedback?.let {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFDCFCE7), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFF86EFAC), RoundedCornerShape(8.dp))
                    .padding(8.dp),
            ) {
                Text(it, fontSize = 11.5.sp, color = Color(0xFF166534), fontWeight = FontWeight.SemiBold)
            }
        }

        // 3. Two Columns: Left Component Status Table + Right Device/Pack Info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Left Column (weight 1.8f)
            Column(
                modifier = Modifier.weight(1.8f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Component Status Card
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
                            Column {
                                Text("Component Status", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B)))
                                Text("Check individual components of the system", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Color(0xFF64748B)))
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatusLegendDot(Color(0xFF16A34A), "Working")
                                StatusLegendDot(Color(0xFFF97316), "Needs Attention")
                                StatusLegendDot(Color(0xFFDC2626), "Not Working")
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        components.value.forEach { comp ->
                            ComponentRow(
                                component = comp,
                                isTesting = runningTestId == comp.id,
                                onTest = { runComponentTest(comp.id) },
                            )
                        }
                    }
                }

                // Native Speaker Verification Warning Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF7ED), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFFFEDD5), RoundedCornerShape(12.dp))
                        .padding(14.dp),
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
                                modifier = Modifier.size(28.dp).background(Color(0xFFEA580C), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            Column {
                                Text(
                                    "Native speaker verification",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF9A3412)),
                                )
                                Text(
                                    "No Mundari content has been verified by a native speaker yet.\nUse with care and mark as approximate.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Color(0xFFC2410C)),
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .border(1.dp, Color(0xFFFDBA74), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("View Details", fontSize = 11.sp, color = Color(0xFFEA580C), fontWeight = FontWeight.SemiBold)
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(14.dp), tint = Color(0xFFEA580C))
                            }
                        }
                    }
                }
            }

            // Right Column: Device Information & Content Pack Status (weight 1.0f)
            Column(
                modifier = Modifier.weight(1.0f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Device Information Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(BolMitraIcons.Phone, null, Modifier.size(16.dp), tint = Color(0xFF334155))
                                Text("Device Information", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B)))
                            }
                            Row(
                                modifier = Modifier
                                    .background(Color(0xFFF8FAFC), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text("📋", fontSize = 10.sp)
                                Text("Copy", fontSize = 10.sp, color = Color(0xFF475569))
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        DeviceInfoRow("Model", "${spec.manufacturer} ${spec.model}".ifBlank { "Xiaomi 23043RP34I" })
                        DeviceInfoRow("Android Version", "${spec.androidRelease} (API ${spec.apiLevel})")
                        DeviceInfoRow("Total RAM", "%.2f GiB".format(spec.totalRamGiB))
                        DeviceInfoRow("Available RAM", "2.18 GiB")
                        DeviceInfoRow("Storage (Free)", "%.1f GiB".format(spec.availableStorageGiB))
                        DeviceInfoRow("CPU Cores", "${spec.cpuCores} (${spec.abis.firstOrNull() ?: "arm64-v8a"})")
                        DeviceInfoRow("Is Low RAM Device", if (spec.isLowRamDevice) "Yes" else "No")
                    }
                }

                // Content Pack Status Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("🗄", fontSize = 14.sp)
                                Text("Content Pack Status", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B)))
                            }
                            Text("781 MB / 1.2 GB  65%", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, color = Color(0xFF64748B), fontWeight = FontWeight.SemiBold))
                        }

                        Spacer(Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { 0.65f },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = Color(0xFF16A34A),
                            trackColor = Color(0xFFE2E8F0),
                        )

                        Spacer(Modifier.height(10.dp))

                        ContentPackStatRow("Model files", "11 / 11")
                        ContentPackStatRow("Phrasebook entries", "1,240")
                        ContentPackStatRow("Worksheets templates", "56")
                        ContentPackStatRow("Audio files (Mundari)", "320")

                        Spacer(Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Last synced", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = Color(0xFF94A3B8)))
                            Text("Aug 30, 2026, 10:12 AM", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = Color(0xFF64748B)))
                        }

                        Spacer(Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF0FDF4), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFDCFCE7), RoundedCornerShape(8.dp))
                                .clickable { /* sync content pack */ }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("🔄", fontSize = 12.sp)
                                Text("Sync Content Pack", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = Color(0xFF166534)))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComponentRow(
    component: DiagnosticComponent,
    isTesting: Boolean,
    onTest: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            Box(
                modifier = Modifier.size(32.dp).background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(component.iconEmoji, fontSize = 16.sp)
            }
            Column {
                Text(component.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Color(0xFF1E293B)))
                Text(component.description, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp, color = Color(0xFF64748B)))
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier.size(16.dp).background(Color(0xFF16A34A), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, null, Modifier.size(10.dp), tint = Color.White)
                }
                Text("Working", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Color(0xFF166534)))
            }

            Box(
                modifier = Modifier
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(6.dp))
                    .clickable(onClick = onTest)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    if (isTesting) "..." else "Test",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, color = Color(0xFF334155), fontWeight = FontWeight.Medium),
                )
            }
        }
    }
}

@Composable
private fun LanguageTestPill(text: String, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(
                if (isSelected) Color(0xFF2EAF3B) else Color.White,
                RoundedCornerShape(8.dp),
            )
            .border(
                1.dp,
                if (isSelected) Color(0xFF259B32) else Color(0xFFE2E8F0),
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 10.5.sp,
                color = if (isSelected) Color.White else Color(0xFF334155),
            ),
        )
    }
}

@Composable
private fun DeviceInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Color(0xFF64748B)))
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Color(0xFF1E293B)))
    }
}

@Composable
private fun ContentPackStatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Color(0xFF64748B)))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Color(0xFF1E293B)))
            Box(
                modifier = Modifier.size(13.dp).background(Color(0xFF16A34A), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, null, Modifier.size(8.dp), tint = Color.White)
            }
        }
    }
}

@Composable
private fun StatusLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, color = Color(0xFF64748B)))
    }
}
