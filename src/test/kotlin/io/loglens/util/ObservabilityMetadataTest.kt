package io.loglens.util

import io.loglens.model.LogEntry
import kotlin.test.Test
import kotlin.test.assertEquals

class ObservabilityMetadataTest {
    @Test
    fun `normalizes json aliases into canonical observability fields`() {
        val entry = ObservabilityMetadata.enrich(
            LogEntry(
                message = "payment started",
                raw = "payment started",
                metadata = mapOf("trace_id" to "abc", "service.name" to "payments"),
            ),
        )

        assertEquals("abc", entry.metadata["traceId"])
        assertEquals("payments", entry.metadata["service"])
    }

    @Test
    fun `extracts key value fields from text logs`() {
        val entry = ObservabilityMetadata.enrich(
            LogEntry(
                message = "request completed traceId=abc123 spanId=def456 host=node-a",
                raw = "request completed traceId=abc123 spanId=def456 host=node-a",
            ),
        )

        assertEquals("abc123", entry.metadata["traceId"])
        assertEquals("def456", entry.metadata["spanId"])
        assertEquals("node-a", entry.metadata["host"])
    }
}
