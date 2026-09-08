package org.bolmitra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.bolmitra.device.DeviceSpec
import org.bolmitra.device.DeviceTier
import org.bolmitra.phrasebook.DemoSeed
import org.bolmitra.phrasebook.InMemoryPhrasebook
import org.bolmitra.phrasebook.LookupResult
import org.bolmitra.phrasebook.SlotFill
import org.bolmitra.speech.AudioCapture
import org.bolmitra.speech.LiveTurnEngine
import org.bolmitra.speech.ModelStore
import org.bolmitra.speech.Support
import org.bolmitra.speech.TargetLanguage
import org.bolmitra.translate.TurnOutcome
import org.bolmitra.ui.common.CircleAction
import org.bolmitra.ui.common.GlassCard
import org.bolmitra.ui.common.GlassSheet
import org.bolmitra.ui.common.InkCard
import org.bolmitra.ui.common.LegendItem
import org.bolmitra.ui.common.PillButton
import org.bolmitra.ui.common.RingProgress
import org.bolmitra.ui.common.RowSectionHeader
import org.bolmitra.ui.common.SectionLabel
import org.bolmitra.ui.common.StatusPill
import org.bolmitra.ui.theme.BolmitraColors
import org.bolmitra.ui.theme.Dimens
import org.bolmitra.ui.theme.Radius

/**
 * Destinations. A flat enum, not a navigation library.
 *
 * `ponytail:` Five siblings with no back stack, no arguments and no deep links do not need
 * Navigation-Compose. Ceiling noted: the moment any of those three appears, this becomes a
 * `NavHost` and the panes below transfer unchanged.
 */
enum class Destination(val icon: ImageVector, val label: String) {
    HOME(Icons.Filled.Home, "Home"),
    LIVE(Icons.Filled.PlayArrow, "Live class"),
    WORKSHEETS(Icons.AutoMirrored.Filled.List, "Worksheets"),
    PHRASEBOOK(Icons.Filled.Search, "Phrasebook"),
    DIAGNOSTICS(Icons.Filled.Build, "Diagnostics"),
}

/**
 * App shell — the supplied dashboard layout, driven entirely by real data.
 *
 * The structure is the reference's: a permanent glass sidebar at or above [Dimens.wideBreakpoint],
 * a pill bottom bar below it, a greeting top bar, then a row of headline panels over grouped
 * cards. What changed is everything inside.
 *
 * ### Why none of the supplied sample data survived
 *
 * `DashboardModels.kt` shipped a complete `sampleDashboardState`: "Hi, Dilan!", Slack and Notion
 * integrations, SEO and Marketing teams, goals like "Bend down with a parachute", tasks like "Buy
 * Susan a gift", a weekly sport-vs-study chart and a 120% month ring. That is the reference
 * image's content for a generic productivity app. Wiring it in would have produced a screen that
 * demos beautifully and says nothing true, and §11.2's prohibition on presenting invented figures
 * as real applies here at least as hard as it does to a slide.
 *
 * So every number below is read from the running app: phrase count and verification state from
 * [DemoSeed], model inventory from [ModelStore], memory and tier from the device probe. **There is
 * no "lessons taught this week" tile because nothing records that yet.** The one chart the design
 * provides lives in Diagnostics, plotting measured native-heap growth, which is a series that
 * actually exists.
 */
@Composable
fun HomeScreen(
    spec: DeviceSpec,
    tier: DeviceTier,
    store: ModelStore,
    start: Destination = Destination.HOME,
    /** Whether `RECORD_AUDIO` is held. Drives whether Live class can capture at all. */
    micGranted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var current by remember { mutableStateOf(start) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= Dimens.wideBreakpoint

        // Everything sits on one outer sheet of glass, as in the reference, with the silk backdrop
        // reading around its edges and through it. The outer margin is what makes it read as a
        // sheet rather than as the window itself — without it there is no "around".
        GlassSheet(
            Modifier
                .fillMaxSize()
                .padding(horizontal = if (wide) 18.dp else 10.dp, vertical = if (wide) 16.dp else 10.dp),
        ) {
            Row(Modifier.fillMaxSize().padding(Dimens.sectionGap)) {
                if (wide) {
                    SidebarRail(current, { current = it }, Modifier.width(218.dp).fillMaxHeight())
                    Spacer(Modifier.width(Dimens.sectionGap))
                }
                Column(Modifier.weight(1f)) {
                    TopBar(current, onDiagnostics = { current = Destination.DIAGNOSTICS })
                    Spacer(Modifier.height(Dimens.sectionGap))
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap),
                    ) {
                        when (current) {
                            Destination.HOME ->
                                DashboardPane(spec, tier, store, wide, micGranted) { current = it }
                            Destination.LIVE -> LivePane(wide, micGranted)
                            Destination.WORKSHEETS -> WorksheetsPane(wide)
                            Destination.PHRASEBOOK -> PhrasebookPane()
                            Destination.DIAGNOSTICS -> DiagnosticsPane(spec, tier)
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    if (!wide) {
                        Spacer(Modifier.height(10.dp))
                        BottomBar(current) { current = it }
                    }
                }
            }
        }
    }
}

/* -------------------------------------------------------------------------- navigation */

/**
 * Sidebar rail.
 *
 * The supplied version listed Integrations (Slack, Notion) and Teams (SEO, Marketing) — neither
 * exists here. The section that replaces them is the one thing a teacher genuinely needs pinned
 * on screen: **which language pair is active**. §2 scopes the prototype to one target language, so
 * this is a statement, not a picker; an empty dropdown reads as a broken feature rather than an
 * honest scope boundary.
 */
