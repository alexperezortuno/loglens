package io.loglens.parser

import io.loglens.model.LogLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlainTextLogParserTest {

    private val parser = PlainTextLogParser()

    @Test
    fun `supports accepts any non-empty line`() {
        assertTrue(parser.supports("hello world"))
        assertTrue(parser.supports("ERROR something"))
        assertFalse(parser.supports(""))
    }

    @Test
    fun `parse preserves raw text as the message`() {
        val entry = parser.parse("just text", lineNumber = 1)
        assertEquals("just text", entry.message)
        assertEquals("just text", entry.raw)
        assertEquals(LogLevel.UNKNOWN, entry.level)
    }

    @Test
    fun `parse extracts leading level token`() {
        val entry = parser.parse("INFO Application started")
        assertEquals(LogLevel.INFO, entry.level)
        assertEquals("Application started", entry.message)
    }

    @Test
    fun `parse extracts level surrounded by delimiters`() {
        assertEquals(LogLevel.WARN, parser.parse("WARN, slow query").level)
        assertEquals(LogLevel.INFO, parser.parse("[INFO] Application started").level)
    }

    @Test
    fun `parse ignores the word INFO inside prose`() {
        // A short word glued to INFO (e.g. "MYINFO") must not trigger a hit.
        val entry = parser.parse("Hello MYINFO world")
        assertEquals(LogLevel.UNKNOWN, entry.level)
    }

    @Test
    fun `parse leaves message intact when no level detected`() {
        val entry = parser.parse("a perfectly mundane line")
        assertEquals(LogLevel.UNKNOWN, entry.level)
        assertEquals("a perfectly mundane line", entry.message)
    }

    @Test
    fun `parse handles long lines without crashing`() {
        val long = "INFO ".repeat(2_000) + "payload"
        val entry = parser.parse(long, lineNumber = 1)
        assertEquals(LogLevel.INFO, entry.level)
        // Stripped version should no longer start with INFO.
        assertTrue(entry.message.startsWith("INFO "))
    }

    @Test
    fun `parse recognises framework-specific synonyms`() {
        assertEquals(LogLevel.WARN, parser.parse("WARNING disk almost full").level)
        assertEquals(LogLevel.ERROR, parser.parse("SEVERE outage detected").level)
    }
}