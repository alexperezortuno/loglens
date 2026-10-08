package io.loglens.parser

import io.loglens.exception.StackTraceDetector
import io.loglens.model.LogEntry
import io.loglens.model.LogLevel

/**
 * Parser for common Logback patterns, including the default console layout:
 *
 *     2026-10-01 10:30:25,123 [main] INFO  com.example.App - Started App in 1.2s
 *
 * The parser also accepts ISO-like timestamps with spaces or `T`, optional
 * fractional seconds, and an optional trailing timezone. Custom PatternLayout
 * configurations remain out of scope.
 */
class LogbackLogParser : LogParser {

    override fun supports(line: String): Boolean {
        if (line.isEmpty()) return false
        return LOGBACK_PROBE.containsMatchIn(line)
    }

    override fun parse(line: String, lineNumber: Int?): LogEntry {
        val match = LOGBACK_PATTERN.find(line)
            ?: return LogEntry(
                message = line,
                raw = line,
                lineNumber = lineNumber,
                level = LogLevel.UNKNOWN,
                throwable = StackTraceDetector.peek(line),
            )

        val timestamp = match.groups["ts"]?.value?.trim()?.ifEmpty { null }
        val thread = match.groups["thread"]?.value?.trim()?.ifEmpty { null }
        val levelToken = match.groups["level"]?.value ?: ""
        val logger = match.groups["logger"]?.value?.trim()?.ifEmpty { null }
        val message = match.groups["rest"]?.value?.trimStart() ?: ""

        return LogEntry(
            timestamp = timestamp,
            level = LogLevel.fromToken(levelToken),
            thread = thread,
            logger = logger,
            message = message,
            raw = line,
            lineNumber = lineNumber,
            throwable = StackTraceDetector.peek(message),
        )
    }

    private companion object {
        val LOGBACK_PROBE: Regex = Regex(
            """^\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}:\d{2}(?:[.,]\d+)?(?:Z|[+-]\d{2}:?\d{2})?\s+\[[^\]]*]\s+(?:TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL|SEVERE)\s+\S+\s+-+\s+""",
            RegexOption.IGNORE_CASE,
        )

        val LOGBACK_PATTERN: Regex = Regex(
            """^(?<ts>\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}:\d{2}(?:[.,]\d+)?(?:Z|[+-]\d{2}:?\d{2})?)\s+\[(?<thread>[^\]]*)\]\s+(?<level>TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL|SEVERE)\s+(?<logger>\S+)\s+-+\s+(?<rest>.*)$""",
            RegexOption.IGNORE_CASE,
        )
    }
}
