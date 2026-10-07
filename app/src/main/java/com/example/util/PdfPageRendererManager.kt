package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Robust, memory-safe manager for resolving, caching, and rendering PDF pages.
 * Features:
 * - Deterministic disk caching (avoids repeated network downloads)
 * - Thread-safe synchronized access to Android's single-threaded PdfRenderer
 * - Strict Bitmap recycling via LruCache to prevent native memory leaks
 * - Bounded rendering dimensions (capped to avoid OOM on large screens)
 * - Safe resource cleanup for ParcelFileDescriptor & PdfRenderer
 */
class PdfPageRendererManager(private val context: Context) {

    private val cacheDir = File(context.cacheDir, "pdf_reader_cache").apply { mkdirs() }

    // Active session handles
    private var activeLocalPdfFile: File? = null
    private var activeParcelFileDescriptor: ParcelFileDescriptor? = null
    private var activeRenderer: PdfRenderer? = null
    private var activeSourceKey: String? = null
    var totalPages: Int = 0
        private set

    // Maximum 6 page bitmaps cached in memory (~24MB max at ARGB_8888)
    private val pageBitmapCache = object : LruCache<Int, Bitmap>(6) {
        override fun entryRemoved(evicted: Boolean, key: Int?, oldValue: Bitmap?, newValue: Bitmap?) {
            if (evicted && oldValue != null && !oldValue.isRecycled) {
                oldValue.recycle()
            }
        }
    }

    /**
     * Prepares and initializes the PdfRenderer for a given source.
     * If the source matches the currently active renderer, it reuses it instantly without re-downloading.
     */
    suspend fun openPdfSource(source: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            synchronized(this@PdfPageRendererManager) {
                if (activeSourceKey == source && activeRenderer != null) {
                    return@synchronized totalPages
                }

                // Close any existing renderer and clean bitmap cache
                closeInternal()

                val localFile = if (source.startsWith("http://") || source.startsWith("https://")) {
                    downloadOrGetCached(source)
                } else if (source.startsWith("content://") || source.startsWith("file://")) {
                    copyUriToCache(Uri.parse(source))
                } else {
                    val f = File(source)
                    if (f.exists()) f else throw IllegalArgumentException("PDF file not found at path: $source")
                }

                if (!localFile.exists() || localFile.length() == 0L) {
                    throw IllegalStateException("PDF file is empty or corrupted")
                }

                val pfd = ParcelFileDescriptor.open(localFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = try {
                    PdfRenderer(pfd)
                } catch (e: Exception) {
                    pfd.close()
                    throw e
                }

                val count = renderer.pageCount
                activeLocalPdfFile = localFile
                activeParcelFileDescriptor = pfd
                activeRenderer = renderer
                activeSourceKey = source
                totalPages = count

                AppLogger.d("PdfRendererManager", "Opened PDF with $count pages from source: $source")
                count
            }
        }
    }

    /**
     * Renders a specific 0-indexed page to an Android Bitmap.
     * Guaranteed opaque background and bounded dimensions.
     */
    suspend fun renderPage(pageIndex: Int, densityMultiplier: Float = 2.0f): Result<Bitmap> = withContext(Dispatchers.IO) {
        runCatching {
            synchronized(this@PdfPageRendererManager) {
                val cached = pageBitmapCache.get(pageIndex)
                if (cached != null && !cached.isRecycled) {
                    return@synchronized cached
                }

                val renderer = activeRenderer
                    ?: throw IllegalStateException("PdfRenderer is not initialized. Call openPdfSource first.")

                if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
                    throw IndexOutOfBoundsException("Page index $pageIndex is out of range [0, ${renderer.pageCount})")
                }

                var pdfPage: PdfRenderer.Page? = null
                try {
                    pdfPage = renderer.openPage(pageIndex)
                    
                    // Cap maximum dimensions to 1440px to protect heap memory on high-DPI devices
                    val rawWidth = (pdfPage.width * densityMultiplier).toInt()
                    val rawHeight = (pdfPage.height * densityMultiplier).toInt()
                    val scaleFactor = if (rawWidth > 1440) 1440f / rawWidth else 1.0f
                    val targetWidth = (rawWidth * scaleFactor).toInt().coerceIn(100, 1440)
                    val targetHeight = (rawHeight * scaleFactor).toInt().coerceIn(100, 2560)

                    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)

                    pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    pageBitmapCache.put(pageIndex, bitmap)
                    bitmap
                } finally {
                    try { pdfPage?.close() } catch (_: Exception) {}
                }
            }
        }
    }

    /**
     * Pre-fetches adjacent pages (current-1, current+1) into cache for smooth scrolling.
     */
    suspend fun prefetchAdjacentPages(currentPage: Int, maxPages: Int) = withContext(Dispatchers.IO) {
        val pagesToPrefetch = listOf(currentPage - 1, currentPage + 1)
            .filter { it in 0 until maxPages }
        for (p in pagesToPrefetch) {
            if (pageBitmapCache.get(p) == null) {
                renderPage(p)
            }
        }
    }

    private fun downloadOrGetCached(httpUrl: String): File {
        val hash = md5(httpUrl)
        val cachedFile = File(cacheDir, "doc_$hash.pdf")

        if (cachedFile.exists() && cachedFile.length() > 512L) {
            return cachedFile
        }

        val tempFile = File(cacheDir, "doc_${hash}_temp_${System.currentTimeMillis()}.pdf")
        var conn: HttpURLConnection? = null
        try {
            val url = URL(httpUrl)
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.instanceFollowRedirects = true
            conn.requestMethod = "GET"
            conn.connect()

            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${conn.responseCode} while downloading PDF")
            }

            conn.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (tempFile.exists() && tempFile.length() > 0) {
                if (cachedFile.exists()) cachedFile.delete()
                tempFile.renameTo(cachedFile)
            }
        } catch (e: Exception) {
            try { tempFile.delete() } catch (_: Exception) {}
            throw e
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }

        return cachedFile
    }

    private fun copyUriToCache(uri: Uri): File {
        val hash = md5(uri.toString())
        val cachedFile = File(cacheDir, "local_$hash.pdf")
        if (cachedFile.exists() && cachedFile.length() > 0) {
            return cachedFile
        }
        val tempFile = File(cacheDir, "local_${hash}_temp.pdf")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Could not open input stream for URI: $uri")

            tempFile.renameTo(cachedFile)
        } catch (e: Exception) {
            try { tempFile.delete() } catch (_: Exception) {}
            throw e
        }
        return cachedFile
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun closeInternal() {
        try { pageBitmapCache.evictAll() } catch (_: Exception) {}
        try { activeRenderer?.close() } catch (_: Exception) {}
        try { activeParcelFileDescriptor?.close() } catch (_: Exception) {}
        activeRenderer = null
        activeParcelFileDescriptor = null
        activeSourceKey = null
        totalPages = 0
    }

    fun close() {
        synchronized(this) {
            closeInternal()
        }
    }
}
