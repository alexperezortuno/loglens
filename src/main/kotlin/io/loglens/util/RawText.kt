package io.loglens.util

import io.loglens.model.LogEntry

/**
 * Helpers for extracting searchable / displayable text from a [LogEntry].
 *
 * Keeping this in one place lets the parsers stay simple and gives the
 * search/highlight code a single source of truth for "what is searchable".
 */
object RawText {

    /**
     * The text indexed by the search subsystem. Includes the structured
     * fields in addition to the raw line so that, for example, a query for
     * `ERROR` matches via the detected level even when the raw text uses a
     * less common token like `SEVERE`.
     */
    fun searchable(entry: LogEntry): String = buildString {
        append(entry.message)
        append('\n')
        entry.logger?.let { append(it); append('\n') }
        entry.thread?.let { append(it); append('\n') }
        entry.level.name.let { append(it); append('\n') }
        entry.timestamp?.let { append(it); append('\n') }
        entry.throwable?.let {
            it.className?.let { c -> append(c); append('\n') }
            it.message?.let { m -> append(m); append('\n') }
        }
        append(entry.raw)
    }

    /** Short label suitable for list rendering. */
    fun summary(entry: LogEntry): String = entry.message.lineSequence().firstOrNull() ?: entry.raw
}