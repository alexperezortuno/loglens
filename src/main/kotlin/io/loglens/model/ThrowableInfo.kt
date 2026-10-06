package io.loglens.model

/**
 * Parsed representation of a single Java/Kotlin throwable block.
 *
 * Captures the exception class plus the first [frames] so the UI can render
 * a compact summary without having to re-scan the raw text. Additional frames
 * remain available via the originating [LogEntry.raw] string.
 */
data class StackFrame(
    val declaringClass: String?,
    val methodName: String?,
    val fileName: String?,
    val lineNumber: Int?,
)

data class ThrowableInfo(
    val className: String?,
    val message: String?,
    val frames: List<StackFrame>,
    val raw: String,
)