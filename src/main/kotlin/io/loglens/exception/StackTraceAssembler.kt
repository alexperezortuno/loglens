package io.loglens.exception

import io.loglens.model.LogEntry
import io.loglens.model.ThrowableInfo

/** Joins physical continuation lines into one bounded logical exception entry. */
class StackTraceAssembler(
    private val maxBlockCharacters: Int = DEFAULT_MAX_BLOCK_CHARACTERS,
    private val maxBlockLines: Int = DEFAULT_MAX_BLOCK_LINES,
) {

    private var pending: LogEntry? = null
    private var pendingLines: Int = 0

    /** Accept a physical entry, returning logical entries that are now complete. */
    fun accept(entry: LogEntry): List<LogEntry> {
        val completed = mutableListOf<LogEntry>()
        val waiting = pending

        if (waiting != null) {
            if (StackTraceDetector.isContinuation(entry.message)) {
                val projectedChars = waiting.raw.length + entry.raw.length + waiting.message.length + entry.message.length
                if (pendingLines >= maxBlockLines || projectedChars > maxBlockCharacters) {
                    completed += completePending(truncated = true)
                    completed += entry
                    return completed
                }
                pending = waiting.copy(
                    message = waiting.message + "\n" + entry.message,
                    raw = waiting.raw + "\n" + entry.raw,
                    isTruncated = waiting.isTruncated || entry.isTruncated,
                )
                pendingLines++
                return completed
            }
            completed += completePending()
        }

        val throwable = entry.throwable
            ?: StackTraceDetector.peek(entry.message)
            ?: StackTraceDetector.peek(entry.raw)
        if (throwable == null) {
            completed += entry
        } else if (entry.message.contains('\n') || throwable.frames.isNotEmpty() ||
            throwable.causes.isNotEmpty() || throwable.suppressed.isNotEmpty() || entry.isTruncated
        ) {
            completed += entry.copy(throwable = StackTraceDetector.parse(entry.message) ?: throwable)
        } else if (entry.message.length + entry.raw.length > maxBlockCharacters || maxBlockLines < 1) {
            completed += entry.copy(
                throwable = throwable.copy(isTruncated = true),
                metadata = entry.metadata + (TRUNCATED_METADATA_KEY to "true"),
            )
        } else {
            pending = entry.copy(throwable = throwable)
            pendingLines = 1
        }
        return completed
    }

    /** Preview of a pending exception, used so a page boundary does not hide it. */
    fun preview(): LogEntry? = pending?.let { entry ->
        entry.copy(
            throwable = StackTraceDetector.parse(entry.message) ?: entry.throwable,
            metadata = entry.metadata + (PENDING_METADATA_KEY to "true"),
        )
    }

    /** Complete a pending exception at EOF or when switching files. */
    fun finish(): List<LogEntry> = if (pending == null) emptyList() else listOf(completePending())

    /** Discard pending state after the service has hit its retention ceiling. */
    fun reset() {
        pending = null
        pendingLines = 0
    }

    private fun completePending(truncated: Boolean = false): LogEntry {
        val entry = requireNotNull(pending)
        pending = null
        pendingLines = 0
        val parsed = StackTraceDetector.parse(entry.message) ?: entry.throwable
        val throwable = if (truncated) parsed?.copy(isTruncated = true) else parsed
        return entry.copy(
            throwable = throwable,
            metadata = if (truncated) entry.metadata + (TRUNCATED_METADATA_KEY to "true") else entry.metadata,
        )
    }

    private companion object {
        const val DEFAULT_MAX_BLOCK_CHARACTERS = 4 * 1024 * 1024
        const val DEFAULT_MAX_BLOCK_LINES = 20_000
        const val TRUNCATED_METADATA_KEY = "exceptionTruncated"
        const val PENDING_METADATA_KEY = "stackTracePending"
    }
}
