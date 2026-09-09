package org.bolmitra.phrasebook

import org.bolmitra.translit.OlChikiNormalizer

/**
 * Word-level Santali for a Hindi sentence the phrasebook has no whole-phrase entry for.
 *
 * ### The gap this fills
 *
 * T0 matches whole phrases. `नमस्ते` is a clean corpus hit, and `नमस्ते बच्चों` was not a hit at all —
 * it fell straight past T0 to the neural model and came back as unreviewed output, even though the
 * corpus held `नमस्ते` → ᱡᱚᱦᱟᱨ and `children` → ᱜᱤᱫᱽᱨᱟ the whole time. Measured over fifteen
 * plausible classroom sentences, **4 of 15 matched as a whole phrase while most of their individual
 * content words were present**. That is the gap: not missing vocabulary, missing *composition*.
 *
 * ### What this is, and what it explicitly is not
 *
 * It is a word-for-word rendering: each Hindi word replaced by the Santali the corpus gives for it,
 * in Hindi word order. **It is not a translation.** Santali is agglutinative and verb-final, and it
 * marks person and number on the verb, so a word-for-word string is missing grammar a speaker would
 * supply. For short classroom imperatives — which is most of what this app carries — the result is
 * usually comprehensible; for anything longer it degrades into a word list.
 *
 * So it sits **below** the phrasebook and its provenance says what it is. It exists because real
 * Santali words in the wrong order beat confident model output that is wrong in ways nobody can see,
 * and because every word in it is traceable to a corpus row a reviewer can check.
 *
 * The alternative considered and rejected was widening `PhraseMatcher`'s fuzzy threshold so long
 * sentences match short rows. That is worse: it would return one word as if it were the translation
 * of a whole sentence, silently dropping the rest, which is the failure
 * `SantaliGlossary`'s single-word hazard note exists to prevent.
 */
object WordComposer {

    /**
     * Minimum share of words that must resolve before a composition is offered at all.
     *
     * Two thirds. Below that the output is more gap than content and a teacher is better served by
     * being told there is no translation — a half-rendered sentence invites the class to guess at the
     * missing half. Chosen, not measured; it is the one number here that wants piloting.
     */
    const val MIN_COVERAGE = 2.0f / 3.0f

    /** Words too grammatical to be worth rendering alone, and harmless to drop. */
    private val SKIPPABLE = setOf("है", "हैं", "था", "थे", "थी", "को", "के", "की", "का", "से", "में", "पर")

    /**
     * Hindi suffixes stripped, longest first, when a word does not match as written.
     *
     * Hindi inflects where the corpus is keyed on a base form: `बच्चों` is the oblique plural of
     * `बच्चा`, `अपनी` the feminine of `अपना`. Sixteen of forty probed classroom words missed, and
     * several missed only for this. Stripping is applied **only after an exact lookup fails**, so it
     * can never override a real entry.
     *
     * This is crude morphology and deliberately so: it is a lookup aid, not a stemmer, and it never
     * touches a target string. The risk it carries is a wrong match on a short word, which is why
     * [MIN_STEM_LENGTH] exists.
     */
    private val SUFFIXES = listOf("ियों", "ाओं", "ों", "ें", "ाँ", "ीं", "े", "ो", "ी", "ा")

    /** A stem shorter than this is too ambiguous to trust. `को` must not become `क`. */
    private const val MIN_STEM_LENGTH = 2

    /** One Hindi word and what the corpus gave for it. */
    data class Word(
        val hindi: String,
        /** Ol Chiki, verbatim from the row. Null when nothing resolved. */
        val santali: String?,
        /** Devanagari, for the voice. Null when nothing resolved. */
        val devanagari: String?,
        /** The corpus the row came from, so the line stays checkable. */
        val src: String?,
        /** The English the corpus actually translated. A near-miss is visible through this. */
        val srcEn: String?,
        /** True when the match needed a suffix stripped, e.g. बच्चों -> बच्चा. */
        val viaStem: Boolean,
    ) {
        val resolved: Boolean get() = santali != null
    }

    /** A whole composed line. */
    data class Composition(
        val words: List<Word>,
        /** Ol Chiki, space-joined, resolved words only. What the class sees. */
        val native: String,
        /** Devanagari, space-joined. What the voice is given. */
        val devanagari: String,
        val resolvedCount: Int,
        val totalCount: Int,
    ) {
        val coverage: Float get() = if (totalCount == 0) 0f else resolvedCount.toFloat() / totalCount

        /** Words that produced nothing, so a teacher can see what is absent rather than guess. */
        val missing: List<String> get() = words.filter { !it.resolved }.map { it.hindi }

        /** Corpora involved, for the provenance line. */
        val sources: List<String> get() = words.mapNotNull { it.src }.distinct().sorted()
    }

