package org.bolmitra.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

/**
 * The on-device database — ARCHITECTURE.md §6.3, §6.9.
 *
 * Two entities with very different lifecycles share it deliberately:
 *
 *  - `phrase` / `phrase_fts` are **pack content**. Replaceable, re-downloadable, disposable.
 *  - `correction_outbox` is **the only irreplaceable data in the app.** A teacher's correction
 *    exists nowhere else until it reaches a prep station. Losing it loses their work silently.
 *
 * That asymmetry drives the migration policy below.
 */
@Database(
    entities = [PhraseEntity::class, PhraseFts::class, CorrectionOutboxEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class BolMitraDatabase : RoomDatabase() {
    abstract fun phraseDao(): PhraseDao
    abstract fun correctionOutboxDao(): CorrectionOutboxDao

    companion object {
        const val NAME = "bolmitra.db"

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

        /** Empty at version 1. Every future version must add an entry here. */
        private val MIGRATIONS = emptyArray<androidx.room.migration.Migration>()
    }
}
