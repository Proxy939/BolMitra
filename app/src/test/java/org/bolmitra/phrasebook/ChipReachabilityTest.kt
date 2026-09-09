package org.bolmitra.phrasebook

import java.io.File
import org.bolmitra.translit.OlChikiNormalizer
import org.bolmitra.translit.OlChikiToDevanagari
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "Try these" chips must not walk a teacher onto the model.
 *
 * ### Why this exists
 *
 * A chip is a recommendation: the app is telling a teacher "say this". A chip that misses T0 and
 * misses [WordComposer] lands on unreviewed model output, which is the weakest rung in the app, and
 * nothing on the suggestion itself says so. That is a **reachability** defect of exactly the kind
 * the project treats as content-shaped rather than model-shaped — the right Santali is usually
 * already ingested and merely not reachable from the Hindi key the chip uses.
 *
 * It is also the check that settles a question a device cannot settle quickly. `फिर से बोलो` was
 * reported as still returning machine output after the composer shipped; the machine rows were from
 * a build installed before it, still on screen. Asserting reachability against the **shipped
 * assets** answers that in a second, with no tablet and no stale APK involved.
 *
 * ### What is asserted, and what deliberately is not
 *
 * Only that each chip reaches a rung better than T1 — an exact T0 hit, or a composition that clears
 * [WordComposer.MIN_COVERAGE] and the Ol Chiki gate. It says nothing about whether the Santali is
 * *right*; no test can, and [SantaliGlossaryTest] already covers the corpus-level claims.
 *
 * Reads the TSVs off disk rather than through a `Context`, the same way [SantaliGlossaryTest] does,
 * and mirrors `LiveTurnEngine`'s source order and single-word filter. If those drift, this file stops
 * testing what the app builds — which is why the order is restated here in one place.
 */
class ChipReachabilityTest {

    /**
     * The chip list from `LiveClassPane`, copied deliberately.
     *
     * A copy rather than a shared constant because the UI file pulls in Compose and Android and
     * cannot be read from a JVM test. The cost is that adding a chip there needs a line here; the
     * benefit is that the addition gets checked at all.
     */
    private val chips = listOf("नमस्ते", "बैठ जाओ", "खड़े हो जाओ", "सुनो ध्यान से", "फिर से बोलो", "अब लिखो")

    /**
     * The one chip that cannot currently do better than the model, and why.
     *
     * `ध्यान` has no Hindi-reachable corpus row, so coverage is 1 of 2 content words — below the
     * two-thirds floor — and the composer correctly declines rather than offering half a sentence.
     * Named here rather than quietly excluded: fixing it means keying a corpus row or having a
     * speaker supply the phrase, both content decisions, and whoever does that should see this line.
     */
    private val knownUnreachable = setOf("सुनो ध्यान से")

    private fun asset(name: String): File = listOf(
        File("src/main/assets/$name"),
        File("app/src/main/assets/$name"),
    ).firstOrNull { it.isFile } ?: error("$name not found; see tools/build-santali-glossary.py")

    /** Glossary rows as `SantaliGlossary.phrasesFor` emits them: one per Hindi alias, CORPUS. */
    private fun glossaryPhrases(): List<Phrase> {
        var id = 1_000_000L
        return asset("santali-glossary.tsv").readLines().drop(1)
            .filter { it.isNotBlank() }
            .flatMap { line ->
                val f = line.split('\t', limit = 8)
                if (f.size < 8 || f[2].isBlank()) return@flatMap emptyList()
                val deva = OlChikiToDevanagari.transliterate(f[2]).devanagari
                if (deva.isBlank()) return@flatMap emptyList()
                f[1].split(';').map { it.trim() }.filter { it.isNotEmpty() }.map { key ->
                    Phrase(
                        id = id++, lakshyaCode = null, hiText = key,
                        hiNormalized = HindiNormalizer.normalize(key),
                        targetTextNative = f[2], targetTextDeva = deva, audioRef = null,
                        verifiedBy = null, packVersion = SantaliGlossary.PACK_VERSION,
                        src = f[3], srcEn = f[0], provenance = Provenance.CORPUS,
                    )
                }
            }
    }

