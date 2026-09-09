package org.bolmitra.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * Reads and writes for the turn history behind the History tab, the Recordings tab and the
 * worksheet generated "from the chats".
 *
 * Newest first everywhere. A teacher opening History mid-lesson wants the last thing they said, and
 * the panel shows about five rows — ordering the other way would put the useful end off screen.
 */
@Dao
interface TurnDao {

    @Insert
    suspend fun insert(turn: TurnEntity): Long

    /**
     * Most recent turns for one language.
     *
     * Filtered by language because the same tablet teaches more than one, and a Santali Ol Chiki
     * line sitting in a Mundari lesson's history is the same wrong-language confusion
     * `DemoSeed.phrasesFor` exists to prevent — here it would just be confusing rather than unsafe,
     * but there is no reason to allow it.
     */
    @Query("SELECT * FROM turn WHERE language = :language ORDER BY created_at_ms DESC LIMIT :limit")
    suspend fun recent(language: String, limit: Int = 50): List<TurnEntity>

    /**
     * Turns that left a playable file behind, for the Recordings tab.
     *
     * A turn with neither file is not a recording, and listing it would give a play button that
     * does nothing.
     */
    @Query(
        """
        SELECT * FROM turn
        WHERE language = :language
          AND (teacher_audio IS NOT NULL OR output_audio IS NOT NULL)
        ORDER BY created_at_ms DESC LIMIT :limit
        """,
    )
    suspend fun recordings(language: String, limit: Int = 50): List<TurnEntity>

    /**
     * Turns worth putting on a worksheet: they have a target string a child could read.
     *
     * `provenance IS NOT NULL` excludes the degrade rungs — a sheet listing "no translation
     * available" as an exercise would be worse than a shorter sheet.
     */
    @Query(
        """
        SELECT * FROM turn
        WHERE language = :language
          AND provenance IS NOT NULL
          AND target_native <> ''
        ORDER BY created_at_ms DESC LIMIT :limit
        """,
    )
    suspend fun translatedForWorksheet(language: String, limit: Int = 40): List<TurnEntity>

    /** Free-text search over both sides, for the History search box. */
    @Query(
        """
        SELECT * FROM turn
        WHERE language = :language
          AND (hi_text LIKE '%' || :query || '%' OR target_native LIKE '%' || :query || '%')
        ORDER BY created_at_ms DESC LIMIT :limit
        """,
    )
    suspend fun search(language: String, query: String, limit: Int = 50): List<TurnEntity>

    @Query("SELECT COUNT(*) FROM turn")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM turn WHERE language = :language")
    suspend fun countFor(language: String): Int

    /**
     * Wipes history. Backs "Clear All History" in Settings.
     *
     * Rows only — the caller deletes the WAV files, because a DAO that reached into the filesystem
     * would be doing two jobs and could leave orphans if either half failed.
     */
    @Query("DELETE FROM turn")
    suspend fun deleteAll()

    /** File names still referenced, so a cleanup can tell an orphan from a live clip. */
    @Query(
        """
        SELECT teacher_audio FROM turn WHERE teacher_audio IS NOT NULL
        UNION
        SELECT output_audio FROM turn WHERE output_audio IS NOT NULL
        """,
    )
    suspend fun referencedAudioFiles(): List<String>
}
