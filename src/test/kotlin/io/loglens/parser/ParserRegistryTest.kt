package io.loglens.parser

import io.loglens.model.LogEntry
import io.loglens.model.LogLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.assertThrows

class ParserRegistryTest {

    @Test
    fun `registry must end with the plain text fallback`() {
        val ex: Throwable = assertThrows<IllegalArgumentException> {
            ParserRegistry(listOf(SpringBootLogParser(), SpringBootLogParser()))
        }
        assertNotNull(ex.message)
        assertTrue(ex.message!!.contains("PlainTextLogParser"))
    }

    @Test
    fun `registry defaults include Spring Boot and plain text parsers`() {
        val registry = ParserRegistry.defaults()
        val line = "2026-10-01 10:30:25  INFO 1 --- [main] c.e.App : ok"
        val entry = registry.parse(line, 1)
        assertEquals(LogLevel.INFO, entry.level)
    }

    @Test
    fun `registry keeps Spring Boot ahead of generic Logback`() {
        val registry = ParserRegistry.defaults()
        val spring = "2026-10-01 10:30:25  INFO 1 --- [main] c.e.App : ok"

        assertEquals("SpringBootLogParser", registry.selectParser(spring)::class.simpleName)
    }

    @Test
    fun `registry routes Logback and Log4j lines`() {
        val registry = ParserRegistry.defaults()

        assertEquals(
            "LogbackLogParser",
            registry.selectParser("2026-10-01 10:30:25,123 [main] INFO  c.e.App - ready")::class.simpleName,
        )
        registry.reset()
        assertEquals(
            "Log4jLogParser",
            registry.selectParser("10:30:25.123 [main] INFO  c.e.App - ready")::class.simpleName,
        )
    }

    @Test
    fun `registry routes plain text lines through the fallback`() {
        val registry = ParserRegistry.defaults()
        val entry = registry.parse("INFO hello", 1)
        assertEquals(LogLevel.INFO, entry.level)
    }

    @Test
    fun `registry routes JSON lines through the structured parser`() {
        val registry = ParserRegistry.defaults()
        val entry = registry.parse(
            """{"timestamp":"2026-01-05T14:57:47","level":"info","message":"Recording saved","app":"meeting-cli"}""",
            lineNumber = 4,
        )

        assertEquals(LogLevel.INFO, entry.level)
        assertEquals("Recording saved", entry.message)
        assertEquals("meeting-cli", entry.metadata["app"])
        assertEquals(4, entry.lineNumber)
    }

    @Test
    fun `registry strips ANSI formatting for parsing and display but preserves raw line`() {
        val registry = ParserRegistry.defaults()
        val raw = "\u001B[34m0.00.083.395\u001B[0m \u001B[32mINFO\u001B[0m hello"

        val entry = registry.parse(raw, lineNumber = 1)

        assertEquals(LogLevel.INFO, entry.level)
        assertEquals("0.00.083.395 hello", entry.message)
        assertEquals(raw, entry.raw)
        assertEquals(1, entry.lineNumber)
    }

    @Test
    fun `registry keeps parser affinity within a file`() {
        val registry = ParserRegistry.defaults()
        val spring = "2026-10-01 10:30:25  INFO 1 --- [main] c.e.App : hi"
        val first = registry.selectParser(spring)
        val second = registry.selectParser(spring)
        assertSame(first, second)
    }

    @Test
    fun `registry reset clears the affinity`() {
        val registry = ParserRegistry.defaults()
        registry.selectParser("2026-10-01 10:30:25  INFO 1 --- [main] c.e.App : hi")
        registry.reset()
        // After reset, the fallback parser is the active one.
        val plain: io.loglens.parser.LogParser = registry.selectParser("hello world")
        assertSame(PlainTextLogParser::class.java, plain::class.java)
    }

    @Test
    fun `registry handles blank lines without changing the active parser`() {
        val registry = ParserRegistry.defaults()
        val initial = registry.selectParser("2026-10-01 10:30:25  INFO 1 --- [main] c.e.App : hi")
        val afterBlank = registry.selectParser("")
        // Blank lines must not flip the parser.
        assertSame(initial, afterBlank)
        // And we should still get an entry back, even if empty.
        val entry: LogEntry = registry.parse("", lineNumber = 1)
        assertNotNull(entry)
    }
}
