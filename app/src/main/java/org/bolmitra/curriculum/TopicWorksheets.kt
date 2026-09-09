package org.bolmitra.curriculum

import org.bolmitra.phrasebook.HindiNormalizer
import org.bolmitra.phrasebook.Provenance

/**
 * Turning "what the teacher just talked about" into a solvable bilingual sheet — §6.16.
 *
 * Three things happen here, in order: [TopicDetector] reads a Hindi transcript and proposes a topic,
 * [TopicWorksheets.modelsFor] produces item templates for that topic within the NIPUN ceiling for the
 * grade, and [TopicWorksheets.build] generates, orders and *resolves* the items.
 *
 * ### The resolve pass, and why it exists
 *
 * An item needs several things that must agree: a picture, the Hindi word for it, the target-language
 * word, and an answer. Binding those as four independent slots is how they come to disagree — the
 * generator draws each slot separately, so a sheet ends up showing a cow above the word for goat.
 *
 * So exactly one slot is bound: the **English `PictureBank` key**, which is also the glossary's join
 * column. Everything else is derived from it afterwards. `{hi}` and `{target}` are left unfilled by
 * [ItemGenerator] — it replaces only slots it knows and leaves the rest of the text alone — and this
 * file fills them once the key is known. One source of truth, so nothing can drift out of step.
 *
 * ### Where the target-language words come from
 *
 * Never from this file. [TargetWord] values are read from the shipped glossary, keep the
 * [Provenance] and `src` the glossary gave them, and travel onto the item. When a word is absent the
 * line degrades to Hindi plus the picture and the sheet *reports* how many lines that happened to,
 * rather than leaving a blank a teacher might read as a translation.
 */

/** A topic a sheet can be built about. [pictureCategory] is null for pure arithmetic. */
enum class WorksheetTopic(
    val hindiLabel: String,
    val englishLabel: String,
    val pictureCategory: PictureBank.Category?,
) {
    NUMBERS("गिनती और जोड़-घटाव", "Numbers and arithmetic", null),
    ANIMALS("जानवर", "Animals", PictureBank.Category.ANIMAL),
    BIRDS("पक्षी", "Birds", PictureBank.Category.BIRD),
    FRUITS("फल और खाना", "Fruit and food", PictureBank.Category.FOOD),
    VEGETABLES("सब्ज़ियाँ", "Vegetables", PictureBank.Category.VEGETABLE),
    BODY("शरीर के अंग", "Body parts", PictureBank.Category.BODY),
    FAMILY("परिवार", "Family", PictureBank.Category.FAMILY),
    COLOURS("रंग", "Colours", PictureBank.Category.COLOUR),
    NATURE("प्रकृति", "Nature", PictureBank.Category.NATURE),
    SCHOOL("पाठशाला की चीज़ें", "School things", PictureBank.Category.SCHOOL),
    ;

    val isArithmetic: Boolean get() = this == NUMBERS
}

/** A target-language word read from the glossary, with the provenance it arrived carrying. */
data class TargetWord(
    /** Glossary `en` column — the join key, matching [PictureBank.Picture.english]. */
    val english: String,
    /** What the class sees. Ol Chiki for Santali. */
    val native: String,
    val provenance: Provenance,
    /** Corpus this came from. Non-null in practice; `CORPUS` is meaningless without it. */
    val src: String?,
)

/**
 * What the teacher's words suggest the sheet should be about.
 *
 * [pictureCategory] is carried separately from [topic] on purpose. "पाँच आम गिनो" is an arithmetic
 * lesson *illustrated with mangoes* — the instruction decides the topic, the nouns decide the
 * pictures, and collapsing the two would either lose the counting or lose the mangoes.
 */
data class TopicMatch(
    val topic: WorksheetTopic,
    val pictureCategory: PictureBank.Category?,
    /** The words that caused this match, so the teacher can see why. */
    val evidence: List<String>,
    /** True when an explicit topic word was found, rather than the topic being inferred from nouns. */
    val viaKeyword: Boolean,
)

/**
 * Maps a Hindi transcript to a topic.
 *
 * `ponytail:` Token matching against two authored word lists, no model. The ceiling is that it
 * only recognises words it has been told about and it cannot parse a sentence — "आम तौर पर" contains
 * the word for mango and will vote for food. That is why [detect] returns a *suggestion* carrying
 * its [TopicMatch.evidence], the caller shows it, and the teacher can override; a wrong guess costs
 * a tap, not a wrong worksheet. Upgrade path: score by position and part of speech using the
 * glossary's own `pos` column, which is already loaded.
 */
object TopicDetector {

