package com.example.concurrency

import com.example.data.remote.LibraryDto
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Task 8: Concurrency and Idempotency Tests.
 * Simulates concurrent purchases and entitlement assignments happening close together.
 * Verifies:
 * - No lost updates on sales count (atomic increments)
 * - No duplicate entitlements created for the same user-book pair (idempotency)
 * - Consistent library state across simultaneous operations
 */
class ConcurrentEntitlementTest {

    data class ServerBookState(
        val bookId: String,
        val copiesSold: AtomicInteger = AtomicInteger(0)
    )

    class SimulatedServerBackend {
        val books = ConcurrentHashMap<String, ServerBookState>()
        // Key: "$userId:$bookId" -> LibraryDto (Primary Key / Unique Constraint)
        val entitlements = ConcurrentHashMap<String, LibraryDto>()

        fun processVerifiedPurchase(userId: String, bookId: String, orderId: String): Boolean {
            val key = "$userId:$bookId"

            // Idempotent entitlement check: if already exists, do not double-increment sales or create duplicate
            val isNewEntitlement = entitlements.putIfAbsent(
                key,
                LibraryDto(userId = userId, bookId = bookId, purchasedAt = System.currentTimeMillis().toString())
            ) == null

            if (isNewEntitlement) {
                val book = books.computeIfAbsent(bookId) { ServerBookState(bookId) }
                book.copiesSold.incrementAndGet()
            }
            return true
        }
    }

    @Test
    fun testConcurrentPurchasesByDifferentUsers_NoLostSalesCount() = runBlocking {
        val backend = SimulatedServerBackend()
        val bookId = "b_concurrent_1"
        backend.books[bookId] = ServerBookState(bookId, AtomicInteger(10)) // starting with 10 sales

        val buyer1 = "user_alpha"
        val buyer2 = "user_beta"

        // Execute concurrent purchase entitlements
        coroutineScope {
            val job1 = async { backend.processVerifiedPurchase(buyer1, bookId, "order_101") }
            val job2 = async { backend.processVerifiedPurchase(buyer2, bookId, "order_102") }
            awaitAll(job1, job2)
        }

        // Assert no lost updates
        val finalSales = backend.books[bookId]?.copiesSold?.get()
        assertEquals("Sales count must increment atomically by exactly 2 (10 -> 12)", 12, finalSales)

        // Assert consistent library states
        assertNotNull(backend.entitlements["$buyer1:$bookId"])
        assertNotNull(backend.entitlements["$buyer2:$bookId"])
        assertEquals(2, backend.entitlements.size)
    }

    @Test
    fun testRapidDuplicatePurchasesBySameUser_PreventsDuplicateEntitlementAndDoubleIncrement() = runBlocking {
        val backend = SimulatedServerBackend()
        val bookId = "b_duplicate_test"
        backend.books[bookId] = ServerBookState(bookId, AtomicInteger(0))

        val buyer = "user_gamma"

        // Simulate rapid double-tap / retry of the exact same purchase order
        coroutineScope {
            val attempt1 = async { backend.processVerifiedPurchase(buyer, bookId, "order_duplicate_1") }
            val attempt2 = async { backend.processVerifiedPurchase(buyer, bookId, "order_duplicate_1") }
            val attempt3 = async { backend.processVerifiedPurchase(buyer, bookId, "order_duplicate_1") }
            awaitAll(attempt1, attempt2, attempt3)
        }

        // Assert unique constraint kept only 1 entitlement
        val countForUser = backend.entitlements.count { it.key == "$buyer:$bookId" }
        assertEquals("Must create only 1 entitlement for the user", 1, countForUser)

        // Assert copies sold only incremented once
        val finalSales = backend.books[bookId]?.copiesSold?.get()
        assertEquals("Copies sold must increment exactly once despite 3 concurrent retries", 1, finalSales)
    }
}
