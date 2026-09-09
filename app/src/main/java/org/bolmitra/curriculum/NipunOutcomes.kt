package org.bolmitra.curriculum

/**
 * The NIPUN Bharat Class 1 and Class 2 lakshyas, transcribed from the official document.
 *
 * ### Source
 *
 * "NIPUN LAKSHYA", Class 1 (age 6–7) and Class 2 (age 7–8), published on the Government of India
 * CDN that serves state education department uploads: see [SOURCE_URL]. Four domains per class —
 * oral language, reading, writing, numeracy — 16 outcomes for Class 1 and 17 for Class 2.
 *
 * The arithmetic ceilings are the part the generator acts on, so they were checked against a second,
 * independently produced document ("Lakshya of NIPN", PM Shri Kendriya Vidyalaya Tikamgarh,
 * [CORROBORATING_URL]). Both agree: **Grade 1 adds and subtracts up to 9, Grade 2 up to 99.**
 * See `latest research.md` §11 for the extraction and the full comparison.
 *
 * The two sources *disagree* on counting range — the grade-wise document puts Class 1 number sense
 * at 20, the KV target board says Grade 1 reads and writes numbers to 99. [GRADE_1_COUNT_MAX] takes
 * 20, because the grade-wise document is the better-sourced of the two and 20 is the safer floor for
 * a six-year-old. The disagreement is recorded rather than averaged away.
 *
 * ### [IMPORTANT] These codes are document positions, not government identifiers
 *
 * A regex for `L-1.1` / `N-2.3` / `FLN-N-01` style identifiers across four NIPUN PDFs returned
 * **zero matches**. The framework numbers its outcomes only by domain heading plus position in a
 * list; it assigns them no codes at all.
 *
 * So `C1-NUM-3` means exactly "Class 1, numeracy, third item as printed" and nothing more. It is
 * built mechanically from [GradeBand], [NipunDomain.abbreviation] and [Outcome.ordinal] so it cannot
 * drift from the position it names. `NumeracySeed` already states the rule this follows: an
 * unverified official code is a stronger and falser claim than an obviously local one. The `C`
 * prefix keeps it obviously local while still being traceable to a page.
 *
 * Nothing here should ever be presented in the UI as an official NIPUN outcome code. It is a
 * citation of a position in a named, linked document.
 */
object NipunOutcomes {

    /** Grade-wise outcomes for Class 1 and Class 2, all four domains. Primary source. */
    const val SOURCE_URL =
        "https://cdnbbsr.s3waas.gov.in/s3kv069b668c9eb33ebf0cf3ad8f84c1f4/uploads/2024/09/2024092417.pdf"

    /** Second source, used only to corroborate the arithmetic ceilings. */
    const val CORROBORATING_URL =
        "https://cdnbbsr.s3waas.gov.in/s3kv03baa4babc34d4d4979d9eb922b920/uploads/2024/06/2024060275.pdf"

    const val SOURCE_TITLE = "NIPUN LAKSHYA — Class 1 and Class 2"

    // ---------------------------------------------------------------------------------------
    // The ceilings. Every generated numeracy item must respect these.
    // ---------------------------------------------------------------------------------------

    /** Class 1 counting and number sense. Corroboration disagreed; see the class note. */
    const val GRADE_1_COUNT_MAX = 20

    /** Class 1 addition and subtraction operate on numbers up to 9. Agreed by both sources. */
    const val GRADE_1_OPERAND_MAX = 9

    /** Class 1 sums must not exceed 20 — stated alongside the operand bound in the source. */
    const val GRADE_1_SUM_MAX = 20

    /** Class 2 number sense, and the addition/subtraction ceiling. Agreed by both sources. */
    const val GRADE_2_NUMBER_MAX = 99

    /** Class 2 builds multiplication facts for these tables, and no others. */
    val GRADE_2_TABLES = listOf(2, 3, 4)

    /** Class 1 recognises Indian notes and coins to this value. */
    const val GRADE_1_MONEY_MAX = 20

    /** Class 2 performs simple transactions to this value. */
    const val GRADE_2_MONEY_MAX = 100

    /** The 2D shapes Class 2 names. Class 1 works with 3D solids instead. */
    val GRADE_2_SHAPES_2D = listOf("rectangle", "triangle", "circle", "oval")

    /** Class 2's spatial vocabulary, as opposing pairs exactly as the source lists them. */
    val GRADE_2_SPATIAL_PAIRS = listOf(
        "far" to "near",
        "in" to "out",
        "above" to "below",
        "left" to "right",
        "front" to "behind",
        "top" to "bottom",
    )

