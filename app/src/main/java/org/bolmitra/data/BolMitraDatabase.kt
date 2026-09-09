package org.bolmitra.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

/**
 * The on-device database — ARCHITECTURE.md §6.3, §6.9.
 *
 * Tables with very different lifecycles share it deliberately:
 *
 *  - `phrase` / `phrase_fts` are **pack content**. Replaceable, re-downloadable, disposable.
 *  - `correction_outbox` is **the only irreplaceable data in the app.** A teacher's correction
 *    exists nowhere else until it reaches a prep station. Losing it loses their work silently.
 *  - `turn` is the lesson record behind History, Recordings and the generated worksheet. Losing it
 *    loses a teacher's afternoon rather than their contribution, so it sits between the two — but it
 *    is still their data, and the migration policy below protects it the same way.
 *
 * That asymmetry drives the migration policy below.
 */
@Database(
    entities = [
        PhraseEntity::class,
        PhraseFts::class,
        CorrectionOutboxEntity::class,
        TurnEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class BolMitraDatabase : RoomDatabase() {
    abstract fun phraseDao(): PhraseDao
    abstract fun correctionOutboxDao(): CorrectionOutboxDao
    abstract fun turnDao(): TurnDao

    companion object {
        const val NAME = "bolmitra.db"

        /**
         * The one shared instance.
         *
         * `open` builds a *new* `RoomDatabase` every call, and Room's own guidance is that this is
         * expensive and that concurrent instances on one file invite locking problems. Nothing used
         * Room at runtime before, so the cost never showed up; the History and Recordings lists now
         * read it on every language switch, so it does.
         */
        @Volatile
        private var instance: BolMitraDatabase? = null

        /** Process-wide singleton. Uses the application context, never an Activity (O17). */
        fun get(context: Context): BolMitraDatabase =
            instance ?: synchronized(this) {
                instance ?: open(context.applicationContext).also { instance = it }
            }

        /**
         * Opens the database.
         *
         * **There is deliberately no `fallbackToDestructiveMigration()` call here, and adding one
         * would be a bug.** O8 in §12 records why: the default destructive fallback wipes every
         * table on a schema version bump, which would delete unexported corrections during a
         * routine app update. The teacher would see no error — the work would simply be gone.
         *
         * The consequence is intentional friction: adding a schema version REQUIRES writing a
         * migration, and Room fails loudly at open time if one is missing. Schemas are exported
         * to `app/schemas` (see the `ksp` block in build.gradle.kts) so migrations can be tested
         * with `MigrationTestHelper` rather than trusted.
         */
        fun open(context: Context): BolMitraDatabase =
            Room.databaseBuilder(context, BolMitraDatabase::class.java, NAME)
                // .fallbackToDestructiveMigration()  <-- NEVER. See O8 above.
                .addMigrations(*MIGRATIONS)
                .build()

        /**
         * Opens the database from a prebuilt file shipped inside a language pack (§6.8.2).
         *
         * Packs carry `phrases.sqlite` with the FTS4 index already built, so a school tablet
         * never pays indexing cost on first run. The index must be FTS4 — V46 established a
         * prebuilt FTS5 index would not open at all on the floor device.
         *
         * `createFromFile` applies only when no database exists yet, so this cannot clobber an
         * outbox that is already on the device.
         */
        fun openFromPack(context: Context, packDb: File): BolMitraDatabase =
            Room.databaseBuilder(context, BolMitraDatabase::class.java, NAME)
                .createFromFile(packDb)
                .addMigrations(*MIGRATIONS)
                .build()

        /**
         * 1 → 2: adds the `turn` table behind History, Recordings and the generated worksheet.
         *
         * Written by hand rather than reached by a destructive fallback, for the reason O8 gives
         * above: the fallback would drop `correction_outbox` on a routine app update and a teacher
         * would lose unexported corrections with no error shown. This migration only CREATEs, so
         * every existing row in every existing table survives it.
         *
         * The column list must match [TurnEntity] exactly — names, types, and nullability. Room
         * validates the result against the compiled schema at open time and throws
         * `IllegalStateException` on any mismatch, which is why this is safe to hand-write: getting
         * it wrong fails loudly on first launch rather than silently later. `app/schemas/2.json` is
         * exported so `MigrationTestHelper` can check it without a device.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `turn` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `created_at_ms` INTEGER NOT NULL,
                        `language` TEXT NOT NULL,
                        `hi_text` TEXT NOT NULL,
                        `target_native` TEXT NOT NULL,
                        `target_deva` TEXT NOT NULL,
                        `provenance` TEXT,
                        `src` TEXT,
                        `src_en` TEXT,
                        `degrade_reason` TEXT,
                        `typed` INTEGER NOT NULL,
                        `asr_ms` INTEGER NOT NULL,
                        `total_ms` INTEGER NOT NULL,
                        `teacher_audio` TEXT,
                        `output_audio` TEXT
                    )
                    """.trimIndent(),
                )
            }
        }

        /** Every future version must add an entry here. See O8 above for why. */
        private val MIGRATIONS = arrayOf<Migration>(MIGRATION_1_2)
    }
}
