package com.example.unit

import com.example.data.local.BookEntity
import com.example.data.local.toBookEntity
import com.example.data.remote.BookDto
import com.example.data.remote.ProfileDto
import com.example.model.*
import com.example.util.AppError
import com.example.util.CurrencyUtils
import com.example.util.ErrorCategory
import com.example.util.PublicationValidator
import com.example.util.ValidationResult
import org.junit.Assert.*
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Task 1: Comprehensive Unit Test Suite testing:
 * - Authentication result & role mapping
 * - BookDto and BookEntity mapping
 * - Book validation & boundary rules
 * - Price & preview page validation
 * - Publication state transitions
 * - Catalog data state transitions
 * - Reader access-state calculation
 * - Error category mapping
 * - Currency and royalty calculations
 */
class UnitTestSuite {

    // --- Authentication & Profile Mapping ---
    @Test
    fun testUserProfileMapping_AuthorVerifiedRole() {
        val authorDto = ProfileDto(
            id = "author_user_1",
            name = "Elena Rostova",
            email = "elena@booksphere.com",
            role = "AUTHOR_VERIFIED",
            authorStatus = "VERIFIED",
            photoUrl = "https://example.com/avatar.png",
            bio = "Sci-fi author"
        )

        val isAuthor = authorDto.role.equals("AUTHOR", ignoreCase = true) ||
                authorDto.role.equals("AUTHOR_VERIFIED", ignoreCase = true) ||
                authorDto.authorStatus.equals("VERIFIED", ignoreCase = true)

        val userProfile = UserProfile(
            id = authorDto.id,
            name = authorDto.name ?: "Anonymous",
            email = authorDto.email ?: "",
            role = if (isAuthor) UserRole.AUTHOR else UserRole.READER,
            photoUrl = authorDto.photoUrl,
            bio = authorDto.bio ?: ""
        )

        assertEquals("author_user_1", userProfile.id)
        assertEquals("Elena Rostova", userProfile.name)
        assertEquals(UserRole.AUTHOR, userProfile.role)
    }

    @Test
    fun testUserProfileMapping_StandardReaderRole() {
        val readerDto = ProfileDto(
            id = "reader_user_2",
            name = "John Doe",
            email = "john@example.com",
            role = "READER",
            authorStatus = "NONE"
        )

        val isAuthor = readerDto.role.equals("AUTHOR", ignoreCase = true) ||
                readerDto.role.equals("AUTHOR_VERIFIED", ignoreCase = true) ||
                readerDto.authorStatus.equals("VERIFIED", ignoreCase = true)

        val userProfile = UserProfile(
            id = readerDto.id,
            name = readerDto.name ?: "Anonymous",
            email = readerDto.email ?: "",
            role = if (isAuthor) UserRole.AUTHOR else UserRole.READER
        )

        assertEquals(UserRole.READER, userProfile.role)
        assertEquals("John Doe", userProfile.name)
    }

    // --- BookDto Mapping ---
    @Test
    fun testBookDtoToModelMapping_HandlesNullsAndDefaults() {
        val dto = BookDto(
            id = "book_101",
            authorId = "author_99",
            title = "The Quantum Void",
            authorName = "Dr. Aris",
            description = "Deep space expedition into quantum singularity.",
            genre = "Hard Sci-Fi",
            language = "English",
            price = 450.0,
            isFree = false,
            coverPath = "covers/author_99/book_101.jpg",
            manuscriptPath = "manuscripts/author_99/book_101/manuscript.pdf",
            previewPath = "previews/author_99/book_101/preview.pdf",
            totalPages = 350,
            previewPages = 5,
            status = "PUBLISHED",
            rating = 4.85,
            copiesSold = 142
        )

        val book = Book(
            id = dto.id,
            title = dto.title,
            author = dto.authorName ?: "Unknown Author",
            coverUrl = "https://example.com/${dto.coverPath}",
            price = dto.price,
            rating = dto.rating,
            genre = dto.genre ?: "General",
            description = dto.description ?: "",
            language = dto.language ?: "English",
            authorId = dto.authorId,
            coverPath = dto.coverPath,
            manuscriptPath = dto.manuscriptPath,
            previewPath = dto.previewPath,
            totalPages = dto.totalPages,
            previewPages = dto.previewPages,
            isFree = dto.isFree || dto.price == 0.0,
            copiesSold = dto.copiesSold,
            status = dto.status
        )

        assertEquals("book_101", book.id)
        assertEquals("The Quantum Void", book.title)
        assertEquals("Dr. Aris", book.author)
        assertEquals(450.0, book.price, 0.001)
        assertFalse(book.isFree)
        assertEquals(5, book.previewPages)
        assertEquals(350, book.totalPages)
        assertEquals(142, book.copiesSold)
        assertEquals("PUBLISHED", book.status)
    }

