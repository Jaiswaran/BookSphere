package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.data.SampleData
import com.example.data.local.BookDao
import com.example.data.local.BookEntity
import com.example.data.local.CachedUserProfileDao
import com.example.data.local.CachedUserProfileEntity
import com.example.data.local.toBookEntity
import com.example.data.remote.*
import com.example.model.Book
import com.example.model.BookStatus
import com.example.model.UserProfile
import com.example.model.UserRole
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.seconds
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class BookSphereRepository(
    private val bookDao: BookDao,
    private val profileDao: CachedUserProfileDao,
    private val supabase: SupabaseClient = SupabaseConfig.client
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    // Cached Room Flow
    val allPublishedBooks: Flow<List<BookEntity>> = bookDao.getAllPublishedBooks()
    val activeProfileCache: Flow<CachedUserProfileEntity?> = profileDao.getActiveProfile()

    private val _currentProfile = MutableStateFlow<UserProfile?>(null)
    val currentProfile: StateFlow<UserProfile?> = _currentProfile.asStateFlow()

    init {
        // Restore cached user profile immediately
        scope.launch {
            activeProfileCache.collect { cached ->
                if (cached != null && _currentProfile.value == null) {
                    _currentProfile.value = UserProfile(
                        id = cached.id,
                        name = cached.name ?: "User",
                        email = cached.email ?: "",
                        role = if (cached.role.equals("AUTHOR", ignoreCase = true)) UserRole.AUTHOR else UserRole.READER,
                        photoUrl = cached.photoUrl
                    )
                }
            }
        }

        // Listen to Supabase Auth session status changes
        scope.launch {
            try {
                supabase.auth.sessionStatus.collect { status ->
                    Log.d("BookSphereRepo", "Supabase Auth status: $status")
                    when (status) {
                        is SessionStatus.Authenticated -> {
                            refreshCurrentUserProfile()
                        }
                        is SessionStatus.NotAuthenticated -> {
                            // Only clear if not authenticated
                            val cur = supabase.auth.currentUserOrNull()
                            if (cur == null) {
                                _currentProfile.value = null
                                profileDao.clearActive()
                            }
                        }
                        else -> Unit
                    }
                }
            } catch (e: Exception) {
                Log.w("BookSphereRepo", "Error observing sessionStatus: ${e.message}")
            }
        }

        // Seed initial room data if room database is empty
        scope.launch {
            seedInitialDataIfEmpty()
        }
    }

    suspend fun seedInitialDataIfEmpty() {
        try {
            if (bookDao.getBookCount() == 0) {
                val initialBooks = listOf(
                    SampleData.sampleCelestialCartographer.toBookEntity(),
                    SampleData.staffPickBook.toBookEntity()
                )
                bookDao.insertBooks(initialBooks)
            }
        } catch (e: Exception) {
            Log.w("BookSphereRepo", "Seeding initial local cache failed: ${e.message}")
        }
    }

    // ==========================================
    // SUPABASE AUTHENTICATION
    // ==========================================

    suspend fun signUpWithEmail(
        name: String,
        email: String,
        pass: String,
        role: UserRole
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        runCatching {
            Log.d("BookSphereRepo", "Signing up with Supabase: email=$email, role=$role")
            supabase.auth.signUpWith(Email) {
                this.email = email
                this.password = pass
                this.data = buildJsonObject {
                    put("name", name)
                    put("role", role.name)
                }
            }

            val user = supabase.auth.currentUserOrNull()
                ?: throw IllegalStateException("Signup submitted. If email confirmation is enabled, please verify your email.")

            val userId = user.id
            val profile = UserProfile(
                id = userId,
                name = name.ifBlank { email.substringBefore("@") },
                email = email,
                role = role
            )

            // Ensure profile record in public.profiles table
            ensureProfileInPostgres(profile)

            // Cache profile locally in Room
            cacheProfileLocally(profile)

            _currentProfile.value = profile
            profile
        }
    }

    suspend fun signInWithEmail(
        email: String,
        pass: String
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        runCatching {
            Log.d("BookSphereRepo", "Signing in with Supabase: email=$email")
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = pass
            }

            val profile = refreshCurrentUserProfile()
                ?: throw IllegalStateException("Could not load user profile after sign-in")
            profile
        }
    }

    suspend fun signInWithGoogleIdToken(
        idToken: String,
        preferredRole: UserRole = UserRole.READER
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        runCatching {
            Log.d("BookSphereRepo", "Signing in to Supabase via Google ID Token")
            supabase.auth.signInWith(IDToken) {
                provider = Google
                this.idToken = idToken
            }

            val user = supabase.auth.currentUserOrNull()
                ?: throw IllegalStateException("Supabase Google authentication failed")

            val loadedProfile = loadProfileFromPostgres(user.id)
            val profile = if (loadedProfile != null) {
                loadedProfile
            } else {
                val newProfile = UserProfile(
                    id = user.id,
                    name = user.userMetadata?.get("full_name")?.toString()?.trim('"')
                        ?: user.email?.substringBefore("@") ?: "Google Reader",
                    email = user.email ?: "",
                    role = preferredRole,
                    photoUrl = user.userMetadata?.get("avatar_url")?.toString()?.trim('"')
                )
                ensureProfileInPostgres(newProfile)
                newProfile
            }

            cacheProfileLocally(profile)
            _currentProfile.value = profile
            profile
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            supabase.auth.signOut()
        } catch (e: Exception) {
            Log.w("BookSphereRepo", "Error signing out: ${e.message}")
        }
        profileDao.clearActive()
        _currentProfile.value = null
    }

    suspend fun refreshCurrentUserProfile(): UserProfile? = withContext(Dispatchers.IO) {
        try {
            val user = supabase.auth.currentUserOrNull() ?: return@withContext null
            val profile = loadProfileFromPostgres(user.id) ?: run {
                // If profile row does not exist yet, build from user metadata
                val roleStr = user.userMetadata?.get("role")?.toString()?.trim('"') ?: "READER"
                val role = if (roleStr.equals("AUTHOR", ignoreCase = true)) UserRole.AUTHOR else UserRole.READER
                val name = user.userMetadata?.get("name")?.toString()?.trim('"')
                    ?: user.userMetadata?.get("full_name")?.toString()?.trim('"')
                    ?: user.email?.substringBefore("@") ?: "Reader"
                val fallbackProfile = UserProfile(
                    id = user.id,
                    name = name,
                    email = user.email ?: "",
                    role = role
                )
                ensureProfileInPostgres(fallbackProfile)
                fallbackProfile
            }
            cacheProfileLocally(profile)
            _currentProfile.value = profile
            profile
        } catch (e: Exception) {
            Log.w("BookSphereRepo", "Failed to refresh profile: ${e.message}")
            null
        }
    }

    private suspend fun loadProfileFromPostgres(userId: String): UserProfile? {
        return try {
            val dto = supabase.from("profiles")
                .select {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeSingleOrNull<ProfileDto>()

            dto?.let {
                UserProfile(
                    id = it.id,
                    name = it.name ?: it.email?.substringBefore("@") ?: "User",
                    email = it.email ?: "",
                    role = if (it.role.equals("AUTHOR", ignoreCase = true)) UserRole.AUTHOR else UserRole.READER,
                    photoUrl = it.photoUrl
                )
            }
        } catch (e: Exception) {
            Log.w("BookSphereRepo", "Error querying public.profiles: ${e.message}")
            null
        }
    }

    private suspend fun ensureProfileInPostgres(profile: UserProfile) {
        try {
            val dto = ProfileDto(
                id = profile.id,
                name = profile.name,
                email = profile.email,
                role = profile.role.name,
                photoUrl = profile.photoUrl
            )
            supabase.from("profiles").upsert(dto)
            Log.d("BookSphereRepo", "Synced profile for ${profile.id} to public.profiles")
        } catch (e: Exception) {
            Log.e("BookSphereRepo", "Failed to upsert to public.profiles: ${e.message}")
        }
    }

    private suspend fun cacheProfileLocally(profile: UserProfile) {
        try {
            profileDao.clearActive()
            profileDao.insertProfile(
                CachedUserProfileEntity(
                    id = profile.id,
                    email = profile.email,
                    name = profile.name,
                    role = profile.role.name,
                    photoUrl = profile.photoUrl,
                    isActive = true
                )
            )
        } catch (e: Exception) {
            Log.w("BookSphereRepo", "Error saving cached profile: ${e.message}")
        }
    }

    // ==========================================
    // BOOKS & CATALOG (POSTGREST & STORAGE)
    // ==========================================

    suspend fun fetchPublishedBooks(): List<Book> = withContext(Dispatchers.IO) {
        try {
            val dtoList = supabase.from("books")
                .select {
                    filter {
                        eq("status", "PUBLISHED")
                    }
                }
                .decodeList<BookDto>()

            if (dtoList.isNotEmpty()) {
                val books = dtoList.map { dtoToBook(it) }
                // Update local Room cache
                bookDao.insertBooks(books.map { it.toBookEntity() })
                books
            } else {
                // If remote table has no books yet, return local cache or fallback
                val cached = bookDao.getBookById("sample") // sample check
                emptyList()
            }
        } catch (e: Exception) {
            Log.w("BookSphereRepo", "Error fetching books from Supabase: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchAuthorBooks(authorId: String): List<Book> = withContext(Dispatchers.IO) {
        try {
            val dtoList = supabase.from("books")
                .select {
                    filter {
                        eq("author_id", authorId)
                    }
                }
                .decodeList<BookDto>()

            dtoList.map { dtoToBook(it) }
        } catch (e: Exception) {
            Log.w("BookSphereRepo", "Error fetching author books for $authorId: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchUserLibrary(userId: String): List<Book> = withContext(Dispatchers.IO) {
        try {
            val libraryEntries = supabase.from("library")
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList<LibraryDto>()

            if (libraryEntries.isEmpty()) return@withContext emptyList()

            val bookIds = libraryEntries.map { it.bookId }
            val books = mutableListOf<Book>()
            for (id in bookIds) {
                try {
                    val dto = supabase.from("books")
                        .select {
                            filter {
                                eq("id", id)
                            }
                        }
                        .decodeSingleOrNull<BookDto>()
                    if (dto != null) {
                        val entry = libraryEntries.find { it.bookId == id }
                        val book = dtoToBook(dto).copy(
                            isPurchased = true,
                            progress = entry?.progress?.toFloat() ?: 0f,
                            badgeType = if (entry?.completed == true) "completed" else "new",
                            badgeValue = if (entry?.completed == true) "Completed" else "${((entry?.progress ?: 0.0) * 100).toInt()}% • In Progress"
                        )
                        books.add(book)
                    }
                } catch (ex: Exception) {
                    Log.w("BookSphereRepo", "Error loading library book $id: ${ex.message}")
                }
            }
            books
        } catch (e: Exception) {
            Log.w("BookSphereRepo", "Error querying public.library: ${e.message}")
            emptyList()
        }
    }

    suspend fun recordPurchase(userId: String, book: Book): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val libraryDto = LibraryDto(
                userId = userId,
                bookId = book.id,
                purchasedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date()),
                progress = 0.0,
                lastPage = 1,
                completed = false
            )
            supabase.from("library").upsert(libraryDto)

            // Increment copies sold on the book
            try {
                val newCopies = book.copiesSold + 1
                supabase.from("books").update({
                    set("copies_sold", newCopies)
                }) {
                    filter {
                        eq("id", book.id)
                    }
                }
            } catch (e: Exception) {
                Log.w("BookSphereRepo", "Error incrementing copies_sold: ${e.message}")
            }
            Unit
        }
    }

    suspend fun addFreeBookToLibrary(userId: String, book: Book): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val libraryDto = LibraryDto(
                userId = userId,
                bookId = book.id,
                purchasedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date()),
                progress = 0.0,
                lastPage = 1,
                completed = false
            )
            supabase.from("library").upsert(libraryDto)
            Unit
        }
    }

    suspend fun updateReadingProgress(
        userId: String,
        bookId: String,
        lastPage: Int,
        totalPages: Int,
        completed: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val progress = if (totalPages > 0) (lastPage.toDouble() / totalPages.toDouble()).coerceIn(0.0, 1.0) else 0.0
            supabase.from("library").update({
                set("last_page", lastPage)
                set("progress", progress)
                set("completed", completed)
            }) {
                filter {
                    eq("user_id", userId)
                    eq("book_id", bookId)
                }
            }
            Unit
        }
    }

    // ==========================================
    // REAL SUPABASE STORAGE & PUBLISHING
    // ==========================================

    suspend fun publishBook(
        context: Context,
        title: String,
        author: String,
        description: String,
        genre: String,
        language: String,
        price: Double,
        isFree: Boolean,
        coverBytes: ByteArray?,
        manuscriptFile: File?,
        previewPages: Int = 3,
        onProgress: (String) -> Unit
    ): Result<Book> = withContext(Dispatchers.IO) {
        runCatching {
            // 1. Verify Supabase Auth session
            val currentUser = supabase.auth.currentUserOrNull()
                ?: throw IllegalStateException("You must be logged in to publish a book.")

            val userId = currentUser.id

            // 2. Load user's profile and verify role == AUTHOR
            val profile = loadProfileFromPostgres(userId)
                ?: _currentProfile.value
                ?: throw IllegalStateException("Could not verify author profile.")

            if (profile.role != UserRole.AUTHOR) {
                throw IllegalStateException("Permission Denied: Only verified Authors can publish books.")
            }

            // 3. Validations
            if (title.isBlank()) throw IllegalArgumentException("Book title is required.")
            if (description.isBlank()) throw IllegalArgumentException("Description is required.")
            if (genre.isBlank()) throw IllegalArgumentException("Genre is required.")

            val bookId = UUID.randomUUID().toString()
            val authorName = author.ifBlank { profile.name }
            val finalPrice = if (isFree) 0.0 else price

            onProgress("Validating manuscript and covers...")

            // 4. Upload Cover to covers/{userId}/{bookId}.jpg
            val coverStoragePath = "$userId/$bookId.jpg"
            val fullCoverPath = "covers/$coverStoragePath"
            var resolvedCoverUrl = SampleData.sampleCelestialCartographer.coverUrl

            if (coverBytes != null && coverBytes.isNotEmpty()) {
                onProgress("Uploading cover image to Supabase Storage...")
                try {
                    supabase.storage.from("covers").upload(
                        path = coverStoragePath,
                        data = coverBytes
                    ) {
                        upsert = true
                    }
                    resolvedCoverUrl = supabase.storage.from("covers").publicUrl(coverStoragePath)
                    Log.d("BookSphereRepo", "Cover uploaded: $resolvedCoverUrl")
                } catch (e: Exception) {
                    Log.e("BookSphereRepo", "Failed to upload cover: ${e.message}", e)
                    throw IllegalStateException("Failed to upload cover to Supabase Storage: ${e.message}")
                }
            }

            // 5. Upload Manuscript to manuscripts/{userId}/{bookId}/manuscript.pdf
            val manuscriptStoragePath = "$userId/$bookId/manuscript.pdf"
            val fullManuscriptPath = "manuscripts/$manuscriptStoragePath"
            var totalPages = 150

            if (manuscriptFile != null && manuscriptFile.exists()) {
                onProgress("Reading manuscript PDF...")
                val manuscriptBytes = manuscriptFile.readBytes()

                // Determine page count
                try {
                    val pfd = ParcelFileDescriptor.open(manuscriptFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(pfd)
                    totalPages = renderer.pageCount
                    renderer.close()
                    pfd.close()
                } catch (_: Exception) {
                    totalPages = 120
                }

                onProgress("Uploading manuscript to Supabase Storage...")
                try {
                    supabase.storage.from("manuscripts").upload(
                        path = manuscriptStoragePath,
                        data = manuscriptBytes
                    ) {
                        upsert = true
                    }
                    Log.d("BookSphereRepo", "Manuscript uploaded: $fullManuscriptPath")
                } catch (e: Exception) {
                    Log.e("BookSphereRepo", "Failed to upload manuscript: ${e.message}", e)
                    throw IllegalStateException("Failed to upload manuscript to Supabase Storage: ${e.message}")
                }

                // 6. Generate and Upload Preview PDF to previews/{userId}/{bookId}/preview.pdf
                val previewStoragePath = "$userId/$bookId/preview.pdf"
                val fullPreviewPath = "previews/$previewStoragePath"
                onProgress("Generating first-$previewPages-pages preview PDF...")

                val previewTempFile = File(context.cacheDir, "preview_$bookId.pdf")
                val previewGenerated = generatePreviewPdf(manuscriptFile, previewTempFile, previewPages)
                val previewBytes = if (previewGenerated && previewTempFile.exists()) {
                    previewTempFile.readBytes()
                } else {
                    manuscriptBytes
                }

                onProgress("Uploading preview PDF to Supabase Storage...")
                try {
                    supabase.storage.from("previews").upload(
                        path = previewStoragePath,
                        data = previewBytes
                    ) {
                        upsert = true
                    }
                    Log.d("BookSphereRepo", "Preview uploaded: $fullPreviewPath")
                } catch (e: Exception) {
                    Log.w("BookSphereRepo", "Preview upload failed: ${e.message}")
                } finally {
                    try { previewTempFile.delete() } catch (_: Exception) {}
                }
            }

            // 7. Insert Book record into public.books
            onProgress("Registering publication in Supabase database...")
            val bookDto = BookDto(
                id = bookId,
                authorId = userId,
                title = title,
                authorName = authorName,
                description = description,
                genre = genre,
                language = language,
                price = finalPrice,
                isFree = isFree,
                coverPath = fullCoverPath,
                manuscriptPath = fullManuscriptPath,
                previewPath = "previews/$userId/$bookId/preview.pdf",
                totalPages = totalPages,
                previewPages = previewPages,
                samplePagesCount = previewPages,
                status = BookStatus.PUBLISHED.name,
                rating = 5.0,
                copiesSold = 0
            )

            try {
                supabase.from("books").insert(bookDto)
                Log.d("BookSphereRepo", "Inserted book $bookId into public.books")
            } catch (e: Exception) {
                Log.e("BookSphereRepo", "Failed to insert book record into public.books: ${e.message}", e)
                throw IllegalStateException("Failed to register book in Supabase database: ${e.message}")
            }

            val publishedBook = dtoToBook(bookDto).copy(
                coverUrl = resolvedCoverUrl
            )

            // Cache in local Room
            bookDao.insertBook(publishedBook.toBookEntity())

            onProgress("Book successfully published!")
            publishedBook
        }
    }

    private fun generatePreviewPdf(inputFile: File, outputFile: File, previewPages: Int): Boolean {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var pdfDoc: PdfDocument? = null
        return try {
            pfd = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            pdfDoc = PdfDocument()
            val total = renderer.pageCount
            val pagesToExtract = previewPages.coerceAtLeast(1).coerceAtMost(total)
            for (i in 0 until pagesToExtract) {
                val page = renderer.openPage(i)
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, i + 1).create()
                val docPage = pdfDoc.startPage(pageInfo)
                docPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                pdfDoc.finishPage(docPage)
                bitmap.recycle()
            }
            FileOutputStream(outputFile).use { out ->
                pdfDoc.writeTo(out)
            }
            true
        } catch (e: Exception) {
            Log.e("BookSphereRepo", "Error generating preview PDF", e)
            false
        } finally {
            try { pdfDoc?.close() } catch (_: Exception) {}
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    private fun dtoToBook(dto: BookDto): Book {
        val coverUrl = resolveStorageUrl("covers", dto.coverPath) ?: SampleData.sampleCelestialCartographer.coverUrl
        return Book(
            id = dto.id,
            title = dto.title,
            author = dto.authorName ?: "Independent Author",
            coverUrl = coverUrl,
            price = dto.price,
            rating = dto.rating,
            genre = dto.genre ?: "Fiction",
            description = dto.description ?: "",
            language = dto.language ?: "English",
            authorId = dto.authorId,
            coverPath = dto.coverPath,
            manuscriptPath = dto.manuscriptPath,
            previewPath = dto.previewPath,
            totalPages = dto.totalPages,
            previewPages = dto.previewPages,
            samplePagesCount = dto.samplePagesCount,
            isPurchased = false,
            isFree = dto.isFree || dto.price == 0.0,
            copiesSold = dto.copiesSold,
            status = dto.status
        )
    }

    fun resolveStorageUrl(bucket: String, storagePath: String?): String? {
        if (storagePath.isNullOrBlank()) return null
        if (storagePath.startsWith("http://") || storagePath.startsWith("https://")) {
            return storagePath
        }
        val cleanPath = if (storagePath.startsWith("$bucket/")) {
            storagePath.removePrefix("$bucket/")
        } else {
            storagePath
        }
        return try {
            supabase.storage.from(bucket).publicUrl(cleanPath)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getManuscriptAccessUrl(book: Book, isPurchased: Boolean): String? {
        if (book.isFree || isPurchased) {
            // Full manuscript
            val path = book.manuscriptPath ?: return book.pdfUri
            val cleanPath = if (path.startsWith("manuscripts/")) path.removePrefix("manuscripts/") else path
            return try {
                supabase.storage.from("manuscripts").createSignedUrl(cleanPath, 3600.seconds)
            } catch (_: Exception) {
                supabase.storage.from("manuscripts").publicUrl(cleanPath)
            }
        } else {
            // Preview only
            val path = book.previewPath ?: return null
            val cleanPath = if (path.startsWith("previews/")) path.removePrefix("previews/") else path
            return try {
                supabase.storage.from("previews").publicUrl(cleanPath)
            } catch (_: Exception) {
                null
            }
        }
    }
}
