package com.example

import com.example.model.Book
import com.example.model.BookStatus
import com.example.model.RoyaltyConfig
import com.example.util.CurrencyUtils
import com.example.util.PublicationValidator
import org.junit.Assert.*
import org.junit.Test

/**
 * Application domain tests verifying fundamental BookSphere business logic,
 * royalty splits, currency formatting, and publication validation constraints.
 */
class ExampleUnitTest {

    @Test
    fun testRoyaltySplit_MatchesAuthorEightyFivePercent() {
        val retailPrice = 500.0
        val authorRevenue = RoyaltyConfig.calculateAuthorNet(retailPrice)
        val platformFee = RoyaltyConfig.calculatePlatformFee(retailPrice)

        assertEquals(425.0, authorRevenue, 0.001)
        assertEquals(75.0, platformFee, 0.001)
        assertEquals(retailPrice, authorRevenue + platformFee, 0.001)
    }

    @Test
    fun testCurrencyFormatting() {
        assertEquals("₹499", CurrencyUtils.formatInr(499.0))
        assertEquals("₹0", CurrencyUtils.formatInr(0.0))
    }

    @Test
    fun testFreeBookZeroPriceValidation() {
        val validation = PublicationValidator.validateMetadata(
            title = "Open Horizons",
            author = "Elena Rostova",
            description = "A free journey through open space.",
            genre = "Sci-Fi",
            language = "English",
            price = 0.0,
            isFree = true,
            previewPages = 3,
            totalPages = 120
        )
        assertTrue(validation.isSuccess)
    }
}
