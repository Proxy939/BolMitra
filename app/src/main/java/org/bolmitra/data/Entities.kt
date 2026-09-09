package org.bolmitra.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.PrimaryKey

/**
 * Room schema for the T0 phrasebook and the correction outbox — ARCHITECTURE.md §6.3.
 */

/**
 * The `phrase` table from §6.3.
 *
 * `rowid` rather than `id` because [PhraseFts] uses this as an external content table, and
 * SQLite FTS requires the content table's key to be an INTEGER `rowid`. The §6.3 schema calls
 * it `id`; same column, name chosen to satisfy FTS.
 */
@Entity(tableName = "phrase")
data class PhraseEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    @ColumnInfo(name = "lakshya_code") val lakshyaCode: String?,
    @ColumnInfo(name = "hi_text") val hiText: String,
    /**
     * Output of `HindiNormalizer.normalize`, stored rather than computed at query time.
     *
     * Storing it is what makes the exact-match rung a single indexed lookup. It also means the
     * pack builder and the device MUST use the same normaliser version — a mismatch degrades
     * exact matching to fuzzy silently, so `pack_version` exists partly to detect that.
     */
    @ColumnInfo(name = "hi_normalized") val hiNormalized: String,
    @ColumnInfo(name = "target_text_native") val targetTextNative: String,
    @ColumnInfo(name = "target_text_deva") val targetTextDeva: String,
    @ColumnInfo(name = "audio_ref") val audioRef: String?,
    /** Native-speaker attribution. Null means unverified, which the UI must surface. */
    @ColumnInfo(name = "verified_by") val verifiedBy: String?,
    @ColumnInfo(name = "confidence") val confidence: Double?,
    @ColumnInfo(name = "pack_version") val packVersion: String,
    @ColumnInfo(name = "is_template") val isTemplate: Boolean = false,
)

/**
 * FTS4 index over `phrase`, supplying the candidate set for the fuzzy rung.
 *
 * **FTS4, not FTS5, and this is not a preference.** V46: AOSP's platform SQLite enables
 * FTS3/FTS4 and *not* FTS5, and also sets `SQLITE_OMIT_LOAD_EXTENSION` so it cannot be loaded
 * at runtime. Room exposes only `@Fts3`/`@Fts4`. Shipping a prebuilt FTS5 index inside a pack
 * would fail to open at all on the floor device — a hard failure at pack verify, not a slow path.
 *
 * `unicode61` is the tokenizer that segments Devanagari sensibly; the FTS5 trigram tokenizer is
 * unavailable, which is why trigram scoring lives in Kotlin (`PhraseMatcher.dice`) instead.
 *
 * External content (`contentEntity`) keeps the text in `phrase` only, rather than duplicating
 * every string into the index — relevant because §5.4 budgets the whole install.
 */
@Entity(tableName = "phrase_fts")
@Fts4(contentEntity = PhraseEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
data class PhraseFts(
    @ColumnInfo(name = "hi_normalized") val hiNormalized: String,
)

/**
 * Correction outbox — §6.3's correction loop, the mechanism that turns teachers into the data
 * pipeline for a language with almost no digital corpus.
 *
 * **Text only, by invariant.** §6.9.4: audio never leaves the device. Storing a recording here
 * would breach that, and DPDP Act §9 obligations around children's data (V37) make it worse.
 * The row carries what a reviewer needs and nothing more.
 */
@Entity(tableName = "correction_outbox")
data class CorrectionOutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The phrase corrected, or null if the teacher corrected a machine translation. */
    @ColumnInfo(name = "phrase_rowid") val phraseRowId: Long?,
    @ColumnInfo(name = "hi_text") val hiText: String,
    /** What the app produced. */
    @ColumnInfo(name = "produced_text") val producedText: String,
    /** What the teacher says it should be. */
    @ColumnInfo(name = "suggested_text") val suggestedText: String,
    @ColumnInfo(name = "provenance") val provenance: String,
    @ColumnInfo(name = "pack_version") val packVersion: String,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long,
    /** Set once handed to a prep station over USB / Wi-Fi Direct, so it is not sent twice. */
    @ColumnInfo(name = "exported_at_ms") val exportedAtMs: Long? = null,
)

/**
 * One completed turn, kept so the History and Recordings lists show what actually happened.
 *
 * ### Why this is a table and not a list in the UI
 *
 * The History panel used to render five hardcoded `ChatHistoryItem`s whose Mundari was invented.
 * A teacher scrolling back to check what the class heard was being shown fiction. Persisting the
 * real turn is the only way that panel can be honest, and it is also what a bilingual worksheet
 * built "from the chats" has to read.
 *
 * ### Provenance travels with the row
 *
 * [provenance] is stored, never re-derived. §4.5 requires it to be decided at lookup and carried,
 * and a history row is the furthest downstream that rule has to hold: re-deriving it later from
 * the text would be guessing. [src] / [srcEn] come with a `CORPUS` row so the citation survives
 * into the history view, which is what lets a reviewer judge an old turn at all.
 *
 * ### Audio is referenced, not embedded
 *
 * The clips live as WAV files under `filesDir/recordings` and only their names are here. A BLOB
 * column would put tens of megabytes of PCM in the same file as the correction outbox, and Room
 * would rewrite it on every schema change. §6.9.4 still applies to the files themselves: they are
 * local, and nothing exports them.
 */
@Entity(tableName = "turn")
data class TurnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Wall clock, for display. Not `elapsedRealtime` — that is meaningless across reboots. */
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long,
    /** `TargetLanguage.name`, so a history list can be filtered per language. */
    @ColumnInfo(name = "language") val language: String,
    /** What the tablet heard, or what the teacher typed. */
    @ColumnInfo(name = "hi_text") val hiText: String,
    /** What the class SAW — Ol Chiki for Santali, Devanagari for Mundari. May be blank. */
    @ColumnInfo(name = "target_native") val targetNative: String,
    /** What the voice was given. Devanagari by construction; see the TTS contract. */
    @ColumnInfo(name = "target_deva") val targetDeva: String,
    /** `Provenance.name`, or null where the turn produced no translation at all. */
    @ColumnInfo(name = "provenance") val provenance: String?,
    /** Corpus citation, present only for `CORPUS` rows. */
    @ColumnInfo(name = "src") val src: String? = null,
    @ColumnInfo(name = "src_en") val srcEn: String? = null,
    /** `DegradeReason.name` when the turn did not produce audio, for an honest history row. */
    @ColumnInfo(name = "degrade_reason") val degradeReason: String? = null,
    /** True when this came from the keyboard rather than the microphone. */
    @ColumnInfo(name = "typed") val typed: Boolean = false,
    @ColumnInfo(name = "asr_ms") val asrMs: Long = 0,
    @ColumnInfo(name = "total_ms") val totalMs: Long = 0,
    /** File name under `recordings/` holding what the teacher said. Null for a typed turn. */
    @ColumnInfo(name = "teacher_audio") val teacherAudio: String? = null,
    /** File name under `recordings/` holding what the class heard. Null when nothing was spoken. */
    @ColumnInfo(name = "output_audio") val outputAudio: String? = null,
)
