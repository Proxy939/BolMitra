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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Add
import org.bolmitra.ui.common.BolMitraIcons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bolmitra.phrasebook.DemoSeed
import org.bolmitra.phrasebook.InMemoryPhrasebook
import org.bolmitra.phrasebook.PhrasebookBrowser
import org.bolmitra.phrasebook.Provenance
import org.bolmitra.phrasebook.SantaliGlossary
import org.bolmitra.speech.LiveTurnEngine
import org.bolmitra.speech.TargetLanguage
import org.bolmitra.ui.common.OlChikiFont
import org.bolmitra.ui.theme.BolmitraColors

// `PhraseCategory` and `PhraseItemModel` lived here.
//
// `PhraseItemModel` held eight hand-written rows whose Mundari was INVENTED — बेसो, दाड़ा,
// सेंते रे सुतु, मिते होड़ो, जोहा! and an example sentence for each — displayed with no provenance
// marker at all. The project's first invariant is that a tribal-language string must come from a
// named speaker or a labelled machine translation, and those came from neither. `PhraseCategory`
// carried thirteen invented counts summing to 203 for a screen that showed eight rows.
//
// Both are replaced by `PhrasebookBrowser.Row` / `.Category`, which are projections of DemoSeed and
// the Santali glossary. Nothing on this screen is authored here any more.

/**
 * Phrasebook Screen — Exact replica of Image 4.
 */
