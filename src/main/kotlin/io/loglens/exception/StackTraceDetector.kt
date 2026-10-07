package io.loglens.exception

import io.loglens.model.StackFrame
import io.loglens.model.ThrowableInfo

/** Best-effort parser for Java/Kotlin throwable headers, stack frames, causes, and suppressed exceptions. */
object StackTraceDetector {

    fun peek(line: String): ThrowableInfo? = parseHeader(line)?.let { header ->
        ThrowableInfo(
            className = header.className,
            message = header.message,
            frames = emptyList(),
            raw = line,
        )
    }

    /** Parse one complete throwable block, including causes and suppressed exceptions. */
    fun parse(raw: String): ThrowableInfo? {
        val lines = raw.lineSequence().toList()
        if (lines.isEmpty()) return null
        val rootHeader = parseHeader(lines.first()) ?: return null
        val root = MutableThrowable(rootHeader.className, rootHeader.message)
        var current = root
        var currentCause = root
        root.rawLines += lines.first()

        for (line in lines.drop(1)) {
            val trimmed = line.trim()
            when {
                CAUSE_PATTERN.matches(trimmed) -> {
                    val header = parseHeader(CAUSE_PATTERN.matchEntire(trimmed)!!.groupValues[1])
                    if (header == null) {
                        current.rawLines += line
                    } else {
                        val cause = MutableThrowable(header.className, header.message)
                        cause.rawLines += line
                        currentCause.causes += cause
                        currentCause = cause
                        current = cause
                    }
                }
                SUPPRESSED_PATTERN.matches(trimmed) -> {
                    val header = parseHeader(SUPPRESSED_PATTERN.matchEntire(trimmed)!!.groupValues[1])
                    if (header == null) {
                        current.rawLines += line
                    } else {
                        val suppressed = MutableThrowable(header.className, header.message)
                        suppressed.rawLines += line
                        currentCause.suppressed += suppressed
                        current = suppressed
                    }
                }
                parseFrame(line) != null -> {
                    current.frames += parseFrame(line)!!
                    current.rawLines += line
                }
                OMITTED_FRAMES_PATTERN.matches(trimmed) -> {
                    val omitted = OMITTED_FRAMES_PATTERN.matchEntire(trimmed)!!.groupValues[1].toIntOrNull() ?: 0
                    current.omittedFrameCount += omitted
                    current.rawLines += line
                }
                else -> current.rawLines += line
            }
        }
        return root.toInfo(raw)
    }

    /** True for physical lines that can continue an open stack-trace entry. */
    fun isContinuation(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.isEmpty() ||
            parseFrame(trimmed) != null ||
            CAUSE_PATTERN.matches(trimmed) ||
            SUPPRESSED_PATTERN.matches(trimmed) ||
            OMITTED_FRAMES_PATTERN.matches(trimmed)
    }

    private data class Header(val className: String?, val message: String?)

    private fun parseHeader(line: String): Header? {
        val trimmed = line.trimStart()
        if (trimmed.startsWith("Caused by:") || trimmed.startsWith("Suppressed:")) return null
        val threadHeader = THREAD_HEADER_PATTERN.matchEntire(trimmed)?.groupValues?.get(1)
        val candidate = threadHeader ?: trimmed
        val direct = HEADER_PATTERN.matchEntire(candidate)
        val match = direct ?: EMBEDDED_HEADER_PATTERN.find(candidate)
        if (match == null) return null
        val (className, message) = match.destructured
        return Header(className.trim().ifEmpty { null }, message.trim().ifEmpty { null })
    }

    private fun parseFrame(line: String): StackFrame? {
        val match = FRAME_PATTERN.matchEntire(line.trim()) ?: return null
        val (className, methodName, source) = match.destructured
        val sourceText = source.trim()
        val sourceLine = sourceText.substringAfterLast(':', missingDelimiterValue = "").toIntOrNull()
        val fileName = when {
            sourceText.isEmpty() || sourceText == "Native Method" || sourceText == "Unknown Source" -> null
            sourceLine != null -> sourceText.substringBeforeLast(':').ifEmpty { null }
            else -> sourceText
        }
        return StackFrame(
            declaringClass = className.ifEmpty { null },
            methodName = methodName.ifEmpty { null },
            fileName = fileName,
            lineNumber = sourceLine,
        )
    }

    private class MutableThrowable(
        val className: String?,
        val message: String?,
    ) {
        val frames = mutableListOf<StackFrame>()
        val causes = mutableListOf<MutableThrowable>()
        val suppressed = mutableListOf<MutableThrowable>()
        val rawLines = mutableListOf<String>()
        var omittedFrameCount: Int = 0
        var isTruncated: Boolean = false

        fun toInfo(raw: String = rawLines.joinToString("\n")): ThrowableInfo = ThrowableInfo(
            className = className,
            message = message,
            frames = frames.toList(),
            raw = raw,
            causes = causes.map { it.toInfo() },
            suppressed = suppressed.map { it.toInfo() },
            omittedFrameCount = omittedFrameCount,
            isTruncated = isTruncated,
        )
    }

    private val THREAD_HEADER_PATTERN = Regex("""^Exception in thread "[^"]+"\s+(.+)$""")
    private val HEADER_PATTERN = Regex(
        """^([\w.$]+(?:Exception|Error|Throwable|Failure))(?::\s?(.*))?$""",
    )
    private val EMBEDDED_HEADER_PATTERN = Regex(
        """(?:^|:\s+)([\w.$]+(?:Exception|Error|Throwable|Failure))(?::\s?(.*))?$""",
    )
    private val CAUSE_PATTERN = Regex("""^Caused by:\s*(.+)$""")
    private val SUPPRESSED_PATTERN = Regex("""^Suppressed:\s*(.+)$""")
    private val OMITTED_FRAMES_PATTERN = Regex("""^\.\.\.\s+(\d+)\s+more$""")
    private val FRAME_PATTERN = Regex(
        """^at\s+(?:[\w.]+/)?([\w.$]+)\.([\w$<>]+)\(([^()]*)\)$""",
    )
}