    /** Pack rows as `ClassroomPacks.phrasesFor` emits them, including its first-wins de-duplication. */
    private fun packPhrases(): List<Phrase> {
        var id = 2_000_000L
        val seen = HashSet<String>()
        return asset("santali-classroom-packs.tsv").readLines().drop(1)
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val f = line.split('\t', limit = 4)
                if (f.size < 4) return@mapNotNull null
                val hindi = f[0].trim()
                val santali = f[1].trim()
                if (hindi.isEmpty() || santali.isEmpty()) return@mapNotNull null
                if (!seen.add(HindiNormalizer.normalize(hindi))) return@mapNotNull null
                val deva = OlChikiToDevanagari.transliterate(santali).devanagari
                if (deva.isBlank()) return@mapNotNull null
                Phrase(
                    id = id++, lakshyaCode = null, hiText = hindi,
                    hiNormalized = HindiNormalizer.normalize(hindi),
                    targetTextNative = santali, targetTextDeva = deva, audioRef = null,
                    verifiedBy = null, packVersion = ClassroomPacks.PACK_VERSION,
                    src = ClassroomPacks.SOURCE, srcEn = f[2].trim(),
                    provenance = ClassroomPacks.PROVENANCE,
                )
            }
    }

    /** Glossary before packs — the provenance ladder, asserted elsewhere and relied on here. */
    private val allPhrases: List<Phrase> by lazy { glossaryPhrases() + packPhrases() }

    /** `LiveTurnEngine.singleWordIndex`: single-word keys only, first source wins. */
    private val wordIndex: Map<String, Phrase> by lazy {
        val out = LinkedHashMap<String, Phrase>(1024)
        for (p in allPhrases) {
            val key = p.hiNormalized
            if (key.isBlank() || key.contains(' ')) continue
            if (p.targetTextNative.isBlank() || p.targetTextDeva.isBlank()) continue
            out.putIfAbsent(key, p)
        }
        out
    }

    /**
     * An empty index disables the composer rung entirely, because `LiveTurnEngine` passes
     * `composeWords = null` when it is empty. Every T0 miss would then go straight to the model with
     * nothing to show that a whole rung had vanished.
     */
    @Test
    fun `the shipped assets yield a non-empty single-word index`() {
        assertTrue("index has only ${wordIndex.size} entries", wordIndex.size > 300)
    }

    @Test
    fun `every suggested chip reaches a rung better than the model`() {
        val failures = mutableListOf<String>()
        for (chip in chips) {
            if (chip in knownUnreachable) continue
            val hit = PhraseMatcher.lookup(chip, allPhrases)
            if (hit != null) continue
            val composed = WordComposer.compose(chip) { wordIndex[it] }
            if (composed == null) {
                val missing = HindiNormalizer.normalize(chip).split(' ').filter { wordIndex[it] == null }
                failures += "'$chip' reaches neither T0 nor the composer; unresolved words: $missing"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * The reported case, pinned end to end.
     *
     * `फिर से बोलो` has no whole-phrase row, and both content words do have one — `फिर` -> again and
     * `बोलो` -> speak — with `से` skippable. So it must compose at full coverage and pass the Ol Chiki
     * gate, which is what makes it `ComposedAudio` rather than model output on the device.
     */
    @Test
    fun `phir se bolo composes from corpus words rather than falling to the model`() {
        assertTrue("it should not be a whole-phrase hit", PhraseMatcher.lookup("फिर से बोलो", allPhrases) == null)

        val composed = WordComposer.compose("फिर से बोलो") { wordIndex[it] }
        assertNotNull("फिर से बोलो did not compose", composed)
        assertEquals(2, composed!!.resolvedCount)
        assertEquals(1.0f, composed.coverage, 1e-6f)
        assertEquals("से is grammatical and must not be invented", listOf("से"), composed.missing)
        assertTrue(
            "composed Ol Chiki was rejected: ${OlChikiNormalizer.rejectionReason(composed.native)}",
            OlChikiNormalizer.rejectionReason(composed.native) == null,
        )
        assertTrue("the voice would get nothing", composed.devanagari.isNotBlank())
    }

    /**
     * The floor is doing its job, not accidentally passing everything.
     *
     * `सुनो ध्यान से` resolves one content word of two, and a half-rendered instruction spoken to a
     * class is worse than an honest machine label. If a `ध्यान` key is ever added this test fails,
     * which is the right time to move the chip out of [knownUnreachable].
     */
    @Test
    fun `a chip below the coverage floor is declined rather than half-rendered`() {
        assertTrue("ध्यान is unexpectedly reachable", wordIndex["ध्यान"] == null)
        assertTrue(
            "सुनो ध्यान से composed at only 1 of 2 content words",
            WordComposer.compose("सुनो ध्यान से") { wordIndex[it] } == null,
        )
    }
}
