package io.loglens.observability

import io.loglens.model.LogEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TraceTimelineTest {
    @Test
    fun `returns related events and elapsed time`() {
        val first = entry(1, "2026-10-07T12:00:00Z", "start")
        val second = entry(2, "2026-10-07T12:00:00.125Z", "finish")
        val unrelated = entry(3, "2026-10-07T12:00:01Z", "other", "trace-other")

        val timeline = TraceTimeline.forEntry(first, listOf(first, second, unrelated))

        assertEquals(listOf("start", "finish"), timeline.map { it.entry.message })
        assertNull(timeline[0].deltaMillis)
        assertEquals(125, timeline[1].deltaMillis)
    }

    private fun entry(line: Int, timestamp: String, message: String, trace: String = "trace-main") = LogEntry(
        timestamp = timestamp,
        message = message,
        raw = message,
        lineNumber = line,
        metadata = mapOf("traceId" to trace),
    )
}