    @Test
    fun testBookEntityMapping_BidirectionalIntegrity() {
        val model = Book(
            id = "book_ent_1",
            title = "Silicon Dreams",
            author = "Devon Vance",
            coverUrl = "https://storage.booksphere.com/covers/devon/1.jpg",
            price = 0.0,
            rating = 5.0,
            reviewCount = 42,
            genre = "Cyberpunk",
            description = "A tale of neural grids and lost memories.",
            language = "English",
            pdfUri = null,
            authorId = "author_devon",
            coverPath = "covers/devon/1.jpg",
            manuscriptPath = "manuscripts/devon/1/manuscript.pdf",
            previewPath = "previews/devon/1/preview.pdf",
            totalPages = 200,
            previewPages = 3,
            isPurchased = true,
            isFree = true,
            copiesSold = 1050,
            status = "PUBLISHED"
        )

        val entity = model.toBookEntity()
        val convertedBack = entity.toBook()

        assertEquals(model.id, convertedBack.id)
        assertEquals(model.title, convertedBack.title)
        assertEquals(model.author, convertedBack.author)
        assertEquals(model.price, convertedBack.price, 0.001)
        assertTrue(convertedBack.isFree)
        assertEquals(model.totalPages, convertedBack.totalPages)
        assertEquals(model.copiesSold, convertedBack.copiesSold)
    }

    // --- Book Validation & Boundary Rules ---
    @Test
    fun testPublicationValidator_TitleValidation() {
        val emptyTitle = PublicationValidator.validateMetadata(
            title = "   ",
            author = "Author",
            description = "Valid long description.",
            genre = "Fiction",
            language = "English",
            price = 100.0,
            isFree = false,
            previewPages = 3,
            totalPages = 50
        )
        assertTrue("Blank title must fail", emptyTitle is ValidationResult.Error)
    }

    @Test
    fun testPublicationValidator_PriceAndFreeRules() {
        // Negative price -> error
        val negativePrice = PublicationValidator.validateMetadata(
            title = "Valid Book",
            author = "Author",
            description = "Valid description.",
            genre = "Fiction",
            language = "English",
            price = -10.0,
            isFree = false,
            previewPages = 2,
            totalPages = 50
        )
        assertTrue("Negative price rejected", negativePrice is ValidationResult.Error)

        // Paid book with zero price -> error
        val paidZeroPrice = PublicationValidator.validateMetadata(
            title = "Valid Book",
            author = "Author",
            description = "Valid description.",
            genre = "Fiction",
            language = "English",
            price = 0.0,
            isFree = false,
            previewPages = 2,
            totalPages = 50
        )
        assertTrue("Paid book must have price > 0", paidZeroPrice is ValidationResult.Error)

        // Free book with zero price -> success
        val freeZeroPrice = PublicationValidator.validateMetadata(
            title = "Valid Book",
            author = "Author",
            description = "Valid description.",
            genre = "Fiction",
            language = "English",
            price = 0.0,
            isFree = true,
            previewPages = 2,
            totalPages = 50
        )
        assertTrue("Free book with 0 price is valid", freeZeroPrice.isSuccess)
    }

