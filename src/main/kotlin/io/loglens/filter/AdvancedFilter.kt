package io.loglens.filter

import io.loglens.model.LogEntry
import io.loglens.util.RawText
import io.loglens.util.Timestamps

/** Structured filters applied after severity and before/alongside text search. */
data class AdvancedFilter(
    val loggerContains: String = "",
    val threadContains: String = "",
    val excludeTerms: String = "",
    val fromTimestamp: String = "",
    val toTimestamp: String = "",
) {

    fun matches(entry: LogEntry): Boolean {
        if (loggerContains.isNotBlank() && !entry.logger.orEmpty().contains(loggerContains.trim(), ignoreCase = true)) {
            return false
        }
        if (threadContains.isNotBlank() && !entry.thread.orEmpty().contains(threadContains.trim(), ignoreCase = true)) {
            return false
        }

        val haystack = RawText.searchable(entry)
        val exclusions = excludeTerms.split(',', '\n')
            .map(String::trim)
            .filter(String::isNotEmpty)
        if (exclusions.any { haystack.contains(it, ignoreCase = true) }) return false

        val from = Timestamps.parseBoundary(fromTimestamp)
        val to = Timestamps.parseBoundary(toTimestamp)
        if (from != null || to != null) {
            val timestamp = Timestamps.parse(entry.timestamp) ?: return false
            if (from != null && timestamp.isBefore(from)) return false
            if (to != null && timestamp.isAfter(to)) return false
        }
        return true
    }

    companion object {
        val EMPTY = AdvancedFilter()
    }
}
