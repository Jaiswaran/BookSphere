package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.Book
import com.example.model.PublishedWork

/**
 * Room entity representing a book cached locally for offline responsiveness.
 * Supabase PostgreSQL is the primary source of truth.
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
    val status: String = "PUBLISHED",
    val copiesSold: Int = 0,
    val netEarned: Double = 0.0,
    val rating: Double = 5.0,
    val publishedAt: Long = System.currentTimeMillis()
) {
    fun toBook(): Book {
        val parsedPages = try {
            val arr = org.json.JSONArray(contentPagesJson)
            List(arr.length()) { i -> arr.getString(i) }
        } catch (_: Exception) {
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
            rating = rating,
            isPurchased = false,
            isFree = price == 0.0
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

fun Book.toBookEntity(): BookEntity {
    val pagesJson = try {
        org.json.JSONArray(contentPages).toString()
    } catch (_: Exception) {
        "[]"
    }
    return BookEntity(
        id = id,
        title = title,
        author = author,
        genre = genre,
        description = description,
        price = price,
        coverUrl = coverUrl,
        pdfUri = pdfUri,
        language = language,
        totalPages = totalPages,
        previewPages = previewPages,
        samplePagesCount = samplePagesCount,
        contentPagesJson = pagesJson,
        status = "PUBLISHED",
        copiesSold = 120,
        netEarned = price * 0.70 * 120,
        rating = rating,
        publishedAt = System.currentTimeMillis()
    )
}

/**
 * Room entity for caching the active Supabase authenticated user's profile.
 * IMPORTANT: No passwords or authentication tokens are stored in Room.
 * Supabase Auth is the single source of truth.
 */
@Entity(tableName = "cached_user_profiles")
data class CachedUserProfileEntity(
    @PrimaryKey
    val id: String, // Supabase Auth user UUID
    val email: String? = null,
    val name: String? = null,
    val role: String = "READER", // "READER" or "AUTHOR"
    val photoUrl: String? = null,
    val isActive: Boolean = true,
    val cachedAt: Long = System.currentTimeMillis()
)