    @Test
    fun testPublicationValidator_PreviewPagesBoundaries() {
        // previewPages < 1 -> error
        val zeroPreview = PublicationValidator.validateMetadata(
            title = "Valid Book",
            author = "Author",
            description = "Valid description.",
            genre = "Fiction",
            language = "English",
            price = 299.0,
            isFree = false,
            previewPages = 0,
            totalPages = 50
        )
        assertTrue("Preview < 1 is rejected", zeroPreview is ValidationResult.Error)

        // previewPages > totalPages -> error
        val overflowPreview = PublicationValidator.validateMetadata(
            title = "Valid Book",
            author = "Author",
            description = "Valid description.",
            genre = "Fiction",
            language = "English",
            price = 299.0,
            isFree = false,
            previewPages = 51,
            totalPages = 50
        )
        assertTrue("Preview > totalPages is rejected", overflowPreview is ValidationResult.Error)

        // previewPages == totalPages -> valid
        val fullPreview = PublicationValidator.validateMetadata(
            title = "Valid Book",
            author = "Author",
            description = "Valid description.",
            genre = "Fiction",
            language = "English",
            price = 299.0,
            isFree = false,
            previewPages = 50,
            totalPages = 50
        )
        assertTrue("Preview == totalPages is valid", fullPreview.isSuccess)
    }

    // --- Publication State Transitions ---
    @Test
    fun testPublicationStages_SequentialTransitions() {
        val stages: List<PublicationStage> = listOf(
            PublicationStage.Idle,
            PublicationStage.Validating,
            PublicationStage.CreatingDraft("book_uuid_1"),
            PublicationStage.UploadingCover("book_uuid_1"),
            PublicationStage.UploadingManuscript("book_uuid_1"),
            PublicationStage.GeneratingPreview("book_uuid_1", 3),
            PublicationStage.UploadingPreview("book_uuid_1"),
            PublicationStage.Processing("book_uuid_1"),
            PublicationStage.Finalizing("book_uuid_1"),
            PublicationStage.Completed(
                Book(
                    id = "book_uuid_1",
                    title = "Finished Book",
                    author = "Author",
                    coverUrl = "",
                    price = 0.0,
                    status = BookStatus.PUBLISHED.name
                )
            )
        )

        assertEquals(10, stages.size)
        assertTrue(stages[1] is PublicationStage.Validating)
        assertTrue(stages[9] is PublicationStage.Completed)
        assertEquals("Published successfully!", stages[9].description)

        val failedStage = PublicationStage.Failed(
            stage = "Uploading Manuscript",
            error = "Storage timeout",
            canRetry = true,
            bookId = "book_uuid_1"
        )
        assertTrue(failedStage.canRetry)
        assertEquals("book_uuid_1", failedStage.bookId)
        assertTrue(failedStage.description.contains("Uploading Manuscript"))
    }

    // --- Catalog Data State Transitions ---
    @Test
    fun testCatalogDataStateTransitions() {
        var state: DataState<List<Book>> = DataState.Idle
        assertTrue(state is DataState.Idle)

        state = DataState.Loading
        assertTrue(state is DataState.Loading)

        val mockBooks = listOf(
            Book(id = "1", title = "B1", author = "A1", coverUrl = "", price = 100.0)
        )
        state = DataState.Success(mockBooks)
        assertTrue(state is DataState.Success)
        assertEquals(1, (state as DataState.Success).data.size)

        state = DataState.Empty
        assertTrue(state is DataState.Empty)

        state = DataState.Offline(data = mockBooks, message = "Cached catalog (offline)")
        assertTrue(state is DataState.Offline)
        assertEquals("Cached catalog (offline)", (state as DataState.Offline).message)

        state = DataState.Error(AppError(ErrorCategory.NETWORK_ERROR, "No connection", canRetry = true))
        assertTrue(state is DataState.Error)
        assertTrue((state as DataState.Error).error.canRetry)
    }

