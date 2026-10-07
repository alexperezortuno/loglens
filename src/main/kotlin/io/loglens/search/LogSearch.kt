package io.loglens.search

import io.loglens.model.LogEntry
import io.loglens.util.RawText

/**
 * Search criteria. Combine via [matches] with the rest of the filter pipeline.
 *
 * The three modes are mutually exclusive:
 *  - [Mode.PLAIN] – literal substring, optionally case-sensitive.
 *  - [Mode.REGEX] – full regular expression compiled with [patternFlags].
 *
 * An empty query always matches — this is the default state and lets the
 * viewer behave like a plain reader when the user hasn't searched anything.
 */
data class LogSearch(
    val query: String = "",
    val mode: Mode = Mode.PLAIN,
    val caseSensitive: Boolean = false,
) {
    private val compiledRegex: Regex? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (query.isEmpty() || mode != Mode.REGEX) {
            null
        } else {
            val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
            runCatching { Regex(query, options) }.getOrNull()
        }
    }

    enum class Mode { PLAIN, REGEX }

    /**
     * Returns true when [entry] matches the search criteria.
     *
     * The match is performed against [RawText.searchable] which unions
     * the structured fields (`message`, `logger`, `throwable.message`)
     * with the raw line so that framework-specific tags stay findable.
     */
    fun matches(entry: LogEntry): Boolean {
        if (query.isEmpty()) return true
        val haystack = RawText.searchable(entry)
        return when (mode) {
            Mode.PLAIN -> {
                if (caseSensitive) {
                    haystack.contains(query)
                } else {
                    haystack.contains(query, ignoreCase = true)
                }
            }

            Mode.REGEX -> {
                compiledRegex?.containsMatchIn(haystack) ?: false
            }
        }
    }

    companion object {
        val EMPTY: LogSearch = LogSearch()
    }
}
