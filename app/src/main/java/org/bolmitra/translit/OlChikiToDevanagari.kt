package org.bolmitra.translit

/**
 * Ol Chiki (Santali) to Devanagari transliteration — the last hop before Santali can be heard.
 *
 * ### Why this exists, and why it is one converter and not two
 *
 * IndicTrans2 emits Santali in Ol Chiki. Two different consumers need something else:
 *
 * 1. **The teacher.** `MtOutput.targetTextDeva` is what a Hindi-reading teacher falls back to in
 *    the `TextOnly` degrade rungs. Ol Chiki is unreadable to them, so that field would otherwise
 *    be a lie.
 * 2. **The voice.** The only Santali-capable voice we can run is the Mundari VITS, and that model
 *    is trained on **Odia** orthography (V63).
 *
 * Rather than write Ol Chiki→Devanagari and Ol Chiki→Odia separately, this produces Devanagari and
 * the existing, already-tested [DevanagariToOdia] does the second hop. That converter is already
 * pinned to the voice's exact 50-symbol inventory and already carries its own lossy-fold notes, so
 * chaining reuses all of it:
 *
 * ```
 * Ol Chiki ──this──> Devanagari ──┬──> targetTextDeva (teacher reads)
 *                                 └──> DevanagariToOdia ──> VITS (class hears)
 * ```
 *
 * ### Where the letter values come from
 *
 * Ol Chiki is an **alphabet**, not an abugida: vowels are full letters, there is no inherent vowel
 * and no virama. Devanagari is an abugida. So this is not a character map — it has to assemble
 * syllables, and [transliterate] is a small state machine rather than a table lookup.
 *
 * Letter-to-sound values are taken from **Unicode CLDR's own `sat_Olck` → `sat_FONIPA` transform**,
 * which is machine-readable and citable, rather than from anybody's recollection:
 * https://www.unicode.org/cldr/cldr-aux/charts/28/delta/transform-sat_Olck-sat_FONIPA.html
 * That transform in turn cites Everson's encoding proposal (WG2 N2984R) and Campbell's
 * *Compendium of the World's Languages*.
 *
 * Three things it taught us that a character table would have got wrong:
 *
 * - **Positional voicing.** `ᱛ ᱜ ᱡ ᱵ` are voiceless/ejective word-initially and voiced after a
 *   letter. CLDR's rule is `$inword {ᱛ} → d`. Ignoring it renders ᱯᱚᱛᱚᱵ as *potop* rather than
 *   *potob*.
 * - **`ᱷ` is not a consonant**, it is the aspiration marker, forming digraphs: `ᱠᱷ` → /kʰ/.
 * - **`ᱽ` AHAD and `ᱼ` PHAARKAA are voicing switches**, not sounds. AHAD forces the voiced reading
 *   (`ᱵᱽ` → /b/), PHAARKAA forces the ejective one (`ᱵᱼ` → /pʼ/).
 *
 * ### `ponytail:` what is approximated, and the one place we overrode CLDR
 *
 * Sound-to-Devanagari follows standard Devanagari Santali practice — Santali is a scheduled
 * language and is written in Devanagari in Jharkhand — but **no Santali speaker has reviewed this
 * table**. Every approximation is tagged [Fold.LOSSY] and listed in [LOSSY_NOTES] so a reviewer can
 * see exactly what was traded away, the same contract [DevanagariToOdia] uses.
 *
 * The deliberate override: CLDR maps `ᱭ` to /h/, but its own source comment says
 * "seems unlikely; would be good to verify". The character is named OL CHIKI LETTER **UY** and is
 * described as /j/ everywhere else, so it is mapped to `य` here. That disagreement is the single
 * highest-priority item for native-speaker review.
 *
 * Ceiling: this is a script conversion by people who do not speak Santali. It cannot be fixed with
 * more code. What fixes it is a named Santali speaker working through [LOSSY_NOTES] — the same
 * Phase 0 risk that keeps `DemoSeed.DEMO_STRINGS_VERIFIED` false.
 */
object OlChikiToDevanagari {

    /** Whether a mapping preserves the sound or approximates it. */
    enum class Fold { EXACT, LOSSY }

    // ---- Devanagari output characters, named so the table below reads as linguistics ----------

