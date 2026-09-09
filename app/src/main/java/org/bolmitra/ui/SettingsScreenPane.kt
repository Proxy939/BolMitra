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
import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.bolmitra.data.Settings
import org.bolmitra.data.TurnRecorder
import org.bolmitra.device.DeviceSpec
import org.bolmitra.device.DeviceTier
import org.bolmitra.phrasebook.DemoSeed
import org.bolmitra.phrasebook.SantaliGlossary
import org.bolmitra.speech.ModelStore
import org.bolmitra.speech.TargetLanguage
import org.bolmitra.ui.common.AppNotice
import org.bolmitra.ui.common.NoticeKind
import org.bolmitra.ui.common.NotificationHost
import org.bolmitra.ui.common.rememberNotifier

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
    /* ------------------------------------------------------------- persisted state */
    //
    // Every control below used to be `remember { mutableStateOf(...) }` — it moved on screen, reset on
    // the next recomposition-scope loss, and nothing downstream read it. These are backed by
    // `Settings` (SharedPreferences) and the behaviour-changing ones are honoured for real.
    //
    // Three former controls are deliberately absent rather than persisted: Wi-Fi-only downloads, pack
    // update notifications, and "Translation Mode". The app makes no network calls by design and
    // phrasebook-first is a safety property, not a preference. See `Settings`' class docs.

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { Settings(context) }
    val recorder = remember { TurnRecorder(context) }
    val store = remember { ModelStore(context) }
    val notifier = rememberNotifier()

    var selectedTab by remember { mutableStateOf(0) }

    // Seeded from the store, written back on change.
    var showTips by remember { mutableStateOf(settings.showTips) }
    var storeHistory by remember { mutableStateOf(settings.storeHistory) }
    var keepRecordings by remember { mutableStateOf(settings.keepRecordings) }
    var hapticFeedback by remember { mutableStateOf(settings.hapticFeedback) }
    var defaultLanguage by remember { mutableStateOf(settings.defaultLanguage) }
    var startupScreen by remember { mutableStateOf(settings.startupScreen) }
    var fontScale by remember { mutableStateOf(settings.fontScale) }

    // Live figures for the Device Status card.
    var availableRamGiB by remember { mutableStateOf<Double?>(null) }
    var historyCount by remember { mutableStateOf(0) }
    var recordingBytes by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        while (true) {
            val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            availableRamGiB = mem.availMem / DeviceSpec.GIB
            historyCount = recorder.count()
            recordingBytes = recorder.recordingsBytes()
            delay(2_000)
        }
    }

    /** Clears history rows and the WAV files, then reports what happened. */
    fun clearHistory() {
        scope.launch {
            val had = historyCount
            val ok = recorder.clearAll()
            historyCount = recorder.count()
            recordingBytes = recorder.recordingsBytes()
            notifier.show(
                if (ok) {
                    AppNotice(
                        title = "History cleared",
                        message = "$had saved ${if (had == 1) "turn" else "turns"} and all " +
                            "recordings were deleted from this tablet.",
                        messageHi = "इतिहास मिटा दिया गया",
                    )
                } else {
                    AppNotice(
                        title = "Could not clear history",
                        message = "Some files could not be deleted. Try again.",
                        kind = NoticeKind.WARNING,
                    )
                },
            )
        }
    }

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
                    // Cycles through the three languages and persists. Was a static "Mundari
                    // (मुंडारी)" label with no click handler.
                    SettingsChoiceItem(
                        label = "Default target language",
                        value = "${defaultLanguage.englishName} (${defaultLanguage.endonym})",
                        hint = "Restored the next time the app opens",
                    ) {
                        val all = TargetLanguage.entries
                        val next = all[(all.indexOf(defaultLanguage) + 1) % all.size]
                        defaultLanguage = next
                        settings.defaultLanguage = next
                    }
                    SettingsDivider()
                    SettingsChoiceItem(
                        label = "Startup screen",
                        value = startupScreen.lowercase().replaceFirstChar { it.uppercase() },
                        hint = "Where the app opens",
                    ) {
                        val options = listOf("HOME", "LIVE", "WORKSHEETS", "PHRASEBOOK")
                        val next = options[(options.indexOf(startupScreen) + 1).mod(options.size)]
                        startupScreen = next
                        settings.startupScreen = next
                    }
                    SettingsDivider()
                    SettingsChoiceItem(
                        label = "Text size",
                        value = Settings.FONT_SCALE_STEPS
                            .minByOrNull { kotlin.math.abs(it.second - fontScale) }
                            ?.first ?: "Medium",
                        hint = "Applies across the app",
                    ) {
                        val steps = Settings.FONT_SCALE_STEPS.map { it.second }
                        val current = steps.indexOfFirst { kotlin.math.abs(it - fontScale) < 0.01f }
                        val next = steps[(current + 1).mod(steps.size)]
                        fontScale = next
                        settings.fontScale = next
                    }
                    SettingsDivider()
                    SettingsToggleItem(
                        "Show tips & hints",
                        "Guidance text at the bottom of each screen",
                        showTips,
                    ) {
                        showTips = it
                        settings.showTips = it
                    }
                    SettingsDivider()
                    SettingsToggleItem(
                        "Haptic feedback",
                        "Vibration when the microphone starts and stops",
                        hapticFeedback,
                    ) {
                        hapticFeedback = it
                        settings.hapticFeedback = it
                    }
                }

                SettingsCard(
                    title = "Data & Privacy",
                    subtitle = "Your data stays on your device",
                ) {
                    // Real: with this off, TurnRecorder writes nothing at all.
                    SettingsToggleItem(
                        "Store lesson history",
                        "Keep what was said and translated on this tablet",
                        storeHistory,
                    ) {
                        storeHistory = it
                        settings.storeHistory = it
                    }
                    SettingsDivider()
                    // Separate from the above on purpose: a text record of a lesson and a recording of
                    // children speaking are different privacy questions (§6.9.4, DPDP §9).
                    SettingsToggleItem(
                        "Keep voice recordings",
                        if (recordingBytes > 0) {
                            "Currently %.1f MB on this tablet".format(recordingBytes / 1048576.0)
                        } else {
                            "Audio of the class and the translation"
                        },
                        keepRecordings && storeHistory,
                        enabled = storeHistory,
                    ) {
                        keepRecordings = it
                        settings.keepRecordings = it
                    }
                    SettingsDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Clear all history",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = Color(0xFF1E293B),
                                ),
                            )
                            Text(
                                // The real count, so a teacher knows what they are about to delete.
                                if (historyCount == 0) {
                                    "Nothing saved yet"
                                } else {
                                    "$historyCount saved ${if (historyCount == 1) "turn" else "turns"}" +
                                        if (recordingBytes > 0) {
                                            " · %.1f MB of audio".format(recordingBytes / 1048576.0)
                                        } else {
                                            ""
                                        }
                                },
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
                                // Was an empty lambda.
                                .clickable(enabled = historyCount > 0) { clearHistory() }
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
                                    color = if (historyCount > 0) Color(0xFFDC2626) else Color(0xFFCBD5E1),
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

                // Replaces "Quick Settings", which held four display-only dropdowns and a toggle for
                // downloads the app cannot make. What a teacher can actually be told about the
                // translation pipeline is what it will and will not do for the language they picked.
                SettingsCard(
                    title = "Translation",
                    subtitle = "How this tablet handles ${defaultLanguage.englishName}",
                    iconEmoji = "⇌",
                ) {
                    SettingsFactRow(
                        label = "Phrasebook first",
                        value = "Always",
                        hint = "Reviewed and corpus phrases outrank the model. Not configurable — " +
                            "putting machine output first is a safety change, not a preference.",
                    )
                    SettingsDivider()
                    SettingsFactRow(
                        label = "Machine translation",
                        value = if (defaultLanguage.mtDir == null) {
                            "Not available"
                        } else if (store.hasMtGraphs(defaultLanguage)) {
                            "Ready"
                        } else {
                            "Model not staged"
                        },
                        hint = if (defaultLanguage.mtDir == null) {
                            "${defaultLanguage.englishName} is not a scheduled language and appears " +
                                "in no released MT model. The phrasebook serves it alone."
                        } else {
                            "Used only when the phrasebook has no match."
                        },
                    )
                    SettingsDivider()
                    SettingsFactRow(
                        label = "Voice",
                        value = if (store.hasTts(defaultLanguage)) "Ready" else "Not staged",
                        hint = if (defaultLanguage == TargetLanguage.SANTALI) {
                            "Santali is spoken by the Mundari voice. The words are Santali; the " +
                                "accent is not."
                        } else {
                            "Speech is synthesised on this tablet."
                        },
                    )
                }

                // Replaces "Notifications", whose four toggles governed pushes from a server that does
                // not exist in an app with no INTERNET permission.
                SettingsCard(
                    title = "Privacy",
                    subtitle = "Nothing leaves this tablet",
                    iconEmoji = "🔒",
                ) {
                    SettingsFactRow(
                        label = "Network access",
                        value = "None",
                        hint = "The app holds no internet permission. Speech, translations and " +
                            "recordings cannot be uploaded because there is no code that could.",
                    )
                    SettingsDivider()
                    SettingsFactRow(
                        label = "Recordings location",
                        value = "App-private storage",
                        hint = "Not the shared media folder, so no gallery or file manager can " +
                            "reach recordings of children.",
                    )
                    SettingsDivider()
                    SettingsFactRow(
                        label = "Content packs",
                        value = "USB or Wi-Fi Direct",
                        hint = store.rootPath,
                    )
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
                            "Model" to "${spec.manufacturer} ${spec.model}".trim().ifBlank { "unknown" },
                            "Android Version" to "${spec.androidRelease} (API ${spec.apiLevel})",
                            "Total RAM" to "%.2f GiB".format(spec.totalRamGiB),
                            // Was `spec.totalRamGiB * 0.29` — a fabricated figure from a magic
                            // multiplier. This is ActivityManager.MemoryInfo.availMem, polled live.
                            "Available RAM" to (
                                availableRamGiB?.let { "%.2f GiB".format(it) } ?: "reading…"
                                ),
                            "Storage (Free)" to "%.1f GiB".format(spec.availableStorageGiB),
                            "Device tier" to tier.name.lowercase(),
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
                                if (defaultLanguage == TargetLanguage.SANTALI) {
                                    SantaliGlossary.PACK_VERSION
                                } else {
                                    "demo-v0"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFF64748B),
                                    fontSize = 10.sp,
                                ),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        // Counted from the inventory. Was the literal "11 / 11".
                        val inv = store.inventory()
                        val present = inv.count { it.present }
                        Text(
                            "$present / ${inv.size} model files",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF475569),
                                fontSize = 11.sp,
                            ),
                        )
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            // Was a literal 0.65f.
                            progress = { if (inv.isEmpty()) 0f else present.toFloat() / inv.size },
                            modifier = Modifier.fillMaxWidth().height(7.dp),
                            color = if (present == inv.size) Color(0xFF2EAF3B) else Color(0xFFF59E0B),
                            trackColor = Color(0xFFE2E8F0),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            // Real bytes. Was "781 MB / 1.2 GB".
                            "%.0f MB staged".format(inv.filter { it.present }.sumOf { it.sizeMb }),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF64748B),
                                fontSize = 10.sp,
                            ),
                        )
                        if (present < inv.size) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Missing: " + inv.filterNot { it.present }.joinToString { it.label },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFFB45309),
                                    fontSize = 9.5.sp,
                                ),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        // Was a "Sync Content Pack" button with an empty handler. There is no importer
                        // and no network, so this says where to put a pack instead.
                        Text(
                            "Side-load packs to:\n${store.rootPath}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF94A3B8),
                                fontSize = 9.5.sp,
                            ),
                        )
                    }
                }

                // "Help & Support" and "Send Feedback" lived here, both with empty click handlers.
                // Removed rather than wired: help would be a web page in an offline app, and feedback
                // would be a network call the app cannot make. The correction outbox (§6.3) is the
                // real feedback channel and it travels by USB, not from this screen.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(14.dp))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                ) {
                    Column {
                        Text(
                            "ℹ️  About",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF1E293B),
                            ),
                        )
                        Spacer(Modifier.height(6.dp))
                        listOf(
                            "Languages" to TargetLanguage.entries.joinToString { it.englishName },
                            "Native-speaker review" to
                                if (DemoSeed.DEMO_STRINGS_VERIFIED) "Signed off" else "Pending",
                            "Saved turns" to historyCount.toString(),
                        ).forEach { (l, v) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(l, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Color(0xFF64748B)))
                                Text(v, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1E293B)))
                            }
                        }
                        if (!DemoSeed.DEMO_STRINGS_VERIFIED) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Seeded phrases are placeholders until a named speaker signs off.",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.5.sp,
                                    color = Color(0xFFB45309),
                                ),
                            )
                        }
                    }
                }

                /* Reset Settings — now actually resets. */
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF7ED), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFFED7AA), RoundedCornerShape(12.dp))
                        .clickable {
                            settings.resetAll()
                            // Re-read rather than assuming the defaults, so the screen shows exactly
                            // what was stored.
                            showTips = settings.showTips
                            storeHistory = settings.storeHistory
                            keepRecordings = settings.keepRecordings
                            hapticFeedback = settings.hapticFeedback
                            defaultLanguage = settings.defaultLanguage
                            startupScreen = settings.startupScreen
                            fontScale = settings.fontScale
                            notifier.show(
                                AppNotice(
                                    title = "Settings reset",
                                    message = "Preferences are back to their defaults. Your lesson " +
                                        "history was not touched.",
                                    messageHi = "सेटिंग्स रीसेट हो गईं",
                                ),
                            )
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("🔃", fontSize = 18.sp)
                    Column {
                        Text(
                            "Reset settings",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFFC2410C),
                            ),
                        )
                        Text(
                            // States what it does NOT do. A reset that also wiped a lesson would be a
                            // nasty surprise from a button labelled "restore defaults".
                            "Restore defaults. Lesson history is kept.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                            ),
                        )
                    }
                }
            }
        }

        if (showTips) {
            // Honours its own setting. It previously showed regardless of "Show Tips & Hints".
            FooterTipBar(
                tipText = "You can change the target language anytime from the Live Class screen too.",
            )
        }
    }

    NotificationHost(notifier)
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