    /**
     * Explicit topic words. Deliberately disjoint from [PictureBank] vocabulary: these name a
     * *subject*, the picture words name things, and keeping them separate is what lets an
     * arithmetic instruction and a noun both count without one overwriting the other.
     */
    private val keywords: Map<WorksheetTopic, List<String>> = mapOf(
        WorksheetTopic.NUMBERS to listOf(
            "गिनती", "गिनो", "गिनना", "गिनकर", "संख्या", "अंक", "जोड़", "जोड़ो", "घटाव", "घटाओ",
            "गुणा", "भाग", "पहाड़ा", "पहाड़े", "गणित", "हिसाब", "जमा",
        ),
        WorksheetTopic.ANIMALS to listOf("जानवर", "पशु", "जानवरों"),
        WorksheetTopic.BIRDS to listOf("पक्षी", "पक्षियों", "चिड़ियाँ"),
        WorksheetTopic.FRUITS to listOf("फल", "फलों", "खाना", "मिठाई"),
        WorksheetTopic.VEGETABLES to listOf("सब्ज़ी", "सब्जी", "सब्ज़ियाँ", "सब्जियां", "तरकारी"),
        WorksheetTopic.BODY to listOf("शरीर", "अंग", "अंगों", "बदन"),
        WorksheetTopic.FAMILY to listOf("परिवार", "रिश्ते", "घरवाले"),
        WorksheetTopic.COLOURS to listOf("रंग", "रंगों", "रंगीन"),
        WorksheetTopic.NATURE to listOf("प्रकृति", "मौसम", "आसमान", "बगीचा"),
        WorksheetTopic.SCHOOL to listOf("पाठशाला", "विद्यालय", "स्कूल", "कक्षा", "पढ़ाई"),
    )

    /**
     * Best topic for [hindi], or null when nothing matched.
     *
     * **Null rather than a default.** Silently falling back to arithmetic would put a sheet in front
     * of a class that the lesson never asked for, and the teacher would have no way to tell it was a
     * guess. "I could not tell, choose a topic" is the honest result.
     */
    fun detect(hindi: String): TopicMatch? {
        if (hindi.isBlank()) return null
        val tokens = tokenise(hindi)
        if (tokens.isEmpty()) return null

        // Pass 1: explicit topic words.
        val keywordHits = LinkedHashMap<WorksheetTopic, MutableList<String>>()
        for ((topic, words) in keywords) {
            for (word in words) {
                val normalised = HindiNormalizer.normalize(word)
                if (tokens.any { it.matchesStem(normalised) }) {
                    keywordHits.getOrPut(topic) { mutableListOf() } += word
                }
            }
        }

        // Pass 2: picture nouns, which decide what gets drawn.
        val nounHits = LinkedHashMap<PictureBank.Category, MutableList<String>>()
        for (picture in PictureBank.pictures) {
            val normalised = HindiNormalizer.normalize(picture.hindi)
            if (tokens.any { it.matchesStem(normalised) }) {
                nounHits.getOrPut(picture.category) { mutableListOf() } += picture.hindi
            }
        }

        val topNounCategory = nounHits.maxByOrNull { it.value.size }?.key

        // Arithmetic wins the topic when an arithmetic instruction is present, because the verb is
        // the lesson and the nouns are only what it is counting.
        keywordHits[WorksheetTopic.NUMBERS]?.let { hits ->
            return TopicMatch(
                topic = WorksheetTopic.NUMBERS,
                pictureCategory = topNounCategory,
                evidence = hits + nounHits.values.flatten(),
                viaKeyword = true,
            )
        }

        keywordHits.maxByOrNull { it.value.size }?.let { (topic, hits) ->
            return TopicMatch(
                topic = topic,
                pictureCategory = topic.pictureCategory ?: topNounCategory,
                evidence = hits,
                viaKeyword = true,
            )
        }

        // No topic word at all: infer from the nouns that were named.
        if (topNounCategory != null) {
            val topic = WorksheetTopic.entries.first { it.pictureCategory == topNounCategory }
            return TopicMatch(
                topic = topic,
                pictureCategory = topNounCategory,
                evidence = nounHits.getValue(topNounCategory),
                viaKeyword = false,
            )
        }
        return null
    }

    /** Normalise once, then split. The normaliser is what makes "5" and "पाँच" the same token. */
    private fun tokenise(hindi: String): List<String> =
        HindiNormalizer.normalize(hindi)
            .split(' ', '\t', '\n', ',', '।', '?', '!')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /**
     * Token match with a suffix allowance for Hindi inflection.
     *
     * `जानवरों` must match `जानवर` and `फलों` must match `फल`. Prefix matching in that direction
     * only — requiring the *token* to start with the keyword — so a short keyword cannot swallow an
     * unrelated longer word from the other side.
     */
    private fun String.matchesStem(keyword: String): Boolean =
        this == keyword || (length > keyword.length && startsWith(keyword))
}

/** A generated sheet, with everything needed to reprint or share it. */
data class Sheet(
    val topic: WorksheetTopic,
    val gradeBand: GradeBand,
    val seed: Long,
    val titleHindi: String,
    val items: List<GeneratedItem>,
    /** NIPUN outcome positions this sheet practises. Citations, not official codes. */
    val lakshyaCodes: List<String>,
    /** Everything that had to give, surfaced rather than hidden. */
    val relaxations: List<Relaxation>,
    /** Glossary pack the target words came from, so a shared sheet can be checked against it. */
    val packVersion: String?,
) {
    /** Items whose target-language line is real, as opposed to Hindi-only. */
    val bilingualItemCount: Int get() = items.count { it.targetProvenance != null }
}

