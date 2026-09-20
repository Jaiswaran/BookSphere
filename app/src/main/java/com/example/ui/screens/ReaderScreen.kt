package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.net.Uri
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    book: Book,
    isPurchased: Boolean,
    onNavigateBack: () -> Unit,
    onPurchase: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var isDarkMode by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    
    val isPreviewMode = !book.isFree && !isPurchased
    val maxAllowedPages = if (isPreviewMode) book.previewPages else book.totalPages
    val actualPages = if (maxAllowedPages > 0) maxAllowedPages else 1
    
    // We add +1 page to show the "Paywall" if in preview mode and limit reached
    val pagerState = rememberPagerState(pageCount = { if (isPreviewMode) actualPages + 1 else actualPages })
    
    val backgroundColor = if (isDarkMode) Color.Black else Color.White
    val textColor = if (isDarkMode) Color.White else Color.Black
    
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
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
                                if (pagerState.currentPage < pagerState.pageCount - 1) {
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
            if (isPreviewMode && page == actualPages) {
                // Paywall Page
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "End of Free Preview",
                        style = MaterialTheme.typography.headlineMedium,
                        color = textColor,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Buy now to unlock the rest of the book and support the author.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = onPurchase,
                        modifier = Modifier.fillMaxWidth(0.8f).height(56.dp)
                    ) {
                        Text("Buy Now for ₹${book.price}")
                    }
                }
            } else {
                // Render Page Content
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showControls = !showControls }
                ) {
                    if (book.pdfUri != null) {
                        // PDF Rendering
                        var bitmap by remember { mutableStateOf<Bitmap?>(null) }
                        
                        LaunchedEffect(page) {
                            withContext(Dispatchers.IO) {
                                try {
                                    val uri = Uri.parse(book.pdfUri)
                                    val fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
                                    if (fileDescriptor != null) {
                                        val renderer = PdfRenderer(fileDescriptor)
                                        if (page < renderer.pageCount) {
                                            val pdfPage = renderer.openPage(page)
                                            // Increase resolution for clarity
                                            val density = context.resources.displayMetrics.density
                                            val renderBitmap = Bitmap.createBitmap(
                                                (pdfPage.width * density).toInt(),
                                                (pdfPage.height * density).toInt(),
                                                Bitmap.Config.ARGB_8888
                                            )
                                            // Fill with white before rendering to avoid black background on transparent PDFs
                                            renderBitmap.eraseColor(android.graphics.Color.WHITE)
                                            pdfPage.render(renderBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                            pdfPage.close()
                                            bitmap = renderBitmap
                                        }
                                        renderer.close()
                                        fileDescriptor.close()
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                        
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap!!.asImageBitmap(),
                                contentDescription = "Page ${page + 1}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    } else if (book.contentPages.isNotEmpty() && book.contentPages.getOrNull(page)?.startsWith("http") == true) {
                        // Image Comic Rendering
                        AsyncImage(
                            model = book.contentPages[page],
                            contentDescription = "Page ${page + 1}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop // Fit or crop depending on comic style
                        )
                    } else {
                        // Text Rendering
                        val pageContent = book.contentPages.getOrNull(page) ?: "Page content missing."
                        Text(
                            text = pageContent,
                            color = textColor,
                            modifier = Modifier.padding(24.dp).padding(top = 48.dp, bottom = 48.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            lineHeight = androidx.compose.ui.unit.TextUnit(24f, androidx.compose.ui.unit.TextUnitType.Sp)
                        )
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
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                // Top Nav Bar
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp).statusBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { isDarkMode = !isDarkMode }) {
                            Icon(if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode, contentDescription = "Toggle Theme")
                        }
                    }
                }
                
                // Bottom Progress
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).navigationBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        val displayPage = if (pagerState.currentPage >= actualPages) actualPages else pagerState.currentPage + 1
                        Text(
                            text = "Page $displayPage of $actualPages",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}
