package io.loglens.parser

import io.loglens.model.LogLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LogbackLogParserTest {

    private val parser = LogbackLogParser()

    @Test
    fun `supports and parses the common Logback console pattern`() {
        val line = "2026-10-01 10:30:25,123 [main] INFO  com.example.App - Started App"

        assertTrue(parser.supports(line))
        val entry = parser.parse(line, lineNumber = 4)

        assertEquals("2026-10-01 10:30:25,123", entry.timestamp)
        assertEquals("main", entry.thread)
        assertEquals(LogLevel.INFO, entry.level)
        assertEquals("com.example.App", entry.logger)
        assertEquals("Started App", entry.message)
        assertEquals(4, entry.lineNumber)
        assertEquals(line, entry.raw)
    }

    @Test
    fun `supports ISO timestamps and preserves throwable headers`() {
        val line = "2026-10-01T10:30:25.123Z [worker-1] ERROR com.example.App - java.lang.IllegalStateException: failed"

        val entry = parser.parse(line)

        assertEquals(LogLevel.ERROR, entry.level)
        assertEquals("2026-10-01T10:30:25.123Z", entry.timestamp)
        assertNotNull(entry.throwable)
        assertEquals("java.lang.IllegalStateException", entry.throwable!!.className)
    }

    @Test
    fun `rejects Spring Boot and unrelated lines`() {
        assertFalse(parser.supports("2026-10-01 10:30:25 INFO 123 --- [main] c.e.App : ready"))
        assertFalse(parser.supports("INFO ready"))
    }
}