sealed interface SheetResult {
    data class Ready(val sheet: Sheet) : SheetResult

    /** Not enough content for this topic and grade. Carries what to author, as §6.16 requires. */
    data class NotEnoughContent(
        val topic: WorksheetTopic,
        val gradeBand: GradeBand,
        val diagnosis: String,
    ) : SheetResult
}

/**
 * One flashcard: a picture with the word in both languages.
 *
 * [targetNative] is nullable and stays null when the corpus has no word for this term. That is the
 * whole discipline of this type — a card is allowed to be Hindi-only, and is never allowed to be
 * Hindi plus a guess. [targetProvenance] is null in exactly the same case, so a card can never
 * display a trust marker it did not earn.
 */
data class Flashcard(
    /** English `PictureBank` key, and the glossary join column. */
    val term: String,
    val glyph: String,
    /** Set only for colours, where a drawn swatch replaces the glyph. */
    val colourArgb: Long?,
    val hindi: String,
    val targetNative: String?,
    val targetProvenance: Provenance?,
    val targetSrc: String?,
) {
    val isBilingual: Boolean get() = targetNative != null
}

/** A deck for one topic. */
data class FlashcardDeck(
    val topic: WorksheetTopic,
    val titleHindi: String,
    val cards: List<Flashcard>,
    val packVersion: String?,
) {
    val bilingualCardCount: Int get() = cards.count { it.isBilingual }
}

/**
 * Builds flashcard decks from the same picture bank and glossary the worksheets use.
 *
 * Sharing the source is the point: a child who practised on the animals sheet meets the same
 * drawings and the same corpus words on the animals cards, rather than a second vocabulary that
 * happens to overlap.
 */
object Flashcards {

    /**
     * Cards for [topic].
     *
     * Cards that have a target word come first. Not cosmetic: the deck is for a bilingual class, so
     * the cards that can actually do that job should be the ones a teacher reaches first, and the
     * Hindi-only remainder is still there rather than hidden. Order within each group follows
     * [PictureBank], so a deck is stable across runs and needs no seed.
     */
    fun deckFor(
        topic: WorksheetTopic,
        vocabulary: Map<String, TargetWord> = emptyMap(),
        packVersion: String? = null,
        limit: Int = Int.MAX_VALUE,
    ): FlashcardDeck {
        val pictures = TopicWorksheets.defaultPictures(topic)
        val cards = pictures.map { picture ->
            val word = vocabulary[picture.english.lowercase()]
            Flashcard(
                term = picture.english,
                glyph = picture.glyph,
                colourArgb = picture.colourArgb,
                hindi = picture.hindi,
                targetNative = word?.native,
                targetProvenance = word?.provenance,
                targetSrc = word?.src,
            )
        }
        // sortedByDescending is stable in Kotlin, so PictureBank order survives inside each group.
        return FlashcardDeck(
            topic = topic,
            titleHindi = topic.hindiLabel,
            cards = cards.sortedByDescending { it.isBilingual }.take(limit),
            packVersion = packVersion,
        )
    }

    /** Decks for every topic that has pictures. */
    fun allDecks(
        vocabulary: Map<String, TargetWord> = emptyMap(),
        packVersion: String? = null,
    ): List<FlashcardDeck> = WorksheetTopic.entries
        .filter { it.pictureCategory != null }
        .map { deckFor(it, vocabulary, packVersion) }

    /** Self-check: a card must never carry a provenance without a word, or a word without one. */
    fun validate(vocabulary: Map<String, TargetWord> = emptyMap()): List<String> = buildList {
        allDecks(vocabulary).forEach { deck ->
            if (deck.cards.isEmpty()) add("${deck.topic.name} deck is empty")
            deck.cards.forEach { card ->
                if (card.hindi.isBlank()) add("${deck.topic.name}/${card.term} has no Hindi")
                if ((card.targetNative == null) != (card.targetProvenance == null)) {
                    add("${deck.topic.name}/${card.term} has a word without provenance or vice versa")
                }
                if (card.targetProvenance == Provenance.CORPUS && card.targetSrc == null) {
                    add("${deck.topic.name}/${card.term} is CORPUS with no source named")
                }
            }
            // A repeated card in a deck is a child seeing the same word twice in one pass.
            val terms = deck.cards.map { it.term }
            if (terms.size != terms.distinct().size) add("${deck.topic.name} deck repeats a card")
        }
    }
}

object TopicWorksheets {

    /**
     * Picture-counting is capped here regardless of the grade's number ceiling.
     *
     * Class 2 counts to 99, and ninety-nine drawings on one line is not a counting exercise, it is
     * a wall. Twenty is both the drawable limit and exactly Class 1's stated range, so the cap
     * costs nothing at Class 1 and Class 2 gets its larger numbers through numerals and tables
     * instead. Named rather than inlined because it is a judgement, not a fact from the source.
     */
    const val MAX_DRAWN_OBJECTS = 20