@Composable
fun PhrasebookScreenPane(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedLang by remember { mutableStateOf(TargetLanguage.MUNDARI) }
    var selectedCategoryIndex by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var quickAddText by remember { mutableStateOf("") }

    // Per-language engine, so playback uses the voice for the language being browsed. The screen
    // previously held `LiveTurnEngine.get(context)` with no language, i.e. always the default.
    val engine = remember(selectedLang) { LiveTurnEngine.get(context, selectedLang) }

    /**
     * The real rows. Loaded off the main thread because for Santali this parses a 5,151-row asset and
     * expands it per Hindi alias.
     */
    var allRows by remember { mutableStateOf<List<PhrasebookBrowser.Row>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(selectedLang) {
        loading = true
        selectedCategoryIndex = 0
        allRows = withContext(Dispatchers.Default) {
            PhrasebookBrowser.rows(context, selectedLang)
        }
        loading = false
    }

    val categories = remember(allRows) { PhrasebookBrowser.categories(allRows) }
    val activeCategory = categories.getOrNull(selectedCategoryIndex)
    val phrasesList = remember(allRows, selectedCategoryIndex, searchQuery, categories) {
        PhrasebookBrowser.filter(allRows, activeCategory, searchQuery)
    }

    // Kept as an id so the selection survives a filter change rather than snapping back to the top.
    var selectedRowId by remember(selectedLang) { mutableStateOf<Long?>(null) }
    val selectedPhrase = phrasesList.firstOrNull { it.id == selectedRowId } ?: phrasesList.firstOrNull()

    /** T0 lookup for playback only. Same construction LiveTurnEngine uses, so the same rows match. */
    val phrasebook = remember(selectedLang, allRows) {
        InMemoryPhrasebook(
            DemoSeed.phrasesFor(selectedLang) +
                SantaliGlossary.phrasesFor(context, selectedLang),
        )
    }

    var speaking by remember { mutableStateOf(false) }
    var speakNote by remember { mutableStateOf<String?>(null) }

    /**
     * Speaks a row, and says so when it cannot.
     *
     * The old version resolved a ref and then did nothing if there was none — a silent no-op with no
     * feedback, which on this screen was most rows. A glossary row has `audioRef == null` by
     * construction (no pack audio exists for it), so synthesising from its own Devanagari is the only
     * way to hear it. That is the same thing `TurnOrchestrator` does for a T0 hit with no asset, and
     * it changes only the voice, never the words.
     */
    fun speakRow(row: PhrasebookBrowser.Row) {
        if (speaking) return
        scope.launch {
            speaking = true
            speakNote = null
            try {
                withContext(Dispatchers.Default) {
                    if (engine.ensureLoaded() !is LiveTurnEngine.LoadState.Ready) {
                        speakNote = "The voice for ${selectedLang.englishName} is not on this tablet."
                        return@withContext
                    }
                    val player = engine.audioPlayer
                    if (player == null) {
                        speakNote = "No audio player available."
                        return@withContext
                    }
                    val ref = row.audioRef
                    val played = when {
                        ref != null -> player.play(ref)
                        row.targetDeva.isNotBlank() -> {
                            val clip = engine.synthesize(row.targetDeva)
                            clip != null && player.play(clip)
                        }
                        else -> false
                    }
                    if (!played) speakNote = "Could not play this phrase."
                }
            } catch (e: Throwable) {
                speakNote = e.message ?: e::class.java.simpleName
            } finally {
                speaking = false
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 1. Header Banner
        BannerHeaderCard(
            icon = BolMitraIcons.Book,
            title = "Phrasebook",
            subtitleEn = "Essential phrases for classroom teaching",
            subtitleHi = "कक्षा में उपयोग के लिए महत्वपूर्ण वाक्य",
            badgeLines = listOf("Same", "Words", "Brighter", "Tomorrows"),
        )

        // 2. Language Selector Bar + Search Field
        LanguageSelectorBar(
            currentLanguage = selectedLang,
            onSelectLanguage = { selectedLang = it },
            label = "Select Language",
            trailingContent = {
                // Search Input Box
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.Search, null, Modifier.size(16.dp), tint = Color(0xFF94A3B8))
                    // A real input. This was a Text, so `searchQuery` could never become non-empty
                    // and the filter below it was unreachable.
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            color = Color(0xFF1E293B),
                        ),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    "Search Hindi, English or ${selectedLang.englishName}…",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        color = Color(0xFF94A3B8),
                                    ),
                                )
                            }
                            inner()
                        },
                    )
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            Icons.Filled.Clear,
                            contentDescription = "Clear search",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(14.dp).clickable { searchQuery = "" },
                        )
                    }
                }
            },
        )

        // 3. Three-Column Layout: Categories (Left) + All Phrases (Center) + Phrase Details (Right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Column 1: Categories List (~200.dp)
            Box(
                modifier = Modifier
                    .width(200.dp)
                    .height(440.dp)
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                    .padding(12.dp),
            ) {
                Column(modifier = Modifier.fillMaxHeight()) {
                    Text(
                        "Categories",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = Color(0xFF1E293B),
                        ),
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        itemsIndexed(categories) { index, cat ->
                            val isSelected = selectedCategoryIndex == index
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isSelected) Color(0xFF2EAF3B) else Color.Transparent,
                                        RoundedCornerShape(8.dp),
                                    )
                                    .clickable { selectedCategoryIndex = index }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(cat.emoji, fontSize = 12.sp)
                                    Text(
                                        cat.name,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else Color(0xFF334155),
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    cat.count.toString(),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        color = if (isSelected) Color.White.copy(alpha = 0.9f) else Color(0xFF94A3B8),
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            // Column 2: All Phrases List (weight 1.4f)
            Box(
                modifier = Modifier
                    .weight(1.4f)
                    .height(440.dp)
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                    .padding(14.dp),
            ) {
                Column(modifier = Modifier.fillMaxHeight()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            // Real counts. The literal "(120)" described nothing.
                            when {
                                loading -> "Loading…"
                                searchQuery.isNotBlank() ->
                                    "${phrasesList.size} of ${allRows.size} match"
                                else ->
                                    "${activeCategory?.name ?: "All Phrases"} (${phrasesList.size})"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = Color(0xFF1E293B),
                            ),
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(Color(0xFFF1F5F9), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                        ) {
                            // Was "Sort: Most Used" — there is no usage counter in the app, so it
                            // described a sort that did not exist. This states the real order.
                            Text(
                                if (selectedLang == TargetLanguage.SANTALI) {
                                    "Seeded first, then corpus"
                                } else {
                                    "Pack order"
                                },
                                fontSize = 10.sp,
                                color = Color(0xFF475569),
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (phrasesList.isEmpty() && !loading) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        if (searchQuery.isNotBlank()) {
                                            "Nothing matches \u201C$searchQuery\u201D"
                                        } else {
                                            "No phrasebook for ${selectedLang.englishName} on this " +
                                                "tablet yet."
                                        },
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 12.sp,
                                            color = Color(0xFF94A3B8),
                                        ),
                                    )
                                }
                            }
                        }
                        items(phrasesList, key = { it.id }) { phrase ->
                            val isSelected = selectedPhrase?.id == phrase.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isSelected) Color(0xFFF1F8F1) else Color.White,
                                        RoundedCornerShape(10.dp),
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) Color(0xFFD6EFE0) else Color(0xFFF1F5F9),
                                        RoundedCornerShape(10.dp),
                                    )
                                    .clickable { selectedRowId = phrase.id }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Column {
                                        Text(
                                            phrase.hiText,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = Color(0xFF1E293B),
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            // The English the corpus actually translated, where there
                                            // is one. Seeded rows have none, so this is not invented.
                                            phrase.english ?: phrase.lakshyaCode ?: "",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 11.sp,
                                                color = Color(0xFF64748B),
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            // Follows the selected language. This was the literal
                                            // "मुंडारी" even when Santali was selected.
                                            selectedLang.endonym,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = Color(0xFF94A3B8)),
                                        )
                                        Text(
                                            // annotate() styles only the Ol Chiki runs; the bundled
                                            // face has no Devanagari and would tofu a Mundari row.
                                            text = OlChikiFont.annotate(phrase.targetNative),
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp,
                                                color = Color(0xFF166534),
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }

                                    // Speaker Play button
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clickable { speakRow(phrase) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text("🔊", fontSize = 13.sp)
                                    }

                                    // Was a favourite star that flipped a `var` inside a data class
                                    // held in a `remember`ed list — it did not recompose reliably and
                                    // was never persisted anywhere. The space now carries the one
                                    // thing this screen was missing: how much to trust the row.
                                    ProvenanceMark(phrase.provenance)
                                }
                            }
                        }
                    }
                }
            }

            // Column 3: Phrase Details Panel (weight 1.1f)
            Box(
                modifier = Modifier
                    .weight(1.1f)
                    .height(440.dp)
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                    .padding(14.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Header: Phrase Details + Heart icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Phrase Details",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = Color(0xFF1E293B),
                            ),
                        )
                        // Was a decorative heart left over from the favourites feature. Replaced with
                        // the count, which is information.
                        selectedPhrase?.let {
                            Text(
                                "${phrasesList.indexOfFirst { r -> r.id == it.id } + 1} of " +
                                    "${phrasesList.size}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    color = Color(0xFF94A3B8),
                                ),
                            )
                        }
                    }

                    // Hindi Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text("Hindi (Teacher says)", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = Color(0xFF166534), fontWeight = FontWeight.SemiBold))
                                Text(selectedPhrase?.hiText ?: "—", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF1E293B)))
                                Text(selectedPhrase?.english ?: "", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, color = Color(0xFF64748B)))
                            }

                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(Color(0xFFF97316), CircleShape)
                                    .clickable { selectedPhrase?.let { speakRow(it) } },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("🔊", fontSize = 16.sp)
                            }
                        }
                    }

                    // Swap divider
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Text("⇅", fontSize = 14.sp, color = Color(0xFF94A3B8))
                    }

                    // Mundari Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF0FDF4), RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFFDCFCE7), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${selectedLang.englishName} (Students hear)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = Color(0xFFC2410C), fontWeight = FontWeight.SemiBold),
                                )
                                Text(
                                    text = OlChikiFont.annotate(selectedPhrase?.targetNative ?: "—"),
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF14532D)),
                                )
                                // The Devanagari the voice is actually given. For Santali this differs
                                // from the line above, which is Ol Chiki — showing both is what lets a
                                // reviewer see the transliteration that feeds the speaker.
                                selectedPhrase?.targetDeva
                                    ?.takeIf { it.isNotBlank() && it != selectedPhrase?.targetNative }
                                    ?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, color = Color(0xFF15803D)),
                                        )
                                    }
                            }

                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(Color(0xFFF97316), CircleShape)
                                    .clickable { selectedPhrase?.let { speakRow(it) } },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("🔊", fontSize = 16.sp)
                            }
                        }
                    }

                    // Was three Slow/Normal/Repeat pills that set a `speedOption` nothing read — the
                    // TTS speed parameter was never plumbed, so all three did the same thing. The
                    // space now carries the provenance, which is the claim a teacher needs.
                    selectedPhrase?.let { row ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            ProvenanceChip(row.provenance)
                            if (row.isPlaceholder) {
                                // A "Verified" chip beside `[unr-1 अनुवाद-लंबित]` would be the exact
                                // false claim §4.5 exists to stop, so the placeholder says so itself.
                                Text(
                                    "\u26A0 placeholder",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFB45309),
                                    ),
                                )
                            }
                        }
                    }

                    // Where this row came from. Replaces an invented "Use in a Sentence" example whose
                    // Mundari was written by hand.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                            .padding(8.dp),
                    ) {
                        Column {
                            Text(
                                "Where this comes from",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = Color(0xFF1E293B)),
                            )
                            Spacer(Modifier.height(2.dp))
                            val row = selectedPhrase
                            if (row == null) {
                                Text(
                                    "Select a phrase.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp, color = Color(0xFF94A3B8)),
                                )
                            } else if (row.isPlaceholder) {
                                // Both facts, because they disagree and a teacher needs to know it:
                                // the app assigns this row VERIFIED, and the string itself is a
                                // placeholder no speaker has written yet.
                                Text(
                                    "This is a PLACEHOLDER, not ${selectedLang.englishName}. " +
                                        "The phrase is seeded so the app can be tested; a named " +
                                        "speaker has not supplied the translation yet.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFB45309),
                                    ),
                                )
                            } else {
                                Text(
                                    text = when (row.provenance) {
                                        Provenance.VERIFIED ->
                                            "Reviewed for classroom use."
                                        Provenance.CORPUS ->
                                            "Quoted from ${row.src ?: "a published corpus"}. " +
                                                "No speaker has reviewed it for this classroom."
                                        Provenance.APPROXIMATE ->
                                            "Close match from the phrasebook — judge before using."
                                        Provenance.MACHINE ->
                                            "Machine translation. No human has seen this string."
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp, color = Color(0xFF334155)),
                                )
                                row.english?.takeIf { it.isNotBlank() }?.let {
                                    Text(
                                        "Corpus translated: \u201C$it\u201D",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp, color = Color(0xFF64748B)),
                                    )
                                }
                                row.pos?.takeIf { it.isNotBlank() }?.let {
                                    Text(
                                        "Part of speech: $it",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp, color = Color(0xFF64748B)),
                                    )
                                }
                                // Cross-source disagreement, shown rather than resolved. No single
                                // authoritative Santali lexicon exists, so a majority vote here would
                                // be inventing a decision the data cannot support.
                                if (row.variants.isNotEmpty()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "Other sources say:",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF92400E)),
                                    )
                                    row.variants.take(3).forEach { v ->
                                        val form = v.substringBefore('|')
                                        val from = v.substringAfter('|', "")
                                        Text(
                                            text = OlChikiFont.annotate(
                                                if (from.isBlank()) form else "$form  ($from)",
                                            ),
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, color = Color(0xFF92400E)),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    speakNote?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 10.5.sp,
                                color = Color(0xFFB45309),
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }
                }
            }
        }

        // 4. Bottom Quick Add Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(14.dp))
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
                .padding(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Lightbulb & Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("💡", fontSize = 16.sp)
                    Column {
                        Text("Quick Add", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF1E293B)))
                        Text("Type your own phrase to translate and save.", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, color = Color(0xFF64748B)))
                    }
                }

                // Text Input
                OutlinedTextField(
                    value = quickAddText,
                    onValueChange = { quickAddText = it },
                    placeholder = { Text("Type a Hindi phrase...", fontSize = 11.5.sp, color = Color(0xFF94A3B8)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF2EAF3B),
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    modifier = Modifier.weight(1f).height(46.dp),
                )

                // Language dropdown pill
                Row(
                    modifier = Modifier
                        .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Hindi → Mundari", fontSize = 11.sp, color = Color(0xFF334155), fontWeight = FontWeight.Medium)
                    Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(12.dp), tint = Color(0xFF64748B))
                }

                // Add Phrase button
                Button(
                    onClick = { quickAddText = "" },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2EAF3B)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(40.dp),
                ) {
                    Icon(Icons.Filled.Add, null, Modifier.size(14.dp), tint = Color.White)
                    Spacer(Modifier.width(4.dp))
                    Text("Add Phrase", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SpeedButton(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                if (isSelected) Color(0xFFDCFCE7) else Color(0xFFF8FAFC),
                RoundedCornerShape(8.dp),
            )
            .border(
                1.dp,
                if (isSelected) Color(0xFF86EFAC) else Color(0xFFE2E8F0),
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 10.5.sp,
                color = if (isSelected) Color(0xFF166534) else Color(0xFF475569),
            ),
        )
    }
}

// `ActionPillButton` lived here — "＋ Add to Class", "📋 Copy Text", "🔗 Share". It had no `onClick`
// parameter at all, so all three were decoration. They are gone rather than wired: "Add to Class" has
// no class model to add to, and "Share" would be an export path for content the offline invariant and
// §6.9.4 keep on the device.

/**
 * Single-letter provenance marker for a dense list row.
 *
 * The full [ProvenanceChip] is used in the detail pane where there is room. Here a hue alone would
 * breach §4.5's rule that colour is never the only signal, so the letter carries it.
 */
@Composable
private fun ProvenanceMark(provenance: Provenance) {
    val (color, letter) = when (provenance) {
        Provenance.VERIFIED -> BolmitraColors.Verified to "V"
        Provenance.CORPUS -> BolmitraColors.Corpus to "C"
        Provenance.APPROXIMATE -> BolmitraColors.Approximate to "A"
        Provenance.MACHINE -> BolmitraColors.Unavailable to "M"
    }
    Box(
        modifier = Modifier
            .size(16.dp)
            .background(color.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            letter,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = color,
            ),
        )
    }
}