@Composable
private fun SidebarRail(
    current: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier, contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(28.dp).background(BolmitraColors.Ink, Radius.sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "\u092C",
                    style = MaterialTheme.typography.labelLarge,
                    color = BolmitraColors.OnInk,
                )
            }
            Spacer(Modifier.width(9.dp))
            Text("BolMitra", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(Modifier.height(20.dp))

        Destination.entries.forEach { d ->
            NavRow(d.label, d.icon, selected = d == current) { onSelect(d) }
        }

        Spacer(Modifier.height(22.dp))
        SectionLabel("Language")
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "\u0939\u093F\u0928\u094D\u0926\u0940",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "  \u2192  ",
                style = MaterialTheme.typography.bodyMedium,
                color = BolmitraColors.InkMuted,
            )
            Text(
                "\u092E\u0941\u0902\u0921\u093E\u0930\u0940",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Devanagari \u00B7 pack demo-v0",
            style = MaterialTheme.typography.bodySmall,
            color = BolmitraColors.InkMuted,
        )

        Spacer(Modifier.weight(1f))
        Text(
            "Santali and Ho are selectable in Live class, with what each one can actually do " +
                "stated there. Adding a language is a content pack plus a voice, not a code branch.",
            style = MaterialTheme.typography.bodySmall,
            color = BolmitraColors.InkMuted,
        )
    }
}

/**
 * A rail row.
 *
 * `heightIn(min = …)` rather than a fixed height: the supplied version used vertical padding alone
 * and landed at roughly 35 dp, under V39's 48 dp floor, and a fixed height would clip when the
 * system font scale grows.
 */
@Composable
private fun NavRow(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouchTarget)
            .background(if (selected) BolmitraColors.Ink else Color.Transparent, Radius.md)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null, // the adjacent label is the accessible name
            tint = if (selected) BolmitraColors.OnInk else BolmitraColors.InkMuted,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) BolmitraColors.OnInk else BolmitraColors.Ink,
        )
    }
}

@Composable
private fun BottomBar(current: Destination, onSelect: (Destination) -> Unit) {
    GlassCard(contentPadding = 8.dp, shape = Radius.pill) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Destination.entries.forEach { d ->
                val selected = d == current
                Box(
                    Modifier
                        // 48 dp, not the supplied 42 dp.
                        .size(Dimens.minTouchTarget)
                        .background(
                            if (selected) BolmitraColors.Ink else Color.Transparent,
                            CircleShape,
                        )
                        .clickable(role = Role.Tab, onClick = { onSelect(d) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        d.icon,
                        contentDescription = d.label,
                        tint = if (selected) BolmitraColors.OnInk else BolmitraColors.InkMuted,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
        }
    }
}

/**
 * Top bar.
 *
 * The supplied version had "Create", search and notifications. None of those exist: nothing is
 * created here, there is no global search, and an offline app with no accounts has nothing to
 * notify. The offline pill takes the space instead, which is the status a teacher actually wants
 * confirmed.
 */
@Composable
private fun TopBar(current: Destination, onDiagnostics: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (current == Destination.HOME) {
                "\u0928\u092E\u0938\u094D\u0924\u0947, \u0905\u0927\u094D\u092F\u093E\u092A\u0915 \u091C\u0940"
            } else {
                current.label
            },
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        StatusPill("Offline")
        Spacer(Modifier.width(Dimens.targetGap))
        CircleAction(Icons.Filled.Build, "Open diagnostics", onDiagnostics)
    }
}

/* --------------------------------------------------------------------------- dashboard */

@Composable
private fun DashboardPane(
    spec: DeviceSpec,
    tier: DeviceTier,
    store: ModelStore,
    wide: Boolean,
    micGranted: Boolean,
    onNavigate: (Destination) -> Unit,
) {
    val inventory = remember { store.inventory() }
    val present = remember(inventory) { inventory.count { it.present } }
    val modelMb = remember(inventory) { inventory.filter { it.present }.sumOf { it.sizeMb } }
    val phrases = remember { DemoSeed.phrases }
    val verified = remember(phrases) { phrases.count { it.verifiedBy != null } }
    val lakshyaCodes = remember(phrases) { phrases.mapNotNull { it.lakshyaCode }.distinct().size }

    if (!DemoSeed.DEMO_STRINGS_VERIFIED) {
        WarningBanner(
            "Placeholder content: no Mundari string in this build has been reviewed by a native " +
                "speaker. Native-speaker verification is Phase 0 risk #1 and it is open.",
        )
    }

    if (wide) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sectionGap)) {
            ContentCard(phrases.size, lakshyaCodes, present, inventory.size, modelMb, tier, spec, Modifier.weight(1.2f))
            VerificationCard(verified, phrases.size, Modifier.weight(1f))
        }
    } else {
        ContentCard(phrases.size, lakshyaCodes, present, inventory.size, modelMb, tier, spec, Modifier.fillMaxWidth())
        VerificationCard(verified, phrases.size, Modifier.fillMaxWidth())
    }

    if (wide) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sectionGap)) {
            ReadinessCard(store, micGranted, Modifier.weight(1f))
            Column(Modifier.weight(1.3f)) {
                RowSectionHeader("What would you like to do?")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.targetGap)) {
                    ActionCard(
                        "Live class",
                        "Speak Hindi, hear Mundari",
                        "placeholder Mundari",
                        Modifier.weight(1f).height(160.dp),
                    ) { onNavigate(Destination.LIVE) }
                    ActionCard(
                        "Worksheets",
                        "Lakshya \u2192 bilingual sheet",
                        "PDF output pending",
                        Modifier.weight(1f).height(160.dp),
                    ) { onNavigate(Destination.WORKSHEETS) }
                    ActionCard(
                        "Phrasebook",
                        "${phrases.size} classroom phrases",
                        "working now",
                        Modifier.weight(1f).height(160.dp),
                    ) { onNavigate(Destination.PHRASEBOOK) }
                }
            }
        }
    } else {
        ReadinessCard(store, micGranted, Modifier.fillMaxWidth())
        RowSectionHeader("What would you like to do?")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.targetGap),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            item {
                ActionCard("Live class", "Speak Hindi, hear Mundari", "placeholder Mundari", Modifier.width(230.dp).height(160.dp)) {
                    onNavigate(Destination.LIVE)
                }
            }
            item {
                ActionCard("Worksheets", "Lakshya \u2192 bilingual sheet", "PDF output pending", Modifier.width(230.dp).height(160.dp)) {
                    onNavigate(Destination.WORKSHEETS)
                }
            }
            item {
                ActionCard("Phrasebook", "${phrases.size} classroom phrases", "working now", Modifier.width(230.dp).height(160.dp)) {
                    onNavigate(Destination.PHRASEBOOK)
                }
            }
        }
    }

    RowSectionHeader("Language pack", "read from storage")
    if (wide) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sectionGap)) {
            inventory.filter { !it.label.contains("tokens") }.forEach { m ->
                PackCard(m.label, m.present, m.sizeMb, Modifier.weight(1f).height(126.dp))
            }
        }
    } else {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.targetGap),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(inventory.filter { !it.label.contains("tokens") }, key = { it.label }) { m ->
                PackCard(m.label, m.present, m.sizeMb, Modifier.width(250.dp).height(126.dp))
            }
        }
    }
    Text(
        store.rootPath,
        style = MaterialTheme.typography.bodySmall,
        color = BolmitraColors.InkMuted,
    )
}

