package io.loglens.service

import io.loglens.model.LogEntry
import io.loglens.model.LogLevel
import io.loglens.util.RawText
import io.loglens.util.Timestamps

/**
 * Compact, append-only index for records already scanned by the background
 * reader. It deliberately stores metadata rather than full LogEntry objects;
 * the latter remain bounded by the viewer cache.
 */
class LogIndex {
    private val lock = Any()
    private val records = ArrayList<Record>()
    private val indexedStarts = HashSet<Long>()

    @Volatile
    var indexedBytes: Long = 0
        private set

    @Volatile
    var fileSize: Long = 0
        private set

    fun add(startByte: Long, endByte: Long, entry: LogEntry) {
        synchronized(lock) {
            if (!indexedStarts.add(startByte)) return
            records += Record(
                lineNumber = entry.lineNumber ?: 0,
                startByte = startByte,
                endByte = endByte,
                level = entry.level,
                timestamp = entry.timestamp,
                logger = entry.logger,
                thread = entry.thread,
                searchable = RawText.searchable(entry).take(MAX_SEARCHABLE_CHARS),
            )
            indexedBytes = maxOf(indexedBytes, endByte)
        }
    }

    fun setFileSize(size: Long) {
        fileSize = size
    }

    fun size(): Int = synchronized(lock) { records.size }

    fun snapshot(): List<Record> = synchronized(lock) { records.toList() }

    fun clear() {
        synchronized(lock) {
            records.clear()
            indexedStarts.clear()
            indexedBytes = 0
            fileSize = 0
        }
    }

    /** Metadata-only filtering used by the viewer and background searches. */
    fun matching(
        levels: Set<LogLevel> = emptySet(),
        loggerContains: String = "",
        threadContains: String = "",
        fromTimestamp: String = "",
        toTimestamp: String = "",
        excludeTerms: List<String> = emptyList(),
        search: (String) -> Boolean = { true },
    ): List<Record> = snapshot().filter { record ->
        (levels.isEmpty() || record.level in levels) &&
            (loggerContains.isBlank() || record.logger.orEmpty().contains(loggerContains.trim(), true)) &&
            (threadContains.isBlank() || record.thread.orEmpty().contains(threadContains.trim(), true)) &&
            timestampMatches(record.timestamp, fromTimestamp, toTimestamp) &&
            excludeTerms.none { term -> record.searchable.contains(term, true) } &&
            search(record.searchable)
    }

    private fun timestampMatches(timestamp: String?, from: String, to: String): Boolean {
        if (from.isBlank() && to.isBlank()) return true
        val value = Timestamps.parse(timestamp) ?: return false
        val lower = Timestamps.parseBoundary(from)
        val upper = Timestamps.parseBoundary(to)
        return (lower == null || !value.isBefore(lower)) && (upper == null || !value.isAfter(upper))
    }

    data class Record(
        val lineNumber: Int,
        val startByte: Long,
        val endByte: Long,
        val level: LogLevel,
        val timestamp: String?,
        val logger: String?,
        val thread: String?,
        val searchable: String,
    )

    private companion object {
        const val MAX_SEARCHABLE_CHARS = 1024
    }
}
