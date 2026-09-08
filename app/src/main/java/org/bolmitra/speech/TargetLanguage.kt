package org.bolmitra.speech

/**
 * The target languages, and an honest statement of what each one can actually do.
 *
 * ### Why this is an enum with capability flags rather than a list of names
 *
 * The app was built around one hardcoded target. Adding a second revealed that "which languages do
 * we support" is the wrong question — each language supports a *different subset of the pipeline*,
 * for reasons that are external to this codebase and will not change quickly:
 *
 * | | Hindi→X translation | X voice |
 * |---|---|---|
 * | **Mundari** `unr` | nothing exists | `facebook/mms-tts-unr`, VITS, working |
 * | **Santali** `sat` | `ai4bharat/indictrans2-indic-indic-1B`, MIT | `ai4bharat/indic-parler-tts`, Apache-2.0 |
 * | **Ho** `hoc` | nothing exists | `facebook/mms-tts-hoc`, VITS, unconverted |
 *
 * Mundari and Ho are absent from both NLLB-200 and IndicTrans2 for the same structural reason: they
 * are not scheduled languages of India. Santali was added to the Eighth Schedule, which is why
 * AI4Bharat covers it. That is a policy fact, not a modelling gap, so it is not going to be fixed by
 * waiting.
 *
 * Encoding this as [Support] on each language means the UI can *say* what a language does instead of
 * offering a picker that silently does nothing for two of three entries. A dropdown that lets a
 * teacher select Ho and then fails is worse than one that shows Ho greyed out with a reason.
 *
 * ### Mundari stays selectable on purpose
 *
 * Its voice works — verified synthesising and playing on device. What it lacks is text: no MT model,
 * and a phrasebook whose target strings are still placeholders. Both are fixable, one cheaply (11
 * human translations), so removing it would throw away the working half.
 */