/** The reference's "Overall Information" panel, carrying content figures instead of task counts. */
@Composable
private fun ContentCard(
    phraseCount: Int,
    lakshyaCodes: Int,
    modelsPresent: Int,
    modelsTotal: Int,
    modelMb: Double,
    tier: DeviceTier,
    spec: DeviceSpec,
    modifier: Modifier = Modifier,
) {
    InkCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "This tablet's content",
                style = MaterialTheme.typography.titleLarge,
                color = BolmitraColors.OnInk,
                modifier = Modifier.weight(1f),
            )
            Text(
                "demo-v0",
                style = MaterialTheme.typography.labelSmall,
                color = BolmitraColors.OnInkMuted,
            )
        }

        Spacer(Modifier.height(20.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            HeadlineMetric(phraseCount.toString(), "Hindi phrases\nready to speak")
            Spacer(Modifier.width(28.dp))
            HeadlineMetric(lakshyaCodes.toString(), "NIPUN lakshya\ncodes covered")
        }

        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InkTile("$modelsPresent/$modelsTotal", "Model files", Modifier.weight(1f))
            InkTile("%.0f MB".format(modelMb), "On storage", Modifier.weight(1f))
            InkTile(tier.name.lowercase(), "%.1f GiB \u00B7 %d cores".format(spec.totalRamGiB, spec.cpuCores), Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeadlineMetric(value: String, caption: String) {
    Column {
        Text(value, style = MaterialTheme.typography.displayLarge, color = BolmitraColors.OnInk)
        Box(
            Modifier
                .padding(top = 4.dp, bottom = 8.dp)
                .width(44.dp)
                .height(2.dp)
                .background(BolmitraColors.OnInk),
        )
        Text(caption, style = MaterialTheme.typography.bodySmall, color = BolmitraColors.OnInkMuted)
    }
}

@Composable
private fun InkTile(value: String, caption: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(BolmitraColors.InkSoft, Radius.md)
            .border(1.dp, BolmitraColors.HairlineOnInk, Radius.md)
            .padding(14.dp),
    ) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = BolmitraColors.OnInk)
        Spacer(Modifier.height(2.dp))
        Text(caption, style = MaterialTheme.typography.bodySmall, color = BolmitraColors.OnInkMuted)
    }
}

/**
 * The reference's "Month progress" ring, pointed at the number that decides whether this app may
 * be used with children: **how much of the content a native speaker has signed off.**
 *
 * It reads 0%, and that is the correct, useful answer. §4.5 makes native-speaker verification the
 * thing that lets T0 stand in a classroom, and §12 lists reviewer capacity as risk #1, still open.
 * A dashboard that hid this behind a friendlier metric would be lying by omission.
 */
