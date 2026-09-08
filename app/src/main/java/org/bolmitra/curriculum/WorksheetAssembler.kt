package org.bolmitra.curriculum

/**
 * Worksheet assembly — ARCHITECTURE.md §6.16.6 (selection) and §6.16.7 (ordering).
 *
 * Two passes, deliberately separate. V45's operative definition of interleaving is that
 * **adjacent items must not share a solution strategy**, and selection cannot guarantee that —
 * picking a valid set says nothing about the order it ends up in. So selection produces a set,
 * and a second pass orders it.
 *
 * Why bother: interleaved practice scored **61% against 38%** for blocked practice on an
 * unannounced delayed test across 54 classes over four months, d ≈ 0.83, with teachers
 * implementing it without training (V45). That is a large effect for a change that costs one
 * ordering function, and it is the single best-evidenced pedagogical decision in this project.
 */

/** What a worksheet must contain. Mirrors production ATA constraint vocabulary (V43). */
data class WorksheetSpec(
    val lakshyaCode: String,
    /**
     * Desired item count.
     *
     * **Expressed as a range, never an equality.** V43's infeasibility guidance is explicit that
     * equality constraints are a main cause of unnecessary infeasibility, and recommends narrow
     * ranges instead. `itemsPerWorksheet` is also one of the three things V42 found genuinely
     * unspecified in every source checked — so it is a configurable design decision requiring
     * pilot testing, never presented as pedagogy.
     */
    val minItems: Int,
    val maxItems: Int,
    /** Structural limits from the lakshya. Never relaxed — see [Relaxation]. */
    val limits: ItemLimits,
    /** Radical slot values, chosen by difficulty rather than randomly. */
    val radicalChoices: Map<String, String> = emptyMap(),
    /** Base seed. Item seeds derive from it, so a worksheet is reproducible from this alone. */
    val seed: Long = 0,
) {
    init {
        require(minItems in 1..maxItems) { "invalid item range $minItems..$maxItems" }
    }
}

/**
 * Outcome of assembly. Failure is a first-class result carrying a *diagnosis*, not an exception.
 *
 * V43 distinguishes a **deficient pool** from **contradictory constraints**, because the two need
 * opposite responses: the first needs authoring, the second needs the spec loosened. Reporting
 * "infeasible" without saying which is what makes a content pipeline unmaintainable.
 */
sealed interface AssemblyResult {
    data class Success(
        val items: List<GeneratedItem>,
        /** Non-core constraints that were relaxed to reach feasibility, with reasons. */
        val relaxations: List<Relaxation> = emptyList(),
    ) : AssemblyResult

    /** The pool cannot satisfy the structural constraints. Authoring is required. */
    data class DeficientPool(
        val lakshyaCode: String,
        val availableAfterFiltering: Int,
        val required: Int,
        val diagnosis: String,
    ) : AssemblyResult

    /** No pool addresses this lakshya at all — the content-authoring backlog signal (§6.16). */
    data class NoItemModels(val lakshyaCode: String) : AssemblyResult
}

/** A relaxation that was applied, so it can be surfaced rather than hidden. */
data class Relaxation(val constraint: String, val reason: String)

object WorksheetAssembler {

