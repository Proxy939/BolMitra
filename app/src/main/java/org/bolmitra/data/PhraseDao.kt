package org.bolmitra.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * T0 queries, ordered to match the §6.3 lookup ladder: cheapest rung first.
 *
 * Note what is NOT here: ranking. FTS4 supplies a candidate set, and trigram scoring happens in
 * Kotlin (`PhraseMatcher.dice`) because V46 established the FTS5 trigram tokenizer is
 * unavailable on Android's platform SQLite. Keeping ranking out of SQL also means the scoring
 * rule is unit-testable without a database, which is why it already is.
 */
@Dao
interface PhraseDao {

    /** Rung 1. Single indexed lookup — the reason `hi_normalized` is stored, not computed. */
    @Query("SELECT * FROM phrase WHERE hi_normalized = :normalized AND is_template = 0 LIMIT 1")
    suspend fun findExact(normalized: String): PhraseEntity?

    /**
     * Rung 2. Templates are few, so scanning them costs less than indexing them, and slot
     * matching cannot be expressed as a SQL equality anyway.
     */
    @Query("SELECT * FROM phrase WHERE is_template = 1")
    suspend fun templates(): List<PhraseEntity>

    /**
     * Rung 3 candidates. [match] MUST be built by [FtsQuery.build] — raw user text passed
     * straight into `MATCH` is both a syntax hazard and an injection surface, since FTS has its
     * own query language (`OR`, `NEAR`, `*`, quoting).
     */
    @Query(
        """
        SELECT p.* FROM phrase AS p
        JOIN phrase_fts AS f ON p.rowid = f.rowid
        WHERE phrase_fts MATCH :match AND p.is_template = 0
        LIMIT :limit
        """,
    )
    suspend fun findFtsCandidates(match: String, limit: Int = 50): List<PhraseEntity>

    @Query("SELECT COUNT(*) FROM phrase")
    suspend fun count(): Int

    @Query("SELECT * FROM phrase WHERE lakshya_code = :lakshyaCode")
    suspend fun byLakshya(lakshyaCode: String): List<PhraseEntity>
}

@Dao
interface CorrectionOutboxDao {

    @Insert
    suspend fun insert(correction: CorrectionOutboxEntity): Long

    /** Pending corrections, oldest first, for the next prep-station visit or sync. */
    @Query("SELECT * FROM correction_outbox WHERE exported_at_ms IS NULL ORDER BY created_at_ms")
    suspend fun pending(): List<CorrectionOutboxEntity>

    /**
     * Marks rows exported rather than deleting them.
     *
     * Deleting on export would mean a failed or lost transfer silently discards a teacher's
     * correction, and they would have no way to know. Keeping the row makes re-export possible.
     */
    @Query("UPDATE correction_outbox SET exported_at_ms = :atMs WHERE id IN (:ids)")
    suspend fun markExported(ids: List<Long>, atMs: Long)

    @Query("SELECT COUNT(*) FROM correction_outbox WHERE exported_at_ms IS NULL")
    suspend fun pendingCount(): Int
}

/**
 * Builds a safe FTS4 `MATCH` expression from arbitrary text.
 *
 * FTS has its own query syntax, so passing a transcript through unfiltered can throw on stray
 * punctuation or be coaxed into unintended operators. Tokens are stripped to letters, digits and
 * combining marks, then double-quoted and OR-joined.
 *
 * OR rather than AND on purpose: §4.3 measured Hindi ASR at 0.328 WER, so requiring every token
 * to be present would discard most real utterances. Recall is what matters at this rung —
 * precision is restored afterwards by the Dice threshold.
 */
object FtsQuery {

    /** Returns null when nothing usable survives, so the caller can skip the query entirely. */
    fun build(rawText: String): String? {
        val tokens = rawText
            .split(' ', '\t', '\n')
            .map { token -> token.filter { it.isLetterOrDigit() || it.isCombining() } }
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null
        // Double-quote each token; a literal quote inside is escaped by doubling, per SQLite.
        return tokens.joinToString(" OR ") { "\"${it.replace("\"", "\"\"")}\"" }
    }

    /** Devanagari matras and signs are Mn/Mc and are NOT letters — see HindiNormalizer. */
    private fun Char.isCombining(): Boolean = when (category) {
        CharCategory.NON_SPACING_MARK,
        CharCategory.COMBINING_SPACING_MARK,
        CharCategory.ENCLOSING_MARK -> true
        else -> false
    }
}