    private const val VIRAMA = '\u094D'        // ्
    private const val CANDRABINDU = '\u0901'   // ँ  vowel nasalisation
    private const val NUKTA = '\u093C'         // ़
    private const val DANDA = '\u0964'         // ।
    private const val DOUBLE_DANDA = '\u0965'  // ॥

    // ---- Ol Chiki code points (U+1C50..U+1C7F) -----------------------------------------------

    private const val DIGIT_ZERO = '\u1C50'    // ᱐ .. ᱙
    private const val ASPIRATE = '\u1C77'      // ᱷ  digraph marker, not a sound
    private const val MU_TUDDAG = '\u1C78'     // ᱸ  nasalisation
    private const val GAHLA_TUDDAG = '\u1C79'  // ᱹ  vowel-quality shift
    private const val MU_GAHLA = '\u1C7A'      // ᱺ  both of the above
    private const val RELAA = '\u1C7B'         // ᱻ  length, or gemination on a consonant
    private const val PHAARKAA = '\u1C7C'      // ᱼ  force the ejective/voiceless reading
    private const val AHAD = '\u1C7D'          // ᱽ  force the voiced reading
    private const val MUCAAD = '\u1C7E'        // ᱾  sentence end
    private const val DOUBLE_MUCAAD = '\u1C7F' // ᱿

    /**
     * A vowel letter and its two Devanagari realisations.
     *
     * [matra] is empty for `ᱚ`, whose /ɔ/ is carried by Devanagari's inherent vowel — that is what
     * makes `ᱠᱚ` come out as a bare `क` rather than `क` plus a mark.
     */
    private data class Vowel(
        val independent: String,
        val matra: String,
        /** Distinct long forms, where Devanagari has one. Null means length is not expressible. */
        val longIndependent: String? = null,
        val longMatra: String? = null,
    )

    private val VOWELS: Map<Char, Vowel> = mapOf(
        // ᱚ LA /ɔ/ — the inherent vowel, hence an empty matra.
        '\u1C5A' to Vowel("\u0905", ""),
        // ᱟ LAA /a/
        '\u1C5F' to Vowel("\u0906", "\u093E"),
        // ᱤ LI /i/ — and Devanagari does distinguish the long form.
        '\u1C64' to Vowel("\u0907", "\u093F", "\u0908", "\u0940"),
        // ᱩ LU /u/
        '\u1C69' to Vowel("\u0909", "\u0941", "\u090A", "\u0942"),
        // ᱮ LE /e/
        '\u1C6E' to Vowel("\u090F", "\u0947"),
        // ᱳ LO /o/
        '\u1C73' to Vowel("\u0913", "\u094B"),
    )

    /**
     * A consonant letter and the Devanagari it becomes under each modifier.
     *
     * [plain] and [voiced] differ only for the four stops with positional voicing. For everything
     * else they are the same character and the distinction costs nothing.
     */
    private data class Consonant(
        /** Word-initial, or after PHAARKAA. */
        val plain: String,
        /** After a letter, or after AHAD. */
        val voiced: String = plain,
        /** With the ᱷ aspiration digraph. Null where Devanagari has no aspirated counterpart. */
        val aspirated: String? = null,
        val fold: Fold = Fold.EXACT,
    )

