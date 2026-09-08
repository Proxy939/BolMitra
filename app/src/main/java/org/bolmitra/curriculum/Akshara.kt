package org.bolmitra.curriculum

/**
 * Devanagari akshara segmentation — ARCHITECTURE.md §6.16.5.
 *
 * ### Why akshara and not phoneme
 *
 * V41 changed the difficulty model. In akshara scripts the akshara encodes a syllable-sized unit,
 * and **akshara knowledge, not phoneme awareness, is the main predictor of word reading**.
 * Phoneme awareness turns out to be largely a *product* of instruction rather than a
 * prerequisite — it became related to word reading only after explicit instruction in akshara
 * construction, and word reading predicted phoneme awareness rather than the reverse.
 *
 * The practical consequence: an English-style CVC to CCVC phonics ladder does not transfer. The
 * ladder is **base akshara -> akshara + matra -> samyuktakshara**, and that requires counting
 * aksharas, matras and conjuncts, which is what this file does.
 *
 * Applies to Devanagari, which covers both Hindi and Mundari as written in Jharkhand (V7). Ol
 * Chiki (Santhali) is alphabetic rather than abugida (V21), so it needs different rules — a
 * Phase 5 concern, deliberately not generalised for here.
 */
object AksharaAnalyzer {

    // Devanagari code points, per the Unicode block.
    private const val VIRAMA = '\u094D'          // ् — binds consonants into a conjunct
    private const val NUKTA = '\u093C'           // ़
    private const val ANUSVARA = '\u0902'        // ं
    private const val CANDRABINDU = '\u0901'     // ँ
    private const val VISARGA = '\u0903'         // ः

    private fun Char.isConsonant(): Boolean =
        this in '\u0915'..'\u0939' || this in '\u0958'..'\u095F'

    private fun Char.isIndependentVowel(): Boolean = this in '\u0905'..'\u0914'

    /** Dependent vowel signs — the matras. */
    private fun Char.isMatra(): Boolean =
        this in '\u093A'..'\u094C' && this != VIRAMA ||
            this in '\u094E'..'\u094F' ||
            this in '\u0955'..'\u0957' ||
            this in '\u0962'..'\u0963'

    private fun Char.isNasalOrVisarga(): Boolean =
        this == ANUSVARA || this == CANDRABINDU || this == VISARGA

    /** True for anything that attaches to the akshara before it rather than starting a new one. */
    private fun Char.isAttaching(): Boolean =
        isMatra() || isNasalOrVisarga() || this == NUKTA || this == VIRAMA

    /**
     * Splits a word into akshara clusters.
     *
     * A cluster starts at a consonant or independent vowel, and absorbs any matras, nasals,
     * nukta and virama that follow. A virama binds the *next* consonant into the same cluster —
     * that is what makes क्ष one akshara rather than two, and getting it wrong would misgrade
     * every conjunct word in the corpus.
     */
    fun segment(word: String): List<String> {
        val clusters = mutableListOf<StringBuilder>()
        var pendingVirama = false

        for (ch in word) {
            when {
                ch.isAttaching() -> {
                    // Attaches to the current cluster. A leading matra with no base is malformed
                    // input; keep it rather than silently dropping a character.
                    if (clusters.isEmpty()) clusters.add(StringBuilder())
                    clusters.last().append(ch)
                    pendingVirama = ch == VIRAMA
                }
                ch.isConsonant() || ch.isIndependentVowel() -> {
                    if (pendingVirama && clusters.isNotEmpty()) {
                        // Conjunct: this consonant belongs to the previous cluster.
                        clusters.last().append(ch)
                    } else {
                        clusters.add(StringBuilder().append(ch))
                    }
                    pendingVirama = false
                }
                else -> {
                    // Non-Devanagari (space, punctuation, Latin). Ends the current cluster.
                    pendingVirama = false
                }
            }
        }
        return clusters.map { it.toString() }.filter { it.isNotEmpty() }
    }

    fun countAksharas(word: String): Int = segment(word).size

    fun countMatras(word: String): Int = word.count { it.isMatra() }

    /** True if the word contains a samyuktakshara — the top rung of the §6.16.5 ladder. */
    fun hasConjunct(word: String): Boolean {
        val v = word.indexOf(VIRAMA)
        // A virama at the very end is a halant marker, not a conjunct.
        return v >= 0 && v < word.length - 1 && word[v + 1].isConsonant()
    }

    /**
     * Places a word on the §6.16.5 difficulty ladder.
     *
     * Ordered most-difficult-first: a word with a conjunct sits on the top rung regardless of how
     * many matras it also has, because the conjunct is what makes it hard.
     */
    fun rung(word: String): AksharaRung = when {
        hasConjunct(word) -> AksharaRung.SAMYUKTAKSHARA
        countMatras(word) > 0 -> AksharaRung.AKSHARA_WITH_MATRA
        else -> AksharaRung.BASE_AKSHARA
    }

    /**
     * Whether a word is admissible for a lakshya's structural limits.
     *
     * Used by item selection to filter a candidate pool before scoring, so an out-of-range word
     * never reaches a child's worksheet.
     */
    fun satisfies(word: String, limits: ItemLimits): Boolean {
        limits.maxAksharas?.let { if (countAksharas(word) > it) return false }
        limits.maxMatras?.let { if (countMatras(word) > it) return false }
        if (!limits.allowConjuncts && hasConjunct(word)) return false
        return true
    }
}

/** The §6.16.5 ladder, replacing the CVC -> CCVC model that V41 established does not transfer. */
enum class AksharaRung { BASE_AKSHARA, AKSHARA_WITH_MATRA, SAMYUKTAKSHARA }
