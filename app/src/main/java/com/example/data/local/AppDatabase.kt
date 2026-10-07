package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BookEntity::class, CachedUserProfileEntity::class],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun cachedUserProfileDao(): CachedUserProfileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Version 1 to 2: Added previewPath, totalPages, previewPages
                try {
                    db.execSQL("ALTER TABLE published_books ADD COLUMN previewPath TEXT")
                    db.execSQL("ALTER TABLE published_books ADD COLUMN totalPages INTEGER NOT NULL DEFAULT 280")
                    db.execSQL("ALTER TABLE published_books ADD COLUMN previewPages INTEGER NOT NULL DEFAULT 3")
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Version 2 to 3: Added cached_user_profiles table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cached_user_profiles` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `email` TEXT,
                        `name` TEXT,
                        `role` TEXT NOT NULL DEFAULT 'READER',
                        `photoUrl` TEXT,
                        `bio` TEXT,
                        `readingListsJson` TEXT NOT NULL DEFAULT '[]',
                        `isActive` INTEGER NOT NULL DEFAULT 1,
                        `cachedAt` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE published_books ADD COLUMN isFree INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE published_books ADD COLUMN language TEXT NOT NULL DEFAULT 'English'")
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE published_books ADD COLUMN status TEXT NOT NULL DEFAULT 'PUBLISHED'")
                    db.execSQL("ALTER TABLE published_books ADD COLUMN copiesSold INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE published_books ADD COLUMN netEarned REAL NOT NULL DEFAULT 0.0")
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE published_books ADD COLUMN rating REAL NOT NULL DEFAULT 5.0")
                    db.execSQL("ALTER TABLE published_books ADD COLUMN publishedAt INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Ensure all tables and columns are strictly consistent with entity models
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `published_books_new` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `title` TEXT NOT NULL,
                        `author` TEXT NOT NULL,
                        `genre` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `price` REAL NOT NULL,
                        `coverUrl` TEXT NOT NULL,
                        `pdfUri` TEXT,
                        `authorId` TEXT,
                        `coverPath` TEXT,
                        `manuscriptPath` TEXT,
                        `previewPath` TEXT,
                        `isFree` INTEGER NOT NULL DEFAULT 0,
                        `language` TEXT NOT NULL DEFAULT 'English',
                        `totalPages` INTEGER NOT NULL DEFAULT 280,
                        `previewPages` INTEGER NOT NULL DEFAULT 3,
                        `samplePagesCount` INTEGER NOT NULL DEFAULT 20,
                        `contentPagesJson` TEXT NOT NULL DEFAULT '[]',
                        `status` TEXT NOT NULL DEFAULT 'PUBLISHED',
                        `copiesSold` INTEGER NOT NULL DEFAULT 0,
                        `netEarned` REAL NOT NULL DEFAULT 0.0,
                        `rating` REAL NOT NULL DEFAULT 5.0,
                        `publishedAt` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                try {
                    db.execSQL(
                        """
                        INSERT OR IGNORE INTO `published_books_new` (
                            `id`, `title`, `author`, `genre`, `description`, `price`, `coverUrl`,
                            `pdfUri`, `authorId`, `coverPath`, `manuscriptPath`, `previewPath`,
                            `isFree`, `language`, `totalPages`, `previewPages`, `samplePagesCount`,
                            `contentPagesJson`, `status`, `copiesSold`, `netEarned`, `rating`, `publishedAt`
                        ) SELECT 
                            `id`, `title`, `author`, `genre`, `description`, `price`, `coverUrl`,
                            `pdfUri`, `authorId`, `coverPath`, `manuscriptPath`, `previewPath`,
                            `isFree`, `language`, `totalPages`, `previewPages`, `samplePagesCount`,
                            `contentPagesJson`, `status`, `copiesSold`, `netEarned`, `rating`, `publishedAt`
                        FROM `published_books`
                        """.trimIndent()
                    )
                    db.execSQL("DROP TABLE `published_books`")
                } catch (_: Exception) {}
                db.execSQL("ALTER TABLE `published_books_new` RENAME TO `published_books`")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cached_user_profiles` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `email` TEXT,
                        `name` TEXT,
                        `role` TEXT NOT NULL DEFAULT 'READER',
                        `photoUrl` TEXT,
                        `bio` TEXT,
                        `readingListsJson` TEXT NOT NULL DEFAULT '[]',
                        `isActive` INTEGER NOT NULL DEFAULT 1,
                        `cachedAt` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "booksphere_database"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
