package io.loglens

import io.loglens.filter.LevelFilter
import io.loglens.model.LogLevel
import io.loglens.parser.ParserRegistry
import io.loglens.search.LogSearch
import io.loglens.service.FileReadingService
import org.junit.jupiter.api.assertTimeout
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end smoke test that exercises the full pipeline:
 *
 *   buffered file reader -> parser registry -> level filter -> search
 *
 * against the sample file at `examples/sample-app.log`. Runs as part of the
 * regular test suite to catch regressions across the whole stack.
 */
class EndToEndPipelineTest {

    @Test
    fun `full pipeline reads sample log and filters by ERROR`() {
        val logFile = locateSampleLog()
        val registry = ParserRegistry.defaults()
        val entries = mutableListOf<io.loglens.model.LogEntry>()
        registry.reset()
        FileReadingService.forEachLine(logFile) { line, number ->
            entries += registry.parse(line, number)
        }

        assertTrue(entries.isNotEmpty(), "expected the sample file to produce entries")

        // Sanity: at least one Spring Boot entry was recognised.
        val withLogger = entries.firstOrNull { it.logger == "com.example.app.Application" }
        assertNotNull(withLogger)
        assertEquals(LogLevel.INFO, withLogger!!.level)
        assertNotNull(withLogger.timestamp)

        // ERROR filter must keep at least the PaymentService failure.
        val filter = LevelFilter(enabled = setOf(LogLevel.ERROR))
        val errors = entries.filter { filter.isAllowed(it) }
        assertTrue(errors.any { it.message.contains("Payment processing failed") })
    }

    @Test
    fun `search composes with level filter`() {
        val logFile = locateSampleLog()
        val registry = ParserRegistry.defaults()
        val entries = mutableListOf<io.loglens.model.LogEntry>()
        FileReadingService.forEachLine(logFile) { line, number ->
            entries += registry.parse(line, number)
        }
        val filter = LevelFilter(enabled = setOf(LogLevel.WARN, LogLevel.ERROR, LogLevel.FATAL))
        val search = LogSearch(query = "Payment")
        val matches = entries.filter { filter.isAllowed(it) && search.matches(it) }
        assertTrue(matches.any { it.message.contains("Payment") || it.raw.contains("Payment") })
    }

    @Test
    fun `parsing the sample log completes quickly`() {
        val logFile = locateSampleLog()
        assertTimeout(Duration.ofSeconds(5)) {
            val registry = ParserRegistry.defaults()
            FileReadingService.forEachLine(logFile) { line, number ->
                registry.parse(line, number)
            }
        }
    }

    /**
     * Locate the sample log relative to the working directory. We try a few
     * candidate locations because tests may be invoked from the project root
     * or from a Gradle worker.
     */
    private fun locateSampleLog(): Path {
        val candidates = listOf(
            Path.of("examples/sample-app.log"),
            Path.of("../examples/sample-app.log"),
            Path.of("../../../examples/sample-app.log"),
        )
        for (candidate in candidates) {
            if (Files.exists(candidate)) return candidate
        }
        throw IllegalStateException(
            "examples/sample-app.log not found. Tried: ${candidates.joinToString()}",
        )
    }
}