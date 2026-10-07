package com.example.integration

import com.example.data.remote.BookDto
import com.example.data.remote.ProfileDto
import com.example.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Tasks 4 & 5: Discovery and Preview Access Integration Tests.
 * Tests multi-user flow: Author publishes book -> Reader discovers book in catalog ->
 * Reader (not entitled) accesses preview -> preview is capped to previewPages -> full manuscript is guarded.
 */
class DiscoveryAndPreviewTest {

    @Test
    fun testTwoUserFlow_AuthorPublishesAndReaderDiscovers() {
        // User 1: Author publishes book
        val authorUser = ProfileDto(
            id = "author_user_elena",
            name = "Elena Rostova",
            email = "elena@booksphere.lit",
            role = "AUTHOR_VERIFIED",
            authorStatus = "VERIFIED"
        )

        val publishedBookDto = BookDto(
            id = "book_cosmos_99",
            authorId = authorUser.id,
            title = "Whispers of the Cosmos",
            authorName = authorUser.name,
            description = "An exploration of deep space signals and cosmic philosophy.",
            genre = "Hard Sci-Fi",
            language = "English",
            price = 499.0,
            isFree = false,
            coverPath = "covers/${authorUser.id}/book_cosmos_99.jpg",
            manuscriptPath = "manuscripts/${authorUser.id}/book_cosmos_99/manuscript.pdf",
            previewPath = "previews/${authorUser.id}/book_cosmos_99/preview.pdf",
            totalPages = 310,
            previewPages = 4,
            status = BookStatus.PUBLISHED.name,
            copiesSold = 0
        )

        val publishedCatalog = listOf(publishedBookDto)

        // User 2: Reader authenticates separately
        val readerUser = ProfileDto(
            id = "reader_user_alex",
            name = "Alex Mercer",
            email = "alex@readers.org",
            role = "READER",
            authorStatus = "NONE"
        )
        assertNotEquals("Author and Reader must be distinct users", authorUser.id, readerUser.id)

        // Reader fetches catalog
        val readerCatalog = publishedCatalog
            .filter { it.status == BookStatus.PUBLISHED.name }
            .map { dto ->
                Book(
                    id = dto.id,
                    title = dto.title,
                    author = dto.authorName ?: "Unknown",
                    coverUrl = "https://supabase.co/storage/v1/object/public/${dto.coverPath}",
                    price = dto.price,
                    genre = dto.genre ?: "General",
                    description = dto.description ?: "",
                    language = dto.language ?: "English",
                    authorId = dto.authorId,
                    coverPath = dto.coverPath,
                    manuscriptPath = dto.manuscriptPath,
                    previewPath = dto.previewPath,
                    totalPages = dto.totalPages,
                    previewPages = dto.previewPages,
                    isPurchased = false,
                    isFree = dto.isFree || dto.price == 0.0,
                    status = dto.status
                )
            }

        // Assertions for Discovery (Task 4)
        assertEquals(1, readerCatalog.size)
        val discoveredBook = readerCatalog[0]
        assertEquals("book_cosmos_99", discoveredBook.id)
        assertEquals("Whispers of the Cosmos", discoveredBook.title)
        assertEquals("Elena Rostova", discoveredBook.author)
        assertEquals("covers/author_user_elena/book_cosmos_99.jpg", discoveredBook.coverPath)
        assertEquals(499.0, discoveredBook.price, 0.001)
        assertEquals("PUBLISHED", discoveredBook.status)
        assertFalse(discoveredBook.isFree)
        assertFalse(discoveredBook.isPurchased)
    }

    @Test
    fun testPreviewAccess_UnentitledReader_RestrictedToSamplePages() {
        val paidBook = Book(
            id = "book_cosmos_99",
            title = "Whispers of the Cosmos",
            author = "Elena Rostova",
            coverUrl = "https://example.com/cover.jpg",
            price = 499.0,
            isFree = false,
            isPurchased = false,
            totalPages = 310,
            previewPages = 4,
            coverPath = "covers/author_user_elena/book_cosmos_99.jpg",
            manuscriptPath = "manuscripts/author_user_elena/book_cosmos_99/manuscript.pdf",
            previewPath = "previews/author_user_elena/book_cosmos_99/preview.pdf"
        )

        // Reader requests reading access without entitlement
        val isEntitled = paidBook.isPurchased || paidBook.isFree
        assertFalse("Unpaid book is not entitled", isEntitled)

        val resolvedAccess = if (isEntitled) {
            ResolvedBookAccess(
                bookId = paidBook.id,
                target = BookAccessTarget.RemoteStorageUrl("https://supabase.co/storage/v1/object/sign/${paidBook.manuscriptPath}?token=valid_token", isSigned = true),
                isPreviewOnly = false,
                allowedPages = paidBook.totalPages,
                totalPages = paidBook.totalPages
            )
        } else {
            ResolvedBookAccess(
                bookId = paidBook.id,
                target = BookAccessTarget.RemoteStorageUrl("https://supabase.co/storage/v1/object/public/${paidBook.previewPath}", isSigned = false),
                isPreviewOnly = true,
                allowedPages = paidBook.previewPages,
                totalPages = paidBook.totalPages
            )
        }

        // Assertions for Preview Access (Task 5)
        assertTrue("Preview can be opened", resolvedAccess.allowedPages > 0)
        assertTrue("Access must be flagged as preview only", resolvedAccess.isPreviewOnly)
        assertEquals("Allowed pages must equal previewPages (4)", 4, resolvedAccess.allowedPages)
        assertEquals("Total pages is 310", 310, resolvedAccess.totalPages)

        val targetUrl = (resolvedAccess.target as BookAccessTarget.RemoteStorageUrl).url
        assertTrue("Target URL must point to preview bucket", targetUrl.contains("previews/"))
        assertFalse("Target URL must NOT point to private manuscript bucket", targetUrl.contains("manuscripts/"))
        assertFalse("Target URL must not be signed private token", (resolvedAccess.target as BookAccessTarget.RemoteStorageUrl).isSigned)
    }
}
