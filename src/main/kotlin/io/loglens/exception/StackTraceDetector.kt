package io.loglens.exception

import io.loglens.model.StackFrame
import io.loglens.model.ThrowableInfo

/**
 * Best-effort detector for Java/Kotlin throwable blocks embedded in log lines.
 *
 * For the 0.1 MVP this is only used to extract single-line throwable headers
 * (e.g. `java.lang.IllegalStateException: Invalid state`) so the parser can
 * attach a [ThrowableInfo] to a [LogEntry]. Full multi-line grouping and
 * navigation land in 0.2.
 *
 * The detector is intentionally tolerant — it never throws and falls back to
 * `null` whenever it can't reach a confident answer.
 */
object StackTraceDetector {

    /**
     * If [line] starts a throwable block, return the corresponding
     * [ThrowableInfo]; otherwise `null`.
     */
    fun peek(line: String): ThrowableInfo? {
        val trimmed = line.trimStart()
        val match = HEADER_PATTERN.find(trimmed) ?: return null
        val (className, message) = match.destructured
        return ThrowableInfo(
            className = className.trim().ifEmpty { null },
            message = message.trim().ifEmpty { null },
            frames = emptyList(),
            raw = line,
        )
    }

    /**
     * Parse a multiline [raw] block into a [ThrowableInfo] containing
     * every recognised frame. Returns null if [raw] does not look like a
     * throwable at all.
     */
    fun parse(raw: String): ThrowableInfo? {
        val lines = raw.lineSequence().toList()
        if (lines.isEmpty()) return null
        val headerMatch = HEADER_PATTERN.find(lines.first().trimStart()) ?: return null
        val (className, message) = headerMatch.destructured

        val frames = lines.drop(1).mapNotNull { parseFrame(it) }

        return Some(
            className = className.trim().ifEmpty { null },
            message = message.trim().ifEmpty { null },
            frames = frames,
            raw = raw,
        )
    }

    private fun parseFrame(line: String): StackFrame? {
        val match = FRAME_PATTERN.matchEntire(line.trim()) ?: return null
        val (klass, method, file, lineNumber) = match.destructured
        return StackFrame(
            declaringClass = klass.ifEmpty { null },
            methodName = method.ifEmpty { null },
            fileName = file.ifEmpty { null },
            lineNumber = lineNumber.toIntOrNull(),
        )
    }

    /**
     * Helper used by `parse` above. Defined as a private top-level alias
     * to make call sites readable.
     */
    private fun Some(
        className: String?,
        message: String?,
        frames: List<StackFrame>,
        raw: String,
    ) = ThrowableInfo(className, message, frames, raw)

    private val HEADER_PATTERN: Regex = Regex(
        """^([\w.$]+(?:Exception|Error|Throwable|Failure))(?::\s?(.*))?$""",
    )

    private val FRAME_PATTERN: Regex = Regex(
        """^(?:[\w.]+\.\s)?at\s+([\w.$]+)\.([\w$<>]+)\((?:([\w.]+)(?::(\d+))?)?\)$""",
    )
}