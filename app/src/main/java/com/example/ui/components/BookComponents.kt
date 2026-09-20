package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import androidx.compose.ui.platform.LocalContext
import android.util.Log
import com.example.BuildConfig
import com.example.R
import coil.compose.AsyncImage
import com.example.data.SampleData
import com.example.model.*
import com.example.util.CurrencyUtils
import com.example.data.local.UserCredentialEntity

fun Modifier.bookSpineEffect() = this.drawWithContent {
    drawContent()
    drawRect(
        brush = Brush.horizontalGradient(
            0f to Color.Black.copy(alpha = 0.45f),
            0.08f to Color.Black.copy(alpha = 0.12f),
            0.14f to Color.Transparent
        ),
        size = Size(width = 12.dp.toPx(), height = size.height)
    )
    drawRect(
        color = Color.White.copy(alpha = 0.25f),
        topLeft = Offset(x = 4.dp.toPx(), y = 0f),
        size = Size(width = 1.dp.toPx(), height = size.height)
    )
}

@Composable
fun TopNavBar(
    userRole: UserRole,
    onToggleRole: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenAdminMetrics: () -> Unit,
    onOpenSignUp: () -> Unit = {},
    onOpenStoredAccounts: () -> Unit = {},
    onLogoClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.clickable { onLogoClick() }
            ) {
                AsyncImage(
                    model = SampleData.LOGO_URL,
                    contentDescription = "BookSphere Logo",
                    modifier = Modifier.height(34.dp)
                )
                Column {
                    Text(
                        text = "BOOKSPHERE",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "FAIR LITERARY MARKET",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp,
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onOpenSearch) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search Catalog",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onOpenAdminMetrics) {
                    Icon(
                        imageVector = Icons.Outlined.Insights,
                        contentDescription = "Platform Metrics",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onOpenStoredAccounts) {
                    Icon(
                        imageVector = Icons.Outlined.VpnKey,
                        contentDescription = "Credentials Vault (Room DB)",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                IconButton(onClick = onOpenSignUp) {
                    Icon(
                        imageVector = Icons.Outlined.PersonAdd,
                        contentDescription = "Join / Register",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                // Role Switcher Badge / Avatar
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onToggleRole() }
                ) {
                    Row(
                        modifier = Modifier.padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AsyncImage(
                            model = SampleData.AUTHOR_AVATAR,
                            contentDescription = "User Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                        )
                        Text(
                            text = if (userRole == UserRole.AUTHOR) "Author" else "Reader",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BottomNavBar(
    currentTab: ScreenTab,
    onTabSelected: (ScreenTab) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(
                icon = Icons.Outlined.AutoStories,
                label = "Discover",
                isSelected = currentTab == ScreenTab.DISCOVER,
                onClick = { onTabSelected(ScreenTab.DISCOVER) }
            )
            BottomNavItem(
                icon = Icons.Filled.LocalLibrary,
                label = "My Library",
                isSelected = currentTab == ScreenTab.MY_LIBRARY,
                onClick = { onTabSelected(ScreenTab.MY_LIBRARY) }
            )
            BottomNavItem(
                icon = Icons.Outlined.EditNote,
                label = "Author Studio",
                isSelected = currentTab == ScreenTab.AUTHOR_STUDIO,
                onClick = { onTabSelected(ScreenTab.AUTHOR_STUDIO) }
            )
            BottomNavItem(
                icon = Icons.Outlined.MenuBook,
                label = "Book Detail",
                isSelected = currentTab == ScreenTab.BOOK_DETAIL,
                onClick = { onTabSelected(ScreenTab.BOOK_DETAIL) }
            )
        }
    }
}

@Composable
fun BottomNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val weight = if (isSelected) FontWeight.Bold else FontWeight.Normal

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = weight),
            color = color
        )
    }
}

@Composable
fun CheckoutDialog(
    book: Book,
    onDismiss: () -> Unit,
    onConfirmPurchase: (Book) -> Unit
) {
    var isProcessing by remember { mutableStateOf(false) }
    var isSuccess by remember { mutableStateOf(false) }

    val authorPayout = book.price * 0.70
    val platformFee = book.price * 0.30

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                if (!isSuccess) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Fair Checkout",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(70.dp)
                                .height(100.dp)
                                .shadow(4.dp, RoundedCornerShape(4.dp))
                                .clip(RoundedCornerShape(4.dp))
                                .bookSpineEffect()
                        ) {
                            AsyncImage(
                                model = book.coverUrl,
                                contentDescription = book.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Column(verticalArrangement = Arrangement.Center) {
                            Text(
                                text = book.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 2
                            )
                            Text(
                                text = "By ${book.author}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = CurrencyUtils.formatInr(book.price),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Transparent Fair Revenue Split Breakdown
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Transparent Fair Split",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "100% Transparent",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Author Royalty (70%):",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${CurrencyUtils.formatInr(authorPayout)} direct to ${book.author}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "BookSphere Platform (30%):",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${CurrencyUtils.formatInr(platformFee)} (Hosting, CDN, Payment)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Secure",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Stripe Secure Checkout • DRM-Free ePub & PDF Storage",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            isProcessing = true
                            // Simulate quick Stripe token processing
                            isSuccess = true
                            isProcessing = false
                            onConfirmPurchase(book)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = CircleShape,
                        enabled = !isProcessing,
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                        } else {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Unlock Book for ${CurrencyUtils.formatInr(book.price)}",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                } else {
                    // Success View
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Unlocked",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Volume Added to Library!",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "\"${book.title}\" is now fully unlocked. ${CurrencyUtils.formatInr(authorPayout)} has been directly credited to ${book.author}.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = CircleShape
                        ) {
                            Text(text = "Go to My Library")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminMetricsDialog(
    metrics: PlatformMetrics,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Platform Analytics",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Real-time fair market health metrics",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        MetricCard(
                            title = "Total Volume Transacted",
                            value = CurrencyUtils.formatInr(metrics.totalPlatformVolume),
                            subtitle = "70% (${CurrencyUtils.formatInr(metrics.creatorPayouts)}) paid direct to creators",
                            icon = Icons.Default.CurrencyRupee,
                            highlight = true
                        )
                    }
                    item {
                        MetricCard(
                            title = "Sample Preview Views",
                            value = "${metrics.previewPageViews} readers",
                            subtitle = "Readers tried first 2-3 pages before buying (72% conversion)",
                            icon = Icons.Outlined.AutoStories
                        )
                    }
                    item {
                        MetricCard(
                            title = "Active Community",
                            value = "${metrics.activeAuthors} Authors • ${metrics.activeReaders} Readers",
                            subtitle = "100% direct-to-creator communication & patronage",
                            icon = Icons.Default.People
                        )
                    }
                    item {
                        MetricCard(
                            title = "Catalog & Circulation",
                            value = "${metrics.totalBooksListed} Listed • ${metrics.totalCopiesSold} Sold",
                            subtitle = "Average customer rating: ${metrics.avgRating} ★",
                            icon = Icons.Default.Book
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape
                ) {
                    Text("Close Analytics")
                }
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    highlight: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (highlight) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SearchFilterDialog(
    allBooks: List<Book>,
    onSelectBook: (Book) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedGenre by remember { mutableStateOf("All") }

    val genres = listOf("All", "Fiction", "Cosmic Realism", "High Fantasy", "Historical", "Travelogue")

    val filteredBooks = remember(searchQuery, selectedGenre, allBooks) {
        allBooks.filter { book ->
            val matchesGenre = selectedGenre == "All" || book.genre.contains(selectedGenre, ignoreCase = true)
            val matchesQuery = searchQuery.isBlank() ||
                    book.title.contains(searchQuery, ignoreCase = true) ||
                    book.author.contains(searchQuery, ignoreCase = true)
            matchesGenre && matchesQuery
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 580.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Discover Literary Works",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by title, author, or keyword...") },
                    leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape,
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Genres filter chips
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(genres) { genre ->
                        val isSelected = selectedGenre == genre
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedGenre = genre },
                            label = { Text(genre, fontSize = 12.sp) },
                            shape = CircleShape
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "${filteredBooks.size} works found",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredBooks) { book ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectBook(book)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(42.dp)
                                        .height(60.dp)
                                        .shadow(2.dp, RoundedCornerShape(4.dp))
                                        .clip(RoundedCornerShape(4.dp))
                                        .bookSpineEffect()
                                ) {
                                    AsyncImage(
                                        model = book.coverUrl,
                                        contentDescription = book.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = book.title,
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = book.author,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${book.genre} • ${CurrencyUtils.formatInr(book.price)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                Button(
                                    onClick = {
                                        onSelectBook(book)
                                        onDismiss()
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    shape = CircleShape,
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Preview", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SignUpDialog(
    initialRole: UserRole = UserRole.READER,
    onDismiss: () -> Unit,
    onOpenStoredAccounts: () -> Unit = {},
    onRegistrationSuccess: (userRole: UserRole, identifier: String, method: String) -> Unit,
    onStoreCredentials: ((username: String, phone: String?, email: String?, pass: String?, role: String, method: String) -> Unit)? = null
) {
    var selectedRole by remember { mutableStateOf(initialRole) }
    var username by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var isGoogleLoading by remember { mutableStateOf(false) }
    var isAppleLoading by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .shadow(16.dp, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Join BookSphere",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "A fair literary community for all",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Role Toggle (Reader vs Author)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selectedRole == UserRole.READER) MaterialTheme.colorScheme.primary
                                else Color.Transparent
                            )
                            .clickable { selectedRole = UserRole.READER }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Reader",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (selectedRole == UserRole.READER) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selectedRole == UserRole.AUTHOR) MaterialTheme.colorScheme.primary
                                else Color.Transparent
                            )
                            .clickable { selectedRole = UserRole.AUTHOR }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Author (70% Royalty)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (selectedRole == UserRole.AUTHOR) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Status banner
                if (statusMessage != null) {
                    Surface(
                        color = Color(0xFFE6F4EA),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF137333),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = statusMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF137333)
                            )
                        }
                    }
                }

                // Error banner
                if (errorMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = errorMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // ============================================
                // Social Auth Buttons (Firebase Auth Providers)
                // ============================================

                // Google Sign-In with Firebase GoogleAuthProvider
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFDADCE0), RoundedCornerShape(12.dp))
                        .clickable(enabled = !isGoogleLoading && !isAppleLoading && !isSubmitting) {
                            isGoogleLoading = true
                            errorMessage = null
                            coroutineScope.launch {
                                try {
                                    val webClientId = BuildConfig.WEB_CLIENT_ID
                                    if (webClientId == "YOUR_WEB_CLIENT_ID" || webClientId.isBlank()) {
                                        errorMessage = "Missing Web Client ID for Google Sign-In"
                                        isGoogleLoading = false
                                        return@launch
                                    }
                                    
                                    val credentialManager = CredentialManager.create(context)
                                    val googleIdOption = GetGoogleIdOption.Builder()
                                        .setFilterByAuthorizedAccounts(false)
                                        .setServerClientId(webClientId)
                                        .setAutoSelectEnabled(false)
                                        .build()

                                    val request = GetCredentialRequest.Builder()
                                        .addCredentialOption(googleIdOption)
                                        .build()

                                    val result = credentialManager.getCredential(
                                        context = context,
                                        request = request
                                    )

                                    val credential = result.credential
                                    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                                        val idToken = googleIdTokenCredential.idToken
                                        val auth = FirebaseAuth.getInstance()
                                        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                                        
                                        auth.signInWithCredential(firebaseCredential).await()
                                        val currentUser = auth.currentUser
                                        val email = currentUser?.email ?: "reader.google@example.com"
                                        
                                        statusMessage = "Signed in with Google (Firebase GoogleAuthProvider)"
                                        onStoreCredentials?.invoke(
                                            "reader.google",
                                            null,
                                            email,
                                            "firebase_google_token_verified",
                                            selectedRole.name,
                                            "google.com"
                                        )
                                        kotlinx.coroutines.delay(650)
                                        onRegistrationSuccess(selectedRole, email, "google.com")
                                    } else {
                                        errorMessage = "Unexpected credential type"
                                    }
                                } catch (e: androidx.credentials.exceptions.NoCredentialException) {
                                    Log.w("Auth", "No credentials available on device")
                                    errorMessage = "No Google Account found. Please add an account in Android Settings."
                                } catch (e: Exception) {
                                    Log.w("Auth", "Google Sign-In failed", e)
                                    errorMessage = "Google Sign-In Failed: ${e.message}"
                                } finally {
                                    isGoogleLoading = false
                                }
                            }
                        },
                    color = Color.White,
                    shadowElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isGoogleLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Connecting with Google...",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                color = Color(0xFF3C4043)
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_google_logo),
                                contentDescription = "Google Logo",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Continue with Google",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(0xFF3C4043)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "By continuing, you agree to our Terms of Service and Privacy Policy.",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun StoredCredentialsDialog(
    credentials: List<UserCredentialEntity>,
    activeId: Long?,
    onSwitchActive: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .shadow(16.dp, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Credentials Vault",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Room DB: user_credentials (${credentials.size} stored)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (credentials.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No user credentials stored yet.\nSign up with Google, Apple, or Password to save.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(credentials) { cred ->
                            val isActive = cred.isActive || cred.id == activeId
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                else MaterialTheme.colorScheme.surfaceContainerLow,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .background(
                                                if (cred.authMethod == "google.com") Color(0xFFE8F0FE)
                                                else if (cred.authMethod == "apple.com") Color(0xFFECEFF1)
                                                else MaterialTheme.colorScheme.secondaryContainer,
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        when (cred.authMethod) {
                                            "google.com" -> {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_google_logo),
                                                    contentDescription = null,
                                                    tint = Color.Unspecified,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            "apple.com" -> {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_apple_logo),
                                                    contentDescription = null,
                                                    tint = Color.Black,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            else -> {
                                                Icon(
                                                    imageVector = Icons.Default.Key,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = cred.username,
                                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Surface(
                                                shape = CircleShape,
                                                color = if (cred.role.equals("AUTHOR", ignoreCase = true)) MaterialTheme.colorScheme.secondaryContainer
                                                else MaterialTheme.colorScheme.surfaceContainerHigh
                                            ) {
                                                Text(
                                                    text = cred.role,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                                    color = if (cred.role.equals("AUTHOR", ignoreCase = true)) MaterialTheme.colorScheme.onSecondaryContainer
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        val detail = cred.email ?: cred.phoneNumber ?: "Password Protected"
                                        Text(
                                            text = "$detail • via ${cred.authMethod}",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        if (isActive) {
                                            Text(
                                                text = "✓ Currently Active Account",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (!isActive) {
                                            OutlinedButton(
                                                onClick = { onSwitchActive(cred.id) },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                shape = CircleShape,
                                                modifier = Modifier.height(30.dp)
                                            ) {
                                                Text("Switch", fontSize = 11.sp)
                                            }
                                        }
                                        IconButton(
                                            onClick = { onDelete(cred.id) },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Delete Credential",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape
                ) {
                    Text("Close Vault")
                }
            }
        }
    }
}

