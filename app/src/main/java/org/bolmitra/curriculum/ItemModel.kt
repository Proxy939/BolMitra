package org.bolmitra.curriculum

import kotlin.random.Random
import org.bolmitra.phrasebook.Provenance

/**
 * Template-based item generation — ARCHITECTURE.md §6.16.
 *
 * ### Why templates and not a model
 *
 * V40: the field is called **AIG** in educational measurement, and a mini-review of
 * large-scale assessment found that most operational AIG uses template or rule-based methods,
 * while non-template data-driven models still fall short of operational quality. So this is not
 * a shortcut around not having an LLM — template generation *is* the state of the practice. It
 * also runs in milliseconds on a 2 GB tablet, which an on-device LLM cannot (§1.4).
 *
 * ### The finding that shapes this whole file
 *
 * V40 again: **isomorphic variants are not equivalent.** Across 46 item models, 21 showed
 * facility differences of 0.15 or more between variants, and one model's variants admitted more
 * than one correct answer — caught only by manual review. That is why slots are classified
 * rather than simply randomised, and why [ItemModel.validate] exists but cannot replace a human.
 */

/**
 * Matches a `{slot}` placeholder.
 *
 * **The closing brace must stay escaped, and a JVM unit test cannot prove it.** This was
 * `"""\{([^}]+)}"""` — a bare `}` — which `java.util.regex` accepts as a literal, so every
 * desktop test passed. Android's `java.util.regex` is ICU-backed and rejects it outright with
 * `PatternSyntaxException: Syntax error in regexp pattern near index 10`, so the first tap on a
 * generate button killed the process. Nothing about the platform difference is visible from the
 * test suite; it only appeared in logcat on the tablet.
 *
 * Hoisted to a `val` as well as fixed: it was being recompiled once per template per item, inside
 * the generation loop.
 */
private val SLOT_PATTERN = Regex("""\{([^}]+)\}""")

/**
 * A template variable.
 *
 * The distinction is Bejar's, via V40, and it is the load-bearing idea: changing an incidental
 * slot leaves difficulty intact, while changing a radical slot changes difficulty or the
 * construct being measured. Conflating them is how a worksheet drifts off-level silently.
 */
sealed interface Slot {
    val name: String
    val values: List<String>

    /**
     * Free to vary. A child's name, the pictured object, a colour, item order.
     *
     * This is also where F7's per-student variants come from (§6.7): same construct, different
     * surface, so two children cannot simply copy from each other.
     */
    data class Incidental(override val name: String, override val values: List<String>) : Slot

    /**
     * **Pinned by the difficulty tag. Never randomised.**
     *
     * Akshara count, presence of a matra, samyuktakshara, number magnitude, whether an addition
     * carries. Randomising any of these produces an item that no longer measures what its
     * lakshya says it measures.
     */
    data class Radical(override val name: String, override val values: List<String>) : Slot
}

/**
 * How an item is solved. Used only for ordering, never for scoring.
 *
 * Exists because of V45's operative definition of interleaving: **adjacent items should not
 * share a solution strategy.** Without an explicit strategy tag there is nothing to compare, and
 * "interleaved" degrades into "shuffled", which is not the same intervention.
 */
enum class SolutionStrategy {
    LETTER_RECOGNITION,
    MATRA_IDENTIFICATION,
    WORD_READING,
    PICTURE_WORD_MATCH,
    SENTENCE_READING,
    COUNTING,
    NUMERAL_WRITING,
    ADDITION_NO_CARRY,
    ADDITION_WITH_CARRY,
    SUBTRACTION_NO_BORROW,

    /**
     * Class 2's framing, not a shortcut for "times".
     *
     * `C2-NUM-4` asks for multiplication *as repeated addition*, so the prompt shows the addition
     * it stands for. Tagging it separately from [ADDITION_NO_CARRY] also keeps interleaving honest:
     * the two are solved differently even though one is defined via the other.
     */
    MULTIPLICATION_AS_REPEATED_ADDITION,

    /** `C2-NUM-4`'s other half: division presented as sharing equally, never as long division. */
    DIVISION_AS_SHARING,

    /** `C1-NUM-2` and `C2-NUM-2`: continue a sequence of numbers or shapes. */
    PATTERN_EXTENSION,
}

