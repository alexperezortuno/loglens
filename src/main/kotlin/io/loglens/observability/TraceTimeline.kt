package io.loglens.observability

import io.loglens.model.LogEntry
import io.loglens.util.Timestamps
import java.time.Duration

/** Builds a related-event timeline for one trace from the currently loaded records. */
object TraceTimeline {
    fun forEntry(entry: LogEntry, source: List<LogEntry>): List<Event> {
        val traceId = entry.metadata[TRACE_ID]?.takeIf(String::isNotBlank) ?: return emptyList()
        var previous: LogEntry? = null
        return source.asSequence()
            .filter { it.metadata[TRACE_ID] == traceId }
            .map { current ->
                val delta = elapsedMillis(previous, current)
                previous = current
                Event(current, delta)
            }
            .toList()
    }

    private fun elapsedMillis(previous: LogEntry?, current: LogEntry): Long? {
        val before = Timestamps.parse(previous?.timestamp) ?: return null
        val now = Timestamps.parse(current.timestamp) ?: return null
        return Duration.between(before, now).toMillis().coerceAtLeast(0)
    }

    data class Event(val entry: LogEntry, val deltaMillis: Long?)

    private const val TRACE_ID = "traceId"
}
