package com.example.stability

import com.example.model.Book
import com.example.model.DataState
import com.example.util.AppError
import com.example.util.AppLogger
import com.example.util.CurrencyUtils
import com.example.util.ErrorCategory
import com.example.util.PublicationValidator
import org.junit.Assert.*
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class StabilityAndDataStateTest {

    @Test
    fun testAppError_NetworkErrorMapping() {
        val hostEx = UnknownHostException("Unable to resolve host supabase.co")
        val appError = AppError.from(hostEx)

        assertEquals(ErrorCategory.NETWORK_ERROR, appError.category)
        assertTrue(appError.userMessage.contains("internet connection", ignoreCase = true))
        assertTrue(appError.canRetry)

        val timeoutEx = SocketTimeoutException("Read timed out")
        val timeoutError = AppError.from(timeoutEx)
        assertEquals(ErrorCategory.NETWORK_ERROR, timeoutError.category)
    }

    @Test
    fun testAppError_AuthErrorMapping() {
        val authEx = RuntimeException("JWT expired: invalid_grant")
        val appError = AppError.from(authEx)

        assertEquals(ErrorCategory.AUTH_ERROR, appError.category)
        assertTrue(appError.userMessage.contains("session expired", ignoreCase = true))
    }

    @Test
    fun testAppError_PermissionErrorMapping() {
        val permEx = RuntimeException("Permission Denied: row-level security violation")
        val appError = AppError.from(permEx)

        assertEquals(ErrorCategory.PERMISSION_ERROR, appError.category)
        assertFalse(appError.canRetry)
    }

    @Test
    fun testAppError_ValidationAndStorageMapping() {
        val valEx = IllegalArgumentException("Invalid preview page count: 0")
        val valError = AppError.from(valEx)
        assertEquals(ErrorCategory.VALIDATION_ERROR, valError.category)

        val stEx = RuntimeException("Upload failed on bucket 'covers'")
        val stError = AppError.from(stEx)
        assertEquals(ErrorCategory.STORAGE_ERROR, stError.category)
    }

    @Test
    fun testDataState_Variants() {
        val emptyState: DataState<List<Book>> = DataState.Empty
        assertTrue(emptyState is DataState.Empty)

        val successState: DataState<List<String>> = DataState.Success(listOf("Book 1", "Book 2"))
        assertTrue(successState is DataState.Success)
        assertEquals(2, (successState as DataState.Success).data.size)

        val offlineState: DataState<List<String>> = DataState.Offline(listOf("Cached Book"))
        assertTrue(offlineState is DataState.Offline)
        assertEquals(1, (offlineState as DataState.Offline).data.size)
        assertTrue((offlineState as DataState.Offline).message.contains("cached", ignoreCase = true))

        val errorState: DataState<List<String>> = DataState.Error(
            AppError(
                category = ErrorCategory.NETWORK_ERROR,
                userMessage = "No network"
            )
        )
        assertTrue(errorState is DataState.Error)
        assertEquals(ErrorCategory.NETWORK_ERROR, (errorState as DataState.Error).error.category)
    }

    @Test
    fun testPublicationValidator_BoundaryRules() {
        // Free book with 0 price -> valid
        val freeValid = PublicationValidator.validateMetadata(
            title = "Open Source Almanac",
            author = "Dev",
            description = "A free programming compendium for readers.",
            genre = "Technology",
            language = "English",
            price = 0.0,
            isFree = true,
            previewPages = 3,
            totalPages = 100
        )
        assertTrue(freeValid.isSuccess)

        // Paid book with 0 price -> invalid
        val paidZeroPrice = PublicationValidator.validateMetadata(
            title = "Paid Masterpiece",
            author = "Dev",
            description = "Should have price > 0",
            genre = "Technology",
            language = "English",
            price = 0.0,
            isFree = false,
            previewPages = 3,
            totalPages = 100
        )
        assertFalse(paidZeroPrice.isSuccess)

        // Preview pages greater than total pages -> invalid
        val previewOverflow = PublicationValidator.validateMetadata(
            title = "Short Story",
            author = "Dev",
            description = "A very short story.",
            genre = "Fiction",
            language = "English",
            price = 99.0,
            isFree = false,
            previewPages = 20,
            totalPages = 10
        )
        assertFalse(previewOverflow.isSuccess)
    }

    @Test
    fun testCurrencyFormatting() {
        assertEquals("₹499", CurrencyUtils.formatInr(499.0))
        assertEquals("₹0", CurrencyUtils.formatInr(0.0))
        assertEquals("₹1,250", CurrencyUtils.formatInr(1250.0))
    }
}
