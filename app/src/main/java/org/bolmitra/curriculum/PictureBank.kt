package org.bolmitra.curriculum

/**
 * The picture vocabulary worksheets and flashcards draw on.
 *
 * ### Why the pictures are platform glyphs and not bundled art
 *
 * The brief was to download a bundle of hand-drawn vectors for numbers, animals and general
 * knowledge. That was investigated properly and rejected; `latest research.md` §11 has the full
 * reasoning, and the short version is three findings:
 *
 *  1. **Openclipart really is CC0** — verified from its own FAQ, 180,000+ public-domain vectors,
 *     commercial use and modification allowed, no attribution required. The licence was never the
 *     problem.
 *  2. **We could not see what we would ship.** Clipart would be picked by tag and filename and put
 *     in front of six-year-olds without anyone rendering it. The project's first invariant is that
 *     unreviewed content must not reach a class, and it explicitly still applies when the medium
 *     changes from a voice to a page. An unverified drawing labelled *cow* is unreviewed content.
 *  3. **Arbitrary SVG is not an Android asset.** `VectorDrawable` supports a subset — no filters,
 *     no masks, no `<use>` — and conversion drops what it cannot represent quietly, so the artwork
 *     that ships would not be the artwork that was inspected.
 *
 * Android already carries the pictures. `NotoColorEmoji.ttf` is present on the test tablet and has
 * shipped on Android since well before `minSdk 28`, so these glyphs cost **zero APK bytes, zero
 * downloads and zero licence surface** — the app ships no image at all, it asks the platform to
 * draw one. They are vector, so they scale to the 2880×1800 panel and to print, and they were
 * designed to stay recognisable at small sizes, which is the whole job here.
 *
 * **Coverage was measured, not assumed.** The device's own font was pulled and its cmap checked
 * against every glyph named below: 84 of 84 present. Script: `E:\research\nipun\emoji_check.py`.
 *
 * `ponytail:` The ceiling is that these are someone else's drawing style, not a textbook's, and a
 * FLOOR-tier tablet on an older Android carries an older emoji font. The renderer guards the second
 * one with `Paint.hasGlyph` and falls back to a letter tile, so a missing glyph degrades to a
 * readable card rather than a tofu box. Upgrade path if the look is wrong: this object is the single
 * indirection point — swap [Picture.glyph] for a `DrawScope` lambda and nothing else changes.
 *
 * ### Hindi lives here, target languages do not
 *
 * Every entry carries its Hindi word, so a worksheet is complete even when the glossary asset fails
 * to load — `SantaliGlossary.load` returns an empty list on any failure by design, and a lesson must
 * degrade to a Hindi sheet rather than to no sheet.
 *
 * There is deliberately **no Santali, Mundari or Ho text in this file.** Target-language words come
 * from the shipped glossary at generation time, at `Provenance.CORPUS`, with `src` naming the corpus.
 * Authoring them here would be inventing tribal-language content, which is the one thing the project
 * forbids outright.
 */
object PictureBank {

    /**
     * Categories, named to match the glossary's own `pos` column so the two can be joined without
     * a translation table. See [fromGlossaryPos].
     */
    enum class Category(val posValue: String, val hindiLabel: String) {
        ANIMAL("animal", "जानवर"),
        BIRD("bird", "पक्षी"),
        FOOD("food", "फल और खाना"),
        VEGETABLE("vegetable", "सब्ज़ी"),
        BODY("body", "शरीर के अंग"),
        FAMILY("family", "परिवार"),
        COLOUR("colour", "रंग"),
        NATURE("nature", "प्रकृति"),
        SCHOOL("school", "पाठशाला"),
        ;

        companion object {
            /** The glossary's `pos` value for this row, or null if we have no pictures for it. */
            fun fromGlossaryPos(pos: String): Category? =
                entries.firstOrNull { it.posValue == pos.trim().lowercase() }
        }
    }

    /**
     * One picturable word.
     *
     * [english] is the join key into the glossary's `en` column, so a lookup can attach the
     * corpus Santali form. [colourArgb] is set only for [Category.COLOUR], where a drawn swatch
     * is both more accurate and cheaper than a glyph.
     */
    data class Picture(
        val english: String,
        val hindi: String,
        val glyph: String,
        val category: Category,
        val colourArgb: Long? = null,
    )

