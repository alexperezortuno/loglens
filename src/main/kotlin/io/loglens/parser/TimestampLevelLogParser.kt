package io.loglens.parser

import io.loglens.exception.StackTraceDetector
import io.loglens.model.LogEntry
import io.loglens.model.LogLevel

/** Parser for compact timestamp and bracket-level logs such as `12:34:56.789 [ERROR] message`. */
class TimestampLevelLogParser : LogParser {

    override fun supports(line: String): Boolean =
        line.isNotEmpty() && PATTERN.containsMatchIn(line)

    override fun parse(line: String, lineNumber: Int?): LogEntry {
        val match = PATTERN.find(line)
            ?: return LogEntry(
                message = line,
                raw = line,
                lineNumber = lineNumber,
                level = LogLevel.UNKNOWN,
                throwable = StackTraceDetector.peek(line),
            )
        val timestamp = match.groups["timestamp"]?.value
        val levelToken = match.groups["level"]?.value
        val message = match.groups["message"]?.value?.trimStart().orEmpty()

        return LogEntry(
            timestamp = timestamp,
            level = LogLevel.fromToken(levelToken),
            message = message,
            raw = line,
            lineNumber = lineNumber,
            throwable = StackTraceDetector.peek(message),
        )
    }

    private companion object {
        val PATTERN = Regex(
            """^(?<timestamp>\d{2}:\d{2}:\d{2}(?:[.,]\d+)?)\s+\[(?<level>TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL|SEVERE)]\s+(?<message>.*)$""",
            RegexOption.IGNORE_CASE,
        )
    }
}
