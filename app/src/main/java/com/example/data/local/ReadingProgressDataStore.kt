package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.readingProgressDataStore: DataStore<Preferences> by preferencesDataStore(name = "reading_progress_preferences")

data class ReadingProgressInfo(
    val page: Int = 1,
    val chapter: String? = null,
    val totalPages: Int = 0,
    val lastReadTimestamp: Long = 0L
) {
    val progressPercentage: Int
        get() = if (totalPages > 0) ((page.toFloat() / totalPages.toFloat()) * 100).toInt().coerceIn(0, 100) else 0
}

class ReadingProgressDataStore(private val context: Context) {

    private fun pageKey(bookId: String): Preferences.Key<Int> =
        intPreferencesKey("page_$bookId")

    private fun chapterKey(bookId: String): Preferences.Key<String> =
        stringPreferencesKey("chapter_$bookId")

    private fun totalPagesKey(bookId: String): Preferences.Key<Int> =
        intPreferencesKey("total_pages_$bookId")

    private fun timestampKey(bookId: String): Preferences.Key<Long> =
        longPreferencesKey("timestamp_$bookId")

    /**
     * Observe the last read page for a given book ID. Defaults to 1 (first page).
     */
    fun getLastReadPage(bookId: String): Flow<Int> {
        return context.readingProgressDataStore.data.map { prefs ->
            prefs[pageKey(bookId)] ?: 1
        }
    }

    /**
     * Observe the last read chapter title/name for a given book ID if saved.
     */
    fun getLastReadChapter(bookId: String): Flow<String?> {
        return context.readingProgressDataStore.data.map { prefs ->
            prefs[chapterKey(bookId)]
        }
    }

    /**
     * Observe detailed reading progress info for a given book ID.
     */
    fun getReadingProgressInfo(bookId: String): Flow<ReadingProgressInfo> {
        return context.readingProgressDataStore.data.map { prefs ->
            ReadingProgressInfo(
                page = prefs[pageKey(bookId)] ?: 1,
                chapter = prefs[chapterKey(bookId)],
                totalPages = prefs[totalPagesKey(bookId)] ?: 0,
                lastReadTimestamp = prefs[timestampKey(bookId)] ?: 0L
            )
        }
    }

    /**
     * Save/update the last read page, chapter, and total pages for a book ID in DataStore.
     */
    suspend fun saveReadingProgress(
        bookId: String,
        page: Int,
        chapter: String? = null,
        totalPages: Int = 0
    ) {
        context.readingProgressDataStore.edit { prefs ->
            val validPage = page.coerceAtLeast(1)
            prefs[pageKey(bookId)] = validPage
            if (chapter != null) {
                prefs[chapterKey(bookId)] = chapter
            }
            if (totalPages > 0) {
                prefs[totalPagesKey(bookId)] = totalPages
            }
            prefs[timestampKey(bookId)] = System.currentTimeMillis()
        }
    }

    /**
     * Get a flow of all saved reading progress entries across all books.
     */
    fun getAllReadingProgress(): Flow<Map<String, ReadingProgressInfo>> {
        return context.readingProgressDataStore.data.map { prefs ->
            val map = mutableMapOf<String, ReadingProgressInfo>()
            prefs.asMap().forEach { (key, _) ->
                val keyName = key.name
                if (keyName.startsWith("page_")) {
                    val bookId = keyName.removePrefix("page_")
                    val page = prefs[pageKey(bookId)] ?: 1
                    val chapter = prefs[chapterKey(bookId)]
                    val totalPages = prefs[totalPagesKey(bookId)] ?: 0
                    val timestamp = prefs[timestampKey(bookId)] ?: 0L
                    map[bookId] = ReadingProgressInfo(
                        page = page,
                        chapter = chapter,
                        totalPages = totalPages,
                        lastReadTimestamp = timestamp
                    )
                }
            }
            map
        }
    }

    /**
     * Clear reading progress for a specific book ID.
     */
    suspend fun clearReadingProgress(bookId: String) {
        context.readingProgressDataStore.edit { prefs ->
            prefs.remove(pageKey(bookId))
            prefs.remove(chapterKey(bookId))
            prefs.remove(totalPagesKey(bookId))
            prefs.remove(timestampKey(bookId))
        }
    }
}
