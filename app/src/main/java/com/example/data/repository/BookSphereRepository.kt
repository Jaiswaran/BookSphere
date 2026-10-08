package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.data.SampleData
import com.example.data.local.BookDao
import com.example.data.local.BookEntity
import com.example.data.local.CachedUserProfileDao
import com.example.data.local.CachedUserProfileEntity
import com.example.data.local.toBookEntity
import com.example.data.remote.*
import com.example.model.Book
import com.example.model.BookAccessTarget
import com.example.model.BookStatus
import com.example.model.DataState
import com.example.model.PublicationStage
import com.example.model.ReadingList
import com.example.model.ResolvedBookAccess
import com.example.model.SamplePage
import com.example.model.UserProfile
import com.example.model.UserRole
import com.example.util.AppError
import com.example.util.AppLogger
import com.example.util.PublicationValidator
import com.example.util.SamplePdfHelper
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
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
    // Room Cache Flows
    val allPublishedBooks: Flow<List<BookEntity>> = bookDao.getAllPublishedBooks()
    val activeProfileCache: Flow<CachedUserProfileEntity?> = profileDao.getActiveProfile()

    private val _currentProfile = MutableStateFlow<UserProfile?>(null)
    val currentProfile: StateFlow<UserProfile?> = _currentProfile.asStateFlow()

    suspend fun initializeAuthObserver() = withContext(Dispatchers.IO) {
        try {
            // Restore from active profile cache first
            val cached = profileDao.getActiveProfile().firstOrNull()
            if (cached != null && _currentProfile.value == null) {
                val parsedLists = parseReadingListsJson(cached.readingListsJson)
                _currentProfile.value = UserProfile(
                    id = cached.id,
                    name = cached.name ?: "User",
                    email = cached.email ?: "",
                    role = if (cached.role.equals("AUTHOR", ignoreCase = true)) UserRole.AUTHOR else UserRole.READER,
                    photoUrl = cached.photoUrl,
                    bio = cached.bio ?: "Passionate literary enthusiast, avid reader of speculative fiction & philosophy.",
                    readingLists = if (parsedLists.isNotEmpty()) parsedLists else createDefaultReadingLists()
                )
            }

            // Sync with Supabase Auth state
            val user = supabase.auth.currentUserOrNull()
            if (user != null) {
                refreshCurrentUserProfile()
            }
        } catch (e: Exception) {
            AppLogger.w("BookSphereRepo", "Auth initialization warning: ${e.message}")
        }
    }

    fun startSessionStatusObserver(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                supabase.auth.sessionStatus.collect { status ->
                    when (status) {
                        is SessionStatus.Authenticated -> {
                            AppLogger.d("BookSphereRepo", "Supabase session authenticated: ${status.session.user?.id}")
                            refreshCurrentUserProfile()
                        }
                        is SessionStatus.NotAuthenticated -> {
                            if (status.isSignOut) {
                                AppLogger.d("BookSphereRepo", "Supabase signed out")
                                profileDao.clearActive()
                                _currentProfile.value = null
                            }
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                AppLogger.w("BookSphereRepo", "SessionStatus observer error: ${e.message}")
            }
        }
    }

    // ==========================================
    // DISCOVER CATALOG WITH EXPLICIT STATES
    // ==========================================

    suspend fun fetchPublishedBooks(): DataState<List<Book>> = withContext(Dispatchers.IO) {
        try {
            val dtoList = supabase.from("books")
                .select {
                    filter {
                        eq("status", "PUBLISHED")
                    }
                }
                .decodeList<BookDto>()

            if (dtoList.isNotEmpty()) {
                val remoteBooks = dtoList.map { dtoToBook(it) }
                // Update local Room cache
                bookDao.insertBooks(remoteBooks.map { it.toBookEntity() })
                AppLogger.d("BookSphereRepo", "Fetched ${remoteBooks.size} published books from Supabase")
                DataState.Success(remoteBooks)
            } else {
                // Legitimate empty catalog from remote database
                AppLogger.d("BookSphereRepo", "Supabase published books returned 0 records (empty catalog)")
                DataState.Empty
            }
        } catch (e: Exception) {
            AppLogger.w("BookSphereRepo", "Network error fetching books from Supabase: ${e.message}")
            // Check Room local cache
            val cachedEntities = bookDao.getAllPublishedBooks().firstOrNull() ?: emptyList()
            if (cachedEntities.isNotEmpty()) {
                val cachedBooks = cachedEntities.map { it.toBook() }
                AppLogger.d("BookSphereRepo", "Falling back to ${cachedBooks.size} cached books from Room")
                DataState.Offline(cachedBooks)
            } else {
                val appError = AppError.from(e, "Could not load book catalog. Please check your internet connection.")
                DataState.Error(appError)
            }
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
            AppLogger.w("BookSphereRepo", "Error fetching author books: ${e.message}")
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
                    AppLogger.w("BookSphereRepo", "Error loading library book $id: ${ex.message}")
                }
            }
            books
        } catch (e: Exception) {
            AppLogger.w("BookSphereRepo", "Error querying public.library: ${e.message}")
            emptyList()
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
            val normalizedEmail = email.trim()
            val normalizedName = name.trim().ifBlank { normalizedEmail.substringBefore("@") }
            val roleName = if (role == UserRole.AUTHOR) "AUTHOR" else "READER"
            AppLogger.d("BookSphereRepo", "Initiating signup with Supabase: role=$roleName, email=$normalizedEmail")

            supabase.auth.signUpWith(Email) {
                this.email = normalizedEmail
                this.password = pass
                this.data = buildJsonObject {
                    put("name", normalizedName)
                    put("role", roleName)
                }
            }

            val session = supabase.auth.currentSessionOrNull()
            val user = supabase.auth.currentUserOrNull()

            if (session == null || user == null) {
                // Email confirmation is required by Supabase Auth configuration
                throw IllegalStateException("Confirmation link sent to $normalizedEmail. Please check your inbox and verify your email to log in.")
            }

            // Session exists immediately (e.g. email confirmation disabled or auto-confirmed)
            // Load the existing profile created by the database trigger (handle_new_user)
            var profile: UserProfile? = null
            for (attempt in 1..5) {
                try {
                    profile = loadProfileFromPostgres(user.id)
                    if (profile != null) break
                } catch (e: Exception) {
                    AppLogger.w("BookSphereRepo", "Attempt $attempt loading profile after signup: ${e.message}")
                }
                delay(200L * attempt)
            }

            val finalProfile = profile ?: UserProfile(
                id = user.id,
                name = normalizedName,
                email = user.email ?: normalizedEmail,
                role = role
            )

            cacheProfileLocally(finalProfile)
            _currentProfile.value = finalProfile
            finalProfile
        }
    }

    suspend fun signInWithEmail(
        email: String,
        pass: String
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedEmail = email.trim()
            AppLogger.d("BookSphereRepo", "Signing in with email: $normalizedEmail")
            supabase.auth.signInWith(Email) {
                this.email = normalizedEmail
                this.password = pass
            }

            val user = supabase.auth.currentUserOrNull()
                ?: throw IllegalStateException("Authentication succeeded but no active session found.")

            var profile: UserProfile? = null
            for (attempt in 1..3) {
                try {
                    profile = loadProfileFromPostgres(user.id)
                    if (profile != null) break
                } catch (e: Exception) {
                    AppLogger.w("BookSphereRepo", "Attempt $attempt loading profile: ${e.message}")
                }
                delay(150L * attempt)
            }

            val finalProfile = profile ?: run {
                val roleStr = user.userMetadata?.get("role")?.toString()?.trim('"') ?: "READER"
                val role = if (roleStr.equals("AUTHOR", ignoreCase = true)) UserRole.AUTHOR else UserRole.READER
                val displayName = user.userMetadata?.get("name")?.toString()?.trim('"')
                    ?: user.userMetadata?.get("full_name")?.toString()?.trim('"')
                    ?: normalizedEmail.substringBefore("@")
                UserProfile(
                    id = user.id,
                    name = displayName,
                    email = user.email ?: normalizedEmail,
                    role = role
                )
            }

            cacheProfileLocally(finalProfile)
            _currentProfile.value = finalProfile
            finalProfile
        }
    }

    suspend fun signInWithGoogleIdToken(
        idToken: String,
        preferredRole: UserRole = UserRole.READER
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        runCatching {
            AppLogger.d("BookSphereRepo", "Authenticating via Google ID Token")
            supabase.auth.signInWith(IDToken) {
                provider = Google
                this.idToken = idToken
            }

            val user = supabase.auth.currentUserOrNull()
                ?: throw IllegalStateException("Supabase Google authentication failed")

            var profile: UserProfile? = null
            for (attempt in 1..3) {
                try {
                    profile = loadProfileFromPostgres(user.id)
                    if (profile != null) break
                } catch (e: Exception) {
                    AppLogger.w("BookSphereRepo", "Attempt $attempt loading Google user profile: ${e.message}")
                }
                delay(150L * attempt)
            }

            val finalProfile = profile ?: UserProfile(
                id = user.id,
                name = user.userMetadata?.get("full_name")?.toString()?.trim('"')
                    ?: user.email?.substringBefore("@") ?: "Google Reader",
                email = user.email ?: "",
                role = preferredRole,
                photoUrl = user.userMetadata?.get("avatar_url")?.toString()?.trim('"')
            )

            cacheProfileLocally(finalProfile)
            _currentProfile.value = finalProfile
            finalProfile
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            supabase.auth.signOut()
            AppLogger.d("BookSphereRepo", "Successfully signed out of Supabase Auth")
        } catch (e: Exception) {
            AppLogger.w("BookSphereRepo", "Error signing out: ${e.message}")
        }
        profileDao.clearActive()
        _currentProfile.value = null
    }

    suspend fun refreshCurrentUserProfile(): UserProfile? = withContext(Dispatchers.IO) {
        try {
            val user = supabase.auth.currentUserOrNull() ?: return@withContext null
            val profile = loadProfileFromPostgres(user.id) ?: run {
                val roleStr = user.userMetadata?.get("role")?.toString()?.trim('"') ?: "READER"
                val role = if (roleStr.equals("AUTHOR", ignoreCase = true)) UserRole.AUTHOR else UserRole.READER
                val name = user.userMetadata?.get("name")?.toString()?.trim('"')
                    ?: user.userMetadata?.get("full_name")?.toString()?.trim('"')
                    ?: user.email?.substringBefore("@") ?: "Reader"
                UserProfile(
                    id = user.id,
                    name = name,
                    email = user.email ?: "",
                    role = role
                )
            }
            cacheProfileLocally(profile)
            _currentProfile.value = profile
            profile
        } catch (e: Exception) {
            AppLogger.w("BookSphereRepo", "Failed to refresh profile: ${e.message}")
            null
        }
    }

    private fun parseReadingListsJson(json: String?): List<ReadingList> {
        if (json.isNullOrBlank() || json == "[]") return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            val list = mutableListOf<ReadingList>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val bookIdsArr = obj.optJSONArray("bookIds")
                val bookIds = mutableListOf<String>()
                if (bookIdsArr != null) {
                    for (b in 0 until bookIdsArr.length()) {
                        bookIds.add(bookIdsArr.getString(b))
                    }
                }
                list.add(
                    ReadingList(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.optString("name", "Reading List"),
                        description = obj.optString("description", ""),
                        bookIds = bookIds,
                        isPublic = obj.optBoolean("isPublic", true),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun readingListsToJson(lists: List<ReadingList>): String {
        val arr = org.json.JSONArray()
        for (item in lists) {
            val obj = org.json.JSONObject()
            obj.put("id", item.id)
            obj.put("name", item.name)
            obj.put("description", item.description)
            obj.put("isPublic", item.isPublic)
            obj.put("createdAt", item.createdAt)
            val booksArr = org.json.JSONArray()
            item.bookIds.forEach { booksArr.put(it) }
            obj.put("bookIds", booksArr)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun createDefaultReadingLists(): List<ReadingList> {
        return listOf(
            ReadingList(
                id = "list_must_reads",
                name = "Essential Speculative Fiction",
                description = "Masterworks exploring existential voyages and cosmic discoveries.",
                bookIds = listOf("sample_celestial_cartographer", "sample_quantum_monk"),
                isPublic = true
            ),
            ReadingList(
                id = "list_weekend",
                name = "Weekend Deep Dives",
                description = "Shorter, thought-provoking novellas to complete in one seating.",
                bookIds = listOf("sample_clockwork_alchemist", "sample_subterranean_silence"),
                isPublic = true
            ),
            ReadingList(
                id = "list_wishlist",
                name = "Anticipated Releases",
                description = "Upcoming independent books directly supporting authors.",
                bookIds = listOf("sample_nebula_loom", "sample_echoes_of_the_abyss"),
                isPublic = false
            )
        )
    }

    private suspend fun loadProfileFromPostgres(userId: String): UserProfile? {
        val dto = try {
            supabase.from("profiles")
                .select {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeSingleOrNull<ProfileDto>()
        } catch (e: Exception) {
            AppLogger.e("BookSphereRepo", "Error querying public.profiles for user $userId: ${e.message}", e)
            throw e
        }

        return dto?.let {
            val cached = profileDao.getActiveProfile().firstOrNull()
            val cachedBio = if (cached?.id == userId) cached.bio else null
            val cachedLists = if (cached?.id == userId) parseReadingListsJson(cached.readingListsJson) else emptyList()

            val isAuthorRole = it.role.equals("AUTHOR", ignoreCase = true) ||
                    it.role.equals("ADMIN", ignoreCase = true)
            UserProfile(
                id = it.id,
                name = it.name ?: it.email?.substringBefore("@") ?: "User",
                email = it.email ?: "",
                role = if (isAuthorRole) UserRole.AUTHOR else UserRole.READER,
                photoUrl = it.photoUrl,
                bio = cachedBio ?: "Passionate literary enthusiast, avid reader of speculative fiction & philosophy.",
                readingLists = if (cachedLists.isNotEmpty()) cachedLists else createDefaultReadingLists()
            )
        }
    }

    suspend fun requestAuthorVerification(penName: String, bio: String): Result<UserProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val user = supabase.auth.currentUserOrNull()
                ?: throw IllegalStateException("You must be logged in to apply for Author verification.")

            try {
                supabase.postgrest.rpc(
                    function = "request_author_verification",
                    parameters = buildJsonObject {
                        put("p_pen_name", penName)
                        put("p_bio", bio)
                    }
                )
            } catch (e: Exception) {
                AppLogger.w("BookSphereRepo", "RPC request_author_verification: ${e.message}")
            }

            val refreshed = refreshCurrentUserProfile() ?: _currentProfile.value?.copy(role = UserRole.AUTHOR)
                ?: throw IllegalStateException("Could not reload profile after verification request")
            refreshed
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
                    bio = profile.bio,
                    readingListsJson = readingListsToJson(profile.readingLists),
                    isActive = true
                )
            )
        } catch (e: Exception) {
            AppLogger.w("BookSphereRepo", "Error saving cached profile: ${e.message}")
        }
    }

    suspend fun updateProfile(
        name: String,
        bio: String,
        photoUrl: String?,
        readingLists: List<ReadingList>
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val current = _currentProfile.value ?: UserProfile(
                id = UUID.randomUUID().toString(),
                name = name,
                email = "guest@booksphere.literary",
                role = UserRole.READER,
                photoUrl = photoUrl,
                bio = bio,
                readingLists = readingLists
            )
            val updated = current.copy(
                name = name,
                bio = bio,
                photoUrl = photoUrl ?: current.photoUrl,
                readingLists = readingLists
            )
            cacheProfileLocally(updated)

            val authUser = supabase.auth.currentUserOrNull()
            if (authUser != null) {
                try {
                    supabase.from("profiles").update(
                        ProfileUpdateDto(
                            name = updated.name,
                            photoUrl = updated.photoUrl,
                            updatedAt = java.time.Instant.now().toString()
                        )
                    ) {
                        filter {
                            eq("id", authUser.id)
                        }
                    }
                    AppLogger.d("BookSphereRepo", "Updated public.profiles for user ${authUser.id}")
                } catch (e: Exception) {
                    AppLogger.w("BookSphereRepo", "Failed to update profile in public.profiles: ${e.message}")
                }
            }

            _currentProfile.value = updated
            updated
        }
    }

    suspend fun uploadProfilePhoto(imageFile: File): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = "profile_${UUID.randomUUID()}.jpg"
            val fileBytes = imageFile.readBytes()
            try {
                supabase.storage.from("avatars").upload(fileName, fileBytes) {
                    upsert = true
                }
                val publicUrl = supabase.storage.from("avatars").publicUrl(fileName)
                publicUrl
            } catch (e: Exception) {
                AppLogger.w("BookSphereRepo", "Storage upload failed: ${e.message}")
                Uri.fromFile(imageFile).toString()
            }
        }
    }

    // ==========================================
    // PURCHASES & ENTITLEMENTS
    // ==========================================

    suspend fun recordPurchase(userId: String, book: Book): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            try {
                supabase.postgrest.rpc(
                    function = "purchase_book_entitlement",
                    parameters = buildJsonObject {
                        put("p_book_id", book.id)
                    }
                )
                AppLogger.d("BookSphereRepo", "Recorded purchase via secure RPC for book ${book.id}")
            } catch (e: Exception) {
                AppLogger.w("BookSphereRepo", "RPC purchase_book_entitlement fallback: ${e.message}")
                val currentAuth = supabase.auth.currentUserOrNull()
                val targetUserId = currentAuth?.id ?: userId
                val libraryDto = LibraryDto(
                    userId = targetUserId,
                    bookId = book.id,
                    purchasedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date()),
                    progress = 0.0,
                    lastPage = 1,
                    completed = false
                )
                supabase.from("library").upsert(libraryDto)
            }
            Unit
        }
    }

    suspend fun addFreeBookToLibrary(userId: String, book: Book): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            try {
                supabase.postgrest.rpc(
                    function = "add_free_book_entitlement",
                    parameters = buildJsonObject {
                        put("p_book_id", book.id)
                    }
                )
                AppLogger.d("BookSphereRepo", "Added free book via secure RPC for book ${book.id}")
            } catch (e: Exception) {
                AppLogger.w("BookSphereRepo", "RPC add_free_book_entitlement fallback: ${e.message}")
                val currentAuth = supabase.auth.currentUserOrNull()
                val targetUserId = currentAuth?.id ?: userId
                val libraryDto = LibraryDto(
                    userId = targetUserId,
                    bookId = book.id,
                    purchasedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date()),
                    progress = 0.0,
                    lastPage = 1,
                    completed = false
                )
                supabase.from("library").upsert(libraryDto)
            }
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
            try {
                supabase.postgrest.rpc(
                    function = "update_reading_progress",
                    parameters = buildJsonObject {
                        put("p_book_id", bookId)
                        put("p_last_page", lastPage)
                        put("p_progress", progress)
                        put("p_completed", completed)
                    }
                )
            } catch (e: Exception) {
                AppLogger.w("BookSphereRepo", "RPC update_reading_progress fallback: ${e.message}")
                val currentAuth = supabase.auth.currentUserOrNull()
                val targetUserId = currentAuth?.id ?: userId
                supabase.from("library").update({
                    set("last_page", lastPage)
                    set("progress", progress)
                    set("completed", completed)
                }) {
                    filter {
                        eq("user_id", targetUserId)
                        eq("book_id", bookId)
                    }
                }
            }
            Unit
        }
    }

    // ==========================================
    // PUBLISHING LIFECYCLE & STORAGE
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
        existingBookId: String? = null,
        onProgress: (PublicationStage) -> Unit
    ): Result<Book> = withContext(Dispatchers.IO) {
        val bookId = existingBookId?.trim()?.ifBlank { null } ?: UUID.randomUUID().toString()
        val uploadedFiles = mutableListOf<Pair<String, String>>()
        var currentStageName = "Validation"
        var currentUserId: String? = null

        try {
            // 1. Validation
            currentStageName = "Validation"
            onProgress(PublicationStage.Validating)

            var totalPages = 100
            if (manuscriptFile != null && manuscriptFile.exists()) {
                val manuscriptInfo = PublicationValidator.inspectManuscriptPdf(manuscriptFile)
                if (!manuscriptInfo.isValid) {
                    throw IllegalArgumentException(manuscriptInfo.errorMessage ?: "Invalid manuscript PDF.")
                }
                totalPages = manuscriptInfo.pageCount
            }

            val metaResult = PublicationValidator.validateMetadata(
                title = title,
                author = author,
                description = description,
                genre = genre,
                language = language,
                price = price,
                isFree = isFree,
                previewPages = previewPages,
                totalPages = totalPages
            )
            if (!metaResult.isSuccess) {
                throw IllegalArgumentException(metaResult.errorMessage ?: "Metadata validation failed.")
            }

            val coverResult = PublicationValidator.validateCover(coverBytes)
            if (!coverResult.isSuccess) {
                throw IllegalArgumentException(coverResult.errorMessage ?: "Cover validation failed.")
            }

            coroutineContext.ensureActive()

            // 2. Authorization
            currentStageName = "Authorization"
            val currentUser = supabase.auth.currentUserOrNull()
                ?: throw IllegalStateException("You must be logged in to publish a book.")
            val userId = currentUser.id
            currentUserId = userId

            val profile = loadProfileFromPostgres(userId) ?: _currentProfile.value
                ?: throw IllegalStateException("Could not verify author profile.")

            if (profile.role != UserRole.AUTHOR) {
                throw IllegalStateException("Permission Denied: Only verified Authors can publish books.")
            }

            val authorName = author.trim().ifBlank { profile.name }
            val finalPrice = if (isFree) 0.0 else price

            coroutineContext.ensureActive()

            // 3. Register Draft Record
            currentStageName = "Draft Creation"
            onProgress(PublicationStage.CreatingDraft(bookId))

            val draftDto = BookDto(
                id = bookId,
                authorId = userId,
                title = title.trim(),
                authorName = authorName,
                description = description.trim(),
                genre = genre.trim(),
                language = language.trim(),
                price = finalPrice,
                isFree = isFree,
                totalPages = totalPages,
                previewPages = previewPages,
                samplePagesCount = previewPages,
                status = BookStatus.UPLOADING.name,
                rating = 5.0,
                copiesSold = 0
            )

            try {
                supabase.from("books").upsert(draftDto)
                AppLogger.d("BookSphereRepo", "Registered draft $bookId in Supabase (status=UPLOADING)")
            } catch (e: Exception) {
                AppLogger.e("BookSphereRepo", "Failed to register draft book in database: ${e.message}", e)
                throw IllegalStateException("Failed to initialize publication in Supabase database: ${e.message}")
            }

            coroutineContext.ensureActive()

            // 4. Upload Cover
            val coverStoragePath = "$userId/$bookId.jpg"
            val fullCoverPath = "covers/$coverStoragePath"
            var resolvedCoverUrl = SampleData.sampleCelestialCartographer.coverUrl

            if (coverBytes != null && coverBytes.isNotEmpty()) {
                currentStageName = "Uploading Cover"
                onProgress(PublicationStage.UploadingCover(bookId))
                try {
                    supabase.storage.from("covers").upload(
                        path = coverStoragePath,
                        data = coverBytes
                    ) {
                        upsert = true
                    }
                    uploadedFiles.add("covers" to coverStoragePath)
                    resolvedCoverUrl = supabase.storage.from("covers").publicUrl(coverStoragePath)
                    AppLogger.d("BookSphereRepo", "Cover uploaded: $resolvedCoverUrl")
                } catch (e: Exception) {
                    AppLogger.e("BookSphereRepo", "Failed to upload cover: ${e.message}", e)
                    throw IllegalStateException("Failed to upload cover art to Supabase Storage: ${e.message}")
                }
            }

            coroutineContext.ensureActive()

            // 5. Upload Manuscript
            val manuscriptStoragePath = "$userId/$bookId/manuscript.pdf"
            val fullManuscriptPath = "manuscripts/$manuscriptStoragePath"

            if (manuscriptFile != null && manuscriptFile.exists()) {
                currentStageName = "Uploading Manuscript"
                onProgress(PublicationStage.UploadingManuscript(bookId))
                try {
                    val manuscriptBytes = manuscriptFile.readBytes()
                    supabase.storage.from("manuscripts").upload(
                        path = manuscriptStoragePath,
                        data = manuscriptBytes
                    ) {
                        upsert = true
                    }
                    uploadedFiles.add("manuscripts" to manuscriptStoragePath)
                    AppLogger.d("BookSphereRepo", "Manuscript uploaded: $fullManuscriptPath")
                } catch (e: Exception) {
                    AppLogger.e("BookSphereRepo", "Failed to upload manuscript: ${e.message}", e)
                    throw IllegalStateException("Failed to upload manuscript to Supabase Storage: ${e.message}")
                }

                coroutineContext.ensureActive()

                // 6. Generate Preview PDF
                currentStageName = "Generating Preview"
                onProgress(PublicationStage.GeneratingPreview(bookId, previewPages))

                val previewTempFile = File(context.cacheDir, "preview_$bookId.pdf")
                val previewGenerated = generatePreviewPdf(manuscriptFile, previewTempFile, previewPages)
                if (!previewGenerated || !previewTempFile.exists() || previewTempFile.length() == 0L) {
                    throw IllegalStateException("Failed to generate preview PDF ($previewPages pages) from manuscript.")
                }

                coroutineContext.ensureActive()

                // 7. Upload Preview PDF
                currentStageName = "Uploading Preview"
                onProgress(PublicationStage.UploadingPreview(bookId))
                val previewStoragePath = "$userId/$bookId/preview.pdf"
                try {
                    val previewBytes = previewTempFile.readBytes()
                    supabase.storage.from("previews").upload(
                        path = previewStoragePath,
                        data = previewBytes
                    ) {
                        upsert = true
                    }
                    uploadedFiles.add("previews" to previewStoragePath)
                    AppLogger.d("BookSphereRepo", "Preview uploaded: previews/$previewStoragePath")
                } catch (e: Exception) {
                    AppLogger.e("BookSphereRepo", "Preview upload failed: ${e.message}", e)
                    throw IllegalStateException("Failed to upload preview PDF to Supabase Storage: ${e.message}")
                } finally {
                    try { previewTempFile.delete() } catch (_: Exception) {}
                }
            }

            coroutineContext.ensureActive()

            // 8. Finalize Publication
            currentStageName = "Finalizing"
            onProgress(PublicationStage.Finalizing(bookId))

            val finalizedDto = BookDto(
                id = bookId,
                authorId = userId,
                title = title.trim(),
                authorName = authorName,
                description = description.trim(),
                genre = genre.trim(),
                language = language.trim(),
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
                supabase.from("books").upsert(finalizedDto)
                AppLogger.d("BookSphereRepo", "Publication finalized for book $bookId (status=PUBLISHED)")
            } catch (e: Exception) {
                AppLogger.e("BookSphereRepo", "Failed to finalize publication in Supabase: ${e.message}", e)
                throw IllegalStateException("Failed to finalize publication in Supabase database: ${e.message}")
            }

            val publishedBook = dtoToBook(finalizedDto).copy(
                coverUrl = resolvedCoverUrl
            )

            // Cache in local Room
            bookDao.insertBook(publishedBook.toBookEntity())

            onProgress(PublicationStage.Completed(publishedBook))
            Result.success(publishedBook)
        } catch (e: kotlinx.coroutines.CancellationException) {
            AppLogger.w("BookSphereRepo", "Publication cancelled by user for book $bookId")
            performPublishingCleanup(bookId, currentUserId, uploadedFiles)
            onProgress(PublicationStage.Cancelled)
            throw e
        } catch (e: Exception) {
            AppLogger.e("BookSphereRepo", "Publication failed at stage '$currentStageName': ${e.message}", e)
            performPublishingCleanup(bookId, currentUserId, uploadedFiles)
            onProgress(
                PublicationStage.Failed(
                    stage = currentStageName,
                    error = e.message ?: "Unknown publishing error",
                    canRetry = true,
                    bookId = bookId
                )
            )
            Result.failure(e)
        }
    }

    private suspend fun performPublishingCleanup(
        bookId: String,
        userId: String?,
        uploadedFiles: List<Pair<String, String>>
    ) {
        if (userId != null) {
            try {
                supabase.from("books").update({
                    set("status", BookStatus.FAILED.name)
                }) {
                    filter {
                        eq("id", bookId)
                        eq("author_id", userId)
                    }
                }
                AppLogger.d("BookSphereRepo", "Updated book $bookId status to FAILED in Supabase")
            } catch (dbEx: Exception) {
                AppLogger.w("BookSphereRepo", "Could not mark book $bookId as FAILED: ${dbEx.message}")
            }
        }

        for ((bucket, path) in uploadedFiles) {
            try {
                supabase.storage.from(bucket).delete(listOf(path))
                AppLogger.d("BookSphereRepo", "Cleaned up orphaned storage asset: $bucket/$path")
            } catch (stEx: Exception) {
                AppLogger.w("BookSphereRepo", "Storage cleanup warning for $bucket/$path: ${stEx.message}")
            }
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
                val targetWidth = page.width.coerceAtMost(1080)
                val targetHeight = ((page.height.toFloat() / page.width.toFloat()) * targetWidth).toInt().coerceAtLeast(100)

                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, i + 1).create()
                val docPage = pdfDoc.startPage(pageInfo)
                val destRect = android.graphics.Rect(0, 0, 595, 842)
                docPage.canvas.drawBitmap(bitmap, null, destRect, null)
                pdfDoc.finishPage(docPage)

                bitmap.recycle()
            }

            FileOutputStream(outputFile).use { out ->
                pdfDoc.writeTo(out)
            }
            true
        } catch (e: Exception) {
            AppLogger.e("BookSphereRepo", "Error generating preview PDF: ${e.message}", e)
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

    suspend fun verifyEntitlement(userId: String?, bookId: String): Boolean = withContext(Dispatchers.IO) {
        if (userId.isNullOrBlank()) return@withContext false
        try {
            val entry = supabase.from("library").select {
                filter {
                    eq("user_id", userId)
                    eq("book_id", bookId)
                }
            }.decodeSingleOrNull<LibraryDto>()
            if (entry != null) return@withContext true
        } catch (e: Exception) {
            AppLogger.w("BookSphereRepo", "Error verifying remote entitlement: ${e.message}")
        }
        try {
            val cached = bookDao.getBookById(bookId)
            if (cached?.isFree == true) return@withContext true
        } catch (_: Exception) {}
        false
    }

    suspend fun resolveBookAccess(book: Book, userId: String?, context: Context? = null): ResolvedBookAccess = withContext(Dispatchers.IO) {
        val isAuthor = userId != null && book.authorId != null && book.authorId == userId
        val isPurchasedOrEntitled = isAuthor || verifyEntitlement(userId, book.id)
        val hasFullAccess = book.isFree || isPurchasedOrEntitled

        if (hasFullAccess) {
            if (!book.manuscriptPath.isNullOrBlank()) {
                val path = book.manuscriptPath
                val cleanPath = if (path.startsWith("manuscripts/")) path.removePrefix("manuscripts/") else path
                val accessUrl = try {
                    supabase.storage.from("manuscripts").createSignedUrl(cleanPath, 3600.seconds)
                } catch (e: Exception) {
                    AppLogger.w("BookSphereRepo", "Could not create signed URL: ${e.message}")
                    null
                }
                if (accessUrl != null) {
                    return@withContext ResolvedBookAccess(
                        bookId = book.id,
                        target = BookAccessTarget.RemoteStorageUrl(accessUrl, isSigned = true),
                        isPreviewOnly = false,
                        allowedPages = book.totalPages,
                        totalPages = book.totalPages
                    )
                }
            }
            if (!book.pdfUri.isNullOrBlank()) {
                return@withContext ResolvedBookAccess(
                    bookId = book.id,
                    target = BookAccessTarget.LocalUri(book.pdfUri),
                    isPreviewOnly = false,
                    allowedPages = book.totalPages,
                    totalPages = book.totalPages
                )
            }
            if (context != null) {
                try {
                    val sampleFile = SamplePdfHelper.getOrCreateSamplePdf(context, book)
                    if (sampleFile.exists() && sampleFile.length() > 0L) {
                        return@withContext ResolvedBookAccess(
                            bookId = book.id,
                            target = BookAccessTarget.LocalUri(sampleFile.absolutePath),
                            isPreviewOnly = false,
                            allowedPages = book.totalPages,
                            totalPages = book.totalPages
                        )
                    }
                } catch (e: Exception) {
                    AppLogger.w("BookSphereRepo", "Sample PDF generation warning: ${e.message}")
                }
            }
            if (book.contentPages.isNotEmpty()) {
                return@withContext ResolvedBookAccess(
                    bookId = book.id,
                    target = BookAccessTarget.EmbeddedPages(book.contentPages),
                    isPreviewOnly = false,
                    allowedPages = book.contentPages.size,
                    totalPages = book.contentPages.size
                )
            }
            if (book.samplePages.isNotEmpty()) {
                val pagesText = formatSamplePagesToText(book.samplePages)
                return@withContext ResolvedBookAccess(
                    bookId = book.id,
                    target = BookAccessTarget.EmbeddedPages(pagesText),
                    isPreviewOnly = false,
                    allowedPages = pagesText.size,
                    totalPages = pagesText.size
                )
            }
        } else {
            val previewCount = if (book.previewPages > 0) book.previewPages else 3
            if (!book.previewPath.isNullOrBlank()) {
                val path = book.previewPath
                val cleanPath = if (path.startsWith("previews/")) path.removePrefix("previews/") else path
                val previewUrl = try {
                    supabase.storage.from("previews").publicUrl(cleanPath)
                } catch (e: Exception) {
                    null
                }
                if (!previewUrl.isNullOrBlank()) {
                    return@withContext ResolvedBookAccess(
                        bookId = book.id,
                        target = BookAccessTarget.RemoteStorageUrl(previewUrl, isSigned = false),
                        isPreviewOnly = true,
                        allowedPages = previewCount,
                        totalPages = book.totalPages
                    )
                }
            }
            if (!book.pdfUri.isNullOrBlank()) {
                return@withContext ResolvedBookAccess(
                    bookId = book.id,
                    target = BookAccessTarget.LocalUri(book.pdfUri),
                    isPreviewOnly = true,
                    allowedPages = previewCount,
                    totalPages = book.totalPages
                )
            }
            if (context != null) {
                try {
                    val sampleFile = SamplePdfHelper.getOrCreateSamplePdf(context, book)
                    if (sampleFile.exists() && sampleFile.length() > 0L) {
                        return@withContext ResolvedBookAccess(
                            bookId = book.id,
                            target = BookAccessTarget.LocalUri(sampleFile.absolutePath),
                            isPreviewOnly = true,
                            allowedPages = previewCount,
                            totalPages = book.totalPages
                        )
                    }
                } catch (e: Exception) {
                    AppLogger.w("BookSphereRepo", "Sample preview PDF generation warning: ${e.message}")
                }
            }
            if (book.contentPages.isNotEmpty()) {
                return@withContext ResolvedBookAccess(
                    bookId = book.id,
                    target = BookAccessTarget.EmbeddedPages(book.contentPages.take(previewCount)),
                    isPreviewOnly = true,
                    allowedPages = previewCount,
                    totalPages = book.contentPages.size
                )
            }
            if (book.samplePages.isNotEmpty()) {
                val pagesText = formatSamplePagesToText(book.samplePages)
                return@withContext ResolvedBookAccess(
                    bookId = book.id,
                    target = BookAccessTarget.EmbeddedPages(pagesText.take(previewCount)),
                    isPreviewOnly = true,
                    allowedPages = previewCount,
                    totalPages = pagesText.size
                )
            }
        }

        ResolvedBookAccess(
            bookId = book.id,
            target = BookAccessTarget.EmbeddedPages(emptyList()),
            isPreviewOnly = !hasFullAccess,
            allowedPages = 0,
            totalPages = 0
        )
    }

    private fun formatSamplePagesToText(samplePages: List<SamplePage>): List<String> {
        return samplePages.map { sp ->
            buildString {
                appendLine(sp.chapterTitle)
                appendLine()
                if (sp.dropCapLetter.isNotBlank()) {
                    append(sp.dropCapLetter)
                    append(sp.firstSentenceRemainder)
                    appendLine()
                    appendLine()
                }
                sp.paragraphs.forEach { p ->
                    appendLine(p)
                    appendLine()
                }
                sp.footnote?.let { fn ->
                    appendLine("---")
                    appendLine(fn)
                }
            }
        }
    }
}
