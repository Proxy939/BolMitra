package org.bolmitra.data

import kotlinx.coroutines.runBlocking
import org.bolmitra.phrasebook.HindiNormalizer
import org.bolmitra.phrasebook.LookupResult
import org.bolmitra.phrasebook.Phrase
import org.bolmitra.phrasebook.PhraseMatcher
import org.bolmitra.phrasebook.Provenance
import org.bolmitra.translate.PhrasebookEngine

/**
 * Room-backed T0 lookup — the production [PhrasebookEngine].
 *
 * The ladder itself is NOT reimplemented here. Rungs 1 and 2 are answered by indexed queries
 * because SQL can do them exactly, and rung 3 delegates to [PhraseMatcher] over the FTS4
 * candidate set. That split keeps the scoring rule in one place, already unit-tested without a
 * database (§6.3, V46).
 */
class RoomPhrasebook(private val dao: PhraseDao) : PhrasebookEngine {

    /**
     * Synchronous because [PhrasebookEngine] is called from the orchestrator's turn path, which
     * is itself driven off the main thread by the caller.
     *
     * `ponytail:` `runBlocking` is the honest shortcut here, not the right long-term shape. The
     * ceiling: it blocks the calling thread, so it must never be invoked on the main thread — a
     * strict-mode violation waiting to happen if a future UI change calls it directly. Upgrade
     * path: make [PhrasebookEngine.lookup] a `suspend fun` once the Live Class screen exists and
     * the calling coroutine scope is known. Deliberately deferred rather than guessed at now,
     * because the right scope depends on UI structure that has not been designed yet.
     */
    override fun lookup(rawHindi: String): LookupResult? = runBlocking { lookupSuspend(rawHindi) }

    suspend fun lookupSuspend(rawHindi: String): LookupResult? {
        val query = HindiNormalizer.normalize(rawHindi)
        if (query.isEmpty()) return null

        // Rung 1 — exact.
        dao.findExact(query)?.let {
            return LookupResult(it.toPhrase(), Provenance.VERIFIED, 1.0)
        }

        // Rungs 2 and 3 — delegate to the shared ladder so ordering and provenance rules
        // cannot drift between the Room and in-memory implementations.
        val candidates = buildList {
            addAll(dao.templates())
            FtsQuery.build(query)?.let { addAll(dao.findFtsCandidates(it)) }
        }.map { it.toPhrase() }

        return PhraseMatcher.lookup(rawHindi, candidates)
    }
}

/** Maps the persistence row to the domain type the matcher and orchestrator work with. */
fun PhraseEntity.toPhrase(): Phrase = Phrase(
    id = rowId,
    lakshyaCode = lakshyaCode,
    hiText = hiText,
    hiNormalized = hiNormalized,
    targetTextNative = targetTextNative,
    targetTextDeva = targetTextDeva,
    audioRef = audioRef,
    verifiedBy = verifiedBy,
    packVersion = packVersion,
    isTemplate = isTemplate,
)

/** Inverse mapping, used by the pack importer and by tests that seed a database. */
fun Phrase.toEntity(confidence: Double? = null): PhraseEntity = PhraseEntity(
    rowId = id,
    lakshyaCode = lakshyaCode,
    hiText = hiText,
    hiNormalized = hiNormalized,
    targetTextNative = targetTextNative,
    targetTextDeva = targetTextDeva,
    audioRef = audioRef,
    verifiedBy = verifiedBy,
    confidence = confidence,
    packVersion = packVersion,
    isTemplate = isTemplate,
)
