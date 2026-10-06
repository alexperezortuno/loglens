package io.loglens.parser

import io.loglens.model.LogEntry
import io.loglens.model.LogLevel

/**
 * Fallback parser that accepts any non-empty line.
 *
 * Its responsibility is twofold:
 *  1. Preserve the original raw text so the viewer can always show *something*.
 *  2. Pull out a level if one happens to be present on the line so that
 *     the level filter still works for ad-hoc text files.
 *
 * Detection of the level scans the first ~6 whitespace-separated tokens,
 * which is enough to catch both `10:30:25 INFO  ...` and `INFO: ...` shapes
 * without false-positives on prose containing the word "INFO".
 */
class PlainTextLogParser : LogParser {

    override fun supports(line: String): Boolean = line.isNotEmpty()

    override fun parse(line: String, lineNumber: Int?): LogEntry {
        val detected = detectLevel(line)
        return LogEntry(
            timestamp = null,
            level = detected.level,
            thread = null,
            logger = null,
            message = detected.stripped ?: line,
            raw = line,
            lineNumber = lineNumber,
        )
    }

    /**
     * Returns the detected level and the line with that token stripped
     * when found. The stripped message is returned so the viewer can show
     * the meaningful payload without the noise.
     */
    internal data class LevelMatch(val level: LogLevel, val stripped: String?)

    internal fun detectLevel(line: String): LevelMatch {
        // Look at the first six tokens, case-insensitively. We require the
        // token to be on its own (surrounded by whitespace or delimiters)
        // so we don't accidentally swallow prose.
        val tokens = line.split(' ', '\t')
        val window = tokens.take(6)
        for ((i, token) in window.withIndex()) {
            val cleaned = token.trim(':', ',', '.', '[', ']', '<', '>', '(', ')')
            val mapped = LogLevel.fromToken(cleaned)
            if (mapped != LogLevel.UNKNOWN && cleaned.length in 3..8) {
                val remaining = (tokens.subList(0, i) + tokens.subList(i + 1, tokens.size))
                    .joinToString(" ")
                    .trim()
                return LevelMatch(mapped, remaining)
            }
        }
        return LevelMatch(LogLevel.UNKNOWN, null)
    }
}