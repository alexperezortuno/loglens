package io.loglens.parser

import io.loglens.model.LogLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpringBootLogParserTest {

    private val parser = SpringBootLogParser()

    @Test
    fun `supports detects canonical Spring Boot line`() {
        val line = "2026-10-01 10:30:25.123  INFO 12345 --- [main] com.example.App : Started App in 1.2s"
        assertTrue(parser.supports(line))
    }

    @Test
    fun `supports rejects unrelated lines`() {
        assertFalse(parser.supports(""))
        assertFalse(parser.supports("hello world"))
        // INFO without a date prefix should not be classified as Spring Boot
        assertFalse(parser.supports("INFO: nothing to do"))
    }

    @Test
    fun `parse extracts every field from a well-formed entry`() {
        val line = "2026-10-01 10:30:25.123  INFO 12345 --- [main] com.example.App : Started App in 1.2s"
        val entry = parser.parse(line, lineNumber = 7)

        assertEquals("2026-10-01 10:30:25.123", entry.timestamp)
        assertEquals(LogLevel.INFO, entry.level)
        assertEquals("main", entry.thread)
        assertEquals("com.example.App", entry.logger)
        assertEquals("Started App in 1.2s", entry.message)
        assertEquals(line, entry.raw)
        assertEquals(7, entry.lineNumber)
    }

    @Test
    fun `parse recognises alternative severity tokens`() {
        val warnLine = "2026-10-01 10:30:25  WARN 1 --- [worker] org.foo.Bar : boom"
        val errorLine = "2026-10-01 10:30:25 ERROR 1 --- [spring] x.y.Z : kaboom"

        assertEquals(LogLevel.WARN, parser.parse(warnLine).level)
        assertEquals(LogLevel.ERROR, parser.parse(errorLine).level)
    }

    @Test
    fun `parse handles missing logger and thread gracefully`() {
        // Trailing " - " is uncommon, but the regex allows empty captures.
        val line = "2026-10-01 10:30:25  INFO 1 --- []  : standalone message"
        val entry = parser.parse(line)

        assertEquals(LogLevel.INFO, entry.level)
        assertNotNull(entry.logger)
        assertEquals("standalone message", entry.message)
    }

    @Test
    fun `parse handles completely malformed input`() {
        val garbage = "this is not a log line"
        // Plain-text parser would accept it; SpringBoot should not.
        assertFalse(parser.supports(garbage))
        // And if it did get forced through parse(), it must not throw.
        val entry = parser.parse(garbage, lineNumber = 1)
        assertEquals(LogLevel.UNKNOWN, entry.level)
        assertEquals(garbage, entry.message)
    }

    @Test
    fun `parse keeps raw even when message extraction is partial`() {
        val line = "2026-10-01 10:30:25  INFO 1 --- [main] com.example.App : "
        val entry = parser.parse(line)
        assertEquals(LogLevel.INFO, entry.level)
        assertEquals(line, entry.raw)
    }

    @Test
    fun `parse attaches throwable when the line is a stack trace header`() {
        val line = "java.lang.IllegalStateException: invalid state"
        val entry = parser.parse(line, lineNumber = 10)
        // This line is not a Spring Boot line, so it falls through with UNKNOWN.
        // We still want the throwable detected and surfaced.
        assertNotNull(entry.throwable)
        assertEquals("java.lang.IllegalStateException", entry.throwable!!.className)
        assertEquals("invalid state", entry.throwable!!.message)
    }

    @Test
    fun `parse returns lineNumber when provided`() {
        val line = "2026-10-01 10:30:25  INFO 1 --- [main] c.e.App : Hi"
        assertEquals(42, parser.parse(line, lineNumber = 42).lineNumber)
        // lineNumber default is null.
        assertNull(parser.parse(line).lineNumber)
    }
}