package io.loglens.search

import io.loglens.model.LogEntry
import io.loglens.model.LogLevel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogSearchTest {

    private fun entry(message: String, logger: String? = null, raw: String = message): LogEntry =
        LogEntry(message = message, raw = raw, level = LogLevel.INFO, logger = logger)

    @Test
    fun `empty query matches every entry`() {
        val search = LogSearch()
        assertTrue(search.matches(entry("anything")))
        assertTrue(search.matches(entry("something completely different")))
    }

    @Test
    fun `plain text search is case insensitive by default`() {
        val search = LogSearch(query = "Payment")
        assertTrue(search.matches(entry("PaymentService failed")))
        assertTrue(search.matches(entry("paymentservice failed")))
        assertFalse(search.matches(entry("OrderService succeeded")))
    }

    @Test
    fun `case-sensitive mode honours casing`() {
        val search = LogSearch(query = "Payment", caseSensitive = true)
        assertTrue(search.matches(entry("PaymentService")))
        assertFalse(search.matches(entry("paymentservice")))
    }

    @Test
    fun `regex mode uses the provided expression`() {
        val search = LogSearch(query = "ERR.*timeout", mode = LogSearch.Mode.REGEX)
        assertTrue(search.matches(entry("ERROR Socket timeout occurred")))
        assertFalse(search.matches(entry("INFO done")))
    }

    @Test
    fun `case-sensitive regex mode honours casing`() {
        val search = LogSearch(query = "Error.*", mode = LogSearch.Mode.REGEX, caseSensitive = true)
        assertTrue(search.matches(entry("Error happened")))
        assertFalse(search.matches(entry("error happened")))
    }

    @Test
    fun `invalid regex is treated as a no match instead of throwing`() {
        val search = LogSearch(query = "[unterminated", mode = LogSearch.Mode.REGEX)
        // Should not throw and should not match.
        assertFalse(search.matches(entry("ERROR something")))
    }

    @Test
    fun `search matches against the logger and raw text too`() {
        val search = LogSearch(query = "PaymentService")
        assertTrue(search.matches(entry("Boom", logger = "com.example.PaymentService")))
        // rawText fallback
        assertTrue(
            search.matches(
                LogEntry(message = "x", raw = "PaymentService crashed", level = LogLevel.INFO),
            ),
        )
    }
}