    /** Bound on the generation loop, mirroring the assembler's own cycle guard. */
    private const val MODEL_CYCLES = 4

    /**
     * Item templates for [topic] at [band], drawing pictures from [pictures].
     *
     * Slot ordering is part of each model's contract — `ItemGenerator` consumes entropy in declared
     * order, so inserting a slot would reshuffle every sheet ever issued. Add new slots at the end.
     */
    fun modelsFor(
        topic: WorksheetTopic,
        band: GradeBand,
        pictures: List<PictureBank.Picture> = defaultPictures(topic),
        /** Lowercased English keys the glossary has a target word for. See [vocabularyModels]. */
        withTargetWords: Set<String> = emptySet(),
    ): List<ItemModel> {
        // English keys, because that is the one binding everything else is derived from.
        val keys = pictures.map { it.english }
        if (keys.isEmpty()) return emptyList()

        return if (topic.isArithmetic) {
            arithmeticModels(band, keys)
        } else {
            vocabularyModels(band, keys, withTargetWords)
        }
    }

    /**
     * Pictures a topic illustrates itself with.
     *
     * Arithmetic has no category of its own, so it counts fruit — a choice, not a fact, and the
     * caller overrides it whenever the lesson named something else.
     */
    fun defaultPictures(topic: WorksheetTopic): List<PictureBank.Picture> =
        topic.pictureCategory
            ?.let { PictureBank.forCategory(it) }
            ?: PictureBank.forCategory(PictureBank.Category.FOOD)

    // -------------------------------------------------------------------------------------------
    // Arithmetic
    // -------------------------------------------------------------------------------------------

    private fun arithmeticModels(band: GradeBand, keys: List<String>): List<ItemModel> =
        if (band == GradeBand.GRADE_2) grade2Arithmetic(keys) else grade1Arithmetic(keys)

    /** Class 1: operands to 9, sums to 20, counting to 20. Both sources agreed on those. */
    private fun grade1Arithmetic(keys: List<String>): List<ItemModel> = listOf(
        ItemModel(
            modelId = "nipun-c1-count-pictures-v1",
            lakshyaCode = "C1-NUM-1",
            strategy = SolutionStrategy.COUNTING,
            hiTemplate = "{hi} गिनो और संख्या लिखो: ______",
            // No target-language words: the child writes a numeral, which every script shares.
            targetTemplate = "______",
            slots = listOf(
                // Radical: counting three things and counting twelve are not the same task.
                Slot.Radical("count", listOf("3", "5", "8", "12")),
                Slot.Incidental("picture", keys),
            ),
            answerTemplate = "{count}",
            pictureSlot = "picture",
            derivedPlaceholders = setOf("hi"),
        ),
        ItemModel(
            modelId = "nipun-c1-add-no-carry-v1",
            lakshyaCode = "C1-NUM-3",
            strategy = SolutionStrategy.ADDITION_NO_CARRY,
            hiTemplate = "{a} + {b} = ______",
            targetTemplate = "{a} + {b} = ______",
            slots = listOf(
                // Sums stay under ten here; the carrying model below is a separate strategy.
                Slot.Radical("a", listOf("2", "3", "4", "5")),
                Slot.Radical("b", listOf("1", "2", "3", "4")),
            ),
        ),
        ItemModel(
            modelId = "nipun-c1-add-carry-v1",
            lakshyaCode = "C1-NUM-3",
            strategy = SolutionStrategy.ADDITION_WITH_CARRY,
            hiTemplate = "{a} + {b} = ______",
            targetTemplate = "{a} + {b} = ______",
            slots = listOf(
                // Largest pair is 9 + 8 = 17, inside the stated ceiling of 20.
                Slot.Radical("a", listOf("6", "7", "8", "9")),
                Slot.Radical("b", listOf("5", "6", "7", "8")),
            ),
        ),
        ItemModel(
            modelId = "nipun-c1-subtract-v1",
            lakshyaCode = "C1-NUM-3",
            strategy = SolutionStrategy.SUBTRACTION_NO_BORROW,
            hiTemplate = "{a} − {b} = ______",
            targetTemplate = "{a} − {b} = ______",
            slots = listOf(
                // Every combination stays positive: the smallest is 6 − 4.
                Slot.Radical("a", listOf("9", "8", "7", "6")),
                Slot.Radical("b", listOf("1", "2", "3", "4")),
            ),
        ),
        ItemModel(
            modelId = "nipun-c1-pattern-v1",
            lakshyaCode = "C1-NUM-2",
            strategy = SolutionStrategy.PATTERN_EXTENSION,
            // {sequence} is resolved from {pattern}, so the shown run and the answer come from one
            // value and cannot disagree.
            hiTemplate = "पैटर्न पूरा करो: {sequence}",
            targetTemplate = "{sequence}",
            slots = listOf(
                // "start,step" — the run stays inside 20 for every value here.
                Slot.Radical("pattern", listOf("2,2", "1,3", "5,5", "4,4")),
            ),
            derivedPlaceholders = setOf("sequence"),
        ),
    )

