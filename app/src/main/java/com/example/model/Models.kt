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
    val totalPages: Int = 320,
    val previewPages: Int = 3,
    val samplePagesCount: Int = 20,
    val samplePages: List<SamplePage> = emptyList(),
    val contentPages: List<String> = emptyList(),
    val isPurchased: Boolean = false,
    val isFree: Boolean = price == 0.0,
    val progress: Float? = null,
    val progressText: String? = null,
    val timeLeft: String? = null,
    val badgeType: String? = null, // "new", "completed", "notes"
    val badgeValue: String? = null,
    val authorBio: String = "Independent Author publishing directly via BookSphere."
)

data class AuthorStats(
    val name: String = "Elena Rostova",
    val avatarUrl: String = "https://lh3.googleusercontent.com/aida-public/AB6AXuCev1HvMLVpVmXZ55glfupYklmea-rnhzteh0q9MzTR6IgY0lxVpWvj9zZBtXGcqnoHbmcaUns3pIgz4XruTt9FdKonNT0K9NxwnNyE9uVNe3Um841VGyvmPD6cNtGriePjxSYrpltpgoAhZDeottqeMxHPM5l9qJf5zEX0M7Vybjr3TB8kO-dBBPriXYMGKOfv3ZesdLoV5QRmS1oRAyUYqRifQHzu8tXBb1G36LIoT6h9EGTJGqCt",
    val grossSales: Double = 698800.0,
    val growthPercent: Double = 18.4,
    val circulationCopies: Int = 1428,
    val royaltyRate: Double = 85.0,
    val netRevenue: Double = 593980.0,
    val platformFee: Double = 104820.0
)

data class PublishedWork(
    val id: String,
    val title: String,
    val price: Double,
    val copiesSold: Int,
    val rating: Double,
    val netEarned: Double,
    val status: String, // "Published", "Draft / In Review"
    val coverUrl: String,
    val genre: String
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

enum class ScreenTab {
    DISCOVER,
    MY_LIBRARY,
    AUTHOR_STUDIO,
    BOOK_DETAIL,
    READER
}

enum class UserRole {
    READER,
    AUTHOR
}
