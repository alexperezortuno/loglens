package io.loglens.model

/**
 * Severity levels recognised across log frameworks.
 *
 * The parser must not assume every format supports every value, so any
 * unrecognised level collapses to [UNKNOWN].
 */
enum class LogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    FATAL,
    UNKNOWN;

    /** Display label used in the viewer. */
    fun display(): String = name

    companion object {
        /**
         * Best-effort mapping from a free-form token (case-insensitive).
         *
         * Returns [UNKNOWN] rather than null so that downstream code can rely
         * on a non-null value. Unknown framework-specific levels still resolve
         * to [UNKNOWN] and remain visible in the viewer.
         *
         * Trims leading/trailing `[`, `]`, `:`, `,`, `.` so the caller doesn't
         * have to clean up tokens before classification.
         */
        fun fromToken(token: String?): LogLevel {
            if (token.isNullOrBlank()) return UNKNOWN
            val cleaned = token.trim().trim(':', ',', '.', '[', ']', '<', '>', '(', ')').uppercase()
            return when (cleaned) {
                "TRACE" -> TRACE
                "DEBUG", "FINE", "FINER", "FINEST" -> DEBUG
                "INFO", "NOTICE" -> INFO
                "WARN", "WARNING" -> WARN
                "ERROR", "SEVERE" -> ERROR
                "FATAL", "CRITICAL", "ALERT", "EMERGENCY", "EMERG" -> FATAL
                else -> UNKNOWN
            }
        }

        /** All levels exposed as viewer filter toggles. */
        val FILTERABLE: List<LogLevel> = listOf(TRACE, DEBUG, INFO, WARN, ERROR, FATAL, UNKNOWN)
    }
}
