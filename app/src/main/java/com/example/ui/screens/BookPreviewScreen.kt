package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.Book
import com.example.ui.components.bookSpineEffect
import com.example.util.CurrencyUtils

@Composable
fun BookPreviewScreen(
    book: Book,
    onBack: () -> Unit,
    onBuyNow: (Book) -> Unit,
    onReadBook: (Book) -> Unit = {}
) {
    var currentPageIndex by remember { mutableIntStateOf(0) }
    
    val pages = remember(book) {
        if (book.contentPages.isNotEmpty()) {
            book.contentPages.mapIndexed { index, content ->
                val lines = content.split("\n\n")
                val text = lines.firstOrNull() ?: ""
                val remainder = text.drop(1)
                com.example.model.SamplePage(
                    pageNumber = index + 1,
                    chapterTitle = if (book.genre.contains("Comic")) "Panel ${index + 1}" else "Page ${index + 1}",
                    dropCapLetter = text.take(1),
                    firstSentenceRemainder = remainder,
                    paragraphs = lines.drop(1)
                )
            }
        } else if (book.samplePages.isNotEmpty()) {
            book.samplePages
        } else {
            listOf(
                com.example.data.SampleData.sampleCelestialCartographer.samplePages[0],
                com.example.data.SampleData.sampleCelestialCartographer.samplePages[1]
            )
        }
    }

    val activePage = pages[currentPageIndex % pages.size]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Sticky Top Preview Header Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close Preview")
                    }
                    Column {
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (book.isFree) "Full Free Edition • ${book.totalPages} Pages" else "Sample Edition • 20 Pages Available",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = if (book.isFree) Color(0xFF4CAF50) else MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = if (book.isFree) "FREE" else CurrencyUtils.formatInr(book.price),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (book.isFree) Color.White else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Main Scrollable Area with Dual Parchment Book Layout
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Book Meta Pill Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Visibility,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (book.isFree) "Full Reader Mode" else "Reader-First Preview Mode",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Text(
                    text = "${book.totalPages} Total Pages",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Realistic Tactile Parchment Book Paper Surface
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFFAF7F0), // Authentic book paper parchment tone
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE3DCCE))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Running Book Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = book.author.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.5.sp,
                                fontSize = 9.sp
                            ),
                            color = Color(0xFF8A8275)
                        )
                        Text(
                            text = "PAGE ${activePage.pageNumber}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.sp,
                                fontSize = 9.sp
                            ),
                            color = Color(0xFF8A8275)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Chapter Title
                    Text(
                        text = activePage.chapterTitle,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp
                        ),
                        color = Color(0xFF1F2937),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Delicate decorative divider
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(1.dp)
                            .background(Color(0xFFC59B27))
                            .align(Alignment.CenterHorizontally)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Drop-Cap paragraph
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = activePage.dropCapLetter,
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontFamily = FontFamily.Serif,
                                fontSize = 52.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 48.sp
                            ),
                            color = Color(0xFF1A2B4C),
                            modifier = Modifier.padding(end = 6.dp, top = 2.dp)
                        )
                        Text(
                            text = activePage.firstSentenceRemainder,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.Serif,
                                fontSize = 16.sp,
                                lineHeight = 26.sp
                            ),
                            color = Color(0xFF2C2A29)
                        )
                    }

                    // Remaining Paragraphs
                    activePage.paragraphs.forEach { paragraph ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "    $paragraph",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.Serif,
                                fontSize = 16.sp,
                                lineHeight = 26.sp
                            ),
                            color = Color(0xFF2C2A29)
                        )
                    }

                    if (activePage.marginNote != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFF3EDE0),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDFD6C2))
                        ) {
                            Text(
                                text = "✎ ${activePage.marginNote}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontStyle = FontStyle.Italic,
                                    fontSize = 12.sp
                                ),
                                color = Color(0xFF5C5242),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    if (activePage.footnote != null) {
                        Spacer(modifier = Modifier.height(18.dp))
                        HorizontalDivider(color = Color(0xFFD6CEBC), thickness = 0.8.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = activePage.footnote,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontStyle = FontStyle.Italic,
                                fontSize = 11.sp
                            ),
                            color = Color(0xFF756C5F)
                        )
                    }
                }
            }

            // Pager Controls: Prev / Page Numbers / Next
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        if (currentPageIndex > 0) currentPageIndex--
                    },
                    enabled = currentPageIndex > 0,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Prev Page", style = MaterialTheme.typography.labelSmall)
                }

                Text(
                    text = if (book.isFree) "Page ${activePage.pageNumber} of ${pages.size}" else "Page ${activePage.pageNumber} of ${pages.size} Sample",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedButton(
                    onClick = {
                        if (currentPageIndex < pages.size - 1) currentPageIndex++
                    },
                    enabled = currentPageIndex < pages.size - 1,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Next Page", style = MaterialTheme.typography.labelSmall)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }

            // Fair System Guarantee Banner
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Fair Creator Commitment",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "70% (${CurrencyUtils.formatInr(book.price * 0.70)}) goes straight to ${book.author}. Unlocks all ${book.totalPages} pages permanently.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Sticky Bottom CTA Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(imageVector = Icons.Default.LockOpen, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Instant Unlocked Reading • Includes Audio Edition",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = CurrencyUtils.formatInr(book.price),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (book.isFree || book.isPurchased) {
                    Button(
                        onClick = { onReadBook(book) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        shape = CircleShape,
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (book.genre.contains("Comic")) "Read Full Comic" else "Read Full Story",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onReadBook(book) },
                            modifier = Modifier.weight(1f),
                            shape = CircleShape,
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text("Free Preview", style = MaterialTheme.typography.labelLarge)
                        }
                        Button(
                            onClick = { onBuyNow(book) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = CircleShape,
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.ShoppingBag, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Buy Now",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}
