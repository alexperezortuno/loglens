package io.loglens.parser

import io.loglens.model.LogEntry

/**
 * Contract for any format-specific log parser.
 *
 * Parsers must remain pure (no UI dependency) and parser-agnostic code must
 * rely solely on [LogEntry] / [LogLevel]. The two responsibilities of every
 * implementation are:
 *
 *  - [supports] returns true only for lines that the parser genuinely knows
 *    how to interpret — it is not a "should I try?" hint, it is a promise.
 *  - [parse] returns a [LogEntry] (never null) when [supports] returned true,
 *    even if the line was malformed; it returns null when invoked for a line
 *    that [supports] rejected.
 *
 * @see ParserApi for versioning and stability guarantees.
 */
@StableApi
interface LogParser {

    /**
     * Cheap format-detection probe. Implementations should avoid expensive
     * regular expressions here; this is invoked for every input line.
     */
    @StableApi
    fun supports(line: String): Boolean

    /**
     * Convert a single source line into a [LogEntry].
     *
     * Must never throw — malformed input yields an entry with
     * [io.loglens.model.LogLevel.UNKNOWN] and the raw payload preserved.
     *
     * @param line the raw log line
     * @param lineNumber optional 1-based line number in the source file
     * @return a [LogEntry] if [supports] returned true for this line, null otherwise
     */
    @StableApi
    fun parse(line: String, lineNumber: Int? = null): LogEntry
}