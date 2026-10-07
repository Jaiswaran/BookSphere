package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
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

    // Supabase & Room Database Backed Lists
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

    val trendingBooks = remember(supabaseBooks, publishedEntities) {
        if (supabaseBooks.isNotEmpty()) {
            (supabaseBooks + SampleData.trendingBooks).distinctBy { it.id }
        } else if (publishedEntities.isNotEmpty()) {
            (publishedEntities.map { it.toBook() } + SampleData.trendingBooks).distinctBy { it.id }
        } else {
            SampleData.trendingBooks
        }
    }

    val allBooks = remember(libraryBooks, trendingBooks) {
        (trendingBooks + libraryBooks + listOf(SampleData.staffPickBook, SampleData.currentVolume)).distinctBy { it.id }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (currentTab != ScreenTab.BOOK_DETAIL) {
                TopNavBar(
                    userRole = userRole,
                    currentProfile = currentProfile,
                    onToggleRole = { viewModel.toggleRole() },
                    onOpenSearch = { viewModel.setShowSearchDialog(true) },
                    onOpenAdminMetrics = { viewModel.setShowAdminMetrics(true) },
                    onOpenSignUp = { viewModel.setShowSignUpDialog(true) },
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
                    val isPurchased = libraryBooks.any { it.id == selectedPreviewBook.id } || selectedPreviewBook.isPurchased || selectedPreviewBook.isFree
                    ReaderScreen(
                        book = selectedPreviewBook,
                        isPurchased = isPurchased,
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
