package org.bolmitra.phrasebook

import android.content.Context
import android.util.Log
import org.bolmitra.speech.TargetLanguage
import org.bolmitra.translit.OlChikiToDevanagari

/**
 * The shipped Santali glossary, loaded from `assets/santali-glossary.tsv`.
 *
 * Built on a desktop by `tools/build-santali-glossary.py` from CC0 and CC-BY sources — Prasanta
 * Hembram's Santali resources and Google's GATITOS. 5,151 English keys, of which the ones carrying a
 * hand-authored Hindi key are reachable from a Hindi transcript.
 *
 * ### Two consumers, one asset
 *
 *  * [phrasesFor] turns the Hindi-keyed rows into T0 [Phrase] rows at [Provenance.CORPUS], so a hit
 *    means T1 never runs.
 *  * [terms] exposes the Hindi -> Santali map for glossary-biased decoding, which is how the model
 *    gets to see attested forms when T0 misses.
 *
 * ### Why every row is CORPUS and not VERIFIED
 *
 * A human wrote these strings, so they are not machine output. They wrote them for a dictionary,
 * not for this classroom, and no Santali speaker has reviewed them *for this app* — so they are not
 * verified either. `src` names the corpus and travels with the row, which is what makes the weaker
 * claim checkable rather than just modest.
 *
 * ### The single-word hazard, and why the threshold saves us
 *
 * Most rows are single words. If a teacher says `किताब खोलो` ("open the book") and T0 fuzzy-matched
 * that to the one-word row `किताब`, the class would hear "book" — the verb silently dropped. That is
 * a worse failure than no translation, because it is confident.
 *
 * It does not happen, and the reason is arithmetic rather than luck: `PhraseMatcher.FUZZY_THRESHOLD`
 * is 0.72, and a short word scores far below that against a sentence containing it (`किताब` vs
 * `किताब खोलो` is about 0.53 by trigram Dice, `पानी` vs `पानी पीना है` about 0.40). Exact matches
 * still fire, which is what we want — a teacher who says only `पानी` should hear `ᱫᱟᱜ`.
 *
 * **That is a safety property this class depends on, so it is asserted in a test rather than left as
 * a comment.** If the threshold is ever lowered, the test fails first.
 */
object SantaliGlossary {

    private const val TAG = "BolMitra/glossary"
    private const val ASSET = "santali-glossary.tsv"
    const val PACK_VERSION = "glossary-v1"

    /** One glossary row, after normalisation and transliteration. */
    data class Term(
        val english: String,
        /**
         * The Hindi lookup keys, `;`-separated as the asset stores them.
         *
         * Plural because Hindi says one instruction several ways and only one can be the dictionary
         * form — `sit` is `बैठो`, but a teacher says `बैठ जाओ`. Use [hindiKeys] rather than parsing
         * this. Blank when the term is deliberately unreachable from Hindi (see the keys file).
         */
        val hindi: String,
        /** Ol Chiki. What the class SEES. */
        val santali: String,
        /** Devanagari. What the voice can actually say — see the TTS contract. */
        val devanagari: String,
        val src: String,
        val kind: String,
        val pos: String,
        /** Other sources' forms for the same English, where they disagreed. Never merged. */
        val variants: List<String>,
    ) {
        /** The Hindi aliases, split and cleaned. Empty when this row is unreachable from Hindi. */
        val hindiKeys: List<String>
            get() = hindi.split(';').map { it.trim() }.filter { it.isNotEmpty() }
    }

    @Volatile
    private var cached: List<Term>? = null

    /**
     * Loads and caches the glossary. Returns an empty list on any failure.
     *
     * Empty rather than throwing, and [Throwable] rather than [Exception]: a missing or truncated
     * asset is an ordinary state on a device that receives content by USB, and the app must degrade
     * to "no glossary" rather than refuse to start a lesson.
     */
    fun load(context: Context): List<Term> {
        cached?.let { return it }
        val loaded = synchronized(this) {
            cached ?: readAsset(context).also { cached = it }
        }
        return loaded
    }