    /** The document's four domain headings. [abbreviation] is the middle segment of a code. */
    enum class NipunDomain(val heading: String, val abbreviation: String) {
        ORAL_LANGUAGE("Oral Language", "ORL"),
        READING("Reading", "RDG"),
        WRITING("Writing", "WRT"),
        NUMERACY("Numeracy", "NUM"),
        ;

        /** Which side of [Domain] this heading falls on. Three of the four are literacy. */
        val domain: Domain
            get() = if (this == NUMERACY) Domain.NUMERACY else Domain.LITERACY
    }

    /**
     * One outcome as printed.
     *
     * [english] is condensed from the source rather than quoted at length. [hindi] is a Hindi
     * rendering for the teacher-facing UI — authoring Hindi breaks no invariant, since the rule
     * that content must come from a named speaker is about the *target* tribal language.
     */
    data class Outcome(
        val gradeBand: GradeBand,
        val nipunDomain: NipunDomain,
        /** 1-based position under the domain heading, as printed. */
        val ordinal: Int,
        val english: String,
        val hindi: String,
        val range: LakshyaRange? = null,
        val limits: ItemLimits = ItemLimits(),
    ) {
        /** e.g. `C1-NUM-3`. Derived, never stored, so it cannot disagree with the position. */
        val code: String
            get() = "${gradeBand.shortCode}-${nipunDomain.abbreviation}-$ordinal"

        /**
         * Bridges to the app's own [Lakshya] type.
         *
         * `languageRole` is **the app's reading, not the document's.** The NIPUN source does not
         * distinguish first from second language anywhere in these lists. Numeracy is genuinely
         * language-neutral — a numeral is not a translation. Literacy outcomes are mapped to
         * [LanguageRole.L1] because foundational literacy is framed in the child's own language,
         * but the source is silent on it, so treat that mapping as an assumption to revisit and
         * not as something the document says.
         */
        fun toLakshya(): Lakshya = Lakshya(
            code = code,
            gradeBand = gradeBand,
            domain = nipunDomain.domain,
            description = english,
            languageRole = if (nipunDomain == NipunDomain.NUMERACY) {
                LanguageRole.LANGUAGE_NEUTRAL
            } else {
                LanguageRole.L1
            },
            range = range,
            limits = limits,
        )
    }

    // ---------------------------------------------------------------------------------------
    // Class 1 (age 6-7) — 16 outcomes
    // ---------------------------------------------------------------------------------------

