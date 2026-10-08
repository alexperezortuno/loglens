package io.loglens.observability

import io.loglens.model.LogEntry
import io.loglens.model.LogLevel
import io.loglens.model.ThrowableInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class LogStatisticsTest {
    @Test
    fun `calculates level errors exceptions loggers and minute buckets`() {
        val entries = listOf(
            entry("2026-10-07T12:00:01Z", LogLevel.INFO, "api", null),
            entry("2026-10-07T12:00:45Z", LogLevel.ERROR, "api", "TimeoutException"),
            entry("2026-10-07T12:01:01Z", LogLevel.FATAL, "worker", "TimeoutException"),
        )

        val stats = LogStatistics.calculate(entries)

        assertEquals(3, stats.totalEntries)
        assertEquals(1, stats.levelCounts[LogLevel.INFO])
        assertEquals(2, stats.errorCount)
        assertEquals(2, stats.exceptionCounts["TimeoutException"])
        assertEquals(2, stats.loggerCounts["api"])
        assertEquals(2, stats.levelsOverTime.size)
    }

    private fun entry(timestamp: String, level: LogLevel, logger: String, exception: String?) = LogEntry(
        timestamp = timestamp,
        level = level,
        logger = logger,
        message = "message",
        raw = "message",
        throwable = exception?.let { ThrowableInfo(className = it, message = null, frames = emptyList(), raw = it) },
    )
}
