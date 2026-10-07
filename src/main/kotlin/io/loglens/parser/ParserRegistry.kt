package io.loglens.parser

import io.loglens.model.LogEntry
import io.loglens.util.AnsiCodes

/**
 * Selects the appropriate [LogParser] for an input line.
 *
 * Registries should be constructed with the most specific parsers first
 * (e.g. structured JSON, Spring Boot, Logback, and Log4j parsers before
 * `PlainTextLogParser`) so that format
 * detection is consistent across the file — once a parser "wins" the first
 * non-empty line, all subsequent lines are routed through the same parser.
 *
 * This per-file affinity prevents the viewer from flipping parsers mid-file
 * when, for example, a plain-text comment happens to look like a level.
 */
class ParserRegistry(
    private val parsers: List<LogParser>,
) {

    init {
        require(parsers.isNotEmpty()) { "at least one parser is required" }
        require(parsers.last() is PlainTextLogParser) {
            "the last parser in the registry must be the PlainTextLogParser fallback"
        }
    }

    private var activeParser: LogParser = parsers.last()

    /**
     * Pick a parser for [line].
     *
     * The first parser whose [LogParser.supports] returns true wins. If the
     * currently active parser already supports the line it is preferred so
     * we don't bounce between formats inside a single file.
     */
    fun selectParser(line: String): LogParser {
        return selectParserFor(AnsiCodes.strip(line))
    }

    private fun selectParserFor(line: String): LogParser {
        if (line.isBlank()) return activeParser
        if (activeParser.supports(line) && activeParser !is PlainTextLogParser) {
            return activeParser
        }
        for (parser in parsers) {
            if (parser.supports(line)) {
                activeParser = parser
                return parser
            }
        }
        return activeParser
    }

    /**
     * Convenience helper that selects the right parser and returns the
     * produced [LogEntry]. Equivalent to:
     *
     * ```
     * val parser = registry.selectParser(line)
     * parser.parse(line, lineNumber)
     * ```
     */
    fun parse(line: String, lineNumber: Int? = null): LogEntry {
        val normalizedLine = AnsiCodes.strip(line)
        val parser = selectParserFor(normalizedLine)
        return parser.parse(normalizedLine, lineNumber).copy(raw = line)
    }

    /** Drop the cached affinity. Call when switching files. */
    fun reset() {
        activeParser = parsers.last()
    }

    companion object {
        /** Default registry with the spec's initial parsers. */
        fun defaults(): ParserRegistry = ParserRegistry(
            listOf(
                JsonLinesLogParser(),
                SpringBootLogParser(),
                LogbackLogParser(),
                Log4jLogParser(),
                PlainTextLogParser(),
            ),
        )
    }
}
