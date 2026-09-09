package org.bolmitra.phrasebook

import android.content.Context
import android.util.Log
import org.bolmitra.speech.TargetLanguage
import org.bolmitra.translit.OlChikiToDevanagari

/**
 * The supplied Hindi→Santali classroom packs, loaded from `assets/santali-classroom-packs.tsv`.
 *
 * Twenty packs of full classroom sentences — commands, questions, encouragement, safety, health,
 * directions — built on a desktop by `tools/build-classroom-packs.py` from
 * `translation for Hindi words to Santali Langauge.md`. 363 rows ship of 423 supplied.
 *
 * ### Why these rows are `MACHINE` and not `VERIFIED`
 *
 * This is the part that matters, and it is not a judgement about quality. The first invariant is
 * that Santali content must come from a **named** native speaker or be **labelled** machine output.
 * The supplied file names no speaker, cites no corpus, and carries evidence of being generated: four
 * rows had Latin words sitting inside the Ol Chiki — one contained a bare `Permission`, another the
 * English word `line` inside brackets — and eight Hindi phrases mapped to two different Santali
 * strings within the same file. Those are rejected by the builder, but they say what the source is.
 *
 * So [Provenance.MACHINE] is the honest level: no human has reviewed these strings *for this
 * classroom*, and the UI already renders that as the weakest rung with its own marker. `CORPUS` is
 * unavailable because there is no citable corpus to name, and `VERIFIED` would assert a review that
 * has not happened. **The moment a Santali speaker signs these off, this is a one-line change** —
 * flip [PROVENANCE] to `VERIFIED` and set `verifiedBy`.
 *
 * ### Precedence: these rows can only ever fill a gap
 *
 * `LiveTurnEngine` concatenates `DemoSeed` → `SantaliGlossary` → `ClassroomPacks`, and
 * `PhraseMatcher`'s exact rung takes the **first** match. That ordering is load-bearing, not
 * cosmetic: the supplied file disagrees with the shipped corpus on **10** normalised Hindi keys,
 * including the two that Revision 27 established as working —
 *
 *  * `बैठ जाओ` — packs say `ᱫᱩᱲᱩᱵ ᱢᱮ`, corpus says `ᱫᱩᱨᱩᱵ` (ᱲ against ᱨ, a different consonant)
 *  * `खड़े हो जाओ` — packs say `ᱛᱤᱸᱜᱩᱱ ᱢᱮ`, corpus says `ᱛᱮᱜᱚ` (a different word entirely)
 *
 * Most of the other eight differ only by the imperative particle `ᱢᱮ`, which is arguably better for
 * a spoken command than a bare dictionary stem — but "arguably" is not a basis for overwriting cited
 * content with uncited content. A citable string that a reviewer can check beats an unreviewed one
 * that might be more idiomatic, so the corpus keeps those ten keys and a test asserts it.
 *
 * ### Fuzzy matching is safe in this direction
 *
 * `SantaliGlossary` documents the single-word hazard: a one-word row must not fuzzy-match a sentence
 * containing it. These rows are the opposite shape — whole sentences — and a long row scores far
 * below `PhraseMatcher.FUZZY_THRESHOLD` against a short query, so they cannot hijack a one-word
 * lookup either. The risk that remains is two long sentences resembling each other, which is what
 * the threshold is for.
 */
object ClassroomPacks {

    private const val TAG = "BolMitra/packs"
    private const val ASSET = "santali-classroom-packs.tsv"
    const val PACK_VERSION = "classroom-packs-v1"

    /** Named so it travels with every row, which is what makes the weak claim checkable. */
    const val SOURCE = "supplied/classroom-packs"

    /**
     * The level every row serves at.
     *
     * A single constant on purpose: promoting this content is a review decision about the whole
     * batch, and scattering the literal across the file would make a partial promotion easy to do
     * by accident.
     */
    val PROVENANCE = Provenance.MACHINE

    /** One row of the supplied packs. */
    data class Entry(
        /** The Hindi a teacher would say. Already split on `/` by the builder. */
        val hindi: String,
        /** Ol Chiki. What the class SEES. */
        val santali: String,
        /** Devanagari. What the voice can actually say — see the TTS contract. */
        val devanagari: String,
        /** The English the row was written from, shown so a reviewer can see the intent. */
        val english: String,
        /** Which pack it came from, e.g. "Pack 9 — Classroom Safety". */
        val pack: String,
    )

    @Volatile
    private var cached: List<Entry>? = null

