package io.loglens.parser

import io.loglens.exception.StackTraceDetector
import io.loglens.model.LogEntry
import io.loglens.model.LogLevel

/**
 * Parser for the classic Spring Boot default pattern:
 *
 *     2026-10-01 10:30:25.123  INFO 12345 --- [main] com.example.App : Started App in 1.2s
 *
 * The same format is produced by the Logback default encoder and by most
 * Spring Boot applications out of the box, so this parser covers a large
 * share of real-world files.
 *
 * Implementation notes:
 *  - Detection is a single regex pass on the timestamp + level substring to
 *    keep the `supports` probe cheap.
 *  - Failure modes collapse to UNKNOWN while preserving `raw`.
 *  - A throwable header in the extracted message is surfaced via [StackTraceDetector.peek].
 */
class SpringBootLogParser : LogParser {

    override fun supports(line: String): Boolean {
        if (line.isEmpty()) return false
        return SPRING_BOOT_PROBE.containsMatchIn(line)
    }

    override fun parse(line: String, lineNumber: Int?): LogEntry {
        val match = SPRING_BOOT_PATTERN.find(line)
        if (match == null) {
            // Not a Spring Boot line — still surface a throwable header if present.
            return LogEntry(
                message = line,
                raw = line,
                lineNumber = lineNumber,
                level = LogLevel.UNKNOWN,
                throwable = StackTraceDetector.peek(line),
            )
        }

        val timestamp = match.groups["ts"]?.value?.trim()?.ifEmpty { null }
        val levelToken = match.groups["level"]?.value ?: ""
        val thread = match.groups["thread"]?.value?.trim()?.ifEmpty { null }
        val logger = match.groups["logger"]?.value?.trim()?.ifEmpty { null }
        val tail = match.groups["rest"]?.value ?: ""
        val message = tail.removePrefix(": ").removePrefix(":").trimStart()

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
        /**
         * Cheap probe used by [supports]. Anchored to the start of the line so
         * we never false-positive on prose that happens to mention "INFO" deep
         * inside the body.
         */
        val SPRING_BOOT_PROBE: Regex = Regex(
            """^\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}(?:[.,]\d+)?\s+(?:TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL|SEVERE)\s+\d+\s+---\s+\[""",
            RegexOption.IGNORE_CASE,
        )

        /** Full capture pattern used by [parse]. */
        val SPRING_BOOT_PATTERN: Regex = Regex(
            """^(?<ts>\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}(?:[.,]\d+)?)\s+(?<level>TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL|SEVERE)\s+\d+\s+---\s+\[(?<thread>[^\]]*)\]\s+(?<logger>\S+)\s*:?\s*(?<rest>.*)$""",
            RegexOption.IGNORE_CASE,
        )
    }
}
