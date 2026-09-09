package org.bolmitra.curriculum

/**
 * The one authored item pool the app ships, so worksheet generation does real work.
 *
 * ### Why numeracy, and only numeracy
 *
 * `ItemModel.targetTemplate` demands a target-language string, and the project's first invariant is
 * that Mundari, Santali and Ho content must come from a named native speaker or be labelled as
 * machine output. Authoring literacy templates here would mean writing tribal-language prompts with
 * no speaker behind them — the exact harm the provenance system exists to prevent.
 *
 * Numeracy sidesteps that honestly rather than by exception: **a numeral is not a translation.**
 * `{a} + {b} = ___` is the same item in every language, so these templates carry digits and
 * arithmetic operators and invent nothing. The Hindi side is Hindi, which needs no tribal-language
 * sign-off.
 *
 * The consequence is worth stating plainly: this pool covers `Domain.NUMERACY` and nothing else, so
 * the literacy worksheets listed on the Worksheets screen are still UI only. Generating those needs
 * a speaker, not more code.
 *
 * ### The lakshya code is a local identifier, not a quoted standard
 *
 * `LakshyaCatalogue` exists so alignment is queryable rather than asserted. Being honest about that
 * cuts both ways: `BM-N-01` is prefixed `BM-` deliberately, because nobody has mapped it to an
 * official NIPUN Bharat outcome code. The description below is a plain statement of what the items
 * do. **Do not relabel it with a real NIPUN code until someone has checked the mapping** — an
 * unverified official code is a stronger and falser claim than an obviously local one.
 */
object NumeracySeed {

    const val LAKSHYA_ADD_WITHIN_10 = "BM-N-01"

    /** Single-digit sums that never cross ten, plus counting and numeral writing at the same range. */
    val lakshya = Lakshya(
        code = LAKSHYA_ADD_WITHIN_10,
        gradeBand = GradeBand.GRADE_1,
        domain = Domain.NUMERACY,
        description = "Count, write and add numbers within 10 without carrying.",
        // Numeracy outcomes apply to L1 and L2 alike — the enum has a value for exactly this.
        languageRole = LanguageRole.LANGUAGE_NEUTRAL,
        range = LakshyaRange(min = 0, max = 10),
    )

    val catalogue = LakshyaCatalogue(listOf(lakshya))

    /**
     * Four models so [InterleaveOrderer] has something to interleave.
     *
     * With one strategy the assembler correctly reports that the interleaving benefit is absent, so
     * a single model would have made every generated sheet carry a relaxation. Four distinct
     * [SolutionStrategy] values let a real sheet come out clean.
     *
     * Slot classification follows the file's own rule and it matters here: the **addend magnitude
     * is radical**, because `2 + 3` and `8 + 9` do not measure the same thing, so those slots are
     * [Slot.Radical] and default to their first (lowest-difficulty) value. The counted object is
     * [Slot.Incidental] — swapping mangoes for pencils changes nothing about the arithmetic, and is
     * what gives two children different-looking sheets.
     */
    val models: List<ItemModel> = listOf(
        ItemModel(
            modelId = "count-objects-v1",
            lakshyaCode = LAKSHYA_ADD_WITHIN_10,
            strategy = SolutionStrategy.COUNTING,
            hiTemplate = "{count} {object} गिनकर संख्या लिखो।",
            // No tribal-language text: the child writes a numeral, which is script-neutral.
            targetTemplate = "{count} — ___",
            slots = listOf(
                Slot.Radical("count", listOf("3", "5", "7", "9")),
                Slot.Incidental("object", listOf("आम", "पेंसिल", "पत्ते", "फूल", "कंकड़")),
            ),
            answerTemplate = "{count}",
        ),
        ItemModel(
            modelId = "write-numeral-v1",
            lakshyaCode = LAKSHYA_ADD_WITHIN_10,
            strategy = SolutionStrategy.NUMERAL_WRITING,
            hiTemplate = "{word} को अंक में लिखो।",
            targetTemplate = "{word} — ___",
            slots = listOf(
                Slot.Radical(
                    "word",
                    listOf("दो", "चार", "छह", "आठ", "दस"),
                ),
            ),
            // Deliberately null: the answer depends on which radical value was bound, and encoding
            // that mapping in a template would be a lie the generator cannot fill. The teacher's
            // key for this model needs authoring — see the class note.
            answerTemplate = null,
        ),
        ItemModel(
            modelId = "add-no-carry-v1",
            lakshyaCode = LAKSHYA_ADD_WITHIN_10,
            strategy = SolutionStrategy.ADDITION_NO_CARRY,
            hiTemplate = "{a} + {b} = ___",
            targetTemplate = "{a} + {b} = ___",
            slots = listOf(
                // Pairs chosen so the sum never exceeds 10 — that is the lakshya, not a detail.
                Slot.Radical("a", listOf("2", "3", "4", "5")),
                Slot.Radical("b", listOf("1", "2", "3", "4")),
            ),
            answerTemplate = null,
        ),
        ItemModel(
            modelId = "subtract-no-borrow-v1",
            lakshyaCode = LAKSHYA_ADD_WITHIN_10,
            strategy = SolutionStrategy.SUBTRACTION_NO_BORROW,
            hiTemplate = "{a} − {b} = ___",
            targetTemplate = "{a} − {b} = ___",
            slots = listOf(
                Slot.Radical("a", listOf("9", "8", "7", "6")),
                Slot.Radical("b", listOf("1", "2", "3", "4")),
            ),
            answerTemplate = null,
        ),
    )

    /** A spec that this pool can actually satisfy. [seed] makes the sheet reproducible. */
    fun spec(seed: Long): WorksheetSpec = WorksheetSpec(
        lakshyaCode = LAKSHYA_ADD_WITHIN_10,
        // A range, never an equality — the assembler's own docs name equality constraints as a
        // leading cause of needless infeasibility.
        minItems = 4,
        maxItems = 8,
        limits = lakshya.limits,
        seed = seed,
    )

    /**
     * Self-check: every model must be well-formed and belong to the declared lakshya.
     *
     * The pattern the project asks for — the assertion that catches the failure which is otherwise
     * silent. A template referencing an undeclared slot renders as a literal `{a}` on a child's
     * worksheet, which no compiler and no green build would notice.
     */
    fun validate(): List<String> = buildList {
        models.forEach { model ->
            addAll(model.validate())
            if (model.lakshyaCode != LAKSHYA_ADD_WITHIN_10) {
                add("${model.modelId} declares lakshya ${model.lakshyaCode}")
            }
        }
        if (models.map { it.strategy }.distinct().size < 2) {
            add("pool has fewer than 2 solution strategies, so items cannot be interleaved")
        }
    }
}