    private val CONSONANTS: Map<Char, Consonant> = mapOf(
        // --- velar -------------------------------------------------------------------------
        // ᱠ AAK /k/, ᱠᱷ /kʰ/, ᱠᱽ /ɡ/
        '\u1C60' to Consonant(plain = "\u0915", voiced = "\u0915", aspirated = "\u0916"),
        // ᱜ AG /kʼ/ initially, /ɡ/ after a letter
        '\u1C5C' to Consonant(plain = "\u0915", voiced = "\u0917", aspirated = "\u0916"),
        // ᱝ ANG /ŋ/
        '\u1C5D' to Consonant(plain = "\u0919"),
        // --- palatal -----------------------------------------------------------------------
        // ᱪ UC /c/, ᱪᱷ /cʰ/
        '\u1C6A' to Consonant(plain = "\u091A", voiced = "\u091A", aspirated = "\u091B"),
        // ᱡ AAJ /cʼ/ initially, /d͡ʒ/ after a letter
        '\u1C61' to Consonant(plain = "\u091A", voiced = "\u091C", aspirated = "\u091B"),
        // ᱧ INY /ɲ/
        '\u1C67' to Consonant(plain = "\u091E"),
        // --- retroflex ---------------------------------------------------------------------
        // ᱴ OTT /ʈ/, ᱴᱷ /ʈʰ/
        '\u1C74' to Consonant(plain = "\u091F", voiced = "\u091F", aspirated = "\u0920"),
        // ᱰ EDD /ɖ/, ᱰᱷ /ɖʰ/
        '\u1C70' to Consonant(plain = "\u0921", voiced = "\u0921", aspirated = "\u0922"),
        // ᱬ UNN /ɳ/
        '\u1C6C' to Consonant(plain = "\u0923"),
        // ᱲ ERR /ɽ/ — ड़, base plus nukta. NFC leaves it decomposed (composition exclusion), which
        // is exactly what DevanagariToOdia's table expects.
        '\u1C72' to Consonant(plain = "\u0921$NUKTA"),
        // --- dental ------------------------------------------------------------------------
        // ᱛ AT /t/ initially, /d/ after a letter; ᱛᱷ /tʰ/
        '\u1C5B' to Consonant(plain = "\u0924", voiced = "\u0926", aspirated = "\u0925"),
        // ᱫ UD /tʼ/ initially, /d/ after a letter
        '\u1C6B' to Consonant(plain = "\u0924", voiced = "\u0926", aspirated = "\u0925"),
        // ᱱ EN /n/
        '\u1C71' to Consonant(plain = "\u0928"),
        // --- labial ------------------------------------------------------------------------
        // ᱯ EP /p/, ᱯᱷ /pʰ/
        '\u1C6F' to Consonant(plain = "\u092A", voiced = "\u092A", aspirated = "\u092B"),
        // ᱵ OB /pʼ/ initially, /b/ after a letter; ᱵᱷ /bʰ/
        '\u1C75' to Consonant(plain = "\u092A", voiced = "\u092C", aspirated = "\u092D"),
        // ᱢ AAM /m/
        '\u1C62' to Consonant(plain = "\u092E"),
        // --- approximants and fricatives ---------------------------------------------------
        // ᱭ UY. CLDR says /h/ and doubts itself; the character name is UY and every description
        // outside CLDR gives /j/. Mapped to य and flagged for review — see LOSSY_NOTES.
        '\u1C6D' to Consonant(plain = "\u092F", fold = Fold.LOSSY),
        // ᱨ IR /r/
        '\u1C68' to Consonant(plain = "\u0930"),
        // ᱞ AL /l/
        '\u1C5E' to Consonant(plain = "\u0932"),
        // ᱣ AAW /w/
        '\u1C63' to Consonant(plain = "\u0935"),
        // ᱶ /w̃/ — nasalised w. Devanagari has no such letter; व plus candrabindu approximates it.
        '\u1C76' to Consonant(plain = "\u0935$CANDRABINDU", fold = Fold.LOSSY),
        // ᱥ IS /s/
        '\u1C65' to Consonant(plain = "\u0938"),
        // ᱦ IH /h/
        '\u1C66' to Consonant(plain = "\u0939"),
    )

    /** Human-readable record of every approximation, for native-speaker review. */
    val LOSSY_NOTES: List<String> = listOf(
        "ᱭ (U+1C6D UY) -> य /j/. CLDR's transform says /h/ but its own comment calls that " +
            "unlikely and asks for verification. HIGHEST PRIORITY to confirm: it changes words, " +
            "not just their colour.",
        "ᱶ (U+1C76) /w̃/ -> व + ँ: Devanagari has no nasalised w, so this is two characters " +
            "standing in for one phoneme.",
        "ᱟᱹ /ə/ -> अ (inherent): folded onto ᱚ /ɔ/, so the gahla-tuddag contrast is lost.",
        "ᱮᱹ /ɛ/ -> ए: folded onto ᱮ /e/; Devanagari has no separate ɛ.",
        "ᱚᱻ /ɔː/ and ᱮᱻ /eː/ and ᱳᱻ /oː/ -> unlengthened: Devanagari marks length only for i and u.",
        "ᱛ/ᱫ both -> त or द. They are distinct letters in Ol Chiki (/t/ vs /tʼ/) that CLDR " +
            "collapses to the same voiced form after a letter; Devanagari cannot separate them.",
        "ᱜ word-initial /kʼ/ -> क and ᱡ word-initial /cʼ/ -> च: the ejective release is dropped, " +
            "because Devanagari has no ejectives at all.",
        "Ol Chiki digits -> Devanagari digits, which DevanagariToOdia then drops: the voice's " +
            "50-symbol inventory has no numerals. Numbers must be spelled as words to be spoken.",
        "᱾ and ᱿ -> । and ॥, which the voice inventory also lacks. Harmless: punctuation is not " +
            "pronounced, and it is dropped with a report rather than silently.",
    )

