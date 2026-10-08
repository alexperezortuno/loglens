package io.loglens.parser

import io.loglens.model.LogLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Log4jLogParserTest {

    private val parser = Log4jLogParser()

    @Test
    fun `parses the common Log4j2 time-first pattern`() {
        val line = "10:30:25.123 [main] WARN  com.example.App - Slow response"

        assertTrue(parser.supports(line))
        val entry = parser.parse(line)

        assertEquals("10:30:25.123", entry.timestamp)
        assertEquals("main", entry.thread)
        assertEquals(LogLevel.WARN, entry.level)
        assertEquals("com.example.App", entry.logger)
        assertEquals("Slow response", entry.message)
    }

    @Test
    fun `parses the common Log4j 1 x date-first pattern`() {
        val line = "2026-10-01 10:30:25,123 ERROR [worker] com.example.App - Failure"

        val entry = parser.parse(line, lineNumber = 9)

        assertEquals("2026-10-01 10:30:25,123", entry.timestamp)
        assertEquals("worker", entry.thread)
        assertEquals(LogLevel.ERROR, entry.level)
        assertEquals("com.example.App", entry.logger)
        assertEquals("Failure", entry.message)
        assertEquals(9, entry.lineNumber)
    }

    @Test
    fun `rejects Spring Boot and unrelated lines`() {
        assertFalse(parser.supports("2026-10-01 10:30:25 INFO 123 --- [main] c.e.App : ready"))
        assertFalse(parser.supports("INFO ready"))
    }
}
