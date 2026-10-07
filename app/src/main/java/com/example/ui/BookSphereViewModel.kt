package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SampleData
import com.example.data.local.AppDatabase
import com.example.data.local.BookEntity
import com.example.data.repository.BookSphereRepository
import com.example.model.*
import com.example.util.AppError
import com.example.util.AppLogger
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class BookSphereViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = BookSphereRepository(
        bookDao = database.bookDao(),
        profileDao = database.cachedUserProfileDao()
    )

    // Current Supabase user profile
    val currentProfile: StateFlow<UserProfile?> = repository.currentProfile

    // User role derived from real Supabase Profile (default READER)
    val userRole: StateFlow<UserRole> = currentProfile
        .map { it?.role ?: UserRole.READER }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserRole.READER)

    // Room Database Backed Published Books (Offline/Cache Layer)
    val publishedBooks: StateFlow<List<BookEntity>> = repository.allPublishedBooks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Discover Data State: Loading | Success | Empty | Offline | Error
    private val _discoverDataState = MutableStateFlow<DataState<List<Book>>>(DataState.Loading)
    val discoverDataState: StateFlow<DataState<List<Book>>> = _discoverDataState.asStateFlow()

    // Active books from Supabase Postgres for Discover
    private val _supabaseBooks = MutableStateFlow<List<Book>>(emptyList())
    val supabaseBooks: StateFlow<List<Book>> = _supabaseBooks.asStateFlow()

    // Author Studio books (only books published by authenticated author)
    private val _authorWorks = MutableStateFlow<List<PublishedWork>>(emptyList())
    val authorWorks: StateFlow<List<PublishedWork>> = _authorWorks.asStateFlow()

    // Author Stats computed from real author books
    private val _authorStats = MutableStateFlow(AuthorStats())
    val authorStats: StateFlow<AuthorStats> = _authorStats.asStateFlow()

    // Library books loaded from public.library for current authenticated user
    private val _libraryBooks = MutableStateFlow<List<Book>>(SampleData.libraryBooks)
    val libraryBooks: StateFlow<List<Book>> = _libraryBooks.asStateFlow()

    // Navigation and UI Dialog States
    private val _currentTab = MutableStateFlow(ScreenTab.DISCOVER)
    val currentTab: StateFlow<ScreenTab> = _currentTab.asStateFlow()

    private val _selectedPreviewBook = MutableStateFlow(SampleData.sampleCelestialCartographer)
    val selectedPreviewBook: StateFlow<Book> = _selectedPreviewBook.asStateFlow()

    private val _checkoutBook = MutableStateFlow<Book?>(null)
    val checkoutBook: StateFlow<Book?> = _checkoutBook.asStateFlow()

    private val _showAdminMetrics = MutableStateFlow(false)
    val showAdminMetrics: StateFlow<Boolean> = _showAdminMetrics.asStateFlow()

    private val _showSearchDialog = MutableStateFlow(false)
    val showSearchDialog: StateFlow<Boolean> = _showSearchDialog.asStateFlow()

    private val _showSignUpDialog = MutableStateFlow(false)
    val showSignUpDialog: StateFlow<Boolean> = _showSignUpDialog.asStateFlow()

    private val _platformMetrics = MutableStateFlow(PlatformMetrics())
    val platformMetrics: StateFlow<PlatformMetrics> = _platformMetrics.asStateFlow()

    // Reader State Management
    sealed interface ReaderUiState {
        object Idle : ReaderUiState
        data class Loading(val message: String = "Opening reader...") : ReaderUiState
        data class Success(
            val book: Book,
            val isPreview: Boolean,
            val readablePages: Int,
            val totalDocPages: Int,
            val sourceKey: String?,
            val embeddedPages: List<String> = emptyList()
        ) : ReaderUiState
        data class Error(val error: AppError, val canRetry: Boolean = true) : ReaderUiState
    }

    private val _readerUiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Idle)
    val readerUiState: StateFlow<ReaderUiState> = _readerUiState.asStateFlow()

    val pdfRendererManager = com.example.util.PdfPageRendererManager(application)
    private var lastPreparedBook: Book? = null

    // Discover Refresh State
    private val _isDiscoverRefreshing = MutableStateFlow(false)
    val isDiscoverRefreshing: StateFlow<Boolean> = _isDiscoverRefreshing.asStateFlow()

    // Publishing state & cancellation
    private val _isPublishing = MutableStateFlow(false)
    val isPublishing: StateFlow<Boolean> = _isPublishing.asStateFlow()

    private val _publicationStage = MutableStateFlow<PublicationStage>(PublicationStage.Idle)
    val publicationStage: StateFlow<PublicationStage> = _publicationStage.asStateFlow()

    private val _publishingProgress = MutableStateFlow<String?>(null)
    val publishingProgress: StateFlow<String?> = _publishingProgress.asStateFlow()

    private val _publishError = MutableStateFlow<String?>(null)
    val publishError: StateFlow<String?> = _publishError.asStateFlow()

    private val _publishSuccess = MutableStateFlow<String?>(null)
    val publishSuccess: StateFlow<String?> = _publishSuccess.asStateFlow()

    private var publishingJob: kotlinx.coroutines.Job? = null

    init {
        // Initialize Auth session & profile observer
        viewModelScope.launch {
            repository.initializeAuthObserver()
        }

        // Initial load of published books
        loadDiscoverBooks()

        // React when user profile changes
        viewModelScope.launch {
            currentProfile.collect { profile ->
                if (profile != null) {
                    loadUserLibrary(profile.id)
                    if (profile.role == UserRole.AUTHOR) {
                        loadAuthorBooks(profile.id)
                    } else {
                        _authorWorks.value = emptyList()
                    }
                } else {
                    _authorWorks.value = emptyList()
                    _authorStats.value = AuthorStats(name = "Guest Reader")
                }
            }
        }
    }

    fun loadDiscoverBooks() {
        viewModelScope.launch {
            _isDiscoverRefreshing.value = true
            _discoverDataState.value = DataState.Loading
            try {
                val state = repository.fetchPublishedBooks()
                _discoverDataState.value = state
                when (state) {
                    is DataState.Success -> {
                        _supabaseBooks.value = state.data
                    }
                    is DataState.Offline -> {
                        _supabaseBooks.value = state.data
                    }
                    is DataState.Empty -> {
                        _supabaseBooks.value = emptyList()
                    }
                    else -> Unit
                }
            } finally {
                _isDiscoverRefreshing.value = false
            }
        }
    }

    fun loadAuthorBooks(authorId: String) {
        viewModelScope.launch {
            val books = repository.fetchAuthorBooks(authorId)
            val works = books.map { b ->
                PublishedWork(
                    id = b.id,
                    title = b.title,
                    price = b.price,
                    copiesSold = b.copiesSold,
                    rating = b.rating,
                    netEarned = com.example.model.RoyaltyConfig.calculateAuthorNet(b.price, b.copiesSold),
                    status = b.status,
                    coverUrl = b.coverUrl,
                    genre = b.genre
                )
            }
            _authorWorks.value = works

            val gross = works.sumOf { it.price * it.copiesSold }
            val copies = works.sumOf { it.copiesSold }
            val net = com.example.model.RoyaltyConfig.calculateAuthorNet(gross)
            val fee = com.example.model.RoyaltyConfig.calculatePlatformFee(gross)
            _authorStats.value = AuthorStats(
                name = currentProfile.value?.name ?: "Author",
                grossSales = gross,
                circulationCopies = copies,
                royaltyRate = com.example.model.RoyaltyConfig.AUTHOR_PERCENT,
                netRevenue = net,
                platformFee = fee
            )
        }
    }

    fun loadUserLibrary(userId: String) {
        viewModelScope.launch {
            val library = repository.fetchUserLibrary(userId)
            if (library.isNotEmpty()) {
                _libraryBooks.value = library
            }
        }
    }

    fun setTab(tab: ScreenTab) {
        if (tab == ScreenTab.AUTHOR_STUDIO && userRole.value != UserRole.AUTHOR) {
            _showSignUpDialog.value = true
            return
        }
        if (tab == ScreenTab.DISCOVER) {
            loadDiscoverBooks()
        }
        _currentTab.value = tab
    }

    fun toggleRole() {
        val cur = currentProfile.value
        if (cur == null) {
            _showSignUpDialog.value = true
        } else {
            if (cur.role == UserRole.AUTHOR) {
                if (_currentTab.value == ScreenTab.AUTHOR_STUDIO) {
                    _currentTab.value = ScreenTab.DISCOVER
                } else {
                    _currentTab.value = ScreenTab.AUTHOR_STUDIO
                }
            } else {
                _showSignUpDialog.value = true
            }
        }
    }

    fun setPreviewBook(book: Book) {
        _selectedPreviewBook.value = book
        _currentTab.value = ScreenTab.BOOK_DETAIL
    }

    fun openCheckout(book: Book) {
        _checkoutBook.value = book
    }

    fun closeCheckout() {
        _checkoutBook.value = null
    }

    fun setShowAdminMetrics(show: Boolean) {
        _showAdminMetrics.value = show
    }

    fun setShowSearchDialog(show: Boolean) {
        _showSearchDialog.value = show
    }

    fun setShowSignUpDialog(show: Boolean) {
        _showSignUpDialog.value = show
    }

    // ==========================================
    // USER PROFILE & READING LISTS
    // ==========================================

    fun requestAuthorVerification(
        penName: String,
        bio: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = repository.requestAuthorVerification(penName, bio)
            res.fold(
                onSuccess = { onSuccess() },
                onFailure = { err ->
                    val appError = AppError.from(err)
                    onError(appError.userMessage)
                }
            )
        }
    }

    fun updateUserProfile(
        name: String,
        bio: String,
        photoUrl: String?,
        readingLists: List<ReadingList>,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = repository.updateProfile(name, bio, photoUrl, readingLists)
            res.fold(
                onSuccess = { onSuccess() },
                onFailure = { err ->
                    val appError = AppError.from(err)
                    onError(appError.userMessage)
                }
            )
        }
    }

    fun uploadProfilePhotoFile(
        file: File,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val res = repository.uploadProfilePhoto(file)
            res.fold(
                onSuccess = { url -> onSuccess(url) },
                onFailure = { err ->
                    val appError = AppError.from(err)
                    onError(appError.userMessage)
                }
            )
        }
    }

    fun addReadingList(name: String, description: String, isPublic: Boolean = true) {
        val current = currentProfile.value ?: return
        val newList = ReadingList(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            bookIds = emptyList(),
            isPublic = isPublic
        )
        val updatedLists = current.readingLists + newList
        updateUserProfile(
            name = current.name,
            bio = current.bio,
            photoUrl = current.photoUrl,
            readingLists = updatedLists
        )
    }

    fun removeReadingList(listId: String) {
        val current = currentProfile.value ?: return
        val updatedLists = current.readingLists.filterNot { it.id == listId }
        updateUserProfile(
            name = current.name,
            bio = current.bio,
            photoUrl = current.photoUrl,
            readingLists = updatedLists
        )
    }

    fun toggleBookInReadingList(listId: String, bookId: String) {
        val current = currentProfile.value ?: return
        val updatedLists = current.readingLists.map { list ->
            if (list.id == listId) {
                val newBookIds = if (list.bookIds.contains(bookId)) {
                    list.bookIds - bookId
                } else {
                    list.bookIds + bookId
                }
                list.copy(bookIds = newBookIds)
            } else {
                list
            }
        }
        updateUserProfile(
            name = current.name,
            bio = current.bio,
            photoUrl = current.photoUrl,
            readingLists = updatedLists
        )
    }

    // ==========================================
    // AUTHENTICATION FLOWS (SUPABASE)
    // ==========================================

    fun signUpWithEmail(
        name: String,
        email: String,
        pass: String,
        role: UserRole,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.signUpWithEmail(name, email, pass, role)
            result.fold(
                onSuccess = { profile ->
                    _showSignUpDialog.value = false
                    if (profile.role == UserRole.AUTHOR) {
                        _currentTab.value = ScreenTab.AUTHOR_STUDIO
                    } else {
                        _currentTab.value = ScreenTab.DISCOVER
                    }
                    onSuccess()
                },
                onFailure = { err ->
                    val appError = AppError.from(err, "Signup failed. Please try again.")
                    onError(appError.userMessage)
                }
            )
        }
    }

    fun signInWithEmail(
        email: String,
        pass: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.signInWithEmail(email, pass)
            result.fold(
                onSuccess = { profile ->
                    _showSignUpDialog.value = false
                    if (profile.role == UserRole.AUTHOR) {
                        _currentTab.value = ScreenTab.AUTHOR_STUDIO
                    } else {
                        _currentTab.value = ScreenTab.DISCOVER
                    }
                    onSuccess()
                },
                onFailure = { err ->
                    val appError = AppError.from(err, "Sign-in failed. Please verify credentials.")
                    onError(appError.userMessage)
                }
            )
        }
    }

    fun signInWithGoogleIdToken(
        idToken: String,
        preferredRole: UserRole,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.signInWithGoogleIdToken(idToken, preferredRole)
            result.fold(
                onSuccess = { profile ->
                    _showSignUpDialog.value = false
                    if (profile.role == UserRole.AUTHOR) {
                        _currentTab.value = ScreenTab.AUTHOR_STUDIO
                    } else {
                        _currentTab.value = ScreenTab.DISCOVER
                    }
                    onSuccess()
                },
                onFailure = { err ->
                    val appError = AppError.from(err, "Google Sign-In failed.")
                    onError(appError.userMessage)
                }
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repository.signOut()
            _libraryBooks.value = SampleData.libraryBooks
            _authorWorks.value = emptyList()
            _currentTab.value = ScreenTab.DISCOVER
        }
    }

    // ==========================================
    // PUBLISHING FLOW
    // ==========================================

    fun cancelPublishing() {
        publishingJob?.cancel()
        publishingJob = null
        _isPublishing.value = false
        _publishingProgress.value = "Publication cancelled."
        _publicationStage.value = PublicationStage.Cancelled
    }

    fun publishBook(
        context: Context,
        title: String,
        author: String,
        description: String,
        genre: String,
        language: String,
        price: Double,
        isFree: Boolean,
        coverUriStr: String?,
        pdfUriStr: String?,
        previewPages: Int,
        existingBookId: String? = null,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val profile = currentProfile.value
        if (profile == null) {
            onError("Please sign in as an Author to publish manuscripts.")
            _showSignUpDialog.value = true
            return
        }
        if (profile.role != UserRole.AUTHOR) {
            onError("Authorization Error: Only verified Authors can publish books.")
            return
        }

        publishingJob?.cancel()
        publishingJob = viewModelScope.launch {
            _isPublishing.value = true
            _publishError.value = null
            _publishSuccess.value = null
            _publicationStage.value = PublicationStage.Validating
            _publishingProgress.value = "Validating publication payload..."

            try {
                val coverBytes = if (!coverUriStr.isNullOrBlank()) {
                    try {
                        val uri = Uri.parse(coverUriStr)
                        if (uri.scheme == "content" || uri.scheme == "file") {
                            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        } else null
                    } catch (_: Exception) {
                        null
                    }
                } else null

                val manuscriptFile = if (!pdfUriStr.isNullOrBlank() && pdfUriStr != "internal_preset_pdf") {
                    try {
                        val uri = Uri.parse(pdfUriStr)
                        if (uri.scheme == "file") {
                            File(uri.path ?: "")
                        } else {
                            val tempFile = File(context.cacheDir, "upload_${UUID.randomUUID()}.pdf")
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                FileOutputStream(tempFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            tempFile
                        }
                    } catch (_: Exception) {
                        null
                    }
                } else {
                    createAutoDraftPdf(context, title, author.ifBlank { profile.name }, description)
                }

                val result = repository.publishBook(
                    context = context,
                    title = title,
                    author = author,
                    description = description,
                    genre = genre,
                    language = language,
                    price = price,
                    isFree = isFree,
                    coverBytes = coverBytes,
                    manuscriptFile = manuscriptFile,
                    previewPages = previewPages,
                    existingBookId = existingBookId,
                    onProgress = { stage ->
                        _publicationStage.value = stage
                        _publishingProgress.value = stage.description
                    }
                )

                result.fold(
                    onSuccess = { newBook ->
                        _isPublishing.value = false
                        _publishingProgress.value = null
                        _publishSuccess.value = "Successfully published '${newBook.title}'!"
                        _publicationStage.value = PublicationStage.Completed(newBook)
                        loadAuthorBooks(profile.id)
                        loadDiscoverBooks()
                        onSuccess()
                    },
                    onFailure = { err ->
                        _isPublishing.value = false
                        _publishingProgress.value = null
                        val appError = AppError.from(err, "Failed to publish book")
                        _publishError.value = appError.userMessage
                        loadAuthorBooks(profile.id)
                        onError(appError.userMessage)
                    }
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                _isPublishing.value = false
                _publishingProgress.value = "Publication cancelled."
                _publicationStage.value = PublicationStage.Cancelled
                loadAuthorBooks(profile.id)
            } catch (e: Exception) {
                _isPublishing.value = false
                _publishingProgress.value = null
                val appError = AppError.from(e, "Publishing failed")
                _publishError.value = appError.userMessage
                loadAuthorBooks(profile.id)
                onError(appError.userMessage)
            }
        }
    }

    private fun createAutoDraftPdf(context: Context, title: String, author: String, desc: String): File {
        val file = File(context.cacheDir, "autodraft_${UUID.randomUUID()}.pdf")
        val doc = android.graphics.pdf.PdfDocument()
        try {
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.DKGRAY
                textSize = 24f
                isAntiAlias = true
            }
            canvas.drawText(title.ifBlank { "Untitled Manuscript" }, 50f, 100f, paint)
            paint.textSize = 16f
            paint.color = android.graphics.Color.GRAY
            canvas.drawText("By $author", 50f, 140f, paint)
            paint.textSize = 14f
            paint.color = android.graphics.Color.BLACK
            val lines = desc.chunked(60)
            var y = 200f
            for (line in lines) {
                canvas.drawText(line, 50f, y, paint)
                y += 24f
            }
            doc.finishPage(page)
            FileOutputStream(file).use { out ->
                doc.writeTo(out)
            }
        } finally {
            doc.close()
        }
        return file
    }

    // ==========================================
    // LIBRARY & PURCHASES
    // ==========================================

    fun confirmPurchase(book: Book) {
        val profile = currentProfile.value
        viewModelScope.launch {
            if (profile != null) {
                repository.recordPurchase(profile.id, book)
                loadUserLibrary(profile.id)
            }
            val unlocked = book.copy(
                isPurchased = true,
                badgeType = "new",
                badgeValue = "0% • Unopened"
            )
            if (_libraryBooks.value.none { it.id == unlocked.id }) {
                _libraryBooks.value = listOf(unlocked) + _libraryBooks.value
            }
            closeCheckout()
        }
    }

    fun openFreeBook(book: Book) {
        val profile = currentProfile.value
        viewModelScope.launch {
            if (profile != null) {
                repository.addFreeBookToLibrary(profile.id, book)
                loadUserLibrary(profile.id)
            }
            if (_libraryBooks.value.none { it.id == book.id }) {
                _libraryBooks.value = listOf(book.copy(isPurchased = true)) + _libraryBooks.value
            }
        }
    }

    fun updateReadingProgress(book: Book, lastPage: Int, totalPages: Int, completed: Boolean) {
        val profile = currentProfile.value ?: return
        viewModelScope.launch {
            repository.updateReadingProgress(profile.id, book.id, lastPage, totalPages, completed)
        }
    }

    // ==========================================
    // PDF & CONTENT READER
    // ==========================================

    fun prepareReader(book: Book) {
        lastPreparedBook = book
        viewModelScope.launch {
            _readerUiState.value = ReaderUiState.Loading("Checking book entitlement...")
            try {
                val profile = currentProfile.value
                val access = repository.resolveBookAccess(book, profile?.id)

                when (val target = access.target) {
                    is BookAccessTarget.RemoteStorageUrl -> {
                        _readerUiState.value = ReaderUiState.Loading(
                            if (access.isPreviewOnly) "Downloading preview (${access.allowedPages} pages)..."
                            else "Downloading manuscript..."
                        )
                        val totalPages = pdfRendererManager.openPdfSource(target.url).getOrThrow()
                        val readablePages = if (access.isPreviewOnly) {
                            minOf(totalPages, access.allowedPages)
                        } else {
                            totalPages
                        }
                        _readerUiState.value = ReaderUiState.Success(
                            book = book,
                            isPreview = access.isPreviewOnly,
                            readablePages = readablePages.coerceAtLeast(1),
                            totalDocPages = totalPages,
                            sourceKey = target.url
                        )
                    }
                    is BookAccessTarget.LocalUri -> {
                        _readerUiState.value = ReaderUiState.Loading("Opening local manuscript...")
                        val totalPages = pdfRendererManager.openPdfSource(target.uriString).getOrThrow()
                        val readablePages = if (access.isPreviewOnly) {
                            minOf(totalPages, access.allowedPages)
                        } else {
                            totalPages
                        }
                        _readerUiState.value = ReaderUiState.Success(
                            book = book,
                            isPreview = access.isPreviewOnly,
                            readablePages = readablePages.coerceAtLeast(1),
                            totalDocPages = totalPages,
                            sourceKey = target.uriString
                        )
                    }
                    is BookAccessTarget.EmbeddedPages -> {
                        val readablePages = if (access.isPreviewOnly) {
                            minOf(target.pages.size, access.allowedPages)
                        } else {
                            target.pages.size
                        }
                        _readerUiState.value = ReaderUiState.Success(
                            book = book,
                            isPreview = access.isPreviewOnly,
                            readablePages = readablePages.coerceAtLeast(1),
                            totalDocPages = access.totalPages,
                            sourceKey = null,
                            embeddedPages = target.pages
                        )
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("BookSphereViewModel", "Failed to open reader for book ${book.id}", e)
                val appError = AppError.from(e, "Failed to open manuscript. Please check your connection.")
                _readerUiState.value = ReaderUiState.Error(
                    error = appError,
                    canRetry = true
                )
            }
        }
    }

    fun retryLoadReader() {
        lastPreparedBook?.let { prepareReader(it) }
    }

    fun closeReader() {
        pdfRendererManager.close()
        _readerUiState.value = ReaderUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        pdfRendererManager.close()
    }
}
