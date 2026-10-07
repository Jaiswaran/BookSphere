package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.model.Book
import com.example.ui.BookSphereViewModel
import com.example.util.CurrencyUtils
import kotlinx.coroutines.launch

@Composable
fun ReaderScreen(
    viewModel: BookSphereViewModel,
    book: Book,
    onNavigateBack: () -> Unit,
    onPurchase: () -> Unit
) {
    val readerState by viewModel.readerUiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    var isDarkMode by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }

    LaunchedEffect(book.id) {
        viewModel.prepareReader(book)
    }

    DisposableEffect(book.id) {
        onDispose {
            viewModel.closeReader()
        }
    }

    BackHandler {
        viewModel.closeReader()
        onNavigateBack()
    }

    val backgroundColor = if (isDarkMode) Color.Black else Color.White
    val textColor = if (isDarkMode) Color.White else Color.Black

    when (val state = readerState) {
        is BookSphereViewModel.ReaderUiState.Idle,
        is BookSphereViewModel.ReaderUiState.Loading -> {
            val message = (state as? BookSphereViewModel.ReaderUiState.Loading)?.message ?: "Opening manuscript..."
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor),
                contentAlignment = Alignment.Center
            ) {
                // Top header so user can exit loading
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            viewModel.closeReader()
                            onNavigateBack()
                        },
                        modifier = Modifier.testTag("reader_loading_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = textColor
                        )
                    }
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("reader_loading_indicator"),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor.copy(alpha = 0.8f)
                    )
                }
            }
        }

        is BookSphereViewModel.ReaderUiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            viewModel.closeReader()
                            onNavigateBack()
                        },
                        modifier = Modifier.testTag("reader_error_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = textColor
                        )
                    }
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Unable to Load Manuscript",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = state.error.userMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedButton(
                            onClick = {
                                viewModel.closeReader()
                                onNavigateBack()
                            },
                            shape = CircleShape
                        ) {
                            Text("Go Back")
                        }

                        if (state.canRetry) {
                            Button(
                                onClick = { viewModel.retryLoadReader() },
                                modifier = Modifier.testTag("reader_retry_button"),
                                shape = CircleShape
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }

        is BookSphereViewModel.ReaderUiState.Success -> {
            val isPreviewMode = state.isPreview
            val readablePages = state.readablePages
            val totalPagerPages = if (isPreviewMode) readablePages + 1 else readablePages

            val pagerState = rememberPagerState(pageCount = { totalPagerPages })
            val focusRequester = remember { FocusRequester() }

            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }

            LaunchedEffect(pagerState.currentPage, state.sourceKey) {
                if (state.sourceKey != null) {
                    viewModel.pdfRendererManager.prefetchAdjacentPages(pagerState.currentPage, readablePages)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor)
                    .focusable()
                    .focusRequester(focusRequester)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyUp) {
                            when (event.key) {
                                Key.DirectionRight -> {
                                    coroutineScope.launch {
                                        if (pagerState.currentPage < totalPagerPages - 1) {
                                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                        }
                                    }
                                    true
                                }
                                Key.DirectionLeft -> {
                                    coroutineScope.launch {
                                        if (pagerState.currentPage > 0) {
                                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                        }
                                    }
                                    true
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    }
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    if (isPreviewMode && page == readablePages) {
                        // Paywall Page after preview pages
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp)
                                .testTag("reader_paywall_page"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(72.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Paywall",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Text(
                                text = "End of Free Preview",
                                style = MaterialTheme.typography.headlineMedium,
                                color = textColor,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "You have previewed the first $readablePages pages of this book. Unlock the full ${state.totalDocPages}-page manuscript and support ${book.author} directly.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = textColor.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center,
                                lineHeight = 24.sp
                            )

                            Spacer(modifier = Modifier.height(28.dp))

                            Button(
                                onClick = {
                                    viewModel.closeReader()
                                    onPurchase()
                                },
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .height(54.dp)
                                    .testTag("preview_buy_button"),
                                shape = CircleShape
                            ) {
                                Text(
                                    text = "Buy Now for ${CurrencyUtils.formatInr(book.price)}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    } else {
                        // Render Content Page
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { showControls = !showControls }
                        ) {
                            if (state.sourceKey != null) {
                                PdfPageView(
                                    viewModel = viewModel,
                                    page = page,
                                    isDarkMode = isDarkMode
                                )
                            } else if (state.embeddedPages.isNotEmpty()) {
                                val content = state.embeddedPages.getOrElse(page) { "Page content missing." }
                                if (content.startsWith("http://") || content.startsWith("https://")) {
                                    AsyncImage(
                                        model = content,
                                        contentDescription = "Page ${page + 1}",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    Text(
                                        text = content,
                                        color = textColor,
                                        modifier = Modifier
                                            .padding(24.dp)
                                            .padding(top = 48.dp, bottom = 48.dp),
                                        style = MaterialTheme.typography.bodyLarge,
                                        lineHeight = 26.sp
                                    )
                                }
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Page ${page + 1} content missing.", color = textColor)
                                }
                            }
                        }
                    }
                }

                // Overlays
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Top Nav Bar
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                            modifier = Modifier.fillMaxWidth(),
                            shadowElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp, vertical = 8.dp)
                                    .statusBarsPadding(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        viewModel.closeReader()
                                        onNavigateBack()
                                    },
                                    modifier = Modifier.testTag("reader_back_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = book.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    if (isPreviewMode) {
                                        Text(
                                            text = "Free Preview Mode",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { isDarkMode = !isDarkMode },
                                    modifier = Modifier.testTag("reader_theme_toggle")
                                ) {
                                    Icon(
                                        imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                        contentDescription = "Toggle Theme"
                                    )
                                }
                            }
                        }

                        // Bottom Navigation Controls
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                            modifier = Modifier.fillMaxWidth(),
                            shadowElevation = 8.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .navigationBarsPadding(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // First Page
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch { pagerState.animateScrollToPage(0) }
                                    },
                                    enabled = pagerState.currentPage > 0,
                                    modifier = Modifier.testTag("reader_first_page_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipPrevious,
                                        contentDescription = "First Page"
                                    )
                                }

                                // Previous Page
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                        }
                                    },
                                    enabled = pagerState.currentPage > 0,
                                    modifier = Modifier.testTag("reader_prev_page_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                                        contentDescription = "Previous Page"
                                    )
                                }

                                // Page Indicator
                                val pageLabel = if (isPreviewMode && pagerState.currentPage >= readablePages) {
                                    "Preview Ended"
                                } else {
                                    "Page ${pagerState.currentPage + 1} of $readablePages${if (isPreviewMode) " • Preview" else ""}"
                                }

                                Text(
                                    text = pageLabel,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    modifier = Modifier.testTag("reader_page_indicator")
                                )

                                // Next Page
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                        }
                                    },
                                    enabled = pagerState.currentPage < totalPagerPages - 1,
                                    modifier = Modifier.testTag("reader_next_page_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                                        contentDescription = "Next Page"
                                    )
                                }

                                // Final Page
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(totalPagerPages - 1)
                                        }
                                    },
                                    enabled = pagerState.currentPage < totalPagerPages - 1,
                                    modifier = Modifier.testTag("reader_final_page_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "Final Page"
                                    )
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
private fun PdfPageView(
    viewModel: BookSphereViewModel,
    page: Int,
    isDarkMode: Boolean
) {
    val density = LocalDensity.current.density
    var bitmap by remember(page) { mutableStateOf<Bitmap?>(null) }
    var isRendering by remember(page) { mutableStateOf(true) }

    LaunchedEffect(page) {
        isRendering = true
        val res = viewModel.pdfRendererManager.renderPage(
            pageIndex = page,
            densityMultiplier = density.coerceIn(1.5f, 3.0f)
        )
        if (res.isSuccess) {
            bitmap = res.getOrNull()
        }
        isRendering = false
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Page ${page + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else if (isRendering) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(40.dp)
                    .testTag("reader_page_rendering_indicator"),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Text(
                text = "Failed to render page ${page + 1}",
                color = if (isDarkMode) Color.White else Color.Black
            )
        }
    }
}