enum class TargetLanguage(
    /** ISO 639-3. This is the code MMS, FLORES and IndicTrans2 all key on. */
    val iso: String,

    /** English name, for logs and developer-facing surfaces. */
    val englishName: String,

    /** The language's own name, which is what a teacher should see. */
    val endonym: String,

    /** Script the teacher reads and the worksheets print. */
    val script: Script,

    /**
     * Subdirectory under [ModelStore]'s root holding the voice this language is spoken with.
     *
     * Not always its *own* voice — see [Support.BORROWED] and [voiceNote]. Santali points at
     * `tts-unr` because no Santali voice exists that this runtime can execute.
     */
    val voiceDir: String,

    /**
     * Why the voice is not this language's own, or null when it is.
     *
     * Surfaced in the picker. A borrowed voice is a real limitation the teacher can hear, so it
     * belongs on screen rather than in a source comment.
     */
    val voiceNote: String? = null,

    /**
     * Subdirectory holding the Hindi→this-language MT model, or null when no model exists.
     *
     * Null is the common case and is not a TODO: Mundari and Ho are absent from IndicTrans2 and
     * NLLB-200 because they are not scheduled languages, so there is nothing to point at. Kept
     * separate from [voiceDir] rather than derived from [iso] because the directory names do not
     * follow from the language — the Santali model is IndicTrans2's indic→indic checkpoint serving
     * one pair out of many, not a purpose-built hi→sat model.
     */
    val mtDir: String? = null,

    val translation: Support,
    val voice: Support,
) {
    MUNDARI(
        iso = "unr",
        englishName = "Mundari",
        endonym = "\u092E\u0941\u0902\u0921\u093E\u0930\u0940",
        script = Script.DEVANAGARI,
        voiceDir = "tts-unr",
        // No Hindi→Mundari model exists in any released system. The only parallel data is the
        // Karya/Microsoft Research corpus, which is a training input, not a model.
        translation = Support.UNAVAILABLE,
        // Works today. Note the voice is trained on Odia orthography, so DevanagariToOdia runs
        // first — see SherpaMundariTts.
        voice = Support.WORKING,
    ),

    SANTALI(
        iso = "sat",
        englishName = "Santali",
        endonym = "\u1C65\u1C5F\u1C71\u1C5B\u1C5F\u1C72\u1C64",
        // IndicTrans2 emits Ol Chiki natively, so no transliteration sits on this path — unlike
        // Mundari, where the voice forced a Devanagari→Odia hop.
        script = Script.OL_CHIKI,
        // Mundari's voice, deliberately. There is no Santali voice this runtime can run: the only
        // one that exists is ai4bharat/indic-parler-tts, a transformer LM plus a neural codec that
        // sherpa-onnx cannot execute, and there is no mms-tts-sat. Mundari is the closest available
        // phonology — both are North Munda — and the text reaching it is Devanagari either way,
        // via OlChikiToDevanagari then DevanagariToOdia.
        voiceDir = "tts-unr",
        voiceNote = "Spoken by the Mundari voice — no Santali voice exists that this tablet can " +
            "run. The words are Santali; the accent is not.",
        // int8 ONNX export of ai4bharat/indictrans2-indic-indic-dist-320M (MIT), fused to one
        // decoder at 195 MB. Verified on device: token ids match the desktop reference 10/11, the
        // one difference being a near-tie flip in int8 arithmetic. Quantisation is not the limit —
        // the 320M's own quality is (~5/11 acceptable, weakest on the short imperatives T0 covers).
        mtDir = "mt-hi-sat",
        translation = Support.WORKING,
        voice = Support.BORROWED,
    ),

    HO(
        iso = "hoc",
        englishName = "Ho",
        endonym = "\u0939\u094B",
        script = Script.DEVANAGARI,
        voiceDir = "tts-hoc",
        translation = Support.UNAVAILABLE,
        // facebook/mms-tts-hoc exists and is VITS, so unlike Santali it would drop straight into
        // the sherpa runtime already running. It just has not been converted.
        voice = Support.PLANNED,
    ),
    ;

    /**
     * True only when a full voice-to-voice turn is possible for this language today.
     *
     * A borrowed voice still counts — it makes real sound in the right words, and refusing to run
     * would leave Santali silent when it does not have to be. [translation] is deliberately not
     * consulted: §4.5 is explicit that T0 alone satisfies every stated requirement, so a language
     * with a voice and no MT is still a working app.
     */
    val canRunFullTurn: Boolean
        get() = voice == Support.WORKING || voice == Support.BORROWED

    /**
     * One line explaining the language's status, for the UI to show under the picker.
     *
     * Deliberately specific. "Not supported" tells a teacher nothing and tells a developer less.
     */
    val statusLine: String
        get() = when {
            translation == Support.WORKING && voice == Support.WORKING ->
                "Translation and voice both working."
            // The Santali case. Kept short and does NOT repeat voiceNote — the warning banner
            // carries that in full, and saying it twice on one screen trains people to skip both.
            translation == Support.WORKING && voice == Support.BORROWED ->
                "Open-domain translation works, but every phrase is unreviewed machine output, " +
                    "and the voice belongs to another language."
            voice == Support.BORROWED ->
                "A borrowed voice can speak this language's script, but no translation model " +
                    "exists, so only verified phrasebook entries can be used."
            translation == Support.WORKING && voice == Support.PLANNED ->
                "Translation works, but there is no voice yet — the text can be shown and read " +
                    "aloud by the teacher, not spoken by the tablet."
            voice == Support.WORKING && translation == Support.UNAVAILABLE ->
                "Voice works. No Hindi\u2013$englishName translation model exists anywhere, so " +
                    "only verified phrasebook entries can be spoken."
            translation == Support.PLANNED && voice == Support.PLANNED ->
                "Models exist and are permissively licensed, but neither is wired in yet. The " +
                    "voice needs a runtime sherpa-onnx cannot provide."
            voice == Support.PLANNED && translation == Support.UNAVAILABLE ->
                "A VITS voice exists upstream and would fit the current runtime, but it has not " +
                    "been converted. No translation model exists."
            else -> "Partially supported."
        }

    companion object {
        /** The language the app runs as today. */
        val DEFAULT = MUNDARI

        /** Selectable in the picker. All of them — with their real status attached. */
        val selectable: List<TargetLanguage> = entries
    }
}

/** How much of the pipeline a given language actually has. */
enum class Support {
    /** Model is present, converted, and the runtime executes it. Verified on device. */
    WORKING,

    /**
     * It works, but using another language's model.
     *
     * Distinct from [WORKING] because the difference is audible. Santali is spoken by the Mundari
     * voice: the closest phonology that will actually run, since the only real Santali voice is a
     * transformer LM plus neural codec that sherpa-onnx cannot execute. Collapsing this into
     * WORKING would let the app claim a Santali voice it does not have; collapsing it into
     * [PLANNED] would keep Santali silent when it need not be. Neither is true, so this exists.
     */
    BORROWED,

    /**
     * A usable model exists upstream but is not yet converted, exported or wired. Blocked on our
     * work, and therefore schedulable.
     */
    PLANNED,

    /**
     * No model exists in any released system. Blocked on data collection or training by someone,
     * which is a research timeline, not a sprint.
     */
    UNAVAILABLE,
}

/**
 * Scripts the app has to render and print.
 *
 * Kept explicit because it drives more than font choice: [org.bolmitra.translit.DevanagariToOdia]
 * exists solely because the Mundari voice reads Odia, and §6.13's PDF worksheets need an embedded
 * font per script. Ol Chiki in particular is not present on most budget Android builds, so Santali
 * text will need a bundled font before it can be shown at all.
 */
enum class Script(val displayName: String) {
    DEVANAGARI("Devanagari"),
    OL_CHIKI("Ol Chiki"),
}
