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

    @Query("SELECT COUNT(*) FROM published_books")
    suspend fun getBookCount(): Int
}

@Dao
interface UserCredentialDao {
    @Query("SELECT * FROM user_credentials ORDER BY registeredAt DESC")
    fun getAllCredentials(): Flow<List<UserCredentialEntity>>

    @Query("SELECT * FROM user_credentials WHERE isActive = 1 ORDER BY registeredAt DESC LIMIT 1")
    fun getActiveCredential(): Flow<UserCredentialEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCredential(credential: UserCredentialEntity): Long

    @Query("UPDATE user_credentials SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE user_credentials SET isActive = 1 WHERE id = :id")
    suspend fun activateUser(id: Long)

    @Query("DELETE FROM user_credentials WHERE id = :id")
    suspend fun deleteCredential(id: Long)

    @Query("SELECT COUNT(*) FROM user_credentials")
    suspend fun getCredentialCount(): Int
}
