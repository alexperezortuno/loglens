package io.loglens.parser

import io.loglens.model.LogLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimestampLevelLogParserTest {

    private val parser = TimestampLevelLogParser()

    @Test
    fun `parses compact bracket-level records`() {
        val line = "21:00:27.261 [ERROR] Handshake error for 3517.client: Try again"

        assertTrue(parser.supports(line))
        val entry = parser.parse(line, lineNumber = 2)

        assertEquals("21:00:27.261", entry.timestamp)
        assertEquals(LogLevel.ERROR, entry.level)
        assertEquals("Handshake error for 3517.client: Try again", entry.message)
        assertEquals(2, entry.lineNumber)
        assertEquals(line, entry.raw)
    }

    @Test
    fun `rejects ordinary text and other log layouts`() {
        assertFalse(parser.supports("ERROR Handshake failed"))
        assertFalse(parser.supports("10:30:25.123 [main] INFO com.example.App - ready"))
    }
}
