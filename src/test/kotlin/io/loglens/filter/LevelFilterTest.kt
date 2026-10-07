package io.loglens.filter

import io.loglens.model.LogEntry
import io.loglens.model.LogLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LevelFilterTest {

    private fun entry(level: LogLevel, message: String = "x"): LogEntry =
        LogEntry(message = message, raw = message, level = level)

    @Test
    fun `default filter hides TRACE and DEBUG`() {
        val filter = LevelFilter()
        assertFalse(filter.isAllowed(entry(LogLevel.TRACE)))
        assertFalse(filter.isAllowed(entry(LogLevel.DEBUG)))
        assertTrue(filter.isAllowed(entry(LogLevel.INFO)))
        assertTrue(filter.isAllowed(entry(LogLevel.WARN)))
        assertTrue(filter.isAllowed(entry(LogLevel.ERROR)))
        assertTrue(filter.isAllowed(entry(LogLevel.FATAL)))
    }

    @Test
    fun `unknown level can be toggled`() {
        val filter = LevelFilter()
        assertTrue(filter.isAllowed(entry(LogLevel.UNKNOWN)))
        filter.setEnabled(LogLevel.UNKNOWN, false)
        assertFalse(filter.isAllowed(entry(LogLevel.UNKNOWN)))
        filter.setEnabled(LogLevel.UNKNOWN, true)
        assertTrue(filter.isAllowed(entry(LogLevel.UNKNOWN)))
    }

    @Test
    fun `empty enabled set filters every level including unknown`() {
        val filter = LevelFilter(enabled = emptySet())
        assertFalse(filter.isAllowed(entry(LogLevel.UNKNOWN)))
        assertFalse(filter.isAllowed(entry(LogLevel.INFO)))
    }

    @Test
    fun `enabling TRACE and DEBUG lets them through`() {
        val filter = LevelFilter(
            enabled = setOf(
                LogLevel.TRACE,
                LogLevel.DEBUG,
                LogLevel.INFO,
                LogLevel.WARN,
                LogLevel.ERROR,
                LogLevel.FATAL,
            ),
        )
        assertTrue(filter.isAllowed(entry(LogLevel.TRACE)))
        assertTrue(filter.isAllowed(entry(LogLevel.DEBUG)))
    }

    @Test
    fun `toggle and setEnabled round-trip`() {
        val filter = LevelFilter(enabled = emptySet())
        assertFalse(filter.isEnabled(LogLevel.ERROR))
        filter.toggle(LogLevel.ERROR)
        assertTrue(filter.isEnabled(LogLevel.ERROR))
        filter.setEnabled(LogLevel.ERROR, false)
        assertFalse(filter.isEnabled(LogLevel.ERROR))
    }

    @Test
    fun `enabledLevels returns a snapshot copy`() {
        val filter = LevelFilter(enabled = setOf(LogLevel.INFO))
        val snap: MutableSet<LogLevel> = filter.enabledLevels().toMutableSet()
        snap.add(LogLevel.DEBUG)
        // The internal set must not have been mutated through the snapshot.
        assertFalse(filter.isEnabled(LogLevel.DEBUG))
    }

    @Test
    fun `combined semantics are OR across active levels`() {
        val filter = LevelFilter(enabled = setOf(LogLevel.ERROR, LogLevel.FATAL))
        assertFalse(filter.isAllowed(entry(LogLevel.INFO)))
        assertTrue(filter.isAllowed(entry(LogLevel.ERROR)))
        assertTrue(filter.isAllowed(entry(LogLevel.FATAL)))
        assertFalse(filter.isAllowed(entry(LogLevel.UNKNOWN)))
    }
}
