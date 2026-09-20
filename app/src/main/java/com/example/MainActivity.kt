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
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import android.util.Log

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val apiKey = BuildConfig.FIREBASE_API_KEY
                val appId = BuildConfig.FIREBASE_APP_ID
                val projectId = BuildConfig.FIREBASE_PROJECT_ID
                
                if (apiKey != "YOUR_API_KEY" && apiKey.isNotBlank()) {
                    val options = FirebaseOptions.Builder()
                        .setApiKey(apiKey)
                        .setApplicationId(appId)
                        .setProjectId(projectId)
                        .build()
                    FirebaseApp.initializeApp(this, options)
                    Log.d("Firebase", "Firebase initialized manually via BuildConfig")
                } else {
                    Log.w("Firebase", "Firebase not initialized: Missing actual keys in Secrets.")
                }
            }
        } catch (e: Exception) {
            Log.e("Firebase", "Failed to initialize Firebase", e)
        }

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
    val userRole by viewModel.userRole.collectAsStateWithLifecycle()
    val selectedPreviewBook by viewModel.selectedPreviewBook.collectAsStateWithLifecycle()
    val checkoutBook by viewModel.checkoutBook.collectAsStateWithLifecycle()
    val showAdminMetrics by viewModel.showAdminMetrics.collectAsStateWithLifecycle()
    val showSearchDialog by viewModel.showSearchDialog.collectAsStateWithLifecycle()
    val showSignUpDialog by viewModel.showSignUpDialog.collectAsStateWithLifecycle()
    val showStoredCredentialsDialog by viewModel.showStoredCredentialsDialog.collectAsStateWithLifecycle()

    val libraryBooks by viewModel.libraryBooks.collectAsStateWithLifecycle()
    val authorStats by viewModel.authorStats.collectAsStateWithLifecycle()
    val platformMetrics by viewModel.platformMetrics.collectAsStateWithLifecycle()

    // Room Database Backed Lists
    val publishedEntities by viewModel.publishedBooks.collectAsStateWithLifecycle()
    val allCredentials by viewModel.allCredentials.collectAsStateWithLifecycle()
    val activeCredential by viewModel.activeCredential.collectAsStateWithLifecycle()

    val publishedWorks = remember(publishedEntities) {
        if (publishedEntities.isEmpty()) {
            SampleData.authorPublishedWorks
        } else {
            publishedEntities.map { it.toPublishedWork() }
        }
    }

    val trendingBooks = remember(publishedEntities) {
        val published = publishedEntities.map { it.toBook() }
        (published + SampleData.trendingBooks).distinctBy { it.id }
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
                    onToggleRole = { viewModel.toggleRole() },
                    onOpenSearch = { viewModel.setShowSearchDialog(true) },
                    onOpenAdminMetrics = { viewModel.setShowAdminMetrics(true) },
                    onOpenSignUp = { viewModel.setShowSignUpDialog(true) },
                    onOpenStoredAccounts = { viewModel.setShowStoredCredentialsDialog(true) },
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
                            viewModel.openCheckout(book)
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
                            // If they are reading it, they don't necessarily buy it yet (could be free or preview).
                            viewModel.setTab(ScreenTab.READER)
                        }
                    )
                }
                ScreenTab.READER -> {
                    val isPurchased = libraryBooks.any { it.id == selectedPreviewBook.id } || selectedPreviewBook.isPurchased
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

    // Sign-Up / Social Registration Dialog with Firebase Auth Providers & Room DB
    if (showSignUpDialog) {
        SignUpDialog(
            initialRole = userRole,
            onDismiss = { viewModel.setShowSignUpDialog(false) },
            onOpenStoredAccounts = {
                viewModel.setShowSignUpDialog(false)
                viewModel.setShowStoredCredentialsDialog(true)
            },
            onStoreCredentials = { username, phone, email, pass, role, method ->
                viewModel.storeUserCredentials(
                    username = username,
                    phoneNumber = phone,
                    email = email,
                    passwordOrToken = pass,
                    role = role,
                    authMethod = method
                )
            },
            onRegistrationSuccess = { registeredRole, identifier, method ->
                if (registeredRole == UserRole.AUTHOR) {
                    viewModel.setTab(ScreenTab.AUTHOR_STUDIO)
                } else {
                    viewModel.setTab(ScreenTab.DISCOVER)
                }
                viewModel.setShowSignUpDialog(false)
            }
        )
    }

    // Room Database Credentials Vault Dialog
    if (showStoredCredentialsDialog) {
        StoredCredentialsDialog(
            credentials = allCredentials,
            activeId = activeCredential?.id,
            onSwitchActive = { id -> viewModel.switchActiveUser(id) },
            onDelete = { id -> viewModel.deleteStoredCredential(id) },
            onDismiss = { viewModel.setShowStoredCredentialsDialog(false) }
        )
    }
}
