package io.loglens.parser

import io.loglens.model.LogLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JsonLinesLogParserTest {

    private val parser = JsonLinesLogParser()

    @Test
    fun `supports JSON objects and rejects other log formats`() {
        assertTrue(parser.supports("{\"level\":\"info\"}"))
        assertTrue(parser.supports("\uFEFF {\"level\":\"info\"}"))
        assertFalse(parser.supports("2026-10-01 10:30:25 INFO started"))
        assertFalse(parser.supports("just plain text"))
    }

    @Test
    fun `parses meeting cli fields and decodes escaped content`() {
        val raw = """{"timestamp":"2026-01-05T14:57:47","level":"info","message":"🎙️ Recording started\nsecond line","module":"cli","function":"record_audio","line":128,"app":"meeting-cli"}"""

        val entry = parser.parse(raw, lineNumber = 7)

        assertEquals("2026-01-05T14:57:47", entry.timestamp)
        assertEquals(LogLevel.INFO, entry.level)
        assertEquals("🎙️ Recording started\nsecond line", entry.message)
        assertEquals(raw, entry.raw)
        assertEquals(7, entry.lineNumber)
        assertEquals("128", entry.metadata["sourceLine"])
        assertEquals("cli", entry.metadata["module"])
        assertEquals("record_audio", entry.metadata["function"])
        assertEquals("meeting-cli", entry.metadata["app"])
    }

    @Test
    fun `preserves additional JSON fields as searchable metadata`() {
        val entry = parser.parse("""{"level":"warning","message":"retrying","attempt":2,"details":{"host":"db"}}""")

        assertEquals(LogLevel.WARN, entry.level)
        assertEquals("2", entry.metadata["attempt"])
        assertEquals("""{"host":"db"}""", entry.metadata["details"])
    }

    @Test
    fun `malformed JSON remains visible with unknown severity`() {
        val raw = "{\"timestamp\":\"broken\",\"message\":"

        val entry = parser.parse(raw, lineNumber = 3)

        assertEquals(LogLevel.UNKNOWN, entry.level)
        assertEquals(raw, entry.message)
        assertEquals(raw, entry.raw)
        assertEquals(3, entry.lineNumber)
    }

    @Test
    fun `unknown levels and missing message are handled safely`() {
        val raw = """{"level":"verbose","app":"meeting-cli"}"""

        val entry = parser.parse(raw)

        assertEquals(LogLevel.UNKNOWN, entry.level)
        assertEquals(raw, entry.message)
        assertEquals("meeting-cli", entry.metadata["app"])
    }
}
