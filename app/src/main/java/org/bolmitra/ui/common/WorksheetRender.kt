package org.bolmitra.ui.common

import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.bolmitra.curriculum.Flashcard
import org.bolmitra.curriculum.FlashcardDeck
import org.bolmitra.curriculum.GeneratedItem
import org.bolmitra.curriculum.PictureBank
import org.bolmitra.curriculum.Sheet
import org.bolmitra.curriculum.SolutionStrategy

/**
 * Rendering for generated worksheets and flashcards — ARCHITECTURE.md §6.16.
 *
 * ### Where the pictures come from
 *
 * The platform's colour emoji font, not bundled art. `PictureBank`'s class note has the full
 * reasoning; the part that matters here is that the app ships no image, so there is nothing to
 * decode, cache or scale, and a picture costs one `Text`.
 *
 * ### Why [rememberGlyphSupport] exists
 *
 * Coverage was measured against the test tablet's own font and every planned glyph was present. That
 * tablet runs Android 14; `minSdk` is 28, and a FLOOR-tier device on Android 9 carries an older
 * emoji font. An absent glyph renders as a tofu box, which on a child's worksheet is worse than no
 * picture at all because the child cannot tell it was meant to be something.
 *
 * `Paint.hasGlyph` answers the question directly rather than by guessing from an API level, so a
 * glyph the device cannot draw falls back to a letter tile carrying the Hindi initial — still a
 * distinct, nameable card. The check runs once per composition and covers the whole bank.
 */
object WorksheetRender {

    /** Largest number of pictures drawn on one line before wrapping. */
    const val PER_ROW = 10
}

/**
 * Which of the picture bank's glyphs this device can actually draw.
 *
 * Returns the set of English keys that are safe to render as a glyph. Computed once and remembered:
 * `hasGlyph` allocates a `Paint` and consults the font stack, which is not something to do per item
 * per recomposition.
 */
@Composable
fun rememberGlyphSupport(): Set<String> = remember {
    val paint = Paint()
    PictureBank.pictures
        .filter { it.glyph.isNotEmpty() && paint.hasGlyph(it.glyph) }
        .map { it.english }
        .toSet()
}

/**
 * One picture: an emoji, a colour swatch, or a letter tile when the device cannot draw the glyph.
 *
 * [term] is the English `PictureBank` key. An unknown term draws nothing rather than a placeholder,
 * because an item with no picture is a valid item — arithmetic has no picture at all.
 */
@Composable
fun PictureGlyph(
    term: String?,
    supported: Set<String>,
    modifier: Modifier = Modifier,
    sizeDp: Int = 34,
) {
    val picture = term?.let { PictureBank.byEnglish(it) } ?: return

    when {
        // Colours are a drawn swatch: exact, and cheaper than a glyph.
        picture.colourArgb != null -> Box(
            modifier = modifier
                .size(sizeDp.dp)
                .background(Color(picture.colourArgb), CircleShape)
                // Outlined so a white swatch is still visible on a white sheet.
                .border(1.5.dp, Color(0xFF94A3B8), CircleShape),
        )

        picture.english in supported -> Text(
            text = picture.glyph,
            modifier = modifier,
            fontSize = (sizeDp * 0.82).sp,
            // No colour set: a colour emoji draws its own, and tinting it would flatten the artwork.
        )

        // Fallback: the Hindi initial in a tile. Nameable, unlike a tofu box.
        else -> Box(
            modifier = modifier
                .size(sizeDp.dp)
                .background(Color(0xFFE0F2FE), RoundedCornerShape(6.dp))
                .border(1.dp, Color(0xFF7DD3FC), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = picture.hindi.take(1),
                fontSize = (sizeDp * 0.45).sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0C4A6E),
            )
        }
    }
}

/**
 * The pictures a counting item asks the child to count.
 *
 * Wrapped into rows of [WorksheetRender.PER_ROW] rather than a single line. Twenty mangoes across
 * one row on a 2880-wide panel would be legible; on a FLOOR-tier 800-wide screen they would not, and
 * a counting exercise the child cannot resolve into separate objects is not a counting exercise.
 */
@Composable
fun CountingStrip(
    term: String?,
    count: Int,
    supported: Set<String>,
    modifier: Modifier = Modifier,
) {
    if (term == null || count <= 0) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // chunked() over a range: the count is the item's own binding, so this draws exactly what
        // the answer key says.
        (1..count).chunked(WorksheetRender.PER_ROW).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { _ -> PictureGlyph(term, supported, sizeDp = 28) }
            }
        }
    }
}

/**
 * A generated worksheet as a child would see it.
 *
 * [showAnswers] draws the teacher's key. Off by default, which is the safe direction: a sheet shown
 * to a class with the answers on it is a wasted lesson, and the mistake is not recoverable once the
 * children have seen it.
 */