@Composable
private fun VerificationCard(verified: Int, total: Int, modifier: Modifier = Modifier) {
    val fraction = if (total == 0) 0f else verified.toFloat() / total
    GlassCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Content verification",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = BolmitraColors.InkMuted,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            "Phrases signed off by a named native speaker",
            style = MaterialTheme.typography.bodySmall,
            color = BolmitraColors.InkMuted,
        )

        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LegendItem("$verified verified", BolmitraColors.Verified)
                LegendItem("${total - verified} awaiting review", BolmitraColors.Approximate)
                LegendItem("0 machine-translated", BolmitraColors.Unavailable)
            }
            RingProgress(
                value = fraction,
                label = "%.0f%%".format(fraction * 100),
                caption = "reviewed",
                diameter = 116.dp,
            )
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "Until this reaches 100%, BolMitra is a prototype and not a teaching tool.",
            style = MaterialTheme.typography.bodyMedium,
            color = BolmitraColors.InkMuted,
        )
    }
}

/**
 * The reference's "Month goals" checklist, repurposed as the build's own readiness list.
 *
 * Not interactive, unlike the original — these are facts about the app, not things a teacher can
 * tick off. A tappable checkbox here would imply the user could change the answer.
 */
@Composable
private fun ReadinessCard(
    store: ModelStore,
    micGranted: Boolean,
    modifier: Modifier = Modifier,
) {
    val items = remember(store, micGranted) {
        listOf(
            "Hindi ASR model present" to store.hasStreamingAsr(),
            "Mundari voice present" to store.hasTts(),
            "Phrasebook lookup working" to true,
            // Was hardcoded false. Both are built now; capture depends on the runtime permission,
            // so it reports the permission rather than the code's existence.
            "Microphone permission granted" to micGranted,
            "Audio playback" to true,
            // Still false, and the one that matters most. Every Mundari string in this build is a
            // placeholder; see DemoSeed. Until a named native speaker signs off, the pipeline
            // working end to end does not make this a teaching tool.
            "Native-speaker verified content" to DemoSeed.DEMO_STRINGS_VERIFIED,
        )
    }
    GlassCard(modifier = modifier) {
        RowSectionHeader("Ready to use", "${items.count { it.second }}/${items.size}")
        Spacer(Modifier.height(10.dp))
        items.forEach { (label, done) ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(22.dp)
                        .background(
                            if (done) BolmitraColors.Verified else BolmitraColors.Glass,
                            Radius.sm,
                        )
                        .border(
                            1.dp,
                            if (done) BolmitraColors.Verified else BolmitraColors.InkMuted,
                            Radius.sm,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (done) Icons.Filled.Check else Icons.Filled.Clear,
                        contentDescription = if (done) "ready" else "not built",
                        tint = if (done) BolmitraColors.OnInk else BolmitraColors.InkMuted,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (done) BolmitraColors.Ink else BolmitraColors.InkMuted,
                )
            }
        }
    }
}

/** The reference's task card. Status line says what is missing rather than omitting it. */
@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    status: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    InkCard(
        modifier = modifier.clickable(role = Role.Button, onClick = onClick),
        contentPadding = 16.dp,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = BolmitraColors.OnInk)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = BolmitraColors.OnInkMuted)
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(status, style = MaterialTheme.typography.labelSmall, color = BolmitraColors.OnInkMuted)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(30.dp).background(BolmitraColors.Paper, Radius.sm),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = BolmitraColors.Ink,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** The reference's project card, carrying one real model file each. */
@Composable
private fun PackCard(
    label: String,
    present: Boolean,
    sizeMb: Double,
    modifier: Modifier = Modifier,
) {
    InkCard(modifier = modifier, contentPadding = 16.dp) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = BolmitraColors.OnInk,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendItem(
                if (present) "%.1f MB".format(sizeMb) else "missing",
                if (present) BolmitraColors.Verified else BolmitraColors.Unavailable,
            )
        }
    }
}

/* -------------------------------------------------------------------------- phrasebook */

/**
 * The one pane that is fully real.
 *
 * Runs [org.bolmitra.phrasebook.PhraseMatcher]'s actual ladder — exact, then template slot fill,
 * then fuzzy, then miss — with provenance decided at lookup and carried, never re-derived at
 * display time (§4.5). Only candidate retrieval is substituted: §6.3's shipping T0 narrows
 * candidates with SQLite FTS4, and scanning a short seed list returns the same answers.
 */