    data class Result(
        /** Devanagari text. Safe to display, and safe to feed to [DevanagariToOdia]. */
        val devanagari: String,
        /** Ol Chiki characters with no mapping. These were DROPPED — see [isClean]. */
        val unmapped: List<Char>,
        /** Characters that were approximated rather than preserved. */
        val approximated: List<Char>,
    ) {
        /** True if nothing was dropped. Approximations do not make a result unclean. */
        val isClean: Boolean get() = unmapped.isEmpty()
    }

    /**
     * Transliterates Ol Chiki to Devanagari, assembling syllables as it goes.
     *
     * Unmapped characters are dropped and reported, never passed through — handing Devanagari text
     * containing stray Ol Chiki to the next stage would just move the failure somewhere harder to
     * see.
     */
    fun transliterate(olChiki: String): Result {
        val text = normaliseMarks(olChiki)
        val sb = StringBuilder(text.length * 2)
        val unmapped = mutableListOf<Char>()
        val approximated = mutableListOf<Char>()

        // True when a consonant has been emitted that no vowel has yet attached to. It decides
        // whether the next vowel becomes a matra or an independent letter, and whether a virama is
        // needed before the next consonant or at the end of a word.
        var pendingConsonant = false
        // CLDR's $inword context: the four stops voice when *preceded* by a letter or mark.
        var afterLetter = false

        var i = 0
        while (i < text.length) {
            val ch = text[i]

            val vowel = VOWELS[ch]
            if (vowel != null) {
                var nasal = false
                var long = false
                var quality = false
                var j = i + 1
                loop@ while (j < text.length) {
                    when (text[j]) {
                        MU_TUDDAG -> nasal = true
                        MU_GAHLA -> { nasal = true; quality = true }
                        GAHLA_TUDDAG -> quality = true
                        RELAA -> long = true
                        else -> break@loop
                    }
                    j++
                }

                // Gahla tuddag shifts vowel quality. Only two of the five shifts are audible in
                // Devanagari, and both land on a vowel that already exists, so both are folds:
                // ᱟᱹ /ə/ onto the inherent vowel and ᱮᱹ /ɛ/ onto ए.
                var v = vowel
                if (quality) {
                    approximated += ch
                    if (ch == '\u1C5F') v = VOWELS.getValue('\u1C5A')   // ᱟᱹ -> ᱚ
                }
                if (long && v.longMatra == null) approximated += ch

                if (pendingConsonant) {
                    sb.append(if (long) v.longMatra ?: v.matra else v.matra)
                } else {
                    sb.append(if (long) v.longIndependent ?: v.independent else v.independent)
                }
                if (nasal) sb.append(CANDRABINDU)
                pendingConsonant = false
                afterLetter = true
                i = j
                continue
            }

            val consonant = CONSONANTS[ch]
            if (consonant != null) {
                var aspirated = false
                var forceVoiced = false
                var forcePlain = false
                var geminate = false
                var j = i + 1
                loop@ while (j < text.length) {
                    when (text[j]) {
                        ASPIRATE -> aspirated = true
                        AHAD -> forceVoiced = true
                        PHAARKAA -> forcePlain = true
                        RELAA -> geminate = true
                        else -> break@loop
                    }
                    j++
                }

                // A consonant directly after another consonant needs an explicit virama, or
                // Devanagari would read an inherent vowel that is not there.
                if (pendingConsonant) sb.append(VIRAMA)

                val form = when {
                    aspirated && consonant.aspirated != null -> consonant.aspirated
                    // Aspiration was written but Devanagari has no aspirated form for this letter.
                    aspirated -> consonant.plain.also { approximated += ch }
                    forceVoiced -> consonant.voiced
                    forcePlain -> consonant.plain
                    // CLDR's positional rule.
                    afterLetter -> consonant.voiced
                    else -> consonant.plain
                }
                if (consonant.fold == Fold.LOSSY) approximated += ch
                if (!afterLetter && consonant.voiced != consonant.plain && !forceVoiced) {
                    // Word-initial ejective, rendered as a plain stop. Worth reporting rather than
                    // silently flattening.
                    approximated += ch
                }
                sb.append(form)
                // Relaa on a consonant is gemination, which Devanagari writes as the doubled
                // consonant joined by a virama.
                if (geminate) {
                    sb.append(VIRAMA).append(form)
                }
                pendingConsonant = true
                afterLetter = true
                i = j
                continue
            }

            // Everything that is not a letter closes the syllable.
            if (pendingConsonant) {
                sb.append(VIRAMA)
                pendingConsonant = false
            }
            afterLetter = false

            when {
                ch in DIGIT_ZERO..'\u1C59' -> {
                    // Devanagari digits. DevanagariToOdia has no numerals in the voice inventory
                    // and will drop these downstream, with a report.
                    sb.append(('\u0966' + (ch - DIGIT_ZERO)))
                    approximated += ch
                }
                ch == MUCAAD -> sb.append(DANDA)
                ch == DOUBLE_MUCAAD -> sb.append(DOUBLE_DANDA)
                // Whitespace and anything already outside the Ol Chiki block passes through: the
                // model's output contains ordinary spaces, and Latin or Devanagari fragments can
                // survive translation intact.
                ch.isWhitespace() || ch.code !in 0x1C50..0x1C7F -> sb.append(ch)
                // Inside the block but unhandled: a stray combining mark with nothing to attach to.
                else -> unmapped += ch
            }
            i++
        }

        if (pendingConsonant) sb.append(VIRAMA)
        return Result(sb.toString(), unmapped, approximated)
    }