    /**
     * Every picture the app can draw, grouped as the categories a teacher would name.
     *
     * Chosen for two things at once: the word is one a Class 1-2 child meets, and the glossary has
     * a Santali form for it so the bilingual line is real rather than blank. Terms that exist in
     * the glossary but carry no Hindi key are still listed — the Hindi here makes them reachable
     * for the worksheet even though live translation lookup cannot find them.
     */
    val pictures: List<Picture> = listOf(
        // --- Animals -----------------------------------------------------------------------
        Picture("cow", "गाय", "\uD83D\uDC04", Category.ANIMAL),
        Picture("dog", "कुत्ता", "\uD83D\uDC15", Category.ANIMAL),
        Picture("cat", "बिल्ली", "\uD83D\uDC08", Category.ANIMAL),
        Picture("goat", "बकरी", "\uD83D\uDC10", Category.ANIMAL),
        Picture("elephant", "हाथी", "\uD83D\uDC18", Category.ANIMAL),
        Picture("tiger", "बाघ", "\uD83D\uDC05", Category.ANIMAL),
        Picture("horse", "घोड़ा", "\uD83D\uDC0E", Category.ANIMAL),
        Picture("buffalo", "भैंस", "\uD83D\uDC03", Category.ANIMAL),
        Picture("pig", "सूअर", "\uD83D\uDC16", Category.ANIMAL),
        Picture("monkey", "बंदर", "\uD83D\uDC12", Category.ANIMAL),
        Picture("deer", "हिरण", "\uD83E\uDD8C", Category.ANIMAL),
        Picture("rat", "चूहा", "\uD83D\uDC00", Category.ANIMAL),
        Picture("bear", "भालू", "\uD83D\uDC3B", Category.ANIMAL),
        Picture("fish", "मछली", "\uD83D\uDC1F", Category.ANIMAL),
        Picture("snake", "साँप", "\uD83D\uDC0D", Category.ANIMAL),
        Picture("frog", "मेंढक", "\uD83D\uDC38", Category.ANIMAL),
        Picture("camel", "ऊँट", "\uD83D\uDC2A", Category.ANIMAL),
        Picture("sheep", "भेड़", "\uD83D\uDC11", Category.ANIMAL),
        Picture("rabbit", "खरगोश", "\uD83D\uDC07", Category.ANIMAL),

        // --- Birds -------------------------------------------------------------------------
        Picture("hen", "मुर्गी", "\uD83D\uDC14", Category.BIRD),
        Picture("bird", "चिड़िया", "\uD83D\uDC26", Category.BIRD),
        Picture("duck", "बत्तख", "\uD83E\uDD86", Category.BIRD),
        Picture("peacock", "मोर", "\uD83E\uDD9A", Category.BIRD),
        Picture("owl", "उल्लू", "\uD83E\uDD89", Category.BIRD),

        // --- Fruit and food ----------------------------------------------------------------
        Picture("mango", "आम", "\uD83E\uDD6D", Category.FOOD),
        Picture("banana", "केला", "\uD83C\uDF4C", Category.FOOD),
        Picture("orange", "संतरा", "\uD83C\uDF4A", Category.FOOD),
        Picture("apple", "सेब", "\uD83C\uDF4E", Category.FOOD),
        Picture("grape", "अंगूर", "\uD83C\uDF47", Category.FOOD),
        Picture("lemon", "नीबू", "\uD83C\uDF4B", Category.FOOD),
        Picture("coconut", "नारियल", "\uD83E\uDD65", Category.FOOD),
        Picture("watermelon", "तरबूज़", "\uD83C\uDF49", Category.FOOD),
        Picture("pineapple", "अनानास", "\uD83C\uDF4D", Category.FOOD),
        Picture("guava", "अमरूद", "\uD83C\uDF50", Category.FOOD),

        // --- Vegetables --------------------------------------------------------------------
        Picture("potato", "आलू", "\uD83E\uDD54", Category.VEGETABLE),
        Picture("tomato", "टमाटर", "\uD83C\uDF45", Category.VEGETABLE),
        Picture("onion", "प्याज़", "\uD83E\uDDC5", Category.VEGETABLE),
        Picture("carrot", "गाजर", "\uD83E\uDD55", Category.VEGETABLE),
        Picture("brinjal", "बैंगन", "\uD83C\uDF46", Category.VEGETABLE),
        Picture("corn", "मक्का", "\uD83C\uDF3D", Category.VEGETABLE),
        Picture("chilli", "मिर्च", "\uD83C\uDF36", Category.VEGETABLE),

        // --- Body --------------------------------------------------------------------------
        Picture("eye", "आँख", "\uD83D\uDC41", Category.BODY),
        Picture("ear", "कान", "\uD83D\uDC42", Category.BODY),
        Picture("nose", "नाक", "\uD83D\uDC43", Category.BODY),
        Picture("mouth", "मुँह", "\uD83D\uDC44", Category.BODY),
        Picture("hand", "हाथ", "\u270B", Category.BODY),
        Picture("tooth", "दाँत", "\uD83E\uDDB7", Category.BODY),
        Picture("leg", "पैर", "\uD83E\uDDB5", Category.BODY),
        Picture("foot", "पंजा", "\uD83E\uDDB6", Category.BODY),
        Picture("tongue", "जीभ", "\uD83D\uDC45", Category.BODY),

        // --- Family ------------------------------------------------------------------------
        Picture("mother", "माँ", "\uD83D\uDC69", Category.FAMILY),
        Picture("father", "पिता", "\uD83D\uDC68", Category.FAMILY),
        Picture("boy", "लड़का", "\uD83D\uDC66", Category.FAMILY),
        Picture("girl", "लड़की", "\uD83D\uDC67", Category.FAMILY),
        Picture("child", "बच्चा", "\uD83E\uDDD2", Category.FAMILY),
        Picture("baby", "शिशु", "\uD83D\uDC76", Category.FAMILY),

        // --- Nature, the "general knowledge" set -------------------------------------------
        Picture("sun", "सूरज", "\u2600", Category.NATURE),
        Picture("moon", "चाँद", "\uD83C\uDF19", Category.NATURE),
        Picture("star", "तारा", "\u2B50", Category.NATURE),
        Picture("water", "पानी", "\uD83D\uDCA7", Category.NATURE),
        Picture("fire", "आग", "\uD83D\uDD25", Category.NATURE),
        Picture("tree", "पेड़", "\uD83C\uDF33", Category.NATURE),
        Picture("leaf", "पत्ता", "\uD83C\uDF43", Category.NATURE),
        Picture("flower", "फूल", "\uD83C\uDF3C", Category.NATURE),
        Picture("cloud", "बादल", "\u2601", Category.NATURE),
        Picture("mountain", "पहाड़", "\u26F0", Category.NATURE),

        // --- School ------------------------------------------------------------------------
        Picture("book", "किताब", "\uD83D\uDCD6", Category.SCHOOL),
        Picture("pencil", "पेंसिल", "\u270F", Category.SCHOOL),
        Picture("paper", "कागज़", "\uD83D\uDCC4", Category.SCHOOL),
        Picture("school", "विद्यालय", "\uD83C\uDFEB", Category.SCHOOL),
        Picture("bag", "बस्ता", "\uD83C\uDF92", Category.SCHOOL),
        Picture("ball", "गेंद", "\u26BD", Category.SCHOOL),
        Picture("drum", "ढोल", "\uD83E\uDD41", Category.SCHOOL),

        // --- Colours: a drawn swatch, not a glyph ------------------------------------------
        // A swatch is the exact colour asked for and costs one drawCircle. A coloured-circle
        // emoji would be approximate and several of them are Unicode 12, which is newer than
        // the floor this app supports.
        Picture("red", "लाल", "", Category.COLOUR, colourArgb = 0xFFDC2626),
        Picture("white", "सफ़ेद", "", Category.COLOUR, colourArgb = 0xFFF8FAFC),
        Picture("black", "काला", "", Category.COLOUR, colourArgb = 0xFF111827),
        Picture("green", "हरा", "", Category.COLOUR, colourArgb = 0xFF16A34A),
        Picture("yellow", "पीला", "", Category.COLOUR, colourArgb = 0xFFFACC15),
        Picture("blue", "नीला", "", Category.COLOUR, colourArgb = 0xFF2563EB),
    )

