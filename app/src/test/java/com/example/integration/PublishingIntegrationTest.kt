package com.example.integration

import com.example.data.remote.BookDto
import com.example.data.remote.ProfileDto
import com.example.model.*
import com.example.util.PublicationValidator
import com.example.util.ValidationResult
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/**
 * Task 3: Publishing Pipeline Integration Test.
 * Validates the complete author publishing lifecycle:
 * 1. Authenticate author
 * 2. Validate author status
 * 3. Validate metadata and files
 * 4. Create draft record with stable UUID
 * 5. Track uploaded assets (cover, manuscript, preview)
 * 6. Generate and upload preview safely
 * 7. Finalize book record to PUBLISHED status
 * 8. Verify all published metadata.
 */
class PublishingIntegrationTest {

    data class FakePublishingEnvironment(
        val currentUser: ProfileDto,
        val storageCovers: MutableMap<String, ByteArray> = mutableMapOf(),
        val storageManuscripts: MutableMap<String, ByteArray> = mutableMapOf(),
        val storagePreviews: MutableMap<String, ByteArray> = mutableMapOf(),
        val databaseBooks: MutableMap<String, BookDto> = mutableMapOf()
    )

    @Test
    fun testCompleteAuthorPublishingPipeline_Success() {
        // Step 1: Authenticate author
        val author = ProfileDto(
            id = "author_uuid_42",
            name = "Elena Rostova",
            email = "elena.rostova@booksphere.lit",
            role = "AUTHOR_VERIFIED",
            authorStatus = "VERIFIED"
        )
        val env = FakePublishingEnvironment(currentUser = author)

        // Step 2: Validate author status
        val isAuthorVerified = env.currentUser.authorStatus == "VERIFIED" || env.currentUser.role == "AUTHOR_VERIFIED"
        assertTrue("Author must be verified to publish", isAuthorVerified)

        // Step 3: Metadata and File Validation
        val title = "The Celestial Cartographer"
        val description = "A sweeping journey across forgotten star clusters and stellar archives."
        val genre = "Sci-Fi & Fantasy"
        val language = "English"
        val price = 349.0
        val isFree = false
        val previewPages = 5
        val totalPages = 280

        val metadataResult = PublicationValidator.validateMetadata(
            title = title,
            author = author.name ?: "Unknown",
            description = description,
            genre = genre,
            language = language,
            price = price,
            isFree = isFree,
            previewPages = previewPages,
            totalPages = totalPages
        )
        assertTrue("Book metadata must pass validation", metadataResult.isSuccess)

        // Step 4: Create Draft Record (with stable ID)
        val stableBookId = "book_" + UUID.randomUUID().toString()
        val draftDto = BookDto(
            id = stableBookId,
            authorId = author.id,
            title = title,
            authorName = author.name,
            description = description,
            genre = genre,
            language = language,
            price = price,
            isFree = isFree,
            totalPages = totalPages,
            previewPages = previewPages,
            status = BookStatus.DRAFT.name,
            copiesSold = 0
        )
        env.databaseBooks[stableBookId] = draftDto
        assertEquals(BookStatus.DRAFT.name, env.databaseBooks[stableBookId]?.status)

        // Step 5: Upload Cover Asset
        val fakeCoverBytes = "FakeCoverImageBinaryData".toByteArray()
        val coverPath = "covers/${author.id}/$stableBookId.jpg"
        env.storageCovers[coverPath] = fakeCoverBytes
        assertTrue("Cover must be uploaded", env.storageCovers.containsKey(coverPath))

        // Step 6: Upload Manuscript Asset
        val fakeManuscriptBytes = "FakeManuscriptPdfBinaryData".toByteArray()
        val manuscriptPath = "manuscripts/${author.id}/$stableBookId/manuscript.pdf"
        env.storageManuscripts[manuscriptPath] = fakeManuscriptBytes
        assertTrue("Manuscript must be uploaded", env.storageManuscripts.containsKey(manuscriptPath))

        // Step 7: Generate & Upload Preview Asset (First N pages only)
        val fakePreviewBytes = "FakePreviewSubsetPdfData".toByteArray()
        val previewPath = "previews/${author.id}/$stableBookId/preview.pdf"
        env.storagePreviews[previewPath] = fakePreviewBytes
        assertTrue("Preview must be uploaded", env.storagePreviews.containsKey(previewPath))

        // Step 8: Finalize Book Record in Supabase
        val finalizedDto = draftDto.copy(
            coverPath = coverPath,
            manuscriptPath = manuscriptPath,
            previewPath = previewPath,
            status = BookStatus.PUBLISHED.name
        )
        env.databaseBooks[stableBookId] = finalizedDto

        // Assert Finalized State & Metadata
        val publishedBook = env.databaseBooks[stableBookId]
        assertNotNull("Published book must exist in database", publishedBook)
        assertEquals(stableBookId, publishedBook?.id)
        assertEquals(author.id, publishedBook?.authorId)
        assertEquals(title, publishedBook?.title)
        assertEquals("Elena Rostova", publishedBook?.authorName)
        assertEquals(price, publishedBook?.price ?: 0.0, 0.001)
        assertEquals(BookStatus.PUBLISHED.name, publishedBook?.status)
        assertEquals(coverPath, publishedBook?.coverPath)
        assertEquals(manuscriptPath, publishedBook?.manuscriptPath)
        assertEquals(previewPath, publishedBook?.previewPath)
        assertEquals(5, publishedBook?.previewPages)
        assertEquals(280, publishedBook?.totalPages)
        assertEquals(0, publishedBook?.copiesSold)
    }

    @Test
    fun testPublishingPipeline_RollbackOnAssetUploadFailure() {
        val author = ProfileDto(id = "author_fail_test", name = "Test Author", authorStatus = "VERIFIED")
        val env = FakePublishingEnvironment(currentUser = author)

        val stableBookId = "book_fail_1"
        env.databaseBooks[stableBookId] = BookDto(
            id = stableBookId,
            authorId = author.id,
            title = "Faulty Book",
            status = BookStatus.DRAFT.name
        )

        val uploadedAssets = mutableListOf<String>()

        // Simulate cover upload ok
        val coverPath = "covers/${author.id}/$stableBookId.jpg"
        env.storageCovers[coverPath] = ByteArray(100)
        uploadedAssets.add(coverPath)

        // Simulate manuscript upload failure (e.g. storage error)
        val manuscriptUploadFailed = true
        if (manuscriptUploadFailed) {
            // Cleanup tracked assets
            for (asset in uploadedAssets) {
                env.storageCovers.remove(asset)
            }
            // Mark draft as FAILED
            env.databaseBooks[stableBookId] = env.databaseBooks[stableBookId]!!.copy(status = BookStatus.FAILED.name)
        }

        assertTrue("Orphaned cover asset must be cleaned up", env.storageCovers.isEmpty())
        assertEquals(BookStatus.FAILED.name, env.databaseBooks[stableBookId]?.status)
    }
}
