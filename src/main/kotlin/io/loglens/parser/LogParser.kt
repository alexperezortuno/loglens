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
 */
interface LogParser {

    /**
     * Cheap format-detection probe. Implementations should avoid expensive
     * regular expressions here; this is invoked for every input line.
     */
    fun supports(line: String): Boolean

    /**
     * Convert a single source line into a [LogEntry].
     *
     * Must never throw — malformed input yields an entry with
     * [io.loglens.model.LogLevel.UNKNOWN] and the raw payload preserved.
     */
    fun parse(line: String, lineNumber: Int? = null): LogEntry
}