    private fun readAsset(context: Context): List<Term> = try {
        val out = ArrayList<Term>(5200)
        var dropped = 0
        context.applicationContext.assets.open(ASSET).bufferedReader().use { reader ->
            var first = true
            reader.forEachLine { line ->
                if (first) {
                    first = false            // header row
                    return@forEachLine
                }
                if (line.isBlank()) return@forEachLine
                // Split with a limit so a trailing empty column is preserved rather than trimmed
                // away, which would shift every field after it.
                val f = line.split('\t', limit = 8)
                if (f.size < 8) {
                    dropped++
                    return@forEachLine
                }
                val santali = f[2]
                if (santali.isBlank()) {
                    dropped++
                    return@forEachLine
                }
                // The asset holds Ol Chiki only. TTS is Devanagari-in by construction, so the
                // Devanagari has to be produced somewhere; here is the cheapest place, once per
                // load rather than once per turn.
                val deva = OlChikiToDevanagari.transliterate(santali)
                out += Term(
                    english = f[0],
                    hindi = f[1],
                    santali = santali,
                    devanagari = deva.devanagari,
                    src = f[3],
                    kind = f[4],
                    pos = f[5],
                    variants = f[7].split(';').filter { it.isNotBlank() },
                )
            }
        }
        Log.i(TAG, "loaded ${out.size} terms (${out.count { it.hindi.isNotBlank() }} Hindi-keyed)" +
            if (dropped > 0) ", dropped $dropped malformed" else "")
        out
    } catch (t: Throwable) {
        Log.w(TAG, "could not read $ASSET; continuing without a glossary", t)
        emptyList()
    }

    /**
     * T0 rows for [language], or none where the glossary does not apply.
     *
     * Only Santali today. The asset is Ol Chiki throughout, so serving it for Mundari or Ho would be
     * the same wrong-language bug `DemoSeed.phrasesFor` exists to prevent.
     *
     * Rows with no Hindi key are skipped: unreachable from a Hindi transcript, and a row that can
     * never match is just memory.
     */
    fun phrasesFor(context: Context, language: TargetLanguage): List<Phrase> {
        if (language != TargetLanguage.SANTALI) return emptyList()
        var id = 1_000_000L      // above DemoSeed's range so ids cannot collide
        return load(context).flatMap { term ->
            // A blank Devanagari means the transliterator dropped everything, which would reach the
            // voice as silence while the UI reported a hit.
            if (term.devanagari.isBlank()) return@flatMap emptyList()
            // One row per Hindi alias. Hindi says the same instruction several ways and only one of
            // them can be the dictionary form: `sit` is बैठो, but a teacher says बैठ जाओ. Each alias
            // becomes its own Phrase pointing at the SAME corpus string, so the aliasing cannot
            // change what a class hears -- only whether the corpus row is reachable at all.
            term.hindiKeys.map { key ->
                Phrase(
                    id = id++,
                    lakshyaCode = null,
                    hiText = key,
                    hiNormalized = HindiNormalizer.normalize(key),
                    targetTextNative = term.santali,
                    targetTextDeva = term.devanagari,
                    // No pre-rendered pack audio exists for a glossary row. The orchestrator
                    // synthesises from targetTextDeva instead of degrading to text-only.
                    audioRef = null,
                    verifiedBy = null,
                    packVersion = PACK_VERSION,
                    src = term.src,
                    srcEn = term.english,
                    provenance = Provenance.CORPUS,
                )
            }
        }
    }

    // A `terms(): Map<normalisedHindi, List<SantaliForm>>` accessor lived here, built to feed
    // glossary-biased decoding into T1. It is gone because that feature was measured and rejected,
    // and an unused accessor is a claim that something needs it.
    //
    // The measurement: of the 22 glossary terms found across the 11 eval sentences, the two that
    // decide the failing cases (बैठ जाओ, खड़े हो जाओ) are now exact T0 hits and return the corpus
    // string verbatim, which strictly beats biasing a model toward it. Of the rest, `जाओ` in
    // बैठ जाओ maps to `go` — the precise error T1 already makes — and `ताली बजाओ` reaches `Palate`
    // through a shared stem. Biasing on those would have reinforced two known-wrong answers.
    // Re-adding this is ~15 lines if a beam search ever makes constrained decoding worth it.
}
