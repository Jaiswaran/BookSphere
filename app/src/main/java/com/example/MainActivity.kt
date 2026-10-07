package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.SampleData
import com.example.model.*
import com.example.ui.BookSphereViewModel
import com.example.ui.components.*
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                BookSphereApp()
            }
        }
    }
}

@Composable
fun BookSphereApp(
    viewModel: BookSphereViewModel = viewModel()
) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val currentProfile by viewModel.currentProfile.collectAsStateWithLifecycle()
    val userRole by viewModel.userRole.collectAsStateWithLifecycle()
    val selectedPreviewBook by viewModel.selectedPreviewBook.collectAsStateWithLifecycle()
    val checkoutBook by viewModel.checkoutBook.collectAsStateWithLifecycle()
    val showAdminMetrics by viewModel.showAdminMetrics.collectAsStateWithLifecycle()
    val showSearchDialog by viewModel.showSearchDialog.collectAsStateWithLifecycle()
    val showSignUpDialog by viewModel.showSignUpDialog.collectAsStateWithLifecycle()

    val libraryBooks by viewModel.libraryBooks.collectAsStateWithLifecycle()
    val authorStats by viewModel.authorStats.collectAsStateWithLifecycle()
    val authorWorks by viewModel.authorWorks.collectAsStateWithLifecycle()
    val platformMetrics by viewModel.platformMetrics.collectAsStateWithLifecycle()

    // Discover Data State & Backed Lists
    val discoverDataState by viewModel.discoverDataState.collectAsStateWithLifecycle()
    val supabaseBooks by viewModel.supabaseBooks.collectAsStateWithLifecycle()
    val publishedEntities by viewModel.publishedBooks.collectAsStateWithLifecycle()

    val publishedWorks = remember(authorWorks, currentProfile) {
        if (authorWorks.isNotEmpty()) {
            authorWorks
        } else if (currentProfile?.role == UserRole.AUTHOR) {
            emptyList()
        } else {
            SampleData.authorPublishedWorks
        }
    }

    val trendingBooks = remember(supabaseBooks, publishedEntities, discoverDataState) {
        when (discoverDataState) {
            is DataState.Success -> supabaseBooks
            is DataState.Empty -> emptyList()
            is DataState.Offline -> {
                if (supabaseBooks.isNotEmpty()) supabaseBooks
                else publishedEntities.map { it.toBook() }
            }
            is DataState.Error -> {
                if (publishedEntities.isNotEmpty()) publishedEntities.map { it.toBook() }
                else emptyList()
            }
            is DataState.Loading, is DataState.Idle -> {
                if (supabaseBooks.isNotEmpty()) supabaseBooks
                else if (publishedEntities.isNotEmpty()) publishedEntities.map { it.toBook() }
                else SampleData.trendingBooks
            }
        }
    }

    val allBooks = remember(libraryBooks, trendingBooks) {
        (trendingBooks + libraryBooks + listOf(SampleData.staffPickBook, SampleData.currentVolume)).distinctBy { it.id }
    }

    BackHandler(enabled = currentTab != ScreenTab.DISCOVER) {
        when (currentTab) {
            ScreenTab.BOOK_DETAIL -> viewModel.setTab(ScreenTab.DISCOVER)
            ScreenTab.READER -> viewModel.setTab(ScreenTab.BOOK_DETAIL)
            ScreenTab.PROFILE -> viewModel.setTab(ScreenTab.DISCOVER)
            ScreenTab.AUTHOR_STUDIO -> viewModel.setTab(ScreenTab.DISCOVER)
            ScreenTab.MY_LIBRARY -> viewModel.setTab(ScreenTab.DISCOVER)
            else -> Unit
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (currentTab != ScreenTab.BOOK_DETAIL && currentTab != ScreenTab.PROFILE) {
                TopNavBar(
                    userRole = userRole,
                    currentProfile = currentProfile,
                    onToggleRole = { viewModel.toggleRole() },
                    onOpenSearch = { viewModel.setShowSearchDialog(true) },
                    onOpenAdminMetrics = { viewModel.setShowAdminMetrics(true) },
                    onOpenSignUp = { viewModel.setShowSignUpDialog(true) },
                    onOpenProfile = { viewModel.setTab(ScreenTab.PROFILE) },
                    onLogoClick = { viewModel.setTab(ScreenTab.DISCOVER) }
                )
            }
        },
        bottomBar = {
            BottomNavBar(
                currentTab = currentTab,
                onTabSelected = { tab ->
                    viewModel.setTab(tab)
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                ScreenTab.DISCOVER -> {
                    DiscoverScreen(
                        staffPick = SampleData.staffPickBook,
                        trendingBooks = trendingBooks,
                        catalogState = discoverDataState,
                        onSelectBookPreview = { book ->
                            viewModel.setPreviewBook(book)
                        },
                        onBuyBook = { book ->
                            if (book.isFree) {
                                viewModel.openFreeBook(book)
                                viewModel.setPreviewBook(book)
                                viewModel.setTab(ScreenTab.READER)
                            } else {
                                viewModel.openCheckout(book)
                            }
                        },
                        onStartSelling = {
                            viewModel.setTab(ScreenTab.AUTHOR_STUDIO)
                        },
                        onRefresh = {
                            viewModel.loadDiscoverBooks()
                        }
                    )
                }
                ScreenTab.MY_LIBRARY -> {
                    LibraryScreen(
                        currentVolume = SampleData.currentVolume,
                        libraryBooks = libraryBooks,
                        onOpenBook = { book ->
                            viewModel.setPreviewBook(book)
                            viewModel.setTab(ScreenTab.READER)
                        }
                    )
                }
                ScreenTab.AUTHOR_STUDIO -> {
                    AuthorStudioScreen(
                        stats = authorStats,
                        publishedWorks = publishedWorks,
                        readerResonanceList = SampleData.readerResonanceList,
                        viewModel = viewModel
                    )
                }
                ScreenTab.PROFILE -> {
                    UserProfileScreen(
                        viewModel = viewModel,
                        allBooks = allBooks,
                        onSelectBook = { book ->
                            viewModel.setPreviewBook(book)
                        },
                        onNavigateBack = {
                            viewModel.setTab(ScreenTab.DISCOVER)
                        }
                    )
                }
                ScreenTab.BOOK_DETAIL -> {
                    BookPreviewScreen(
                        book = selectedPreviewBook,
                        onBack = { viewModel.setTab(ScreenTab.DISCOVER) },
                        onBuyNow = { book -> viewModel.openCheckout(book) },
                        onReadBook = { book -> 
                            if (book.isFree) {
                                viewModel.openFreeBook(book)
                            }
                            viewModel.setTab(ScreenTab.READER)
                        }
                    )
                }
                ScreenTab.READER -> {
                    ReaderScreen(
                        viewModel = viewModel,
                        book = selectedPreviewBook,
                        onNavigateBack = { viewModel.setTab(ScreenTab.BOOK_DETAIL) },
                        onPurchase = { viewModel.openCheckout(selectedPreviewBook) }
                    )
                }
            }
        }
    }

    // Checkout & Transparent Revenue Split Dialog
    checkoutBook?.let { book ->
        CheckoutDialog(
            book = book,
            onDismiss = { viewModel.closeCheckout() },
            onConfirmPurchase = { purchasedBook ->
                viewModel.confirmPurchase(purchasedBook)
            }
        )
    }

    // Admin Metrics Sheet
    if (showAdminMetrics) {
        AdminMetricsDialog(
            metrics = platformMetrics,
            onDismiss = { viewModel.setShowAdminMetrics(false) }
        )
    }

    // Search and Filter Dialog
    if (showSearchDialog) {
        SearchFilterDialog(
            allBooks = allBooks,
            onSelectBook = { book ->
                viewModel.setPreviewBook(book)
            },
            onDismiss = { viewModel.setShowSearchDialog(false) }
        )
    }

    // Supabase Authentication Dialog
    if (showSignUpDialog) {
        SignUpDialog(
            currentProfile = currentProfile,
            initialRole = userRole,
            onDismiss = { viewModel.setShowSignUpDialog(false) },
            onEmailSignUp = { name, email, pass, role ->
                viewModel.signUpWithEmail(
                    name = name,
                    email = email,
                    pass = pass,
                    role = role,
                    onSuccess = {},
                    onError = {}
                )
            },
            onEmailSignIn = { email, pass ->
                viewModel.signInWithEmail(
                    email = email,
                    pass = pass,
                    onSuccess = {},
                    onError = {}
                )
            },
            onGoogleSignIn = { idToken, preferredRole ->
                viewModel.signInWithGoogleIdToken(
                    idToken = idToken,
                    preferredRole = preferredRole,
                    onSuccess = {},
                    onError = {}
                )
            },
            onSignOut = {
                viewModel.signOut()
            }
        )
    }
}
