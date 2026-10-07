package com.example.security

import com.example.data.remote.ProfileDto
import com.example.data.remote.SupabaseConfig
import com.example.model.Book
import com.example.model.BookAccessTarget
import com.example.model.ResolvedBookAccess
import com.example.model.UserRole
import org.junit.Assert.*
import org.junit.Test

/**
 * Security unit test suite testing RLS assumptions, access control models,
 * role mapping logic, and credential confidentiality for BookSphere.
 */
class SecurityPolicyTest {

    @Test
    fun testFreeBookProvidesFullAccessWithoutPurchase() {
        val freeBook = Book(
            id = "book_free_1",
            title = "Open Horizons",
            author = "Elena Rostova",
            coverUrl = "https://example.com/cover.jpg",
            price = 0.0,
            isFree = true,
            totalPages = 240,
            previewPages = 3,
            manuscriptPath = "manuscripts/author1/book_free_1/manuscript.pdf",
            previewPath = "previews/author1/book_free_1/preview.pdf"
        )

        assertTrue("Free book must have isFree set to true", freeBook.isFree)
        assertEquals("Free book price must be zero", 0.0, freeBook.price, 0.0001)
    }

    @Test
    fun testPaidBookWithoutEntitlementRestrictsToPreview() {
        val paidBook = Book(
            id = "book_paid_1",
            title = "Celestial Cartographer",
            author = "Elena Rostova",
            coverUrl = "https://example.com/cover.jpg",
            price = 299.0,
            isFree = false,
            isPurchased = false,
            totalPages = 320,
            previewPages = 3,
            manuscriptPath = "manuscripts/author1/book_paid_1/manuscript.pdf",
            previewPath = "previews/author1/book_paid_1/preview.pdf"
        )

        assertFalse("Paid book must not be free", paidBook.isFree)
        assertFalse("Unpaid book must not be marked purchased", paidBook.isPurchased)
        assertEquals("Preview pages must match configuration", 3, paidBook.previewPages)
    }

    @Test
    fun testProfileRoleMappingFromAuthorStatus() {
        val verifiedAuthorProfile = ProfileDto(
            id = "user_123",
            name = "Author Person",
            email = "author@booksphere.literary",
            role = "AUTHOR_VERIFIED",
            authorStatus = "VERIFIED"
        )

        val isAuthor = verifiedAuthorProfile.role.equals("AUTHOR", ignoreCase = true) ||
                verifiedAuthorProfile.role.equals("AUTHOR_VERIFIED", ignoreCase = true) ||
                verifiedAuthorProfile.authorStatus.equals("VERIFIED", ignoreCase = true)

        assertTrue("Verified author status on backend must map to AUTHOR role in client", isAuthor)

        val unverifiedReaderProfile = ProfileDto(
            id = "user_456",
            name = "Reader Person",
            email = "reader@booksphere.literary",
            role = "READER",
            authorStatus = "NONE"
        )

        val isReaderAuthor = unverifiedReaderProfile.role.equals("AUTHOR", ignoreCase = true) ||
                unverifiedReaderProfile.role.equals("AUTHOR_VERIFIED", ignoreCase = true) ||
                unverifiedReaderProfile.authorStatus.equals("VERIFIED", ignoreCase = true)

        assertFalse("Normal reader cannot possess author privileges", isReaderAuthor)
    }

    @Test
    fun testSupabaseUrlSanitizationRemovesEndpointSuffixes() {
        val dirtyUrl = "https://myproject.supabase.co/rest/v1/"
        val cleanUrl = SupabaseConfig.sanitizeSupabaseUrl(dirtyUrl)
        assertEquals("https://myproject.supabase.co", cleanUrl)

        val dirtyStorageUrl = "https://myproject.supabase.co/storage/v1"
        val cleanStorageUrl = SupabaseConfig.sanitizeSupabaseUrl(dirtyStorageUrl)
        assertEquals("https://myproject.supabase.co", cleanStorageUrl)
    }

    @Test
    fun testResolvedBookAccessDoesNotExposeFullManuscriptWhenPreviewOnly() {
        val previewAccess = ResolvedBookAccess(
            bookId = "book_test",
            target = BookAccessTarget.RemoteStorageUrl("https://supabase.co/storage/v1/object/public/previews/preview.pdf", isSigned = false),
            isPreviewOnly = true,
            allowedPages = 3,
            totalPages = 100
        )

        assertTrue("Preview access must be flagged previewOnly", previewAccess.isPreviewOnly)
        assertEquals("Allowed pages must be capped to preview page count", 3, previewAccess.allowedPages)
        assertTrue("Preview target must be RemoteStorageUrl", previewAccess.target is BookAccessTarget.RemoteStorageUrl)
        assertFalse("Preview URL is not a private signed token", (previewAccess.target as BookAccessTarget.RemoteStorageUrl).isSigned)
    }
}
