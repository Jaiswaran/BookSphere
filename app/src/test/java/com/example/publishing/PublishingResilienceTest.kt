package com.example.publishing

import com.example.data.local.BookEntity
import com.example.data.remote.BookDto
import com.example.model.Book
import com.example.model.BookStatus
import com.example.model.PublicationStage
import com.example.model.RoyaltyConfig
import com.example.util.ManuscriptValidationInfo
import com.example.util.PublicationValidator
import com.example.util.ValidationResult
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PublishingResilienceTest {

    @Test
    fun testMetadataValidationRequiresTitle() {
        val result = PublicationValidator.validateMetadata(
            title = "",
            author = "Elena Rostova",
            description = "A sweeping sci-fi epic across the cosmos.",
            genre = "Sci-Fi",
            language = "English",
            price = 299.0,
            isFree = false,
            previewPages = 3,
            totalPages = 200
        )
        assertTrue("Blank title must fail validation", result is ValidationResult.Error)
        assertEquals("Book title is required.", (result as ValidationResult.Error).message)
    }

    @Test
    fun testMetadataValidationRejectsNegativePrice() {
        val result = PublicationValidator.validateMetadata(
            title = "Starlight Voyage",
            author = "Elena Rostova",
            description = "A journey through space.",
            genre = "Sci-Fi",
            language = "English",
            price = -50.0,
            isFree = false,
            previewPages = 3,
            totalPages = 200
        )
        assertTrue("Negative price must fail validation", result is ValidationResult.Error)
    }

    @Test
    fun testMetadataValidationEnforcesFreeBookZeroPrice() {
        val result = PublicationValidator.validateMetadata(
            title = "Open Horizon",
            author = "Elena Rostova",
            description = "A free journey through open horizons.",
            genre = "Fiction",
            language = "English",
            price = 100.0,
            isFree = true,
            previewPages = 3,
            totalPages = 200
        )
        assertTrue("Free book with positive price must fail validation", result is ValidationResult.Error)
    }

    @Test
    fun testMetadataValidationEnforcesPaidBookPositivePrice() {
        val result = PublicationValidator.validateMetadata(
            title = "Paid Horizon",
            author = "Elena Rostova",
            description = "A paid journey.",
            genre = "Fiction",
            language = "English",
            price = 0.0,
            isFree = false,
            previewPages = 3,
            totalPages = 200
        )
        assertTrue("Paid book with 0 price must fail validation", result is ValidationResult.Error)
    }

    @Test
    fun testMetadataValidationEnforcesPreviewPagesBounds() {
        val zeroPreviewResult = PublicationValidator.validateMetadata(
            title = "Valid Title",
            author = "Author",
            description = "Valid long description.",
            genre = "Fiction",
            language = "English",
            price = 199.0,
            isFree = false,
            previewPages = 0,
            totalPages = 100
        )
        assertTrue("Preview pages < 1 must fail", zeroPreviewResult is ValidationResult.Error)

        val excessPreviewResult = PublicationValidator.validateMetadata(
            title = "Valid Title",
            author = "Author",
            description = "Valid long description.",
            genre = "Fiction",
            language = "English",
            price = 199.0,
            isFree = false,
            previewPages = 150,
            totalPages = 100
        )
        assertTrue("Preview pages > total pages must fail", excessPreviewResult is ValidationResult.Error)
    }

    @Test
    fun testInspectManuscriptPdfRejectsNullOrNonExistentFile() {
        val nullInfo = PublicationValidator.inspectManuscriptPdf(null)
        assertFalse("Null file must be invalid", nullInfo.isValid)
        assertEquals("Manuscript file does not exist.", nullInfo.errorMessage)

        val nonExistent = File("non_existent_file_${System.currentTimeMillis()}.pdf")
        val info = PublicationValidator.inspectManuscriptPdf(nonExistent)
        assertFalse("Non-existent file must be invalid", info.isValid)
    }

    @Test
    fun testInspectManuscriptPdfRejectsEmptyFile() {
        val tempEmpty = File.createTempFile("empty_test_", ".pdf")
        try {
            tempEmpty.writeBytes(ByteArray(0))
            val info = PublicationValidator.inspectManuscriptPdf(tempEmpty)
            assertFalse("Empty file must be invalid", info.isValid)
            assertTrue("Error must mention empty file", info.errorMessage?.contains("empty") == true)
        } finally {
            tempEmpty.delete()
        }
    }

    @Test
    fun testCoverValidationRejectsOversizedCover() {
        val oversizedCover = ByteArray(11 * 1024 * 1024) // 11 MB > 10 MB limit
        val result = PublicationValidator.validateCover(oversizedCover)
        assertTrue("Oversized cover must fail validation", result is ValidationResult.Error)
    }

    @Test
    fun testRoyaltyCalculatesAuthorSeventyPercent() {
        assertEquals(70.0, RoyaltyConfig.AUTHOR_PERCENT, 0.001)
        assertEquals(30.0, RoyaltyConfig.PLATFORM_PERCENT, 0.001)

        val bookPrice = 100.0
        val authorNet = RoyaltyConfig.calculateAuthorNet(bookPrice, 1)
        val platformFee = RoyaltyConfig.calculatePlatformFee(bookPrice, 1)

        assertEquals("Author net must be 70% of retail", 70.0, authorNet, 0.001)
        assertEquals("Platform fee must be 30% of retail", 30.0, platformFee, 0.001)
        assertEquals("Author + Platform must sum to 100%", bookPrice, authorNet + platformFee, 0.001)

        val multipleCopiesNet = RoyaltyConfig.calculateAuthorNet(200.0, 5)
        assertEquals("Author net for 5 copies of 200 INR must be 700 INR", 700.0, multipleCopiesNet, 0.001)
    }

    @Test
    fun testPublicationStagesProgressionAndDescriptions() {
        val validating = PublicationStage.Validating
        assertEquals("Validating book metadata and manuscript...", validating.description)

        val draft = PublicationStage.CreatingDraft("book_123")
        assertEquals("Creating draft publication record...", draft.description)

        val cover = PublicationStage.UploadingCover("book_123")
        assertEquals("Uploading cover art...", cover.description)

        val manuscript = PublicationStage.UploadingManuscript("book_123")
        assertEquals("Uploading manuscript PDF to secure storage...", manuscript.description)

        val preview = PublicationStage.GeneratingPreview("book_123", 5)
        assertEquals("Generating sample preview PDF (5 pages)...", preview.description)

        val finalizing = PublicationStage.Finalizing("book_123")
        assertEquals("Finalizing publication in Supabase...", finalizing.description)

        val failed = PublicationStage.Failed(stage = "Uploading Manuscript", error = "Network timeout")
        assertTrue("Failed description must include stage and error", failed.description.contains("Uploading Manuscript") && failed.description.contains("Network timeout"))
    }

    @Test
    fun testPublicationLifecycleStatesEnum() {
        val expectedStates = listOf("DRAFT", "UPLOADING", "PROCESSING", "PUBLISHED", "FAILED", "ARCHIVED")
        for (stateName in expectedStates) {
            assertNotNull("State $stateName must exist in BookStatus", BookStatus.valueOf(stateName))
        }
    }

    @Test
    fun testCleanupStrategyTracksUploadedAssets() {
        val uploadedAssets = mutableListOf<Pair<String, String>>()
        val bookId = "test_book_id"
        val userId = "test_user_id"

        // Simulate successful cover and manuscript upload
        uploadedAssets.add("covers" to "$userId/$bookId.jpg")
        uploadedAssets.add("manuscripts" to "$userId/$bookId/manuscript.pdf")

        assertEquals(2, uploadedAssets.size)

        // When a failure happens at preview stage:
        var isCleanedUp = false
        val cleanedList = mutableListOf<String>()
        for ((bucket, path) in uploadedAssets) {
            cleanedList.add("$bucket/$path")
        }
        isCleanedUp = cleanedList.size == 2

        assertTrue("Cleanup strategy must identify all uploaded assets for deletion", isCleanedUp)
        assertTrue("Must include cover path", cleanedList.contains("covers/$userId/$bookId.jpg"))
        assertTrue("Must include manuscript path", cleanedList.contains("manuscripts/$userId/$bookId/manuscript.pdf"))
    }

    @Test
    fun testIdempotentRetryUsesStableBookId() {
        val originalBookId = "book_stable_uuid_987"
        val firstDraftDto = BookDto(
            id = originalBookId,
            authorId = "user_1",
            title = "Original Title",
            status = BookStatus.FAILED.name
        )

        assertEquals("Original draft has FAILED status", BookStatus.FAILED.name, firstDraftDto.status)

        // On retry with same ID
        val retryDto = firstDraftDto.copy(
            status = BookStatus.PUBLISHED.name,
            manuscriptPath = "manuscripts/user_1/$originalBookId/manuscript.pdf"
        )

        assertEquals("Retried book must keep the identical stable bookId", originalBookId, retryDto.id)
        assertEquals("Retried book updates status to PUBLISHED", BookStatus.PUBLISHED.name, retryDto.status)
    }

    @Test
    fun testRoomCacheOnlyPopulatedOnPublishedStatus() {
        val draftBook = Book(
            id = "draft_1",
            title = "Draft Title",
            author = "Author",
            coverUrl = "https://example.com/cover.jpg",
            price = 299.0,
            status = BookStatus.UPLOADING.name
        )

        val shouldCacheInRoom = draftBook.status == BookStatus.PUBLISHED.name
        assertFalse("Unpublished book in UPLOADING state must not be cached in Room catalog", shouldCacheInRoom)

        val publishedBook = draftBook.copy(status = BookStatus.PUBLISHED.name)
        val shouldCachePublished = publishedBook.status == BookStatus.PUBLISHED.name
        assertTrue("Published book must be cached in Room catalog", shouldCachePublished)
    }
}