    private val class1 = listOf(
        Outcome(
            GradeBand.GRADE_1, NipunDomain.ORAL_LANGUAGE, 1,
            english = "Converses and asks questions about her needs, familiar people and surroundings.",
            hindi = "अपनी ज़रूरतों, जान-पहचान के लोगों और आस-पास के बारे में बातचीत करता है और प्रश्न पूछता है।",
        ),
        Outcome(
            GradeBand.GRADE_1, NipunDomain.ORAL_LANGUAGE, 2,
            english = "Talks about the print available in the classroom, school and surroundings.",
            hindi = "कक्षा, विद्यालय और आस-पास दिखने वाले लिखे शब्दों के बारे में बात करता है।",
        ),
        Outcome(
            GradeBand.GRADE_1, NipunDomain.ORAL_LANGUAGE, 3,
            english = "Recites rhymes, poems and songs with action.",
            hindi = "कविता, गीत और तुकबंदी हाव-भाव के साथ सुनाता है।",
        ),
        Outcome(
            GradeBand.GRADE_1, NipunDomain.READING, 1,
            english = "Takes an active part in read-aloud and storytelling, answers questions during " +
                "and after the story, and acts out a familiar story with props and puppets.",
            hindi = "कहानी सुनने में सक्रिय भाग लेता है, कहानी के दौरान और बाद में प्रश्नों के उत्तर देता है।",
        ),
        Outcome(
            GradeBand.GRADE_1, NipunDomain.READING, 2,
            english = "Uses sound-symbol correspondence to read new words.",
            hindi = "ध्वनि और चिह्न के मेल से नए शब्द पढ़ता है।",
        ),
        Outcome(
            GradeBand.GRADE_1, NipunDomain.READING, 3,
            english = "Reads small sentences of at least 4-5 simple words in age-appropriate unknown text.",
            hindi = "अपरिचित पाठ में 4-5 सरल शब्दों के छोटे वाक्य पढ़ता है।",
            // The source states the sentence length; the akshara ladder comes from V42, not here.
            limits = ItemLimits(maxWordsPerSentence = 5),
        ),
        Outcome(
            GradeBand.GRADE_1, NipunDomain.WRITING, 1,
            english = "Develops familiarity with matras in words from familiar contexts.",
            hindi = "जाने-पहचाने संदर्भों के शब्दों में मात्राओं से परिचित होता है।",
            limits = ItemLimits(maxMatras = 2),
        ),
        Outcome(
            GradeBand.GRADE_1, NipunDomain.WRITING, 2,
            english = "Writes, draws or makes things to convey meaning, and draws recognisable " +
                "objects and people.",
            hindi = "अर्थ व्यक्त करने के लिए लिखता, चित्र बनाता है और पहचानने योग्य वस्तुएँ बनाता है।",
        ),
        Outcome(
            GradeBand.GRADE_1, NipunDomain.WRITING, 3,
            english = "Uses sound-symbol correspondence to write words with invented spellings.",
            hindi = "ध्वनि और चिह्न के मेल से अपने ढंग से शब्द लिखता है।",
        ),
        Outcome(
            GradeBand.GRADE_1, NipunDomain.WRITING, 4,
            english = "Writes 2-3 sentences with understanding.",
            hindi = "समझ के साथ 2-3 वाक्य लिखता है।",
        ),
        Outcome(
            GradeBand.GRADE_1, NipunDomain.NUMERACY, 1,
            english = "Counts objects and develops number sense up to 20.",
            hindi = "वस्तुएँ गिनता है और 20 तक संख्या-बोध विकसित करता है।",
            range = LakshyaRange(0, GRADE_1_COUNT_MAX),
        ),
        Outcome(
            GradeBand.GRADE_1, NipunDomain.NUMERACY, 2,
            english = "Identifies and extends simple patterns in surrounding shapes and numbers.",
            hindi = "आस-पास की आकृतियों और संख्याओं में सरल पैटर्न पहचानता है और आगे बढ़ाता है।",
            range = LakshyaRange(0, GRADE_1_COUNT_MAX),
        ),
        Outcome(
            GradeBand.GRADE_1, NipunDomain.NUMERACY, 3,
            english = "Adds and subtracts numbers up to 9, with sums not exceeding 20, in daily life.",
            hindi = "रोज़मर्रा की स्थितियों में 9 तक की संख्याओं का जोड़ और घटाव करता है (योग 20 से अधिक नहीं)।",
            range = LakshyaRange(0, GRADE_1_OPERAND_MAX),
        ),
        Outcome(
            GradeBand.GRADE_1, NipunDomain.NUMERACY, 4,
            english = "Observes and describes physical properties of 3D solid shapes around her, " +
                "such as round or flat surfaces, corners and edges.",
            hindi = "अपने आस-पास की ठोस आकृतियों के गुण बताता है — गोल या सपाट सतह, कोने और किनारे।",
        ),
        Outcome(
            GradeBand.GRADE_1, NipunDomain.NUMERACY, 5,
            english = "Estimates and verifies length using non-standard non-uniform units such as " +
                "hand span, footstep and fingers, and capacity using cup, spoon and mug.",
            hindi = "बित्ता, कदम और उँगली से लंबाई और कप, चम्मच, मग से मात्रा का अनुमान लगाकर जाँचता है।",
        ),
        Outcome(
            GradeBand.GRADE_1, NipunDomain.NUMERACY, 6,
            english = "Identifies Indian currency notes and coins up to INR 20.",
            hindi = "20 रुपये तक के भारतीय नोट और सिक्के पहचानता है।",
            range = LakshyaRange(1, GRADE_1_MONEY_MAX),
        ),
    )

    // ---------------------------------------------------------------------------------------
    // Class 2 (age 7-8) — 17 outcomes
    // ---------------------------------------------------------------------------------------