    /**
     * Composes [hindi] word by word from [lookup], or null when too little resolved.
     *
     * @param lookup normalised single Hindi word -> phrase row. Built by the caller from whatever
     *   sources it serves, so this stays free of Android and of any particular asset.
     */
    fun compose(hindi: String, lookup: (String) -> Phrase?): Composition? {
        val tokens = HindiNormalizer.normalize(hindi)
            .split(' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null

        val words = tokens.map { token -> resolve(token, lookup) }

        // Skippable grammatical words are not counted against coverage. Requiring `है` to resolve
        // would fail sentences whose content words all did, which is the opposite of useful.
        val counted = words.filter { it.hindi !in SKIPPABLE }
        if (counted.isEmpty()) return null

        val resolved = counted.count { it.resolved }
        val composition = Composition(
            words = words,
            native = words.mapNotNull { it.santali }.joinToString(" "),
            devanagari = words.mapNotNull { it.devanagari }.joinToString(" "),
            resolvedCount = resolved,
            totalCount = counted.size,
        )

        if (composition.coverage < MIN_COVERAGE) return null
        // A composition whose Ol Chiki would not render is worse than none: the class sees boxes and
        // the teacher cannot tell whether the words were wrong or the font was.
        if (OlChikiNormalizer.rejectionReason(composition.native) != null) return null
        if (composition.devanagari.isBlank()) return null
        return composition
    }

    private fun resolve(token: String, lookup: (String) -> Phrase?): Word {
        lookup(token)?.let { return it.toWord(token, viaStem = false) }

        // Exact failed; try the base form. Longest suffix first, so `बच्चों` is tried as `बच्च` +
        // reconstructions rather than losing only the final vowel.
        for (suffix in SUFFIXES) {
            if (!token.endsWith(suffix)) continue
            val stem = token.removeSuffix(suffix)
            if (stem.length < MIN_STEM_LENGTH) continue
            lookup(stem)?.let { return it.toWord(token, viaStem = true) }
            // Hindi base forms usually end in a vowel, so try the two commonest re-endings. This is
            // what turns `बच्चों` into `बच्चा`, which is the form the corpus is keyed on.
            for (ending in listOf("ा", "ी")) {
                lookup(stem + ending)?.let { return it.toWord(token, viaStem = true) }
            }
        }
        return Word(token, null, null, null, null, viaStem = false)
    }

    private fun Phrase.toWord(hindi: String, viaStem: Boolean) = Word(
        hindi = hindi,
        santali = targetTextNative.ifBlank { null },
        devanagari = targetTextDeva.ifBlank { null },
        src = src,
        srcEn = srcEn,
        viaStem = viaStem,
    )

    /**
     * Self-check for the properties that fail silently.
     *
     * A composer that resolved nothing would look identical to a missing corpus, and one that
     * accepted any coverage would put a one-word answer in front of a class as though it were the
     * whole sentence.
     */
    fun validate(): List<String> = buildList {
        val rows = mapOf(
            "नमस्ते" to "\u1C61\u1C5A\u1C66\u1C5F\u1C68",       // johar
            "बच्चा" to "\u1C6C\u1C64\u1C6B\u1C68\u1C5F",         // stand-in, shape only
        )
        val lookup: (String) -> Phrase? = { key ->
            rows[key]?.let {
                Phrase(
                    id = 0, lakshyaCode = null, hiText = key,
                    hiNormalized = HindiNormalizer.normalize(key),
                    targetTextNative = it, targetTextDeva = "क", audioRef = null,
                    verifiedBy = null, packVersion = "t", src = "test", srcEn = key,
                    provenance = Provenance.CORPUS,
                )
            }
        }

        // The reported case: two words, both reachable, one only via a suffix strip.
        val c = compose("नमस्ते बच्चों", lookup)
        if (c == null) {
            add("नमस्ते बच्चों did not compose")
        } else {
            if (c.resolvedCount != 2) add("expected 2 resolved words, got ${c.resolvedCount}")
            if (!c.words.any { it.viaStem }) add("बच्चों should have resolved via a stem")
        }

        // Below the floor must yield nothing rather than a fragment.
        if (compose("नमस्ते कल परसों चलेंगे घर", lookup) != null) {
            add("a mostly-unresolved sentence was still composed")
        }
        if (compose("", lookup) != null) add("empty input composed")
        // Grammatical words alone are not a sentence worth rendering.
        if (compose("है को के", lookup) != null) add("only skippable words composed")
    }
}
