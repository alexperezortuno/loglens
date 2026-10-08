package io.loglens.observability

import io.loglens.model.LogEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TraceGroupingTest {
    @Test
    fun `groups by first seen trace and keeps ungrouped records`() {
        val entries = listOf(
            entry(1, "trace-b", "b1"),
            entry(2, "trace-a", "a1"),
            entry(3, "trace-b", "b2"),
            entry(4, null, "plain"),
        )

        val grouped = TraceGrouping.group(entries)

        assertEquals(listOf("Trace trace-b · 2 events", "b1", "b2", "Trace trace-a · 1 events", "a1", "plain"), grouped.map { it.message })
        assertTrue(TraceGrouping.isHeader(grouped[0]))
        assertFalse(TraceGrouping.isHeader(grouped[1]))
    }

    private fun entry(line: Int, traceId: String?, message: String) = LogEntry(
        message = message,
        raw = message,
        lineNumber = line,
        metadata = traceId?.let { mapOf("traceId" to it) } ?: emptyMap(),
    )
}
