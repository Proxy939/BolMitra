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
import androidx.compose.material.icons.filled.Add
import org.bolmitra.ui.common.BolMitraIcons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
import org.bolmitra.speech.LiveTurnEngine
import org.bolmitra.speech.TargetLanguage

data class PhraseCategory(val name: String, val count: Int, val emoji: String)

data class PhraseItemModel(
    val id: String,
    val iconEmoji: String,
    val hiText: String,
    val enText: String,
    val nativeText: String,
    val nativeScript: String,
    var isFavorite: Boolean = false,
    val exampleHi: String = "",
    val exampleEn: String = "",
    val exampleNative: String = "",
)

/**
 * Phrasebook Screen — Exact replica of Image 4.
 */
@Composable
fun PhrasebookScreenPane(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { LiveTurnEngine.get(context) }
    val phrasebook = remember { InMemoryPhrasebook(DemoSeed.phrases) }

    var selectedLang by remember { mutableStateOf(TargetLanguage.MUNDARI) }
    var selectedCategoryIndex by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var quickAddText by remember { mutableStateOf("") }

    val categories = remember {
        listOf(
            PhraseCategory("All Phrases", 120, "⊞"),
            PhraseCategory("Greetings", 15, "👋"),
            PhraseCategory("Classroom Instructions", 22, "👥"),
            PhraseCategory("Questions", 18, "❓"),
            PhraseCategory("Numbers", 20, "🔢"),
            PhraseCategory("Colors", 12, "🎨"),
            PhraseCategory("Family & People", 16, "👨‍👩‍👧"),
            PhraseCategory("Animals", 14, "🐾"),
            PhraseCategory("Food & Fruits", 13, "🍎"),
            PhraseCategory("Daily Activities", 18, "🏃"),
            PhraseCategory("Positive Encouragement", 10, "❤️"),
            PhraseCategory("Common Responses", 17, "💬"),
            PhraseCategory("Emergency / Help", 8, "⚠️"),
        )
    }

    val phrasesList = remember {
        listOf(
            PhraseItemModel(
                "1", "🪑", "बैठ जाओ", "Sit down", "बेसो", "Beso (Sit down)", true,
                "सब लोग बैठ जाओ।", "Everyone sit down.", "साबे बेसो। (Sabe beso.)",
            ),
            PhraseItemModel(
                "2", "🧍", "खड़े हो जाओ", "Stand up", "दाड़ा", "Daada (Stand up)", false,
                "सभी बच्चे खड़े हो जाओ।", "All children stand up.", "साबे दाड़ा।",
            ),
            PhraseItemModel(
                "3", "👂", "ध्यान से सुनो", "Listen carefully", "सेंते रे सुतु", "Sente re sutu", false,
                "मेरी बात ध्यान से सुनो।", "Listen to me carefully.", "सेंते रे सुतु।",
            ),
            PhraseItemModel(
                "4", "🔄", "दोहराओ", "Repeat", "मिते होड़ो", "Mite hodo", false,
                "मेरे बाद दोहराओ।", "Repeat after me.", "मिते होड़ो।",
            ),
            PhraseItemModel(
                "5", "👤", "मेरा नाम ... है", "My name is ...", "एन्हे नाव ... एना", "Enhe naw ... ena", false,
                "मेरा नाम सोमा है।", "My name is Soma.", "एन्हे नाव सोमा एना।",
            ),
            PhraseItemModel(
                "6", "😊", "तुम कैसे हो?", "How are you?", "निंन्हे काना?", "Ninhe kana?", false,
                "आप सब कैसे हैं?", "How are you all?", "निंन्हे काना?",
            ),
            PhraseItemModel(
                "7", "👍", "बहुत अच्छा", "Very good", "बाहा चोके", "Baha choke", false,
                "आपने बहुत अच्छा लिखा।", "You wrote very well.", "बाहा चोके।",
            ),
            PhraseItemModel(
                "8", "👏", "शाबाश!", "Well done!", "जोहा!", "Joha!", false,
                "शाबाश बच्चों!", "Well done children!", "जोहा!",
            ),
        )
    }

    var selectedPhrase by remember { mutableStateOf(phrasesList.first()) }
    var speedOption by remember { mutableStateOf(1) } // 0: Slow, 1: Normal, 2: Repeat

    fun speakPhrase(hi: String) {
        scope.launch {
            val hit = phrasebook.lookup(hi)
            withContext(Dispatchers.Default) {
                when (engine.ensureLoaded()) {
                    is LiveTurnEngine.LoadState.Ready -> {
                        val ref = hit?.phrase?.audioRef
                        val player = engine.audioPlayer
                        if (player != null && ref != null) {
                            player.play(ref)
                        }
                    }
                    else -> Unit
                }
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
                    Text(
                        if (searchQuery.isEmpty()) "Search phrases... (e.g., greeting, number, sit down)" else searchQuery,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            color = if (searchQuery.isEmpty()) Color(0xFF94A3B8) else Color(0xFF1E293B),
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Text("⇄", color = Color(0xFF16A34A), fontSize = 14.sp)
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
                            "All Phrases (120)",
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
                            Text("Sort: Most Used", fontSize = 10.sp, color = Color(0xFF475569))
                            Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(12.dp), tint = Color(0xFF475569))
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(phrasesList) { phrase ->
                            val isSelected = selectedPhrase.id == phrase.id
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
                                    .clickable { selectedPhrase = phrase }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(phrase.iconEmoji, fontSize = 18.sp)
                                    Column {
                                        Text(
                                            phrase.hiText,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = Color(0xFF1E293B),
                                            ),
                                        )
                                        Text(
                                            phrase.enText,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 11.sp,
                                                color = Color(0xFF64748B),
                                            ),
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("मुंडारी", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = Color(0xFF94A3B8)))
                                        Text(
                                            phrase.nativeText,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp,
                                                color = Color(0xFF166534),
                                            ),
                                        )
                                    }

                                    // Speaker Play button
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clickable { speakPhrase(phrase.hiText) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text("🔊", fontSize = 13.sp)
                                    }

                                    // Favorite star
                                    Icon(
                                        imageVector = if (phrase.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                        contentDescription = null,
                                        tint = if (phrase.isFavorite) Color(0xFFF97316) else Color(0xFFCBD5E1),
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { phrase.isFavorite = !phrase.isFavorite },
                                    )
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
                        Icon(Icons.Filled.Favorite, null, Modifier.size(16.dp), tint = Color(0xFFEF4444))
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
                                Text(selectedPhrase.hiText, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF1E293B)))
                                Text(selectedPhrase.enText, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, color = Color(0xFF64748B)))
                            }

                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(Color(0xFFF97316), CircleShape)
                                    .clickable { speakPhrase(selectedPhrase.hiText) },
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
                            Column {
                                Text("Mundari (Students hear)", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = Color(0xFFC2410C), fontWeight = FontWeight.SemiBold))
                                Text(selectedPhrase.nativeText, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF14532D)))
                                Text(selectedPhrase.nativeScript, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, color = Color(0xFF15803D)))
                            }

                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(Color(0xFFF97316), CircleShape)
                                    .clickable { speakPhrase(selectedPhrase.hiText) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("🔊", fontSize = 16.sp)
                            }
                        }
                    }

                    // Playback speed options: Slow / Normal / Repeat
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        SpeedButton("🐢 Slow", isSelected = speedOption == 0) { speedOption = 0 }
                        SpeedButton("▶ Normal", isSelected = speedOption == 1) { speedOption = 1 }
                        SpeedButton("🔁 Repeat", isSelected = speedOption == 2) { speedOption = 2 }
                    }

                    // Use in a Sentence Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                            .padding(8.dp),
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Use in a Sentence", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = Color(0xFF1E293B)))
                                Text("🔊", fontSize = 11.sp, modifier = Modifier.clickable { speakPhrase(selectedPhrase.exampleHi) })
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(selectedPhrase.exampleHi, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Color(0xFF334155)))
                            Text(selectedPhrase.exampleEn, style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp, color = Color(0xFF64748B)))
                            Text(selectedPhrase.exampleNative, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium, fontSize = 10.5.sp, color = Color(0xFF166534)))
                        }
                    }

                    // Action buttons row: + Add to Class | Copy Text | Share
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ActionPillButton("＋ Add to Class", Modifier.weight(1.2f))
                        ActionPillButton("📋 Copy Text", Modifier.weight(1f))
                        ActionPillButton("🔗 Share", Modifier.weight(0.8f))
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

@Composable
private fun ActionPillButton(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Color(0xFF334155))
    }
}
