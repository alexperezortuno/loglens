package io.loglens.observability

import io.loglens.model.LogEntry
import io.loglens.model.LogLevel
import io.loglens.util.Timestamps
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Snapshot statistics calculated from the records currently available to the viewer. */
object LogStatistics {
    fun calculate(entries: List<LogEntry>): Snapshot {
        val levels = entries.groupingBy { it.level }.eachCount()
        val exceptions = entries.mapNotNull { it.throwable?.className }
            .groupingBy { it }
            .eachCount()
        val loggers = entries.mapNotNull { it.logger?.takeIf(String::isNotBlank) }
            .groupingBy { it }
            .eachCount()
        val buckets = entries.mapNotNull { entry ->
            Timestamps.parse(entry.timestamp)?.let { it.truncatedTo(ChronoUnit.MINUTES) to entry.level }
        }.groupBy({ it.first }, { it.second })
            .toSortedMap()
            .mapValues { (_, values) -> values.groupingBy { it }.eachCount() }

        return Snapshot(
            totalEntries = entries.size,
            levelCounts = levels,
            errorCount = entries.count { it.level == LogLevel.ERROR || it.level == LogLevel.FATAL },
            exceptionCounts = exceptions,
            loggerCounts = loggers,
            levelsOverTime = buckets,
        )
    }

    data class Snapshot(
        val totalEntries: Int,
        val levelCounts: Map<LogLevel, Int>,
        val errorCount: Int,
        val exceptionCounts: Map<String, Int>,
        val loggerCounts: Map<String, Int>,
        val levelsOverTime: Map<Instant, Map<LogLevel, Int>>,
    )
}