/**
 * A worksheet item template.
 *
 * Bilingual by construction (§6.3): the child's sheet carries Hindi plus the target native
 * script, and the teacher's answer key carries the Devanagari transliteration.
 */
data class ItemModel(
    val modelId: String,
    val lakshyaCode: String,
    val strategy: SolutionStrategy,
    /** Hindi prompt with `{slot}` placeholders. */
    val hiTemplate: String,
    /** Target-language prompt with the same placeholders. */
    val targetTemplate: String,
    val slots: List<Slot>,
    /** Answer template for the teacher's key. Null for open-ended items like tracing. */
    val answerTemplate: String? = null,
    /**
     * Name of the slot whose bound value names a picture, or null for a text-only item.
     *
     * The slot's value is an English `PictureBank` key rather than a Hindi word, because that is
     * also the glossary's join column — one binding then yields the drawing, the Hindi and the
     * corpus target form. Appended last and defaulted so no existing model changes.
     */
    val pictureSlot: String? = null,
    /**
     * Placeholders this model leaves for a later pass to fill, rather than binding from a slot.
     *
     * `{hi}`, `{target}` and `{akshara}` are all derived from the one bound picture key, and
     * `{sequence}`, `{repeated}` and `{total}` are computed from radical values — see
     * `TopicWorksheets.resolve`. Binding them as their own slots is what would let them drift out
     * of step with the picture, which is the failure this design exists to prevent.
     *
     * Declaring them here is not paperwork: it is what keeps [validate] able to tell a deliberate
     * deferral from a typo. Without it `{hi}` and `{hnid}` look identical to the checker, and the
     * typo reaches a child's sheet as literal `{hnid}` text.
     */
    val derivedPlaceholders: Set<String> = emptySet(),
) {
    /**
     * Structural checks that can be automated.
     *
     * Deliberately narrow. V40's cautionary case — variants admitting more than one correct
     * answer — was found by a human reading the items, and nothing here would have caught it.
     * This validates that the template is *well-formed*, not that it is *pedagogically sound*.
     */
    fun validate(): List<String> {
        val problems = mutableListOf<String>()
        val declared = slots.map { it.name }.toSet()

        if (slots.map { it.name }.size != declared.size) {
            problems += "duplicate slot names in $modelId"
        }
        slots.filter { it.values.isEmpty() }.forEach {
            problems += "slot '${it.name}' in $modelId has no values"
        }
        val templates = listOfNotNull(hiTemplate, targetTemplate, answerTemplate)
        for (t in templates) {
            referencedSlots(t).forEach { ref ->
                if (ref !in declared && ref !in derivedPlaceholders) {
                    problems += "$modelId references undeclared slot '$ref'"
                }
            }
        }
        // A derived placeholder nobody references is a stale declaration, and it would silence the
        // typo check for a name the model no longer uses.
        derivedPlaceholders.forEach { name ->
            if (templates.none { name in referencedSlots(it) }) {
                problems += "$modelId declares derived placeholder '$name' but never uses it"
            }
            if (name in declared) {
                problems += "$modelId has '$name' as both a slot and a derived placeholder"
            }
        }
        // A slot declared but never used is dead weight that still consumes seed entropy, which
        // quietly changes every previously issued variant. See generate().
        //
        // The hazard is specific to incidental slots, and narrowing it to those is what makes the
        // check correct rather than merely strict. Only `Slot.Incidental` draws from the Random in
        // generate(); a radical slot takes its value from `radicalChoices` or falls back to
        // `values.first()`, consuming no entropy at all. So an unreferenced radical slot cannot
        // shift any other slot's draw, and several legitimately exist: `pattern` and `share` are
        // pinned by difficulty and then *derived* into the printed text by `TopicWorksheets`,
        // which is a use, just not a template reference.
        //
        // [pictureSlot] counts as a use for the same reason: the generator copies its binding onto
        // the item, so the value reaches the sheet as a drawing instead of as words.
        declared.forEach { name ->
            val isIncidental = slots.firstOrNull { it.name == name } is Slot.Incidental
            if (!isIncidental) return@forEach
            val used = listOfNotNull(hiTemplate, targetTemplate, answerTemplate)
                .any { name in referencedSlots(it) } || name == pictureSlot
            if (!used) problems += "$modelId declares unused slot '$name'"
        }
        return problems
    }

    private fun referencedSlots(template: String): Set<String> =
        SLOT_PATTERN.findAll(template).map { it.groupValues[1] }.toSet()
}

