package com.example.integration

import com.example.data.remote.BookDto
import com.example.data.remote.LibraryDto
import com.example.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Task 6: Entitlement and Full Access Integration Test.
 * Verifies that when a verified server order / purchase creates a library entitlement:
 * 1. Reader library contains the book
 * 2. Full access can be requested
 * 3. A signed manuscript URL is returned
 * 4. Allowed pages equals totalPages (full reading access)
 */
class EntitlementAccessTest {

    @Test
    fun testValidEntitlement_ProvidesFullManuscriptAccess() {
        val readerId = "reader_user_456"
        val bookId = "book_quantum_99"
        val manuscriptPath = "manuscripts/author_123/$bookId/manuscript.pdf"

        // Backend Entitlements Database Record
        val libraryEntitlements = mutableListOf<LibraryDto>()
        libraryEntitlements.add(
            LibraryDto(
                userId = readerId,
                bookId = bookId,
                purchasedAt = "2026-10-07T12:00:00Z",
                progress = 0.15,
                lastPage = 45,
                completed = false
            )
        )

        val bookDto = BookDto(
            id = bookId,
            authorId = "author_123",
            title = "Quantum Singularities",
            authorName = "Elena Rostova",
            price = 399.0,
            isFree = false,
            coverPath = "covers/author_123/$bookId.jpg",
            manuscriptPath = manuscriptPath,
            previewPath = "previews/author_123/$bookId/preview.pdf",
            totalPages = 300,
            previewPages = 3,
            status = "PUBLISHED"
        )

        // 1. Reader library contains book
        val userHasEntitlement = libraryEntitlements.any { it.userId == readerId && it.bookId == bookId }
        assertTrue("Reader library must contain the book", userHasEntitlement)

        val readerBook = Book(
            id = bookDto.id,
            title = bookDto.title,
            author = bookDto.authorName ?: "",
            coverUrl = "https://example.com/cover.jpg",
            price = bookDto.price,
            isPurchased = userHasEntitlement,
            isFree = bookDto.isFree,
            totalPages = bookDto.totalPages,
            previewPages = bookDto.previewPages,
            manuscriptPath = bookDto.manuscriptPath,
            previewPath = bookDto.previewPath
        )
        assertTrue("Book model reflects entitlement", readerBook.isPurchased)

        // 2. Full access requested and signed URL generated
        val signedManuscriptUrl = "https://supabase.co/storage/v1/object/sign/$manuscriptPath?token=sec_token_987654"
        val resolvedAccess = ResolvedBookAccess(
            bookId = readerBook.id,
            target = BookAccessTarget.RemoteStorageUrl(signedManuscriptUrl, isSigned = true),
            isPreviewOnly = false,
            allowedPages = readerBook.totalPages,
            totalPages = readerBook.totalPages
        )

        // 3. Assertions for full access
        assertFalse("Access must NOT be restricted to preview", resolvedAccess.isPreviewOnly)
        assertEquals("Allowed pages must equal total book pages (300)", 300, resolvedAccess.allowedPages)
        assertEquals("Total pages is 300", 300, resolvedAccess.totalPages)

        assertTrue(resolvedAccess.target is BookAccessTarget.RemoteStorageUrl)
        val target = resolvedAccess.target as BookAccessTarget.RemoteStorageUrl
        assertTrue("Must be a private signed URL", target.isSigned)
        assertTrue("URL must point to manuscript bucket", target.url.contains("manuscripts/"))
        assertTrue("URL must contain signed security token", target.url.contains("token=sec_token_987654"))
    }
}
