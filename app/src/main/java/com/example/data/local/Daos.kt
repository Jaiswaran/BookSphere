package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM published_books ORDER BY publishedAt DESC")
    fun getAllPublishedBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM published_books WHERE id = :id LIMIT 1")
    suspend fun getBookById(id: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooks(books: List<BookEntity>)

    @Query("DELETE FROM published_books WHERE id = :id")
    suspend fun deleteBook(id: String)

    @Query("DELETE FROM published_books")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM published_books")
    suspend fun getBookCount(): Int
}

@Dao
interface CachedUserProfileDao {
    @Query("SELECT * FROM cached_user_profiles WHERE isActive = 1 LIMIT 1")
    fun getActiveProfile(): Flow<CachedUserProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: CachedUserProfileEntity)

    @Query("UPDATE cached_user_profiles SET isActive = 0")
    suspend fun clearActive()

    @Query("DELETE FROM cached_user_profiles WHERE id = :id")
    suspend fun deleteProfile(id: String)

    @Query("DELETE FROM cached_user_profiles")
    suspend fun clearAll()
}