/** One concrete item. Identity is `(modelId, seed)` — see [ItemGenerator]. */
data class GeneratedItem(
    val modelId: String,
    val seed: Long,
    val lakshyaCode: String,
    val strategy: SolutionStrategy,
    val hiText: String,
    val targetText: String,
    val answer: String?,
    /**
     * English `PictureBank` key for the illustration, or null for a text-only item.
     *
     * Resolved from [ItemModel.pictureSlot] at generation time, so it is part of what
     * `(modelId, seed)` reproduces and a reprint shows the same picture.
     */
    val pictureTerm: String? = null,
    /**
     * Provenance of [targetText]. Null when the item carries no target-language words at all —
     * an arithmetic item is the same in every language, so there is nothing to vouch for.
     *
     * **Not optional when there are words.** The invariant is explicit that provenance is stored
     * on a worksheet line as well as a history row: the rule does not stop applying because the
     * medium changed from a voice to a page. `TopicWorksheets` sets it from the glossary row it
     * read, never by inference at display time.
     */
    val targetProvenance: Provenance? = null,
    /** Corpus that [targetText] came from, carried alongside [targetProvenance] as `CORPUS` demands. */
    val targetSrc: String? = null,
    /**
     * The slot values this item was generated from.
     *
     * Kept because the alternative is worse: an answer key for `{a} + {b} = ___` otherwise has to
     * be recovered by parsing the rendered prompt back into numbers, and a generator that reads its
     * own output is one formatting change away from printing wrong answers. With the bindings in
     * hand `TopicWorksheets` computes the sum directly from `a` and `b`.
     */
    val bindings: Map<String, String> = emptyMap(),
)

object ItemGenerator {

    /**
     * Generates a deterministic item.
     *
     * **Item identity is `(modelId, seed)` and rendered worksheets are never persisted** (§6.16,
     * V40). Regenerating from the pair is standard practice — STACK stores the seed as part of
     * the attempt, PrairieLearn generates parameters with matching answers — and it buys exact
     * reprints, answer keys, per-child exposure tracking, and an F2 QR payload that is already
     * just a spec of a few hundred bytes rather than a URL (§6.7).
     *
     * @param radicalChoices values for radical slots, chosen by the **difficulty tag** rather
     *   than randomly. A radical slot with no entry here falls back to its first value, which is
     *   the lowest-difficulty option — never a random one.
     *
     * `ponytail:` One discipline is required and it is easy to get wrong: **all randomness is
     * drawn here, in declared slot order, before any template is filled.** Drawing lazily, or
     * adding a slot in the middle of the list later, silently reshuffles every worksheet ever
     * issued for that model — so a reprint would no longer match the original. The ceiling is
     * that slot order is now part of the model's contract; reordering [slots] is a breaking
     * change. Upgrade path: hash the slot name into the seed instead, so order stops mattering.
     */
    fun generate(
        model: ItemModel,
        seed: Long,
        radicalChoices: Map<String, String> = emptyMap(),
    ): GeneratedItem {
        val random = Random(seed)
        val bindings = LinkedHashMap<String, String>(model.slots.size)

        for (slot in model.slots) {
            bindings[slot.name] = when (slot) {
                is Slot.Radical ->
                    radicalChoices[slot.name]
                        ?.also { require(it in slot.values) { "'$it' not a value of ${slot.name}" } }
                        ?: slot.values.first()
                // Entropy is consumed for every incidental slot in order, whether or not the
                // chosen template happens to reference it.
                is Slot.Incidental -> slot.values[random.nextInt(slot.values.size)]
            }
        }

        return GeneratedItem(
            modelId = model.modelId,
            seed = seed,
            lakshyaCode = model.lakshyaCode,
            strategy = model.strategy,
            hiText = fill(model.hiTemplate, bindings),
            targetText = fill(model.targetTemplate, bindings),
            answer = model.answerTemplate?.let { fill(it, bindings) },
            // Read from the same bindings the templates were filled from, so the picture cannot
            // disagree with the words next to it.
            pictureTerm = model.pictureSlot?.let { bindings[it] },
            bindings = bindings,
        )
    }

    private fun fill(template: String, bindings: Map<String, String>): String =
        SLOT_PATTERN.replace(template) { m ->
            bindings[m.groupValues[1]] ?: m.value
        }
}
