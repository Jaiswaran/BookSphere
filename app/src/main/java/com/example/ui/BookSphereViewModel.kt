package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SampleData
import com.example.data.local.AppDatabase
import com.example.data.local.BookEntity
import com.example.data.local.UserCredentialEntity
import com.example.data.repository.BookSphereRepository
import com.example.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class BookSphereViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = BookSphereRepository(database.bookDao(), database.userCredentialDao())

    // Database Flows
    val publishedBooks: StateFlow<List<BookEntity>> = repository.allPublishedBooks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCredentials: StateFlow<List<UserCredentialEntity>> = repository.allCredentials
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeCredential: StateFlow<UserCredentialEntity?> = repository.activeCredential
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // In-memory session state
    private val _currentTab = MutableStateFlow(ScreenTab.DISCOVER)
    val currentTab: StateFlow<ScreenTab> = _currentTab.asStateFlow()

    private val _userRole = MutableStateFlow(UserRole.READER)
    val userRole: StateFlow<UserRole> = _userRole.asStateFlow()

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

    private val _showStoredCredentialsDialog = MutableStateFlow(false)
    val showStoredCredentialsDialog: StateFlow<Boolean> = _showStoredCredentialsDialog.asStateFlow()

    private val _libraryBooks = MutableStateFlow(SampleData.libraryBooks)
    val libraryBooks: StateFlow<List<Book>> = _libraryBooks.asStateFlow()

    private val _authorStats = MutableStateFlow(AuthorStats())
    val authorStats: StateFlow<AuthorStats> = _authorStats.asStateFlow()

    private val _platformMetrics = MutableStateFlow(PlatformMetrics())
    val platformMetrics: StateFlow<PlatformMetrics> = _platformMetrics.asStateFlow()

    init {
        viewModelScope.launch {
            repository.seedInitialDataIfEmpty()
        }

        // Sync active credential with user role and author stats name
        viewModelScope.launch {
            activeCredential.collect { cred ->
                if (cred != null) {
                    if (cred.role.equals("AUTHOR", ignoreCase = true)) {
                        _userRole.value = UserRole.AUTHOR
                        _authorStats.value = _authorStats.value.copy(name = cred.username)
                    } else {
                        _userRole.value = UserRole.READER
                    }
                }
            }
        }
    }

    fun setTab(tab: ScreenTab) {
        _currentTab.value = tab
        if (tab == ScreenTab.AUTHOR_STUDIO) {
            _userRole.value = UserRole.AUTHOR
        }
    }

    fun toggleRole() {
        if (_userRole.value == UserRole.READER) {
            _userRole.value = UserRole.AUTHOR
            _currentTab.value = ScreenTab.AUTHOR_STUDIO
        } else {
            _userRole.value = UserRole.READER
            _currentTab.value = ScreenTab.DISCOVER
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

    fun setShowStoredCredentialsDialog(show: Boolean) {
        _showStoredCredentialsDialog.value = show
    }

    /**
     * Stores newly published manuscript into local Room database.
     */
    fun publishBook(
        title: String,
        author: String,
        genre: String,
        price: Double,
        description: String,
        coverUrl: String,
        pdfUri: String,
        language: String,
        totalPages: Int,
        previewPages: Int,
        isFree: Boolean,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val authorName = author.ifBlank { activeCredential.value?.username ?: _authorStats.value.name }
            repository.publishBook(
                title = title,
                author = authorName,
                genre = genre,
                price = price,
                description = description,
                coverUrl = coverUrl,
                pdfUri = pdfUri,
                language = language,
                totalPages = totalPages,
                previewPages = previewPages,
                isFree = isFree
            )
            _platformMetrics.value = _platformMetrics.value.copy(
                totalBooksListed = _platformMetrics.value.totalBooksListed + 1
            )
            onSuccess()
        }
    }

    /**
     * Stores user credentials into local Room database.
     */
    fun storeUserCredentials(
        username: String,
        phoneNumber: String? = null,
        email: String? = null,
        passwordOrToken: String? = null,
        role: String,
        authMethod: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.storeUserCredential(
                username = username,
                phoneNumber = phoneNumber,
                email = email,
                passwordOrToken = passwordOrToken,
                role = role,
                authMethod = authMethod
            )
            if (role.equals("AUTHOR", ignoreCase = true)) {
                _userRole.value = UserRole.AUTHOR
                _currentTab.value = ScreenTab.AUTHOR_STUDIO
                _authorStats.value = _authorStats.value.copy(name = username)
            } else {
                _userRole.value = UserRole.READER
                _currentTab.value = ScreenTab.DISCOVER
            }
            onSuccess()
        }
    }

    fun switchActiveUser(id: Long) {
        viewModelScope.launch {
            repository.switchActiveUser(id)
        }
    }

    fun deleteStoredCredential(id: Long) {
        viewModelScope.launch {
            repository.deleteCredential(id)
        }
    }

    fun confirmPurchase(book: Book) {
        // Add unlocked book to user's library
        val unlocked = book.copy(
            isPurchased = true,
            badgeType = "new",
            badgeValue = "0% • Unopened"
        )
        if (_libraryBooks.value.none { it.id == unlocked.id }) {
            _libraryBooks.value = listOf(unlocked) + _libraryBooks.value
        }

        // Update Author Stats (INR)
        val price = book.price
        _authorStats.value = _authorStats.value.copy(
            grossSales = _authorStats.value.grossSales + price,
            circulationCopies = _authorStats.value.circulationCopies + 1,
            netRevenue = _authorStats.value.netRevenue + (price * 0.70),
            platformFee = _authorStats.value.platformFee + (price * 0.30)
        )

        // Update Platform Metrics (INR)
        _platformMetrics.value = _platformMetrics.value.copy(
            totalCopiesSold = _platformMetrics.value.totalCopiesSold + 1,
            totalPlatformVolume = _platformMetrics.value.totalPlatformVolume + price,
            creatorPayouts = _platformMetrics.value.creatorPayouts + (price * 0.70)
        )
    }
}
