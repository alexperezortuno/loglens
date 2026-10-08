package io.loglens.service

import io.loglens.model.LogEntry
import io.loglens.model.LogLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogIndexTest {
    @Test
    fun `stores compact metadata and filters records`() {
        val index = LogIndex()
        index.add(0, 40, entry(1, LogLevel.INFO, "api", "worker-1", "started"))
        index.add(40, 90, entry(2, LogLevel.ERROR, "payments", "worker-2", "timeout"))

        val matches = index.matching(
            levels = setOf(LogLevel.ERROR),
            loggerContains = "pay",
            search = { it.contains("timeout") },
        )

        assertEquals(1, matches.size)
        assertEquals(2, matches.single().lineNumber)
        assertEquals(90, index.indexedBytes)
    }

    @Test
    fun `does not duplicate records when page loading and indexing overlap`() {
        val index = LogIndex()
        val record = entry(1, LogLevel.INFO, null, null, "hello")
        index.add(10, 20, record)
        index.add(10, 20, record)

        assertEquals(1, index.size())
        assertTrue(index.snapshot().single().searchable.contains("hello"))
    }

    private fun entry(line: Int, level: LogLevel, logger: String?, thread: String?, message: String) =
        LogEntry(
            level = level,
            logger = logger,
            thread = thread,
            message = message,
            raw = message,
            lineNumber = line,
        )
}
