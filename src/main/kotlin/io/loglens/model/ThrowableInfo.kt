package io.loglens.model

/**
 * Parsed representation of a single Java/Kotlin throwable block.
 *
 * Captures exception classes, frames, causes, and suppressed exceptions so
 * the UI can render navigable details without re-scanning raw text.
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
    val causes: List<ThrowableInfo> = emptyList(),
    val suppressed: List<ThrowableInfo> = emptyList(),
    val omittedFrameCount: Int = 0,
    val isTruncated: Boolean = false,
)