    /** Class 2: numbers to 99, and the tables of 2, 3 and 4. */
    private fun grade2Arithmetic(keys: List<String>): List<ItemModel> = listOf(
        ItemModel(
            modelId = "nipun-c2-count-pictures-v1",
            lakshyaCode = "C2-NUM-1",
            strategy = SolutionStrategy.COUNTING,
            hiTemplate = "{hi} गिनो और संख्या लिखो: ______",
            targetTemplate = "______",
            slots = listOf(
                // Capped at MAX_DRAWN_OBJECTS; Class 2 reaches 99 through numerals, not drawings.
                Slot.Radical("count", listOf("12", "15", "18", "20")),
                Slot.Incidental("picture", keys),
            ),
            answerTemplate = "{count}",
            pictureSlot = "picture",
            derivedPlaceholders = setOf("hi"),
        ),
        ItemModel(
            modelId = "nipun-c2-add-v1",
            lakshyaCode = "C2-NUM-3",
            strategy = SolutionStrategy.ADDITION_WITH_CARRY,
            hiTemplate = "{a} + {b} = ______",
            targetTemplate = "{a} + {b} = ______",
            slots = listOf(
                // Largest pair is 52 + 33 = 85, inside the ceiling of 99.
                Slot.Radical("a", listOf("23", "34", "45", "52")),
                Slot.Radical("b", listOf("14", "21", "25", "33")),
            ),
        ),
        ItemModel(
            modelId = "nipun-c2-subtract-v1",
            lakshyaCode = "C2-NUM-3",
            strategy = SolutionStrategy.SUBTRACTION_NO_BORROW,
            hiTemplate = "{a} − {b} = ______",
            targetTemplate = "{a} − {b} = ______",
            slots = listOf(
                // Smallest result is 48 − 45 = 3, so no combination goes negative.
                Slot.Radical("a", listOf("48", "67", "85", "99")),
                Slot.Radical("b", listOf("12", "23", "31", "45")),
            ),
        ),
        ItemModel(
            modelId = "nipun-c2-multiply-v1",
            lakshyaCode = "C2-NUM-4",
            strategy = SolutionStrategy.MULTIPLICATION_AS_REPEATED_ADDITION,
            // {repeated} is resolved into the addition the product stands for, which is how the
            // source frames the outcome.
            hiTemplate = "{table} × {times} = ______   ({repeated})",
            targetTemplate = "{table} × {times} = ______",
            slots = listOf(
                // Only the tables the source names. Adding 5 here would be a curriculum change.
                Slot.Radical("table", NipunOutcomes.GRADE_2_TABLES.map { it.toString() }),
                Slot.Radical("times", listOf("3", "5", "7", "9")),
            ),
            derivedPlaceholders = setOf("repeated"),
        ),
        ItemModel(
            modelId = "nipun-c2-divide-v1",
            lakshyaCode = "C2-NUM-4",
            strategy = SolutionStrategy.DIVISION_AS_SHARING,
            // {total} is resolved as share x children, so the division is always exact. Binding a
            // dividend directly would let the generator produce 9 sweets among 4 children.
            hiTemplate = "{total} {hi} को {children} बच्चों में बराबर बाँटो। हर बच्चे को कितने मिले? ______",
            targetTemplate = "{total} ÷ {children} = ______",
            slots = listOf(
                Slot.Radical("share", listOf("2", "3", "4", "5")),
                Slot.Radical("children", NipunOutcomes.GRADE_2_TABLES.map { it.toString() }),
                Slot.Incidental("picture", keys),
            ),
            pictureSlot = "picture",
            derivedPlaceholders = setOf("total", "hi"),
        ),
        ItemModel(
            modelId = "nipun-c2-pattern-v1",
            lakshyaCode = "C2-NUM-2",
            strategy = SolutionStrategy.PATTERN_EXTENSION,
            hiTemplate = "पैटर्न पूरा करो: {sequence}",
            targetTemplate = "{sequence}",
            slots = listOf(
                // Every run stays inside 99: the largest ends at 10,20,30,40 -> 50.
                Slot.Radical("pattern", listOf("10,10", "5,5", "6,6", "9,9")),
            ),
            derivedPlaceholders = setOf("sequence"),
        ),
    )

    // -------------------------------------------------------------------------------------------
    // Vocabulary
    // -------------------------------------------------------------------------------------------