    /**
     * Applies CLDR's mark-normalisation rules before anything else looks at the text.
     *
     * Real-world Ol Chiki text writes these marks in either order, and CLDR normalises them first
     * for exactly that reason. Without this, `ᱸᱹ` and `ᱹᱸ` would take different branches and one of
     * them would silently lose a diacritic.
     */
    private fun normaliseMarks(text: String): String {
        if (text.none { it in MU_TUDDAG..PHAARKAA }) return text
        var s = text
        // A decomposed MU-GAAHLAA, in either order, becomes the single character.
        s = s.replace("$GAHLA_TUDDAG$MU_TUDDAG", MU_GAHLA.toString())
        s = s.replace("$MU_TUDDAG$GAHLA_TUDDAG", MU_GAHLA.toString())
        // Uniform ordering: quality and nasal marks precede length and voicing marks.
        for (second in listOf(GAHLA_TUDDAG, MU_TUDDAG, MU_GAHLA)) {
            s = s.replace("$RELAA$second", "$second$RELAA")
            s = s.replace("$PHAARKAA$second", "$second$PHAARKAA")
        }
        return s
    }

    /**
     * Self-check that everything this table can emit survives the next hop to the voice.
     *
     * Returns offending entries; empty means the chain is sound. This is the guard that matters,
     * and it is the reason the two converters are chained rather than parallel: a Devanagari
     * character outside the VITS model's 50-symbol inventory produces either silence or a wrong
     * phoneme at synthesis time, with nothing in the logs to say so.
     *
     * Digits and punctuation are excluded, because they are *known* to be absent from that
     * inventory and are reported as dropped rather than mis-spoken — see [LOSSY_NOTES].
     */
    fun validateAgainstVoice(): List<String> {
        val problems = mutableListOf<String>()
        val emitted = buildSet {
            for (v in VOWELS.values) {
                addAll(listOf(v.independent, v.matra, v.longIndependent, v.longMatra).filterNotNull())
            }
            for (c in CONSONANTS.values) {
                addAll(listOf(c.plain, c.voiced, c.aspirated).filterNotNull())
            }
            add(VIRAMA.toString())
            add(CANDRABINDU.toString())
        }
        for (s in emitted) {
            for (ch in s) {
                val odia = DevanagariToOdia.transliterate(ch.toString())
                if (odia.unmapped.isNotEmpty()) {
                    problems += "U+%04X has no Odia mapping, so the voice cannot say it"
                        .format(ch.code)
                }
            }
        }
        return problems
    }
}