@Composable
private fun PhrasebookPane() {
    val book = remember { InMemoryPhrasebook(DemoSeed.phrases) }
    var query by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<LookupResult?>(null) }
    var searched by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val engine = remember { LiveTurnEngine.get(context) }
    val scope = rememberCoroutineScope()
    var speaking by remember { mutableStateOf(false) }
    var playNote by remember { mutableStateOf<String?>(null) }

    Text(
        "The T0 lookup ladder, running for real. Provenance is decided at lookup, not guessed " +
            "from the result.",
        style = MaterialTheme.typography.bodyMedium,
        color = BolmitraColors.InkMuted,
    )

    GlassCard(Modifier.widthIn(max = 660.dp).fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Hindi phrase") },
            modifier = Modifier.fillMaxWidth(),
            shape = Radius.md,
            singleLine = true,
        )
        Spacer(Modifier.height(10.dp))
        PillButton(
            label = "Look up",
            onClick = {
                result = book.lookup(query)
                searched = true
            },
            enabled = query.isNotBlank(),
        )
    }

    val hit = result
    if (hit != null) {
        GlassCard(Modifier.widthIn(max = 660.dp).fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(hit.phrase.hiText, style = MaterialTheme.typography.titleLarge)
                ProvenanceChip(hit.provenance)
            }
            Spacer(Modifier.height(6.dp))
            Text(hit.phrase.targetTextDeva, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(10.dp))
            DataRow("Match score", "%.2f".format(hit.score))
            DataRow("Lakshya", hit.phrase.lakshyaCode ?: "\u2014")
            DataRow("Template", if (hit.phrase.isTemplate) "yes" else "no")
            hit.slotValue?.let { DataRow("Slot value", it) }
            DataRow("Pack", hit.phrase.packVersion)
            DataRow("Verified by", hit.phrase.verifiedBy ?: "nobody yet")

            // Speaks the matched phrase without needing the microphone.
            //
            // Useful in its own right — a teacher can hear a phrase before using it in front of a
            // class — and it exercises the synthesis-to-speaker leg directly, which is otherwise
            // only reachable by actually saying Hindi at the tablet.
            Spacer(Modifier.height(14.dp))
            PillButton(
                label = if (speaking) "Speaking\u2026" else "Hear it",
                onClick = {
                    scope.launch {
                        speaking = true
                        playNote = withContext(Dispatchers.Default) {
                            when (val s = engine.ensureLoaded()) {
                                is LiveTurnEngine.LoadState.Ready -> {
                                    val ref = hit.phrase.audioRef
                                    val player = engine.audioPlayer
                                    when {
                                        player == null -> "player unavailable"
                                        ref == null -> "this phrase has no audio ref by design"
                                        player.play(ref) -> null
                                        else -> "playback failed"
                                    }
                                }
                                is LiveTurnEngine.LoadState.Missing -> s.detail
                                is LiveTurnEngine.LoadState.Failed -> s.detail
                                is LiveTurnEngine.LoadState.Unsupported -> s.detail
                            }
                        }
                        speaking = false
                    }
                },
                icon = Icons.Filled.PlayArrow,
                filled = false,
                enabled = !speaking,
            )
            playNote?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = BolmitraColors.Unavailable,
                )
            }
        }
    } else if (searched) {
        // A miss is a designed outcome, not an error: §6.3 routes it to T1, which is not built.
        GlassCard(Modifier.widthIn(max = 660.dp).fillMaxWidth()) {
            Text("No phrasebook match", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "In the shipping design this routes to T1 neural translation and returns a result " +
                    "marked Machine. T1 is optional for submission (\u00A74.5) and is not in this " +
                    "build, so the honest answer here is nothing.",
                style = MaterialTheme.typography.bodyMedium,
                color = BolmitraColors.InkMuted,
            )
        }
    }

    RowSectionHeader("Seed phrases", "tap to run the ladder")
    // Tappable, not decorative. A teacher mid-lesson will not type Devanagari on glass, so the
    // phrase list IS the primary input and the text field covers what it does not. It also makes
    // the pane exercisable on a device with no Devanagari IME.
    GlassCard(Modifier.widthIn(max = 660.dp).fillMaxWidth()) {
        DemoSeed.phrases.forEach { p ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.minTouchTarget)
                    .clickable(role = Role.Button) {
                        // Templates get a concrete number substituted, so tapping one exercises
                        // the slot-fill rung instead of trivially exact-matching the template's
                        // own text. 12 is arbitrary; any value works, which is the point.
                        val q = p.hiText.replace(SlotFill.MARKER, "12")
                        query = q
                        result = book.lookup(q)
                        searched = true
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    p.hiText + if (p.isTemplate) "  (template)" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    p.lakshyaCode ?: "\u2014",
                    style = MaterialTheme.typography.bodySmall,
                    color = BolmitraColors.InkMuted,
                )
            }
        }
    }
}

/* ------------------------------------------------------------------------ live class */

/**
 * The live turn, now an actual control rather than a description of one.
 *
 * This pane used to be a hardcoded list of seven stages with four marked "not built", and a note
 * explaining that a dead push-to-talk button would be worse than no button. That was the right call
 * while capture and playback did not exist. They exist now
 * ([org.bolmitra.speech.AudioCapture], [org.bolmitra.speech.RenderingAudioPlayer]), so the button is
 * live and the stage list is derived from real state instead of being typed in.
 *
 * ### What the teacher will actually hear, and why it is nonsense
 *
 * The chain runs: mic → Hindi ASR → T0 lookup → Mundari synthesis → speaker. Every link works. But
 * `DemoSeed`'s target strings are placeholders of the form `[unr-1 अनुवाद-लंबित]` — literally
 * "translation-pending" — so the tablet transliterates *that* into Odia and speaks it. The pipeline
 * is real; the content is not, and [DemoSeed.DEMO_STRINGS_VERIFIED] is false precisely so nobody
 * mistakes one for the other. The banner below says so on screen, because a demo that sounds
 * confident while speaking gibberish to children is the specific failure §4.5 exists to prevent.
 */