    /**
     * Naming items for a picture topic.
     *
     * Four strategies, because a single-strategy pool makes the assembler report a lost interleaving
     * benefit on every sheet. None of them needs distractor options: multiple choice would mean
     * binding three word slots that must not collide, which is precisely V40's case of a variant
     * admitting more than one correct answer. A matching exercise is produced at render time from
     * the sheet's own items instead, where the pairing is known and cannot repeat.
     */
    private fun vocabularyModels(
        band: GradeBand,
        keys: List<String>,
        /**
         * Keys the glossary has a target word for.
         *
         * The bilingual model draws only from these when any exist. Without it the model picks
         * uniformly from the whole category and mostly lands on words the corpus does not cover, so
         * a sheet asking the child to write the word in their own language would print a blank
         * answer key most of the time — the one item that exists to be bilingual, usually not.
         */
        withTargetWords: Set<String> = emptySet(),
    ): List<ItemModel> {
        val outcome = if (band == GradeBand.GRADE_2) "C2-ORL-4" else "C1-ORL-1"
        // Order is preserved from `keys` so the slot values stay deterministic.
        val bilingualKeys = keys.filter { it.lowercase() in withTargetWords }
            .ifEmpty { keys }
        return listOf(
            ItemModel(
                modelId = "nipun-name-picture-v1",
                lakshyaCode = outcome,
                strategy = SolutionStrategy.PICTURE_WORD_MATCH,
                hiTemplate = "यह क्या है? नाम लिखो: ______",
                targetTemplate = "______",
                slots = listOf(Slot.Incidental("picture", keys)),
                answerTemplate = "{hi}",
                pictureSlot = "picture",
                derivedPlaceholders = setOf("hi"),
            ),
            ItemModel(
                modelId = "nipun-first-akshara-v1",
                lakshyaCode = if (band == GradeBand.GRADE_2) "C2-RDG-2" else "C1-RDG-2",
                strategy = SolutionStrategy.LETTER_RECOGNITION,
                hiTemplate = "{hi} — इस शब्द का पहला अक्षर लिखो: ______",
                targetTemplate = "______",
                slots = listOf(Slot.Incidental("picture", keys)),
                // {akshara} is resolved with AksharaAnalyzer rather than by taking the first char:
                // a matra is a separate code point, so "गाय" would otherwise answer "ग" and lose
                // the vowel the child is being asked for.
                answerTemplate = "{akshara}",
                pictureSlot = "picture",
                derivedPlaceholders = setOf("hi", "akshara"),
            ),
            ItemModel(
                modelId = "nipun-write-target-v1",
                lakshyaCode = outcome,
                strategy = SolutionStrategy.WORD_READING,
                hiTemplate = "{hi} — इसे अपनी भाषा में लिखो: ______",
                // The one model whose target line is a word rather than a numeral. It is filled
                // from the glossary in resolve(), never authored here.
                targetTemplate = "{target}",
                slots = listOf(Slot.Incidental("picture", bilingualKeys)),
                answerTemplate = "{target}",
                pictureSlot = "picture",
                derivedPlaceholders = setOf("hi", "target"),
            ),
            ItemModel(
                modelId = "nipun-count-vocab-v1",
                lakshyaCode = if (band == GradeBand.GRADE_2) "C2-NUM-1" else "C1-NUM-1",
                strategy = SolutionStrategy.COUNTING,
                // "कितने {hi} हैं?" was wrong for half the vocabulary: Hindi quantifiers agree with
                // gender and number, so "कितने गाय" mis-declines a feminine noun. Dropping the
                // quantifier avoids agreement entirely — "गाय गिनो" is correct for either gender —
                // and the noun still gets named, which is the point of a vocabulary counting item.
                hiTemplate = "{hi} गिनो और संख्या लिखो: ______",
                targetTemplate = "______",
                slots = listOf(
                    Slot.Radical("count", listOf("3", "5", "8", "12")),
                    Slot.Incidental("picture", keys),
                ),
                answerTemplate = "{count}",
                pictureSlot = "picture",
                derivedPlaceholders = setOf("hi"),
            ),
        )
    }

    // -------------------------------------------------------------------------------------------
    // Build
    // -------------------------------------------------------------------------------------------