    /**
     * Loads and caches the packs. Returns an empty list on any failure.
     *
     * Empty rather than throwing, and [Throwable] rather than [Exception]: a missing or truncated
     * asset is an ordinary state on a device that receives content by USB, and the app must degrade
     * to "no packs" rather than refuse to start a lesson.
     */
    fun load(context: Context): List<Entry> {
        cached?.let { return it }
        return synchronized(this) { cached ?: readAsset(context).also { cached = it } }
    }

    private fun readAsset(context: Context): List<Entry> = try {
        val out = ArrayList<Entry>(400)
        var dropped = 0
        var collided = 0
        // Authoritative de-duplication happens here, not in the builder.
        //
        // The builder de-duplicates too, but with a Python approximation of HindiNormalizer, and the
        // approximation is not the normaliser: the real one also spells digits as Hindi words and
        // folds politeness tokens, so two phrases the builder saw as distinct can normalise to one
        // key at runtime. `रुको` did exactly that — it appears as both "Wait" and "Stop", which are
        // different Santali strings for one Hindi word.
        //
        // Keying on the real normaliser's output is the only way to be sure, and first-wins is
        // deterministic because the asset is written in pack order. The alternative — keeping both —
        // would leave which string a class hears decided by list position, invisibly.
        val seen = HashSet<String>(512)
        context.applicationContext.assets.open(ASSET).bufferedReader().use { reader ->
            var first = true
            reader.forEachLine { line ->
                if (first) {
                    first = false           // header
                    return@forEachLine
                }
                if (line.isBlank()) return@forEachLine
                val f = line.split('\t', limit = 4)
                if (f.size < 4) {
                    dropped++
                    return@forEachLine
                }
                val hindi = f[0].trim()
                val santali = f[1].trim()
                if (hindi.isEmpty() || santali.isEmpty()) {
                    dropped++
                    return@forEachLine
                }
                if (!seen.add(HindiNormalizer.normalize(hindi))) {
                    collided++
                    return@forEachLine
                }
                // TTS is Devanagari-in by construction, so the Devanagari has to be produced
                // somewhere; here is the cheapest place, once per load rather than once per turn.
                val deva = OlChikiToDevanagari.transliterate(santali).devanagari
                if (deva.isBlank()) {
                    // A blank Devanagari reaches the voice as silence while the UI reports a hit —
                    // a working lookup the class cannot hear. Drop it instead.
                    dropped++
                    return@forEachLine
                }
                out += Entry(
                    hindi = hindi,
                    santali = santali,
                    devanagari = deva,
                    english = f[2].trim(),
                    pack = f[3].trim(),
                )
            }
        }
        Log.i(TAG, "loaded ${out.size} classroom pack rows" +
            (if (dropped > 0) ", dropped $dropped unusable" else "") +
            (if (collided > 0) ", dropped $collided colliding on a normalised Hindi key" else ""))
        out
    } catch (t: Throwable) {
        Log.w(TAG, "could not read $ASSET; continuing without the classroom packs", t)
        emptyList()
    }

    /**
     * T0 rows for [language], or none where the packs do not apply.
     *
     * Santali only, and the gate is not optional. The asset is Ol Chiki throughout, so serving it for
     * Mundari or Ho would be the same wrong-language bug [DemoSeed.phrasesFor] exists to prevent.
     */
    /**
     * Whether the packs apply to [language] at all.
     *
     * A named function rather than an inline comparison so the gate is testable without a `Context`.
     * Both [phrasesFor] and [size] go through it, which is the point: two copies of the same
     * condition is how one of them ends up wrong.
     */
    fun appliesTo(language: TargetLanguage): Boolean = language == TargetLanguage.SANTALI

    fun phrasesFor(context: Context, language: TargetLanguage): List<Phrase> {
        if (!appliesTo(language)) return emptyList()
        var id = 2_000_000L      // above SantaliGlossary's range so ids cannot collide
        return load(context).map { entry ->
            Phrase(
                id = id++,
                lakshyaCode = null,
                hiText = entry.hindi,
                hiNormalized = HindiNormalizer.normalize(entry.hindi),
                targetTextNative = entry.santali,
                targetTextDeva = entry.devanagari,
                // No pre-rendered audio exists for a supplied row; the orchestrator synthesises
                // from targetTextDeva rather than degrading to text-only.
                audioRef = null,
                verifiedBy = null,
                packVersion = PACK_VERSION,
                src = SOURCE,
                srcEn = entry.english,
                provenance = PROVENANCE,
            )
        }
    }

    /** Row count without building [Phrase] objects, for the Diagnostics counters. */
    fun size(context: Context, language: TargetLanguage): Int =
        if (!appliesTo(language)) 0 else load(context).size
}
