package org.bolmitra.phrasebook

import org.bolmitra.translate.PhrasebookEngine

/**
 * [PhrasebookEngine] over an in-memory phrase list.
 *
 * This is the seam the interface exists for. §6.3's real T0 is SQLite FTS4 + a prebuilt index
 * shipped inside a language pack, but FTS4 can only be exercised on a device, so this stands in
 * until then. Swapping it is a constructor change, not a rewrite — the lookup ladder itself
 * lives in [PhraseMatcher] and is already unit-tested.
 *
 * Note what this does NOT fake: normalisation, the ladder, provenance and slot fill are all the
 * real implementations. Only candidate retrieval is substituted — FTS4 would narrow the
 * candidate set for speed, and scanning a short list gives identical results.
 */
class InMemoryPhrasebook(private val phrases: List<Phrase>) : PhrasebookEngine {
    override fun lookup(rawHindi: String): LookupResult? =
        PhraseMatcher.lookup(rawHindi, phrases)

    val size: Int get() = phrases.size
}

/**
 * Seed phrases for on-device smoke testing.
 *
 * ### The Mundari here is NOT real
 *
 * Every `targetTextNative` / `targetTextDeva` value below is a transparent placeholder. §6.3
 * requires every shipped phrase to be **native-speaker verified** — that is what makes T0 safe
 * in front of children, and it is the reason T0 outranks every model in the design.
 *
 * Securing native-speaker reviewers is Phase 0 risk #1 and it is still open. Until it closes,
 * these strings exist only to exercise the ladder, and [DEMO_STRINGS_VERIFIED] gates a warning
 * banner in the UI so nobody mistakes this for content.
 *
 * Devanagari is nonetheless the correct target script: V7 confirms Mundari in Jharkhand is
 * written in Devanagari, which is why no transliteration sits on the critical path (§4.6).
 */
object DemoSeed {

    /** Flip to true only when a named native speaker has signed off on every string. */
    const val DEMO_STRINGS_VERIFIED = false

    private var nextId = 1L

    private fun p(
        hi: String,
        lakshya: String,
        template: Boolean = false,
        audio: String? = "demo/placeholder.wav",
    ): Phrase {
        val id = nextId++
        return Phrase(
            id = id,
            lakshyaCode = lakshya,
            hiText = hi,
            hiNormalized = if (template) {
                HindiNormalizer.normalizeTemplate(hi)
            } else {
                HindiNormalizer.normalize(hi)
            },
            // PLACEHOLDER. See the class docs above.
            targetTextNative = "[unr-$id अनुवाद-लंबित]",
            targetTextDeva = "[deva-$id अनुवाद-लंबित]",
            audioRef = audio,
            verifiedBy = null,
            packVersion = "demo-v0",
            isTemplate = template,
        )
    }

    /**
     * Classroom instructions chosen to cover the ladder's branches, not to be a curriculum.
     * Lakshya codes are illustrative — the real taxonomy loads from the official NIPUN Bharat
     * material as local JSON (§6.3).
     */
    val phrases: List<Phrase> = listOf(
        p("किताब खोलो", "FLN-L-01"),
        p("किताब बंद करो", "FLN-L-01"),
        p("ताली बजाओ", "FLN-L-02"),
        p("ध्यान से सुनो", "FLN-L-02"),
        p("अपना नाम बताओ", "FLN-L-03"),
        p("मेरे साथ बोलो", "FLN-L-03"),
        p("बैठ जाओ", "FLN-L-04"),
        p("खड़े हो जाओ", "FLN-L-04"),
        // Template: covers page numbers without storing every variant (§6.3 step 3).
        p("पेज {n} खोलो", "FLN-L-01", template = true),
        p("{n} बार ताली बजाओ", "FLN-N-01", template = true),
        // Deliberately missing audio, so the AUDIO_ASSET_MISSING rung is reachable on device.
        p("पानी पीना है", "FLN-L-05", audio = null),
    )
}