    /**
     * Generates a sheet for [topic] at [band], reproducible from [seed] alone.
     *
     * @param vocabulary target-language words keyed by lowercase English, from the glossary.
     *   Empty is a supported state: the sheet comes out Hindi-only and says so.
     */
    fun build(
        topic: WorksheetTopic,
        band: GradeBand,
        seed: Long,
        vocabulary: Map<String, TargetWord> = emptyMap(),
        packVersion: String? = null,
        minItems: Int = 4,
        maxItems: Int = 8,
        /** Overrides the topic's own pictures, so "count five mangoes" counts mangoes. */
        pictures: List<PictureBank.Picture> = defaultPictures(topic),
    ): SheetResult {
        val models = modelsFor(
            topic = topic,
            band = band,
            pictures = pictures,
            withTargetWords = vocabulary.keys.map { it.lowercase() }.toSet(),
        )
        if (models.isEmpty()) {
            return SheetResult.NotEnoughContent(
                topic, band,
                "No pictures are available for ${topic.englishLabel}, so no item can be built. " +
                    "DEFICIENT POOL: add entries to PictureBank for this category.",
            )
        }

        val relaxations = mutableListOf<Relaxation>()

        // Items are generated here rather than through WorksheetAssembler.assemble, for one
        // reason that matters on a real sheet: a WorksheetSpec carries a single radicalChoices map
        // applied to every item, and radical slots otherwise fall back to values.first(). Two
        // addition items from the same model would then both read "2 + 1 = ___". Eight items, four
        // distinct sums.
        //
        // Stepping each radical slot by the model's own item index gives 2+1, 3+2, 4+3, 5+4 — an
        // ascending ladder. **This is not randomising a radical slot**, which the file's rule
        // forbids: the value is a deterministic function of position, so a reprint from the same
        // seed is identical and the difficulty of item n is a designed property rather than a draw.
        //
        // Candidates are resolved and then de-duplicated on the finished prompt, in that order,
        // because duplication is only visible after resolution. Before it, two counting items read
        // "{hi} गिनो..." whichever picture they hold, so de-duplicating early would collapse
        // different questions into one; after it, an exact repeat really is the same question
        // twice. A sheet that asked "आम — इसे अपनी भाषा में लिखो" as both item 3 and item 7 is what
        // prompted this, and it happens for an ordinary reason: when the glossary covers one word
        // in a category, the bilingual model has nothing else to pick.
        val collected = mutableListOf<GeneratedItem>()
        val seenPrompts = HashSet<String>()
        var index = 0
        while (collected.size < maxItems && index < maxItems * MODEL_CYCLES) {
            val model = models[index % models.size]
            val rung = index / models.size
            val candidate = resolve(
                ItemGenerator.generate(
                    model = model,
                    // From the sheet seed and the position, so the sheet reproduces from `seed`.
                    seed = seed * 31 + index,
                    radicalChoices = model.slots
                        .filterIsInstance<Slot.Radical>()
                        .associate { it.name to it.values[rung % it.values.size] },
                ),
                vocabulary,
            )
            index++
            // Rejection is deterministic, so a reprint from the same seed drops the same candidates.
            if (seenPrompts.add(candidate.hiText)) collected += candidate
        }

        if (collected.size < minItems) {
            return SheetResult.NotEnoughContent(
                topic, band,
                "Only ${collected.size} items could be generated for ${topic.englishLabel} at " +
                    "${band.label}, below the minimum of $minItems. DEFICIENT POOL: more item " +
                    "models or more pictures are needed.",
            )
        }

        val ordered = InterleaveOrderer.order(collected)

        if (!InterleaveOrderer.isFullyInterleaved(ordered) && ordered.size > 1) {
            relaxations += Relaxation(
                constraint = "interleaving",
                reason = "Adjacent items repeat a solution strategy " +
                    "(longest run ${InterleaveOrderer.longestRun(ordered)}). The interleaving " +
                    "benefit is reduced for this sheet.",
            )
        }

        // Reported, never silent: a teacher reading a Hindi-only line must know the target word was
        // missing from the corpus rather than assume the sheet is bilingual throughout.
        val hindiOnly = ordered.count { it.needsTargetWord && it.targetProvenance == null }
        if (hindiOnly > 0) {
            relaxations += Relaxation(
                constraint = "bilingual",
                reason = "$hindiOnly item(s) have no target-language word in the glossary, so they " +
                    "print in Hindi with the picture only. Nothing was invented to fill them.",
            )
        }

        return SheetResult.Ready(
            Sheet(
                topic = topic,
                gradeBand = band,
                seed = seed,
                titleHindi = "${topic.hindiLabel} — ${band.label}",
                items = ordered,
                lakshyaCodes = ordered.map { it.lakshyaCode }.distinct().sorted(),
                relaxations = relaxations,
                packVersion = packVersion,
            ),
        )
    }

    /**
     * Fills the derived placeholders and attaches provenance.
     *
     * Everything here follows from `pictureTerm` and the item's own bindings, which is what keeps
     * the picture, the Hindi, the target word and the answer describing the same thing.
     */
    private fun resolve(item: GeneratedItem, vocabulary: Map<String, TargetWord>): GeneratedItem {
        val picture = item.pictureTerm?.let { PictureBank.byEnglish(it) }
        val word = item.pictureTerm?.lowercase()?.let { vocabulary[it] }

        val derived = HashMap<String, String>(6)
        picture?.let {
            derived["hi"] = it.hindi
            derived["akshara"] = AksharaAnalyzer.segment(it.hindi).firstOrNull() ?: it.hindi
        }
        // Blank rather than a placeholder word: a line that reads "___" is obviously unanswered,
        // where invented filler would read as a translation.
        derived["target"] = word?.native ?: "______"

        item.bindings["pattern"]?.let { derived["sequence"] = expandPattern(it) }
        item.bindings["table"]?.let { table ->
            item.bindings["times"]?.let { times ->
                derived["repeated"] = repeatedAddition(table, times)
            }
        }
        // Dividend is computed so the division is always exact.
        item.bindings["share"]?.let { share ->
            item.bindings["children"]?.let { children ->
                val total = (share.toIntOrNull() ?: 0) * (children.toIntOrNull() ?: 0)
                derived["total"] = total.toString()
            }
        }

        val answer = computeAnswer(item, derived)

        return item.copy(
            hiText = fillDerived(item.hiText, derived),
            targetText = fillDerived(item.targetText, derived),
            answer = answer?.let { fillDerived(it, derived) },
            // Only a real glossary row sets these, and only on a model that actually prints a word.
            targetProvenance = if (item.needsTargetWord) word?.provenance else null,
            targetSrc = if (item.needsTargetWord) word?.src else null,
        )
    }

