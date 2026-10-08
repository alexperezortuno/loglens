package io.loglens.parser

import io.loglens.exception.StackTraceDetector
import io.loglens.model.LogEntry
import io.loglens.model.LogLevel

/**
 * Parser for common Log4j layouts, including Log4j2's default console shape:
 *
 *     10:30:25.123 [main] INFO  com.example.App - Started App
 *
 * It also accepts the common Log4j 1.x date-first layout:
 * `2026-10-01 10:30:25,123 INFO [main] com.example.App - message`.
 */
class Log4jLogParser : LogParser {

    override fun supports(line: String): Boolean =
        line.isNotEmpty() && (LOG4J2_PROBE.containsMatchIn(line) || LOG4J1_PROBE.containsMatchIn(line))

    override fun parse(line: String, lineNumber: Int?): LogEntry {
        val match = LOG4J2_PATTERN.find(line) ?: LOG4J1_PATTERN.find(line)
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
        val LOG4J2_PROBE: Regex = Regex(
            """^\d{2}:\d{2}:\d{2}(?:[.,]\d+)?\s+\[[^\]]*]\s+(?:TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL|SEVERE)\s+\S+\s+-+\s+""",
            RegexOption.IGNORE_CASE,
        )
        val LOG4J1_PROBE: Regex = Regex(
            """^\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}(?:[.,]\d+)?\s+(?:TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL|SEVERE)\s+\[[^\]]*]\s+\S+\s+-+\s+""",
            RegexOption.IGNORE_CASE,
        )
        val LOG4J2_PATTERN: Regex = Regex(
            """^(?<ts>\d{2}:\d{2}:\d{2}(?:[.,]\d+)?)(?:\s+)(?:\[(?<thread>[^\]]*)\])\s+(?<level>TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL|SEVERE)\s+(?<logger>\S+)\s+-+\s+(?<rest>.*)$""",
            RegexOption.IGNORE_CASE,
        )
        val LOG4J1_PATTERN: Regex = Regex(
            """^(?<ts>\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}(?:[.,]\d+)?)(?:\s+)(?<level>TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL|SEVERE)\s+\[(?<thread>[^\]]*)\]\s+(?<logger>\S+)\s+-+\s+(?<rest>.*)$""",
            RegexOption.IGNORE_CASE,
        )
    }
}
