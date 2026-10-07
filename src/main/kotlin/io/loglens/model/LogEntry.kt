package io.loglens.model

/**
 * The internal representation of a single recognised log record.
 *
 * Every parser eventually maps to this shape so that the rest of the plugin
 * (viewer, filter, search, navigation) can stay parser-agnostic.
 *
 * Fields are nullable on purpose: real-world logs are missing timestamps,
 * logger names and even levels. Only [message] is guaranteed to be present
 * — for completely unparseable lines we still surface the raw text.
 */
data class LogEntry(
    /** Best-effort timestamp as it appeared in the log. Not normalised. */
    val timestamp: String? = null,
    val level: LogLevel = LogLevel.UNKNOWN,
    val thread: String? = null,
    val logger: String? = null,
    val message: String,
    val raw: String,
    /** One-based line number in the originating file, if known. */
    val lineNumber: Int? = null,
    val throwable: ThrowableInfo? = null,
    /** Additional structured fields, such as JSON log attributes. */
    val metadata: Map<String, String> = emptyMap(),
    /** True when the source record exceeded the reader's per-record memory limit. */
    val isTruncated: Boolean = false,
) {
    /** Convenience accessor returning a stable identifier — used for selection. */
    fun identity(): String = "${lineNumber ?: 0}#${raw.hashCode()}"
}
