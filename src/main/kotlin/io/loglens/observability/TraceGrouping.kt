package io.loglens.observability

import io.loglens.model.LogEntry
import io.loglens.model.LogLevel

/** Groups trace-bearing records while preserving the first-seen trace order. */
object TraceGrouping {
    private const val TRACE_ID = "traceId"
    private const val HEADER = "traceGroupHeader"

    fun group(entries: List<LogEntry>): List<LogEntry> {
        val groups = linkedMapOf<String, MutableList<LogEntry>>()
        val ungrouped = mutableListOf<LogEntry>()
        entries.forEach { entry ->
            val traceId = entry.metadata[TRACE_ID]?.takeIf(String::isNotBlank)
            if (traceId == null) ungrouped += entry else groups.getOrPut(traceId) { mutableListOf() } += entry
        }

        return buildList {
            groups.forEach { (traceId, records) ->
                add(
                    LogEntry(
                        level = LogLevel.UNKNOWN,
                        message = "Trace $traceId · ${records.size} events",
                        raw = "",
                        metadata = mapOf(HEADER to "true", TRACE_ID to traceId),
                    ),
                )
                addAll(records)
            }
            addAll(ungrouped)
        }
    }

    fun isHeader(entry: LogEntry): Boolean = entry.metadata[HEADER] == "true"
}