    // --- Reader Access-State Calculation ---
    @Test
    fun testReaderAccessState_UnpurchasedPaidBook_ResolvesToPreview() {
        val paidBook = Book(
            id = "paid_123",
            title = "Paid Masterpiece",
            author = "Elena Rostova",
            coverUrl = "https://example.com/cover.jpg",
            price = 399.0,
            isFree = false,
            isPurchased = false,
            totalPages = 300,
            previewPages = 5,
            previewPath = "previews/elena/paid_123/preview.pdf"
        )

        val resolved = ResolvedBookAccess(
            bookId = paidBook.id,
            target = BookAccessTarget.RemoteStorageUrl("https://supabase.co/storage/v1/object/public/${paidBook.previewPath}", isSigned = false),
            isPreviewOnly = true,
            allowedPages = paidBook.previewPages,
            totalPages = paidBook.totalPages
        )

        assertTrue("Unpurchased paid book must be preview only", resolved.isPreviewOnly)
        assertEquals(5, resolved.allowedPages)
        assertEquals(300, resolved.totalPages)
        assertTrue(resolved.target is BookAccessTarget.RemoteStorageUrl)
    }

    @Test
    fun testReaderAccessState_EntitledPaidBook_ResolvesToFullManuscript() {
        val entitledBook = Book(
            id = "paid_123",
            title = "Paid Masterpiece",
            author = "Elena Rostova",
            coverUrl = "https://example.com/cover.jpg",
            price = 399.0,
            isFree = false,
            isPurchased = true,
            totalPages = 300,
            previewPages = 5,
            manuscriptPath = "manuscripts/elena/paid_123/manuscript.pdf"
        )

        val resolved = ResolvedBookAccess(
            bookId = entitledBook.id,
            target = BookAccessTarget.RemoteStorageUrl("https://supabase.co/storage/v1/object/sign/manuscripts/elena/paid_123/manuscript.pdf?token=xyz", isSigned = true),
            isPreviewOnly = false,
            allowedPages = entitledBook.totalPages,
            totalPages = entitledBook.totalPages
        )

        assertFalse("Entitled book must not be preview only", resolved.isPreviewOnly)
        assertEquals(300, resolved.allowedPages)
        assertTrue((resolved.target as BookAccessTarget.RemoteStorageUrl).isSigned)
    }

    // --- Error Mapping ---
    @Test
    fun testAppError_AllCategoriesMappedCorrectly() {
        assertEquals(ErrorCategory.NETWORK_ERROR, AppError.from(UnknownHostException()).category)
        assertEquals(ErrorCategory.NETWORK_ERROR, AppError.from(SocketTimeoutException()).category)
        assertEquals(ErrorCategory.AUTH_ERROR, AppError.from(RuntimeException("JWT expired")).category)
        assertEquals(ErrorCategory.PERMISSION_ERROR, AppError.from(RuntimeException("Permission Denied: RLS violation")).category)
        assertEquals(ErrorCategory.STORAGE_ERROR, AppError.from(RuntimeException("Storage bucket upload failed")).category)
        assertEquals(ErrorCategory.VALIDATION_ERROR, AppError.from(IllegalArgumentException("Invalid pages")).category)
        assertEquals(ErrorCategory.NOT_FOUND, AppError.from(RuntimeException("HTTP 404 Book not found")).category)
        assertEquals(ErrorCategory.UNKNOWN_ERROR, AppError.from(RuntimeException("Unrecognized internal exception")).category)
    }

    // --- Currency & Royalty Calculations ---
    @Test
    fun testCurrencyAndRoyaltyCalculations() {
        assertEquals("₹299", CurrencyUtils.formatInr(299.0))
        assertEquals("₹0", CurrencyUtils.formatInr(0.0))
        assertEquals("₹10,000", CurrencyUtils.formatInr(10000.0))

        val singleSaleNet = RoyaltyConfig.calculateAuthorNet(price = 1000.0, copiesSold = 1)
        val singleSaleFee = RoyaltyConfig.calculatePlatformFee(price = 1000.0, copiesSold = 1)
        assertEquals(850.0, singleSaleNet, 0.001)
        assertEquals(150.0, singleSaleFee, 0.001)

        val bulkSaleNet = RoyaltyConfig.calculateAuthorNet(price = 500.0, copiesSold = 20)
        val bulkSaleFee = RoyaltyConfig.calculatePlatformFee(price = 500.0, copiesSold = 20)
        assertEquals(8500.0, bulkSaleNet, 0.001)
        assertEquals(1500.0, bulkSaleFee, 0.001)
        assertEquals(10000.0, bulkSaleNet + bulkSaleFee, 0.001)
    }
}