@Composable
fun GeneratedSheetView(
    sheet: Sheet,
    modifier: Modifier = Modifier,
    showAnswers: Boolean = false,
) {
    val supported = rememberGlyphSupport()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column {
            Text(
                text = sheet.titleHindi,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                ),
            )
            Text(
                // The outcome positions, so the alignment claim on this sheet is checkable rather
                // than asserted. Labelled as a citation, never as an official NIPUN code.
                text = "NIPUN लक्ष्य: ${sheet.lakshyaCodes.joinToString(", ")}  •  " +
                    "${sheet.items.size} प्रश्न",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                ),
            )
        }

        sheet.items.forEachIndexed { index, item ->
            WorksheetItemRow(index + 1, item, supported, showAnswers)
        }

        // Relaxations are shown, not swallowed. A teacher reading a Hindi-only line is entitled to
        // know the corpus had no word for it, rather than assuming the sheet is bilingual.
        sheet.relaxations.forEach { relaxation ->
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFFBEB), RoundedCornerShape(6.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text("⚠", fontSize = 12.sp, color = Color(0xFF92400E))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = relaxation.reason,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp,
                        color = Color(0xFF92400E),
                    ),
                )
            }
        }
    }
}

@Composable
private fun WorksheetItemRow(
    number: Int,
    item: GeneratedItem,
    supported: Set<String>,
    showAnswers: Boolean,
) {
    val count = item.bindings["count"]?.toIntOrNull()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "$number.",
            modifier = Modifier.width(24.dp),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color(0xFF475569),
            ),
        )

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = item.hiText,
                // 15 sp floor: a child reads this, and it is the one thing on the row that must be
                // legible from a desk.
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    color = Color(0xFF0F172A),
                ),
            )

            if (count != null && item.strategy == SolutionStrategy.COUNTING) {
                CountingStrip(item.pictureTerm, count, supported)
            } else if (item.pictureTerm != null) {
                PictureGlyph(item.pictureTerm, supported, sizeDp = 40)
            }

            // The target line is drawn only when it carries something the Hindi line does not.
            // For arithmetic it is the same expression, and printing "2 + 1 = ___" twice is noise.
            val target = item.targetText
            if (target.isNotBlank() && target != item.hiText && target.trim('_', ' ').isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        // annotate() styles only the Ol Chiki runs; applying the bundled face to the
                        // whole string would tofu a Devanagari target like Mundari's.
                        text = OlChikiFont.annotate(target),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF14532D),
                        ),
                    )
                    item.targetProvenance?.let {
                        Spacer(Modifier.width(8.dp))
                        ProvenanceMark(it.name.first(), it.name)
                    }
                }
                // CORPUS is meaningless without naming the corpus, so the source travels with it.
                item.targetSrc?.let {
                    Text(
                        text = "स्रोत: $it",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.5.sp,
                            color = Color(0xFF94A3B8),
                        ),
                    )
                }
            }

            if (showAnswers) {
                Text(
                    text = "उत्तर: ${item.answer ?: "—"}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB45309),
                    ),
                )
            }
        }
    }
}

/**
 * Provenance marker for a worksheet line.
 *
 * A letter and a word, not a hue alone: §4.5 requires that colour is never the only signal, and a
 * worksheet may well be printed in black and white, where a hue carries nothing whatsoever.
 */
@Composable
private fun ProvenanceMark(initial: Char, label: String) {
    Row(
        modifier = Modifier
            .background(Color(0xFFECFDF5), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$initial",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF047857),
        )
        Spacer(Modifier.width(3.dp))
        Text(text = label, fontSize = 8.5.sp, color = Color(0xFF047857))
    }
}

/**
 * A flashcard deck.
 *
 * Laid out in fixed-width rows rather than a lazy grid, because a deck is at most a couple of dozen
 * cards and a nested scrolling container inside the screen's own scroll is the usual way this kind
 * of pane starts fighting itself.
 */
@Composable
fun FlashcardGrid(
    deck: FlashcardDeck,
    modifier: Modifier = Modifier,
    columns: Int = 4,
) {
    val supported = rememberGlyphSupport()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "${deck.titleHindi} — ${deck.cards.size} कार्ड " +
                "(${deck.bilingualCardCount} द्विभाषी)",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color(0xFF334155),
            ),
        )
        deck.cards.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { card ->
                    FlashcardTile(card, supported, Modifier.weight(1f))
                }
                // Pads a short final row so the tiles keep their width instead of stretching.
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun FlashcardTile(
    card: Flashcard,
    supported: Set<String>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Color.White, RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PictureGlyph(card.term, supported, sizeDp = 44)

        Text(
            text = card.hindi,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
            ),
            textAlign = TextAlign.Center,
        )

        // A card is allowed to be Hindi-only. It is never allowed to be Hindi plus a guess, so the
        // absent case says so in words rather than leaving a gap that reads as a translation.
        if (card.targetNative != null) {
            Text(
                text = OlChikiFont.annotate(card.targetNative),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF14532D),
                ),
                textAlign = TextAlign.Center,
            )
            card.targetProvenance?.let { ProvenanceMark(it.name.first(), it.name) }
        } else {
            Text(
                text = "शब्द उपलब्ध नहीं",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.5.sp,
                    color = Color(0xFF94A3B8),
                ),
                textAlign = TextAlign.Center,
            )
        }
    }
}