@Composable
private fun LivePane(wide: Boolean, micGranted: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var language by remember { mutableStateOf(TargetLanguage.DEFAULT) }
    val engine = remember(language) { LiveTurnEngine.get(context, language) }

    var loadState by remember { mutableStateOf(engine.loadState) }
    var phase by remember { mutableStateOf(TurnPhase.IDLE) }
    var result by remember { mutableStateOf<LiveTurnEngine.TurnResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // Keyed on the engine, so switching language reloads rather than reporting the previous
    // language's state. Load on first visit rather than at app start: it is ~1.1 s of CPU and a
    // teacher who never opens Live class should not pay for it.
    LaunchedEffect(engine) {
        result = null
        error = null
        phase = TurnPhase.LOADING
        loadState = engine.ensureLoaded()
        phase = TurnPhase.IDLE
    }

    val canSpeak = micGranted && loadState is LiveTurnEngine.LoadState.Ready

    /** One turn, start to finish. Capture is driven here so the button owns the utterance. */
    fun runTurn() {
        if (!canSpeak || phase != TurnPhase.IDLE) return
        error = null
        result = null
        scope.launch {
            val capture = AudioCapture()
            try {
                phase = TurnPhase.LISTENING
                withContext(Dispatchers.Default) {
                    capture.start()
                    // Fixed window rather than press-and-hold. A press-and-hold gesture on a
                    // resistive budget-tablet screen drops touches, and losing the release event
                    // would leave the mic open; a bounded window cannot get stuck. Long enough for
                    // the classroom imperatives this is for.
                    val deadline = SystemClock.elapsedRealtime() + LISTEN_WINDOW_MS
                    while (SystemClock.elapsedRealtime() < deadline) {
                        capture.drain()
                    }
                }
                val pcm = capture.stop()

                // The turn clock starts when the teacher STOPS speaking, not when they start.
                //
                // The orchestrator's own note argues the opposite — that ASR time must count, since
                // "measuring only from ASR completion would flatter the numbers" — and it is right
                // about ASR: everything after this line is system latency and is counted. But the
                // listening window is the teacher's own speaking time, and charging that to a
                // latency budget makes the budget meaningless. With a 4 s window, `now - turnStart`
                // exceeded the 3 s R3 deadline before the ladder had done anything at all, so every
                // projection would fire BUDGET_EXHAUSTED regardless of how fast the device was. The
                // first real measurement showed exactly that: 4,600 ms total against a 241 ms ASR.
                //
                // R3 is about how long the class waits after the teacher finishes. That is this
                // instant onward.
                val turnStart = SystemClock.elapsedRealtime()

                if (pcm == null) {
                    error = "Nothing audible was captured. Hold the tablet closer and speak up."
                    phase = TurnPhase.IDLE
                    return@launch
                }
                phase = TurnPhase.THINKING
                result = engine.runTurn(pcm, turnStart)
            } catch (e: Throwable) {
                // Capture throws if the mic is busy or the permission was revoked mid-session.
                error = e.message ?: e::class.java.simpleName
                runCatching { capture.stop() }
            } finally {
                phase = TurnPhase.IDLE
            }
        }
    }

    Text(
        "Voice-to-voice, budgeted end to end at under 3 s by the architecture rather than by " +
            "fast hardware (\u00A76.2.1).",
        style = MaterialTheme.typography.bodyMedium,
        color = BolmitraColors.InkMuted,
    )

    LanguagePicker(language) { language = it }

    (loadState as? LiveTurnEngine.LoadState.Unsupported)?.let {
        WarningBanner("${it.language.englishName} cannot run a turn yet. ${it.detail}")
    }

    // Two separate admissions for a language on a borrowed voice, because they are two different
    // problems and a teacher can act on them differently. The voice being wrong is cosmetic and
    // permanent until a real Santali VITS is trained. The words being unchecked is a safety matter.
    if (language.voice == Support.BORROWED) {
        WarningBanner(
            "${language.voiceNote} Anything outside the verified phrasebook is machine " +
                "translation shown as \u201c\u26a0 Machine\u201d \u2014 no Santali speaker has " +
                "reviewed it, so do not rely on it for anything that matters.",
        )
    }

    if (language == TargetLanguage.MUNDARI && !DemoSeed.DEMO_STRINGS_VERIFIED) {
        WarningBanner(
            "The pipeline below is real. The Mundari is not: every target string in this build is " +
                "a placeholder reading \"\u0905\u0928\u0941\u0935\u093E\u0926-\u0932\u0902\u092C" +
                "\u093F\u0924\" (translation-pending), so that is what you will hear spoken. " +
                "Native-speaker verified content is Phase 0 risk #1 and it is open.",
        )
    }

    if (!micGranted) {
        WarningBanner(
            "Microphone permission is not granted, so capture is unavailable. Grant it in " +
                "Settings \u203A Apps \u203A BolMitra \u203A Permissions.",
        )
    }
    (loadState as? LiveTurnEngine.LoadState.Missing)?.let {
        WarningBanner("Models are not on this tablet. ${it.detail}")
    }
    (loadState as? LiveTurnEngine.LoadState.Failed)?.let {
        WarningBanner("The speech engines failed to load: ${it.detail}")
    }

    if (wide) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sectionGap)) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap),
            ) {
                TurnCard(phase, canSpeak, result, error, ::runTurn)
                LatencyCard()
            }
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap),
            ) {
                GlassCard { StageList(livePipelineStages(micGranted, loadState)) }
            }
        }
    } else {
        TurnCard(phase, canSpeak, result, error, ::runTurn)
        GlassCard(Modifier.fillMaxWidth()) { StageList(livePipelineStages(micGranted, loadState)) }
        LatencyCard()
    }
}

/**
 * Target-language picker that states each language's real capability rather than pretending they
 * are interchangeable.
 *
 * Every language stays selectable, including the two that cannot complete a turn. That is the point:
 * selecting Santali should explain that its models exist but the voice needs a runtime we do not
 * have, and selecting Ho should explain that a VITS voice exists upstream but is unconverted. A
 * picker that hid them would hide the roadmap; one that offered them silently would look broken.
 *
 * The dot follows the app's existing provenance colour language — green for working, amber for
 * planned, red for unavailable — and is never the only signal, because §4.5's rule about colour
 * applies here too. The word is in the status line under the row.
 */