/**
 * A setting that cycles through its options on tap.
 *
 * Replaces `SettingsDropdownItem`, which had **no `onClick` parameter and no menu** — it drew a "▾"
 * affordance next to a value that could never change. Cycling rather than opening a dropdown because
 * every one of these has three or four options and a tap is fewer interactions than a menu, on a screen
 * a teacher uses with one hand.
 */
@Composable
private fun SettingsChoiceItem(
    label: String,
    value: String,
    hint: String = "",
    leadingEmoji: String? = null,
    onCycle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCycle)
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            if (leadingEmoji != null) {
                Text(leadingEmoji, fontSize = 14.sp, color = Color(0xFF64748B))
            }
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
            Text("⇄", fontSize = 10.sp, color = Color(0xFF64748B))
        }
    }
}

/**
 * A fact about how the app behaves, with no control attached.
 *
 * Several former "settings" describe fixed behaviour — phrasebook-first ordering, the absence of
 * network access — and a switch next to them would imply they could be changed. Stating them is
 * useful; offering to toggle them would be a lie.
 */
@Composable
private fun SettingsFactRow(label: String, value: String, hint: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1E293B),
                ),
            )
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 10.sp,
                    color = Color(0xFF64748B),
                ),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            value,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF166534),
            ),
        )
    }
}

@Composable
private fun SettingsToggleItem(
    label: String,
    hint: String,
    checked: Boolean,
    leadingEmoji: String? = null,
    /** False greys the row out — used where one setting only applies while another is on. */
    enabled: Boolean = true,
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
                        color = if (enabled) Color(0xFF1E293B) else Color(0xFF94A3B8),
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
            enabled = enabled,
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