    private val class2 = listOf(
        Outcome(
            GradeBand.GRADE_2, NipunDomain.ORAL_LANGUAGE, 1,
            english = "Converses and talks about the print available in the class.",
            hindi = "कक्षा में दिखने वाले लिखे शब्दों के बारे में बातचीत करता है।",
        ),
        Outcome(
            GradeBand.GRADE_2, NipunDomain.ORAL_LANGUAGE, 2,
            english = "Engages in conversation to ask questions, and listens to others.",
            hindi = "प्रश्न पूछने के लिए बातचीत में शामिल होता है और दूसरों को सुनता है।",
        ),
        Outcome(
            GradeBand.GRADE_2, NipunDomain.ORAL_LANGUAGE, 3,
            english = "Recites songs and poems.",
            hindi = "गीत और कविताएँ सुनाता है।",
        ),
        Outcome(
            GradeBand.GRADE_2, NipunDomain.ORAL_LANGUAGE, 4,
            english = "Recites familiar words occurring in stories, poems and print.",
            hindi = "कहानियों, कविताओं और छपी सामग्री में आए जाने-पहचाने शब्द बोलता है।",
        ),
        Outcome(
            GradeBand.GRADE_2, NipunDomain.READING, 1,
            english = "Reads and retells a story from children's literature and the textbook.",
            hindi = "बाल-साहित्य और पाठ्यपुस्तक की कहानी पढ़कर अपने शब्दों में सुनाता है।",
        ),
        Outcome(
            GradeBand.GRADE_2, NipunDomain.READING, 2,
            english = "Creates a meaningful new word from the letters of a given word.",
            hindi = "दिए गए शब्द के अक्षरों से नया अर्थपूर्ण शब्द बनाता है।",
        ),
        Outcome(
            GradeBand.GRADE_2, NipunDomain.READING, 3,
            english = "Reads age-appropriate unknown text of 6 to 8 simple sentences with " +
                "comprehension, clarity and fluency.",
            hindi = "6 से 8 सरल वाक्यों का अपरिचित पाठ समझ, स्पष्टता और प्रवाह के साथ पढ़ता है।",
            limits = ItemLimits(maxWordsPerSentence = 8),
        ),
        Outcome(
            GradeBand.GRADE_2, NipunDomain.WRITING, 1,
            english = "Writes short simple sentences to express understanding.",
            hindi = "अपनी समझ व्यक्त करने के लिए छोटे और सरल वाक्य लिखता है।",
        ),
        Outcome(
            GradeBand.GRADE_2, NipunDomain.WRITING, 2,
            english = "Writes 4 to 5 short sentences with legible writing.",
            hindi = "सुपाठ्य लेखन में 4 से 5 छोटे वाक्य लिखता है।",
        ),
        Outcome(
            GradeBand.GRADE_2, NipunDomain.NUMERACY, 1,
            english = "Counts objects and develops number sense up to 99.",
            hindi = "वस्तुएँ गिनता है और 99 तक संख्या-बोध विकसित करता है।",
            range = LakshyaRange(0, GRADE_2_NUMBER_MAX),
        ),
        Outcome(
            GradeBand.GRADE_2, NipunDomain.NUMERACY, 2,
            english = "Creates new patterns from different shapes and numbers.",
            hindi = "विभिन्न आकृतियों और संख्याओं से नए पैटर्न बनाता है।",
            range = LakshyaRange(0, GRADE_2_NUMBER_MAX),
        ),
        Outcome(
            GradeBand.GRADE_2, NipunDomain.NUMERACY, 3,
            english = "Adds and subtracts numbers up to 99 in daily life situations.",
            hindi = "रोज़मर्रा की स्थितियों में 99 तक की संख्याओं का जोड़ और घटाव करता है।",
            range = LakshyaRange(0, GRADE_2_NUMBER_MAX),
        ),
        Outcome(
            GradeBand.GRADE_2, NipunDomain.NUMERACY, 4,
            english = "Performs multiplication as repeated addition and division as equal sharing, " +
                "and builds the multiplication tables of 2, 3 and 4.",
            hindi = "बार-बार जोड़कर गुणा और बराबर बाँटकर भाग करता है, और 2, 3, 4 के पहाड़े बनाता है।",
            range = LakshyaRange(1, GRADE_2_NUMBER_MAX),
        ),
        Outcome(
            GradeBand.GRADE_2, NipunDomain.NUMERACY, 5,
            english = "Identifies and describes 2D shapes such as rectangle, triangle, circle and oval.",
            hindi = "आयत, त्रिभुज, वृत्त और अंडाकार जैसी द्विविमीय आकृतियाँ पहचानता और बताता है।",
        ),
        Outcome(
            GradeBand.GRADE_2, NipunDomain.NUMERACY, 6,
            english = "Estimates and measures length, distance and capacity using non-standard " +
                "uniform units, and compares weight using a simple balance.",
            hindi = "अमानक इकाइयों से लंबाई, दूरी और मात्रा मापता है, और साधारण तराज़ू से भार की तुलना करता है।",
        ),
        Outcome(
            GradeBand.GRADE_2, NipunDomain.NUMERACY, 7,
            english = "Uses spatial vocabulary such as far/near, in/out, above/below, left/right, " +
                "front/behind and top/bottom.",
            hindi = "दूर/नज़दीक, अंदर/बाहर, ऊपर/नीचे, बाएँ/दाएँ, आगे/पीछे जैसे स्थान-शब्द प्रयोग करता है।",
        ),
        Outcome(
            GradeBand.GRADE_2, NipunDomain.NUMERACY, 8,
            english = "Performs simple money transactions up to INR 100.",
            hindi = "100 रुपये तक के साधारण लेन-देन करता है।",
            range = LakshyaRange(1, GRADE_2_MONEY_MAX),
        ),
    )