@Composable
private fun LanguagePicker(
    selected: TargetLanguage,
    onSelect: (TargetLanguage) -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth()) {
        RowSectionHeader("Target language", selected.script.displayName)
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.targetGap)) {
            TargetLanguage.selectable.forEach { lang ->
                val active = lang == selected
                Row(
                    Modifier
                        .heightIn(min = Dimens.minTouchTarget)
                        .background(
                            if (active) BolmitraColors.Ink else Color.Transparent,
                            Radius.pill,
                        )
                        .border(
                            1.dp,
                            if (active) BolmitraColors.Ink else BolmitraColors.GlassStroke,
                            Radius.pill,
                        )
                        .clickable(role = Role.RadioButton) { onSelect(lang) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .background(
                                when {
                                    // A borrowed voice is checked BEFORE canRunFullTurn, which is
                                    // true for both. Green would claim a Santali voice this tablet
                                    // does not have; amber says "runs, with a caveat" — the same
                                    // hue the Approximate provenance chip uses, for the same reason.
                                    lang.voice == Support.BORROWED ->
                                        BolmitraColors.Approximate
                                    lang.canRunFullTurn -> BolmitraColors.Verified
                                    lang.voice == Support.PLANNED -> BolmitraColors.Approximate
                                    else -> BolmitraColors.Unavailable
                                },
                                CircleShape,
                            ),
                    )
                    Column {
                        Text(
                            lang.englishName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (active) BolmitraColors.OnInk else BolmitraColors.Ink,
                        )
                        Text(
                            lang.endonym,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (active) {
                                BolmitraColors.OnInkMuted
                            } else {
                                BolmitraColors.InkMuted
                            },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            selected.statusLine,
            style = MaterialTheme.typography.bodySmall,
            color = BolmitraColors.InkMuted,
        )
    }
}

/** How long one push-to-talk window listens for. See the note in `runTurn`. */
private const val LISTEN_WINDOW_MS = 4_000L

private enum class TurnPhase { IDLE, LOADING, LISTENING, THINKING }

/**
 * The stage list, derived rather than declared.
 *
 * The previous version was a hand-written `List<Triple<..., Boolean>>`, which meant the screen's
 * claim about what worked was a comment that could rot — and had: it still said capture and playback
 * were unbuilt after they were. These flags now come from the permission and the load state.
 *
 * Stage 3 is still false, and deliberately: push-to-talk knows where the utterance ends, so the
 * Silero VAD stays unused. See [AudioCapture]. Stage 2 is gone entirely — a foreground service is
 * only needed for recording that continues in the background, which this does not do.
 */
private fun livePipelineStages(
    micGranted: Boolean,
    load: LiveTurnEngine.LoadState?,
): List<Triple<String, String, Boolean>> {
    val enginesReady = load is LiveTurnEngine.LoadState.Ready
    return listOf(
        Triple(
            "1. Microphone capture",
            if (micGranted) "AudioRecord \u00B7 16 kHz mono, granted" else "permission not granted",
            micGranted,
        ),
        Triple(
            "2. Voice activity detection",
            "not needed \u00B7 push-to-talk bounds the utterance",
            false,
        ),
        Triple(
            "3. Hindi speech recognition",
            if (enginesReady) "NeMo CTC \u00B7 loaded" else "not loaded",
            enginesReady,
        ),
        Triple("4. T0 phrasebook lookup", "exact \u2192 template \u2192 fuzzy \u2192 miss", true),
        Triple(
            "5. Mundari synthesis",
            if (enginesReady) "VITS \u00B7 loaded, Odia input (V63)" else "not loaded",
            enginesReady,
        ),
        Triple(
            "6. Playback",
            if (enginesReady) "AudioTrack \u00B7 renders missing pack audio on first use" else "\u2014",
            enginesReady,
        ),
    )
}

/**
 * The push-to-talk control and the last turn's result.
 *
 * Shows transcript, target text, provenance, degrade reason and both timings. §11.2 requires a
 * measured number and an estimate never to look alike, so the timings here are labelled measured —
 * they are, they came off the clock — while [LatencyCard]'s budgets stay marked as budgets.
 */
@Composable
private fun TurnCard(
    phase: TurnPhase,
    canSpeak: Boolean,
    result: LiveTurnEngine.TurnResult?,
    error: String?,
    onSpeak: () -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth()) {
        RowSectionHeader(
            "Speak Hindi",
            when (phase) {
                TurnPhase.LOADING -> "loading models"
                TurnPhase.LISTENING -> "listening"
                TurnPhase.THINKING -> "translating"
                TurnPhase.IDLE -> if (canSpeak) "ready" else "unavailable"
            },
        )
        Spacer(Modifier.height(12.dp))

        PillButton(
            label = when (phase) {
                TurnPhase.LISTENING -> "Listening\u2026"
                TurnPhase.THINKING -> "Translating\u2026"
                TurnPhase.LOADING -> "Loading\u2026"
                TurnPhase.IDLE -> "Hold a phrase \u00B7 tap to speak"
            },
            onClick = onSpeak,
            icon = Icons.Filled.PlayArrow,
            enabled = canSpeak && phase == TurnPhase.IDLE,
        )

        Spacer(Modifier.height(10.dp))
        Text(
            "Try \u0915\u093F\u0924\u093E\u092C \u0916\u094B\u0932\u094B, " +
                "\u0924\u093E\u0932\u0940 \u092C\u091C\u093E\u0913 or " +
                "\u092C\u0948\u0920 \u091C\u093E\u0913. Four seconds per turn.",
            style = MaterialTheme.typography.bodySmall,
            color = BolmitraColors.InkMuted,
        )

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                error,
                style = MaterialTheme.typography.bodyMedium,
                color = BolmitraColors.Unavailable,
            )
        }

        result?.let { r ->
            Spacer(Modifier.height(16.dp))
            DataRow("Heard (Hindi)", r.transcript ?: "\u2014 nothing recognised")

            val outcome = r.outcome
            val target = when (outcome) {
                is TurnOutcome.VerifiedAudio -> outcome.targetText
                is TurnOutcome.ApproximateAudio -> outcome.targetText
                is TurnOutcome.MachineAudio -> outcome.targetText
                is TurnOutcome.TextOnly -> outcome.devanagariText
                is TurnOutcome.Unavailable -> null
            }
            DataRow("Spoken (Mundari)", target ?: "\u2014")

            val reason = when (outcome) {
                is TurnOutcome.TextOnly -> outcome.reason.name
                is TurnOutcome.Unavailable -> outcome.reason.name
                else -> null
            }
            if (reason != null) DataRow("Degraded", reason)

            DataRow("Recognition", "${r.asrMs} ms  (measured)")
            DataRow("Turn total", "${r.totalMs} ms  (measured)")
            r.note?.let { DataRow("Playback note", it) }

            outcome.provenance?.let {
                Spacer(Modifier.height(12.dp))
                ProvenanceChip(it)
            }
        }
    }
}

