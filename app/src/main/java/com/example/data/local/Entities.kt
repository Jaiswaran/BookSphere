package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.Book
import com.example.model.PublishedWork

/**
 * Room entity representing a book published by an author on BookSphere.
 * Persists custom manuscripts, pricing, sales, and catalog information.
 */
@Entity(tableName = "published_books")
data class BookEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val author: String,
    val genre: String,
    val description: String,
    val price: Double,
    val coverUrl: String,
    val pdfUri: String? = null,
    val language: String = "English",
    val totalPages: Int = 280,
    val previewPages: Int = 3,
    val samplePagesCount: Int = 20,
    val contentPagesJson: String = "[]",
    val status: String = "Published",
    val copiesSold: Int = 0,
    val netEarned: Double = 0.0,
    val rating: Double = 5.0,
    val publishedAt: Long = System.currentTimeMillis()
) {
    fun toBook(): Book {
        val parsedPages = try {
            val arr = org.json.JSONArray(contentPagesJson)
            List(arr.length()) { i -> arr.getString(i) }
        } catch (e: Exception) {
            emptyList()
        }
        return Book(
            id = id,
            title = title,
            author = author,
            coverUrl = coverUrl,
            price = price,
            genre = genre,
            description = description,
            language = language,
            pdfUri = pdfUri,
            totalPages = if (parsedPages.isNotEmpty()) parsedPages.size else totalPages,
            previewPages = previewPages,
            samplePagesCount = samplePagesCount,
            contentPages = parsedPages,
            rating = rating
        )
    }

    fun toPublishedWork(): PublishedWork {
        return PublishedWork(
            id = id,
            title = title,
            price = price,
            copiesSold = copiesSold,
            rating = rating,
            netEarned = netEarned,
            status = status,
            coverUrl = coverUrl,
            genre = genre
        )
    }
}

/**
 * Room entity for securely storing registered user credentials locally.
 * Supports credentials login (username, phone, password), Google Auth, and Apple Auth.
 */
@Entity(tableName = "user_credentials")
data class UserCredentialEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val username: String,
    val phoneNumber: String? = null,
    val email: String? = null,
    val passwordOrToken: String? = null,
    val role: String, // "READER" or "AUTHOR"
    val authMethod: String, // "credentials", "google.com", "apple.com"
    val registeredAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)
