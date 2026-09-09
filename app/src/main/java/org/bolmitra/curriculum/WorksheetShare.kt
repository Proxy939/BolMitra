package org.bolmitra.curriculum

/**
 * The format a generated worksheet travels in when a teacher shares it — ARCHITECTURE.md §6.7.
 *
 * ### What the QR is for, and what it deliberately is not
 *
 * It is the sheet, not a link to the sheet. The landing screen promises nothing leaves the tablet,
 * so a QR pointing at a server would relocate the promise rather than keep it — the data would leave
 * the moment anyone scanned it. There is also no download to offer: the app holds no `INTERNET`
 * permission, so a URL payload could not be fetched by this app even if we wanted it to be.
 *
 * ### One payload, two readers
 *
 * A shared sheet has two audiences and they need different things, so the payload serves both
 * without choosing:
 *
 *  * **Any camera app.** The body is plain readable text, so a parent or a teacher on a phone with
 *    no BolMitra installed sees the questions immediately. Nothing to install, nothing to fetch.
 *  * **Another BolMitra tablet.** The second line is a compact spec — topic, grade, seed, glossary
 *    pack — and item identity is `(modelId, seed)` by design, so the receiving tablet *regenerates*
 *    the identical sheet: every item, the pictures, the target-language words and the answer key.
 *
 * That split is what makes the size ceiling a non-issue for fidelity. The readable body truncates on
 * an item boundary when a sheet is long, but the spec line is about forty characters and always
 * survives, so a BolMitra scan is never the truncated version.
 *
 * ### Why the answer key is not in the payload
 *
 * A shared sheet is something a child may be handed. The key is regenerated from the spec on a
 * teacher's tablet instead, which is the one place it is wanted.
 *
 * ### The pack version is not decoration
 *
 * Target-language words come from the shipped glossary, so two tablets on different packs would
 * regenerate *different* bilingual lines from the same seed. [SharedSheetSpec.matchesPack] exists so
 * the receiver can say "this sheet was made with glossary-v1 and you have glossary-v2" rather than
 * silently rendering something the sender never saw.
 */
object WorksheetShare {

    /**
     * Format marker and version. Bump it if the field order ever changes.
     *
     * A version in the payload is what lets an old tablet refuse a new format instead of
     * misreading it — [parseSpec] returns null on anything it does not recognise.
     */
    const val SPEC_PREFIX = "BM1"

    /** Field separator. A pipe cannot appear in an enum name, a number or a pack version. */
    private const val SEP = '|'

    /**
     * A shared sheet's identity, recovered from a scan.
     *
     * These five fields are everything `TopicWorksheets.build` needs, which is the property that
     * makes sharing cheap: the sheet is not transmitted, it is described.
     */
    data class SharedSheetSpec(
        val topic: WorksheetTopic,
        val gradeBand: GradeBand,
        val seed: Long,
        val packVersion: String?,
    ) {
        /**
         * True when [current] is the pack this sheet was built with.
         *
         * A null on either side means unknown rather than matching: a sheet shared before pack
         * versions were recorded cannot be vouched for, and saying so is the honest answer.
         */
        fun matchesPack(current: String?): Boolean =
            packVersion != null && current != null && packVersion == current
    }

    /** The spec line. Deliberately short enough that it can never be the part that truncates. */
    fun specLine(sheet: Sheet): String = listOf(
        SPEC_PREFIX,
        sheet.topic.name,
        sheet.gradeBand.name,
        sheet.seed.toString(),
        sheet.packVersion ?: "",
    ).joinToString(SEP.toString())

    /**
     * Recovers a spec from scanned text, or null if there is not a valid one in it.
     *
     * Scans every line rather than assuming position, because a camera app may add its own text and
     * because a teacher may paste the payload with a changed leading line. Unknown enum names give
     * null instead of an exception — a scan is untrusted input, and a malformed QR must not be able
     * to crash a screen in front of a class.
     */
    fun parseSpec(payload: String): SharedSheetSpec? {
        for (line in payload.lineSequence()) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("$SPEC_PREFIX$SEP")) continue
            val f = trimmed.split(SEP)
            if (f.size < 5) continue
            val topic = WorksheetTopic.entries.firstOrNull { it.name == f[1] } ?: continue
            val band = GradeBand.entries.firstOrNull { it.name == f[2] } ?: continue
            val seed = f[3].toLongOrNull() ?: continue
            return SharedSheetSpec(
                topic = topic,
                gradeBand = band,
                seed = seed,
                packVersion = f[4].ifBlank { null },
            )
        }
        return null
    }

    /**
     * Rebuilds the sheet a scan describes.
     *
     * The receiver supplies its *own* vocabulary, which is why [SharedSheetSpec.matchesPack] matters:
     * pass a different glossary and the arithmetic comes back identical while the bilingual lines
     * may not. Callers should check the pack and tell the teacher, rather than assume.
     */
    fun rebuild(
        spec: SharedSheetSpec,
        vocabulary: Map<String, TargetWord> = emptyMap(),
        packVersion: String? = null,
    ): SheetResult = TopicWorksheets.build(
        topic = spec.topic,
        band = spec.gradeBand,
        seed = spec.seed,
        vocabulary = vocabulary,
        packVersion = packVersion,
    )

    /**
     * The scannable text for [sheet]: spec line first, then the questions in readable Hindi.
     *
     * Truncates whole items and says so, so a parent who scans a shortened sheet can tell that is
     * what happened. [maxChars] mirrors the QR capacity ceiling; see `QrCode.MAX_PAYLOAD_CHARS`.
     */
    fun payloadFor(sheet: Sheet, maxChars: Int = 800): String {
        val header = "BolMitra — ${sheet.titleHindi}\n"
        val spec = specLine(sheet) + "\n"
        val body = StringBuilder(header).append(spec)
        var included = 0

        for ((index, item) in sheet.items.withIndex()) {
            val line = "${index + 1}. ${item.hiText}\n"
            // Reserve room for the truncation notice, so adding it can never overflow.
            if (body.length + line.length > maxChars - TRUNCATION_RESERVE) break
            body.append(line)
            included++
        }
        if (included < sheet.items.size) {
            body.append("… ${sheet.items.size - included} और प्रश्न — पूरा पत्रक BolMitra में खोलें\n")
        }
        return body.toString()
    }

    /** Room kept for the "n more items" line, which is Devanagari and therefore not cheap. */
    private const val TRUNCATION_RESERVE = 80
}
