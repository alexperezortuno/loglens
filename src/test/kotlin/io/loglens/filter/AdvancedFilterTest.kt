package io.loglens.filter

import io.loglens.model.LogEntry
import io.loglens.model.LogLevel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdvancedFilterTest {

    @Test
    fun `matches logger thread and inclusive time range`() {
        val filter = AdvancedFilter(
            loggerContains = "PaymentService",
            threadContains = "worker",
            fromTimestamp = "2026-01-05 14:00:00",
            toTimestamp = "2026-01-05 15:00:00",
        )
        val entry = entry(
            timestamp = "2026-01-05 14:30:00.123",
            logger = "com.example.PaymentService",
            thread = "worker-1",
        )

        assertTrue(filter.matches(entry))
        assertFalse(filter.matches(entry.copy(thread = "main")))
        assertFalse(filter.matches(entry.copy(timestamp = "2026-01-05 15:00:01")))
    }

    @Test
    fun `exclusion terms veto matching records`() {
        val filter = AdvancedFilter(excludeTerms = "healthcheck,heartbeat")

        assertFalse(filter.matches(entry(message = "heartbeat completed")))
        assertTrue(filter.matches(entry(message = "payment completed")))
    }

    @Test
    fun `correlation filter matches trace span and request identifiers`() {
        val entry = LogEntry(
            message = "charged",
            raw = "charged",
            metadata = mapOf("traceId" to "trace-42", "spanId" to "span-7", "requestId" to "req-9"),
        )

        assertTrue(AdvancedFilter(correlationContains = "trace-42").matches(entry))
        assertTrue(AdvancedFilter(correlationContains = "span-7").matches(entry))
        assertTrue(AdvancedFilter(correlationContains = "req-9").matches(entry))
        assertFalse(AdvancedFilter(correlationContains = "missing").matches(entry))
    }

    @Test
    fun `time range does not invent dates for time-only timestamps`() {
        val filter = AdvancedFilter(fromTimestamp = "2026-01-05")

        assertFalse(filter.matches(entry(timestamp = "14:30:00.000")))
    }

    private fun entry(
        timestamp: String? = "2026-01-05 14:30:00",
        logger: String? = "com.example.App",
        thread: String? = "main",
        message: String = "ready",
    ) = LogEntry(
        timestamp = timestamp,
        logger = logger,
        thread = thread,
        message = message,
        raw = message,
        level = LogLevel.INFO,
    )
}