    /**
     * Selects and orders items for [spec].
     *
     * `ponytail:` Greedy selection with an explicit infeasibility report, not an ILP solver.
     * V43 frames ATA as constrained combinatorial optimisation, and a real solver is the correct
     * tool at exam scale with dozens of interacting constraints. Here the constraint set is small
     * and mostly structural, so greedy reaches the same answer. The ceiling is real though:
     * greedy can fail on a pool where a smarter search would succeed, specifically when an early
     * pick starves a later constraint. Upgrade path: swap in a proper solver behind this same
     * function signature if the constraint set grows.
     */
    fun assemble(
        spec: WorksheetSpec,
        models: List<ItemModel>,
        /** Vocabulary the items draw on, filtered against [ItemLimits] before use. */
        wordPool: List<String> = emptyList(),
    ): AssemblyResult {
        val forLakshya = models.filter { it.lakshyaCode == spec.lakshyaCode }
        if (forLakshya.isEmpty()) return AssemblyResult.NoItemModels(spec.lakshyaCode)

        // Structural filtering happens BEFORE selection. These constraints are never relaxed —
        // V43: relax non-core blocks via slack, never structural or logical constraints. An
        // out-of-level word on a child's sheet is not a tolerable trade for a fuller page.
        val admissibleWords = wordPool.filter { AksharaAnalyzer.satisfies(it, spec.limits) }
        val relaxations = mutableListOf<Relaxation>()

        if (wordPool.isNotEmpty() && admissibleWords.isEmpty()) {
            return AssemblyResult.DeficientPool(
                lakshyaCode = spec.lakshyaCode,
                availableAfterFiltering = 0,
                required = spec.minItems,
                diagnosis = "No word in a pool of ${wordPool.size} satisfies the lakshya's " +
                    "structural limits ($spec.limits). This is a DEFICIENT POOL, not a " +
                    "contradictory spec: the limits are correct and the vocabulary needs authoring.",
            )
        }

        // Generate up to maxItems, cycling models so a single model cannot monopolise the sheet.
        val generated = mutableListOf<GeneratedItem>()
        var index = 0
        while (generated.size < spec.maxItems && index < spec.maxItems * MODEL_CYCLE_LIMIT) {
            val model = forLakshya[index % forLakshya.size]
            // Seed derives from the spec seed and the item index, so the whole worksheet is
            // reproducible from spec.seed alone while each item still differs.
            val itemSeed = spec.seed * 31 + index
            generated += ItemGenerator.generate(model, itemSeed, spec.radicalChoices)
            index++
        }

        if (generated.size < spec.minItems) {
            return AssemblyResult.DeficientPool(
                lakshyaCode = spec.lakshyaCode,
                availableAfterFiltering = generated.size,
                required = spec.minItems,
                diagnosis = "Only ${generated.size} items could be generated from " +
                    "${forLakshya.size} model(s), below the minimum of ${spec.minItems}. " +
                    "DEFICIENT POOL: more item models are needed for ${spec.lakshyaCode}.",
            )
        }

        val ordered = InterleaveOrderer.order(generated)
        if (!InterleaveOrderer.isFullyInterleaved(ordered) && ordered.size > 1) {
            // Reported rather than silently accepted: with only one strategy available, the sheet
            // is unavoidably blocked, and the teacher should know the practice benefit is absent.
            relaxations += Relaxation(
                constraint = "interleaving",
                reason = "Only ${ordered.map { it.strategy }.distinct().size} solution " +
                    "strategy/strategies available for ${spec.lakshyaCode}, so adjacent items " +
                    "must repeat one. Interleaving benefit (V45) is reduced or absent.",
            )
        }

        return AssemblyResult.Success(ordered, relaxations)
    }

    /** Guard against an unbounded loop when models produce fewer usable items than requested. */
    private const val MODEL_CYCLE_LIMIT = 4
}

/**
 * The ordering pass — §6.16.7.
 *
 * V45's definition is what is implemented: adjacent items should not share a solution strategy.
 * Note this is *not* shuffling. A shuffle can legally place five counting items in a row; that is
 * blocked practice with extra steps, and blocked practice is the 38% condition.
 */
object InterleaveOrderer {

    /**
     * Greedily places items so that no two neighbours share a strategy, preferring whichever
     * strategy has most items left. Taking from the largest remaining group first is what avoids
     * stranding a big group at the end with nothing to separate its members.
     */
    fun order(items: List<GeneratedItem>): List<GeneratedItem> {
        if (items.size <= 1) return items

        val byStrategy = items.groupBy { it.strategy }
            .mapValues { it.value.toMutableList() }
            .toMutableMap()
        val out = mutableListOf<GeneratedItem>()
        var previous: SolutionStrategy? = null

        while (out.size < items.size) {
            val candidate = byStrategy
                .filter { it.value.isNotEmpty() }
                .filterKeys { it != previous }
                .maxByOrNull { it.value.size }
                ?.key
            // Every remaining item shares the previous strategy — unavoidable repeat.
                ?: byStrategy.filter { it.value.isNotEmpty() }.maxByOrNull { it.value.size }?.key
                ?: break

            out += byStrategy.getValue(candidate).removeAt(0)
            previous = candidate
        }
        return out
    }

    /** True if no two adjacent items share a strategy. */
    fun isFullyInterleaved(items: List<GeneratedItem>): Boolean =
        items.zipWithNext().none { (a, b) -> a.strategy == b.strategy }

    /** Longest run of a single strategy — the metric to watch when full interleaving fails. */
    fun longestRun(items: List<GeneratedItem>): Int {
        if (items.isEmpty()) return 0
        var best = 1
        var current = 1
        for ((a, b) in items.zipWithNext()) {
            current = if (a.strategy == b.strategy) current + 1 else 1
            if (current > best) best = current
        }
        return best
    }
}