    /** True when this item's target line is a word rather than a numeral or a blank. */
    private val GeneratedItem.needsTargetWord: Boolean
        get() = modelId == "nipun-write-target-v1"

    /**
     * The answer key.
     *
     * Computed from the bindings, never by reading back the rendered prompt. Returns null where
     * there genuinely is no single answer, which the key then shows as blank rather than wrong.
     */
    private fun computeAnswer(item: GeneratedItem, derived: Map<String, String>): String? {
        val a = item.bindings["a"]?.toIntOrNull()
        val b = item.bindings["b"]?.toIntOrNull()
        return when (item.strategy) {
            SolutionStrategy.ADDITION_NO_CARRY,
            SolutionStrategy.ADDITION_WITH_CARRY,
            -> if (a != null && b != null) (a + b).toString() else item.answer

            SolutionStrategy.SUBTRACTION_NO_BORROW ->
                if (a != null && b != null) (a - b).toString() else item.answer

            SolutionStrategy.MULTIPLICATION_AS_REPEATED_ADDITION -> {
                val t = item.bindings["table"]?.toIntOrNull()
                val n = item.bindings["times"]?.toIntOrNull()
                if (t != null && n != null) (t * n).toString() else item.answer
            }

            SolutionStrategy.DIVISION_AS_SHARING -> item.bindings["share"]

            SolutionStrategy.PATTERN_EXTENSION ->
                item.bindings["pattern"]?.let { nextInPattern(it) } ?: item.answer

            else -> item.answer
        }
    }

    /** "2,2" -> "2, 4, 6, 8, ______". Four shown terms, the fifth is the answer. */
    internal fun expandPattern(spec: String): String {
        val (start, step) = parsePattern(spec) ?: return spec
        return (0 until PATTERN_SHOWN).joinToString(", ") { (start + it * step).toString() } +
            ", ______"
    }

    /** The term the child must supply — the one after the last shown. */
    internal fun nextInPattern(spec: String): String? {
        val (start, step) = parsePattern(spec) ?: return null
        return (start + PATTERN_SHOWN * step).toString()
    }

    private fun parsePattern(spec: String): Pair<Int, Int>? {
        val parts = spec.split(',')
        if (parts.size != 2) return null
        val start = parts[0].trim().toIntOrNull() ?: return null
        val step = parts[1].trim().toIntOrNull() ?: return null
        return start to step
    }

    /** How many terms of a pattern are printed before the blank. */
    private const val PATTERN_SHOWN = 4

    /** "3" x "5" -> "3 + 3 + 3 + 3 + 3", the repeated addition the outcome asks the product to mean. */
    internal fun repeatedAddition(table: String, times: String): String {
        val n = times.toIntOrNull() ?: return table
        return List(n) { table }.joinToString(" + ")
    }

    /**
     * Fills placeholders left over after generation.
     *
     * Depends on [ItemGenerator] leaving unknown `{slots}` untouched, which it does deliberately —
     * `fill` falls back to the matched text. `TopicWorksheetsTest` pins that behaviour, because a
     * well-meaning change to throw on an unknown slot would break every sheet here.
     */
    private fun fillDerived(text: String, derived: Map<String, String>): String {
        if (derived.isEmpty()) return text
        var out = text
        for ((key, value) in derived) {
            out = out.replace("{$key}", value)
        }
        return out
    }

    /** Self-check over every topic and both grades. */
    fun validate(): List<String> = buildList {
        for (topic in WorksheetTopic.entries) {
            for (band in listOf(GradeBand.GRADE_1, GradeBand.GRADE_2)) {
                val models = modelsFor(topic, band)
                if (models.isEmpty()) {
                    add("${topic.name}/${band.name} has no item models")
                    continue
                }
                models.forEach { model ->
                    model.validate().forEach { add("${topic.name}/${band.name}: $it") }
                    model.pictureSlot?.let { slot ->
                        if (model.slots.none { it.name == slot }) {
                            add("${model.modelId} names picture slot '$slot' which it does not declare")
                        }
                    }
                    if (NipunOutcomes.byCode(model.lakshyaCode) == null) {
                        add("${model.modelId} cites unknown outcome ${model.lakshyaCode}")
                    }
                }
                if (models.map { it.strategy }.distinct().size < 2) {
                    add("${topic.name}/${band.name} has fewer than 2 strategies to interleave")
                }
            }
        }
    }
}