    /** Every transcribed outcome, Class 1 then Class 2, in printed order. */
    val all: List<Outcome> = class1 + class2

    /** The same outcomes as the app's own [Lakshya] type, ready for [LakshyaCatalogue]. */
    val catalogue: LakshyaCatalogue = LakshyaCatalogue(all.map { it.toLakshya() })

    fun byCode(code: String): Outcome? = all.firstOrNull { it.code == code }

    fun forBand(band: GradeBand): List<Outcome> = all.filter { it.gradeBand == band }

    fun numeracy(band: GradeBand): List<Outcome> =
        all.filter { it.gradeBand == band && it.nipunDomain == NipunDomain.NUMERACY }

    /**
     * Self-check for the properties that fail silently.
     *
     * A transcription is exactly the kind of data a green build cannot vouch for. What is checked
     * here is what would actually go wrong: a duplicated or skipped ordinal (so a code points at
     * the wrong printed item), a count that no longer matches the source, a blank string, or a
     * numeric range that contradicts a ceiling the two sources agreed on.
     */
    fun validate(): List<String> = buildList {
        val codes = all.map { it.code }
        codes.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach {
            add("duplicate outcome code $it")
        }

        // Ordinals must run 1..n with no gaps under every (band, domain) heading.
        all.groupBy { it.gradeBand to it.nipunDomain }.forEach { (key, group) ->
            val ordinals = group.map { it.ordinal }.sorted()
            if (ordinals != (1..group.size).toList()) {
                add("${key.first} ${key.second.heading} ordinals are $ordinals, expected 1..${group.size}")
            }
        }

        // Counts as printed in the source. If a transcription drops a line, this is what notices.
        val expected = mapOf(
            (GradeBand.GRADE_1 to NipunDomain.ORAL_LANGUAGE) to 3,
            (GradeBand.GRADE_1 to NipunDomain.READING) to 3,
            (GradeBand.GRADE_1 to NipunDomain.WRITING) to 4,
            (GradeBand.GRADE_1 to NipunDomain.NUMERACY) to 6,
            (GradeBand.GRADE_2 to NipunDomain.ORAL_LANGUAGE) to 4,
            (GradeBand.GRADE_2 to NipunDomain.READING) to 3,
            (GradeBand.GRADE_2 to NipunDomain.WRITING) to 2,
            (GradeBand.GRADE_2 to NipunDomain.NUMERACY) to 8,
        )
        expected.forEach { (key, count) ->
            val actual = all.count { it.gradeBand == key.first && it.nipunDomain == key.second }
            if (actual != count) {
                add("${key.first} ${key.second.heading} has $actual outcomes, source prints $count")
            }
        }

        all.forEach {
            if (it.english.isBlank()) add("${it.code} has no English description")
            if (it.hindi.isBlank()) add("${it.code} has no Hindi description")
        }

        // The corroborated ceilings, asserted against the ranges actually encoded.
        byCode("C1-NUM-3")?.range?.let {
            if (it.max != GRADE_1_OPERAND_MAX) {
                add("C1-NUM-3 operand max is ${it.max}, both sources say $GRADE_1_OPERAND_MAX")
            }
        }
        byCode("C2-NUM-3")?.range?.let {
            if (it.max != GRADE_2_NUMBER_MAX) {
                add("C2-NUM-3 max is ${it.max}, both sources say $GRADE_2_NUMBER_MAX")
            }
        }
        if (GRADE_1_SUM_MAX < GRADE_1_OPERAND_MAX) {
            add("sum ceiling $GRADE_1_SUM_MAX is below the operand ceiling $GRADE_1_OPERAND_MAX")
        }
    }
}

/** Short code used as the first segment of an [NipunOutcomes.Outcome.code]. */
internal val GradeBand.shortCode: String
    get() = when (this) {
        GradeBand.BALVATIKA -> "BV"
        GradeBand.GRADE_1 -> "C1"
        GradeBand.GRADE_2 -> "C2"
        GradeBand.GRADE_3 -> "C3"
    }