@Composable
private fun StageList(stages: List<Triple<String, String, Boolean>>) {
    RowSectionHeader("Pipeline", "${stages.count { it.third }}/${stages.size} built")
    Spacer(Modifier.height(8.dp))
    stages.forEach { (name, detail, built) ->
        Row(
            Modifier.fillMaxWidth().heightIn(min = 46.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(
                        if (built) BolmitraColors.Verified else BolmitraColors.Unavailable,
                        CircleShape,
                    ),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyMedium)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = BolmitraColors.InkMuted)
            }
            Text(
                if (built) "built" else "not built",
                style = MaterialTheme.typography.labelSmall,
                color = BolmitraColors.InkMuted,
            )
        }
    }
}

/**
 * Latency budget with its provenance attached to every row, which §11.2 requires: a measured
 * figure and an estimated one must never be shown the same way.
 */
@Composable
private fun LatencyCard() {
    GlassCard(Modifier.fillMaxWidth()) {
        RowSectionHeader("Turn budget", "\u00A76.2.1")
        Spacer(Modifier.height(8.dp))
        DataRow("Phrasebook hit, total", "620\u2013920 ms  (budget)")
        DataRow("Neural path, total", "1.8\u20132.4 s  (budget)")
        DataRow("ASR model load", "1,135\u20131,298 ms  (measured)")
        DataRow("Voice model load", "946 ms  (measured)")
        DataRow("Synthesis, 0.78 s clip", "887 ms  (measured)")
        Spacer(Modifier.height(10.dp))
        Text(
            "Synthesis runs slower than real time even on this tablet's fastest core, which is " +
                "why phrasebook audio is pre-rendered at pack build rather than synthesised live.",
            style = MaterialTheme.typography.bodySmall,
            color = BolmitraColors.InkMuted,
        )
    }
}

/* ------------------------------------------------------------------------- worksheets */

/** Same discipline as [LivePane]: the generator core is real, the output is not. */
@Composable
private fun WorksheetsPane(wide: Boolean) {
    Text(
        "Bilingual sheets generated deterministically from NIPUN lakshya codes. No language model " +
            "is involved, which is what makes the output reproducible and auditable.",
        style = MaterialTheme.typography.bodyMedium,
        color = BolmitraColors.InkMuted,
    )

    val built = listOf(
        Triple("Akshara ladder analyser", "12 unit tests", true),
        Triple("Item models and generator", "slot-based, deterministic", true),
        Triple("Greedy assembler", "18 unit tests, reports infeasibility", true),
        Triple("Interleave orderer", "spaced practice ordering", true),
        Triple("Devanagari \u2192 Odia transliteration", "17 unit tests (V63)", true),
        Triple("NIPUN lakshya catalogue data", "not authored", false),
        Triple("Verified item bank", "not authored", false),
        Triple("PDF renderer with font subsets", "V20", false),
    )

    if (wide) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sectionGap)) {
            GlassCard(Modifier.weight(1f)) { StageList(built) }
            GlassCard(Modifier.weight(1f)) {
                RowSectionHeader("Why it stops here")
                Spacer(Modifier.height(8.dp))
                Text(
                    "The assembler and the akshara ladder are unit-tested and pass. What is " +
                        "missing is content and a renderer, in that order \u2014 a sheet built " +
                        "from unreviewed items would look finished and teach the wrong thing.\n\n" +
                        "The greedy selector can also fail where a smarter search would succeed. " +
                        "That ceiling is deliberate and marked in the code; an ILP solver is the " +
                        "upgrade path if a real lakshya set ever needs it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BolmitraColors.InkMuted,
                )
            }
        }
    } else {
        GlassCard(Modifier.fillMaxWidth()) { StageList(built) }
    }
}