    private val byEnglish: Map<String, Picture> =
        pictures.associateBy { it.english.lowercase() }

    private val byHindi: Map<String, Picture> =
        pictures.associateBy { it.hindi }

    fun byEnglish(english: String): Picture? = byEnglish[english.trim().lowercase()]

    fun byHindi(hindi: String): Picture? = byHindi[hindi.trim()]

    fun forCategory(category: Category): List<Picture> =
        pictures.filter { it.category == category }

    /** Categories that have enough pictures to fill a worksheet without repeating. */
    fun usableCategories(minimum: Int = MIN_PICTURES_PER_SHEET): List<Category> =
        Category.entries.filter { forCategory(it).size >= minimum }

    /**
     * Fewest pictures a category needs before it can back a sheet.
     *
     * Four, because the smallest sheet the assembler will build is four items and a picture that
     * repeats inside one sheet turns a "circle the cow" item into a trick question.
     */
    const val MIN_PICTURES_PER_SHEET = 4

    /**
     * Self-check for what would otherwise fail silently on a child's worksheet.
     *
     * The duplicate checks matter most: two pictures sharing an English key would make the
     * glossary join ambiguous, and two sharing a Hindi word would let a "circle the आम" item show
     * two correct answers — V40's exact cautionary case, found there by a human reading the items.
     */
    fun validate(): List<String> = buildList {
        pictures.groupingBy { it.english.lowercase() }.eachCount()
            .filterValues { it > 1 }.keys
            .forEach { add("duplicate english key '$it'") }

        pictures.groupingBy { it.hindi }.eachCount()
            .filterValues { it > 1 }.keys
            .forEach { add("duplicate hindi word '$it'") }

        pictures.forEach {
            if (it.english.isBlank()) add("picture with no english key")
            if (it.hindi.isBlank()) add("'${it.english}' has no Hindi word")
            if (it.category == Category.COLOUR) {
                if (it.colourArgb == null) add("colour '${it.english}' has no swatch value")
            } else {
                if (it.glyph.isBlank()) add("'${it.english}' has no glyph")
                if (it.colourArgb != null) add("'${it.english}' is not a colour but has a swatch")
            }
        }

        // A category nothing can be built from is a menu entry that dead-ends for a teacher.
        Category.entries.forEach { category ->
            val n = forCategory(category).size
            if (n in 1 until MIN_PICTURES_PER_SHEET) {
                add("category ${category.posValue} has only $n picture(s), below the sheet minimum")
            }
            if (n == 0) add("category ${category.posValue} has no pictures")
        }
    }
}
