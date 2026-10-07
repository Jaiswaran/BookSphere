package com.example.model

data class SamplePage(
    val pageNumber: Int,
    val chapterTitle: String,
    val dropCapLetter: String,
    val firstSentenceRemainder: String,
    val paragraphs: List<String>,
    val footnote: String? = null,
    val marginNote: String? = null
)

sealed interface BookAccessTarget {
    data class LocalUri(val uriString: String) : BookAccessTarget
    data class RemoteStorageUrl(val url: String, val isSigned: Boolean = false) : BookAccessTarget
    data class EmbeddedPages(val pages: List<String>) : BookAccessTarget
}

data class ResolvedBookAccess(
    val bookId: String,
    val target: BookAccessTarget,
    val isPreviewOnly: Boolean,
    val allowedPages: Int,
    val totalPages: Int
)

data class Book(
    val id: String,
    val title: String,
    val author: String,
    val coverUrl: String,
    val price: Double,
    val rating: Double = 4.9,
    val reviewCount: Int = 128,
    val genre: String = "Literary Fiction",
    val description: String = "",
    val language: String = "English",
    val pdfUri: String? = null,
    val authorId: String? = null,
    val coverPath: String? = null,
    val manuscriptPath: String? = null,
    val previewPath: String? = null,
    val resolvedAccessUrl: String? = null,
    val totalPages: Int = 320,
    val previewPages: Int = 3,
    val samplePagesCount: Int = 20,
    val samplePages: List<SamplePage> = emptyList(),
    val contentPages: List<String> = emptyList(),
    val isPurchased: Boolean = false,
    val isFree: Boolean = price == 0.0,
    val copiesSold: Int = 0,
    val status: String = "PUBLISHED",
    val progress: Float? = null,
    val progressText: String? = null,
    val timeLeft: String? = null,
    val badgeType: String? = null, // "new", "completed", "notes"
    val badgeValue: String? = null,
    val authorBio: String = "Independent Author publishing directly via BookSphere."
)

object RoyaltyConfig {
    const val AUTHOR_PERCENT = 85.0
    const val PLATFORM_PERCENT = 15.0
    const val AUTHOR_RATE = 0.85
    const val PLATFORM_RATE = 0.15

    fun calculateAuthorNet(price: Double, copiesSold: Int = 1): Double =
        price * AUTHOR_RATE * copiesSold

    fun calculatePlatformFee(price: Double, copiesSold: Int = 1): Double =
        price * PLATFORM_RATE * copiesSold
}

sealed interface PublicationStage {
    object Idle : PublicationStage
    object Validating : PublicationStage
    data class CreatingDraft(val bookId: String) : PublicationStage
    data class UploadingCover(val bookId: String) : PublicationStage
    data class UploadingManuscript(val bookId: String) : PublicationStage
    data class GeneratingPreview(val bookId: String, val pages: Int) : PublicationStage
    data class UploadingPreview(val bookId: String) : PublicationStage
    data class Processing(val bookId: String) : PublicationStage
    data class Finalizing(val bookId: String) : PublicationStage
    data class Completed(val book: Book) : PublicationStage
    data class Failed(
        val stage: String,
        val error: String,
        val canRetry: Boolean = true,
        val bookId: String? = null
    ) : PublicationStage
    object Cancelled : PublicationStage

    val description: String
        get() = when (this) {
            is Idle -> ""
            is Validating -> "Validating book metadata and manuscript..."
            is CreatingDraft -> "Creating draft publication record..."
            is UploadingCover -> "Uploading cover art..."
            is UploadingManuscript -> "Uploading manuscript PDF to secure storage..."
            is GeneratingPreview -> "Generating sample preview PDF ($pages pages)..."
            is UploadingPreview -> "Uploading preview PDF..."
            is Processing -> "Processing and verifying publication assets..."
            is Finalizing -> "Finalizing publication in Supabase..."
            is Completed -> "Published successfully!"
            is Failed -> "Failed during $stage: $error"
            is Cancelled -> "Publication cancelled."
        }
}

data class AuthorStats(
    val name: String = "Elena Rostova",
    val avatarUrl: String = "https://lh3.googleusercontent.com/aida-public/AB6AXuCev1HvMLVpVmXZ55glfupYklmea-rnhzteh0q9MzTR6IgY0lxVpWvj9zZBtXGcqnoHbmcaUns3pIgz4XruTt9FdKonNT0K9NxwnNyE9uVNe3Um841VGyvmPD6cNtGriePjxSYrpltpgoAhZDeottqeMxHPM5l9qJf5zEX0M7Vybjr3TB8kO-dBBPriXYMGKOfv3ZesdLoV5QRmS1oRAyUYqRifQHzu8tXBb1G36LIoT6h9EGTJGqCt",
    val grossSales: Double = 0.0,
    val growthPercent: Double = 18.4,
    val circulationCopies: Int = 0,
    val royaltyRate: Double = RoyaltyConfig.AUTHOR_PERCENT,
    val netRevenue: Double = 0.0,
    val platformFee: Double = 0.0
)

data class PublishedWork(
    val id: String,
    val title: String,
    val price: Double,
    val copiesSold: Int,
    val rating: Double,
    val netEarned: Double,
    val status: String = "PUBLISHED", // "PUBLISHED", "DRAFT", "UPLOADING", "PROCESSING", "FAILED", "ARCHIVED"
    val coverUrl: String,
    val genre: String,
    val errorMessage: String? = null
)

data class ReaderResonance(
    val readerName: String,
    val type: String, // "Patron Tip", "Verified Review", "Shared Highlight"
    val tipAmount: String? = null,
    val comment: String,
    val rating: Int? = null,
    val timeAgo: String
)

data class PlatformMetrics(
    val totalBooksListed: Int = 1420,
    val totalCopiesSold: Int = 48920,
    val activeAuthors: Int = 1240,
    val activeReaders: Int = 28450,
    val previewPageViews: Int = 142800,
    val totalPlatformVolume: Double = 24460000.0,
    val creatorPayouts: Double = 20791000.0,
    val avgRating: Double = 4.92
)

data class ReadingList(
    val id: String,
    val name: String,
    val description: String = "",
    val bookIds: List<String> = emptyList(),
    val isPublic: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

data class UserProfile(
    val id: String, // Supabase Auth UUID
    val name: String,
    val email: String,
    val role: UserRole = UserRole.READER,
    val photoUrl: String? = null,
    val bio: String = "Passionate literary enthusiast, avid reader of speculative fiction & philosophy.",
    val readingLists: List<ReadingList> = emptyList(),
    val favoriteGenres: List<String> = listOf("Speculative Fiction", "Classic Literature", "Philosophy")
)

enum class ScreenTab {
    DISCOVER,
    MY_LIBRARY,
    AUTHOR_STUDIO,
    PROFILE,
    BOOK_DETAIL,
    READER
}

enum class UserRole {
    READER,
    AUTHOR
}

enum class BookStatus {
    DRAFT,
    UPLOADING,
    PROCESSING,
    PUBLISHED,
    FAILED,
    ARCHIVED
}
