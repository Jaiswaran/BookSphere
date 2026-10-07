package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SampleData
import com.example.data.local.AppDatabase
import com.example.data.local.BookEntity
import com.example.data.repository.BookSphereRepository
import com.example.model.*
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

    // Publishing state
    private val _isPublishing = MutableStateFlow(false)
    val isPublishing: StateFlow<Boolean> = _isPublishing.asStateFlow()

    private val _publishingProgress = MutableStateFlow<String?>(null)
    val publishingProgress: StateFlow<String?> = _publishingProgress.asStateFlow()

    private val _publishError = MutableStateFlow<String?>(null)
    val publishError: StateFlow<String?> = _publishError.asStateFlow()

    private val _publishSuccess = MutableStateFlow<String?>(null)
    val publishSuccess: StateFlow<String?> = _publishSuccess.asStateFlow()

    init {
        // Initial load of published books from Supabase Postgres
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
                    // Not signed in
                    _authorWorks.value = emptyList()
                    _authorStats.value = AuthorStats(name = "Guest Reader")
                }
            }
        }
    }

    fun loadDiscoverBooks() {
        viewModelScope.launch {
            val remoteBooks = repository.fetchPublishedBooks()
            if (remoteBooks.isNotEmpty()) {
                _supabaseBooks.value = remoteBooks
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
                    netEarned = b.price * 0.70 * b.copiesSold,
                    status = b.status,
                    coverUrl = b.coverUrl,
                    genre = b.genre
                )
            }
            _authorWorks.value = works

            // Compute real author stats from Supabase books
            val gross = works.sumOf { it.price * it.copiesSold }
            val copies = works.sumOf { it.copiesSold }
            val net = gross * 0.70
            val fee = gross * 0.30
            _authorStats.value = AuthorStats(
                name = currentProfile.value?.name ?: "Author",
                grossSales = gross,
                circulationCopies = copies,
                royaltyRate = 70.0,
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
            // Prompt sign in / switch to Author
            _showSignUpDialog.value = true
            return
        }
        _currentTab.value = tab
    }

    fun toggleRole() {
        val cur = currentProfile.value
        if (cur == null) {
            _showSignUpDialog.value = true
        } else {
            // Cannot bypass Supabase role security by UI toggle
            if (cur.role == UserRole.AUTHOR) {
                if (_currentTab.value == ScreenTab.AUTHOR_STUDIO) {
                    _currentTab.value = ScreenTab.DISCOVER
                } else {
                    _currentTab.value = ScreenTab.AUTHOR_STUDIO
                }
            } else {
                // User is a Reader: inform them they need an Author profile to publish
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
                    onError(err.message ?: "Signup failed. Please try again.")
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
                    onError(err.message ?: "Sign-in failed. Please verify credentials.")
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
                    onError(err.message ?: "Google Sign-In failed.")
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
    // REAL SUPABASE PUBLISHING FLOW
    // ==========================================

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
        onSuccess: () -> Unit,
        onError: (String) -> Unit
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

        viewModelScope.launch {
            _isPublishing.value = true
            _publishError.value = null
            _publishSuccess.value = null
            _publishingProgress.value = "Preparing publishing payload..."

            try {
                // Read cover bytes
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

                // Read manuscript file
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
                    // Create auto-draft manuscript PDF
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
                    onProgress = { progressText ->
                        _publishingProgress.value = progressText
                    }
                )

                result.fold(
                    onSuccess = { newBook ->
                        _isPublishing.value = false
                        _publishingProgress.value = null
                        _publishSuccess.value = "Successfully published '${newBook.title}'!"
                        // Refresh Author Studio & Discover
                        loadAuthorBooks(profile.id)
                        loadDiscoverBooks()
                        onSuccess()
                    },
                    onFailure = { err ->
                        _isPublishing.value = false
                        _publishingProgress.value = null
                        _publishError.value = err.message ?: "Failed to publish book to Supabase"
                        onError(err.message ?: "Publishing failed")
                    }
                )
            } catch (e: Exception) {
                _isPublishing.value = false
                _publishingProgress.value = null
                _publishError.value = e.message
                onError(e.message ?: "Publishing exception")
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
    // LIBRARY & PURCHASES (SUPABASE)
    // ==========================================

    fun confirmPurchase(book: Book) {
        val profile = currentProfile.value
        viewModelScope.launch {
            if (profile != null) {
                repository.recordPurchase(profile.id, book)
                loadUserLibrary(profile.id)
            }
            // Update local memory state for immediate UI responsiveness
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
}
