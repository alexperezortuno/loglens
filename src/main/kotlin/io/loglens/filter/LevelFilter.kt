package io.loglens.filter

import io.loglens.model.LogEntry
import io.loglens.model.LogLevel

/**
 * Combination of toggles used to decide whether a [LogEntry] should remain
 * visible. The contract is straightforward OR: an entry passes if any of
 * its enabled levels match.
 *
 * Unknown entries use the same toggle semantics as recognized severities.
 */
class LevelFilter(
    enabled: Set<LogLevel> = DEFAULT_ENABLED,
) {
    private val enabledLevels: MutableSet<LogLevel> = enabled.toMutableSet()

    fun isAllowed(entry: LogEntry): Boolean {
        return entry.level in enabledLevels
    }

    fun setEnabled(level: LogLevel, enabled: Boolean) {
        if (enabled) enabledLevels.add(level) else enabledLevels.remove(level)
    }

    fun isEnabled(level: LogLevel): Boolean = level in enabledLevels

    fun enabledLevels(): Set<LogLevel> = enabledLevels.toSet()

    fun toggle(level: LogLevel) {
        if (isEnabled(level)) enabledLevels.remove(level) else enabledLevels.add(level)
    }

    companion object {
        /** Sane defaults: show INFO and above, hide TRACE/DEBUG. */
        val DEFAULT_ENABLED: Set<LogLevel> = setOf(
            LogLevel.INFO,
            LogLevel.WARN,
            LogLevel.ERROR,
            LogLevel.FATAL,
            LogLevel.UNKNOWN,
        )
    }
}
