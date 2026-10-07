package com.example.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileDto(
    val id: String,
    val name: String? = null,
    val email: String? = null,
    val role: String? = "READER",
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class BookDto(
    val id: String,
    @SerialName("author_id") val authorId: String,
    val title: String,
    @SerialName("author_name") val authorName: String? = null,
    val description: String? = null,
    val genre: String? = null,
    val language: String? = "English",
    val price: Double = 0.0,
    @SerialName("is_free") val isFree: Boolean = false,
    @SerialName("cover_path") val coverPath: String? = null,
    @SerialName("manuscript_path") val manuscriptPath: String? = null,
    @SerialName("preview_path") val previewPath: String? = null,
    @SerialName("total_pages") val totalPages: Int = 100,
    @SerialName("preview_pages") val previewPages: Int = 3,
    @SerialName("sample_pages_count") val samplePagesCount: Int = 3,
    val status: String = "PUBLISHED",
    val rating: Double = 5.0,
    @SerialName("copies_sold") val copiesSold: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class LibraryDto(
    @SerialName("user_id") val userId: String,
    @SerialName("book_id") val bookId: String,
    @SerialName("purchased_at") val purchasedAt: String? = null,
    val progress: Double = 0.0,
    @SerialName("last_page") val lastPage: Int = 1,
    val completed: Boolean = false,
    @SerialName("updated_at") val updatedAt: String? = null
)
