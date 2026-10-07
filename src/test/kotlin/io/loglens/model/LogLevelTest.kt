package io.loglens.model

import kotlin.test.Test
import kotlin.test.assertEquals

class LogLevelTest {

    @Test
    fun `fromToken resolves canonical level names`() {
        assertEquals(LogLevel.TRACE, LogLevel.fromToken("TRACE"))
        assertEquals(LogLevel.DEBUG, LogLevel.fromToken("DEBUG"))
        assertEquals(LogLevel.INFO, LogLevel.fromToken("INFO"))
        assertEquals(LogLevel.WARN, LogLevel.fromToken("WARN"))
        assertEquals(LogLevel.ERROR, LogLevel.fromToken("ERROR"))
        assertEquals(LogLevel.FATAL, LogLevel.fromToken("FATAL"))
    }

    @Test
    fun `fromToken is case insensitive and trims`() {
        assertEquals(LogLevel.WARN, LogLevel.fromToken(" warn "))
        assertEquals(LogLevel.ERROR, LogLevel.fromToken("Error"))
        assertEquals(LogLevel.DEBUG, LogLevel.fromToken("debug"))
    }

    @Test
    fun `fromToken maps framework-specific synonyms`() {
        assertEquals(LogLevel.WARN, LogLevel.fromToken("WARNING"))
        assertEquals(LogLevel.DEBUG, LogLevel.fromToken("FINE"))
        assertEquals(LogLevel.DEBUG, LogLevel.fromToken("FINER"))
        assertEquals(LogLevel.DEBUG, LogLevel.fromToken("FINEST"))
        assertEquals(LogLevel.INFO, LogLevel.fromToken("NOTICE"))
        assertEquals(LogLevel.ERROR, LogLevel.fromToken("SEVERE"))
        assertEquals(LogLevel.FATAL, LogLevel.fromToken("CRITICAL"))
        assertEquals(LogLevel.FATAL, LogLevel.fromToken("ALERT"))
        assertEquals(LogLevel.FATAL, LogLevel.fromToken("EMERGENCY"))
        assertEquals(LogLevel.FATAL, LogLevel.fromToken("EMERG"))
    }

    @Test
    fun `fromToken collapses null blank and unknown tokens to UNKNOWN`() {
        assertEquals(LogLevel.UNKNOWN, LogLevel.fromToken(null))
        assertEquals(LogLevel.UNKNOWN, LogLevel.fromToken(""))
        assertEquals(LogLevel.UNKNOWN, LogLevel.fromToken("   "))
        assertEquals(LogLevel.UNKNOWN, LogLevel.fromToken("FATALITY"))
        assertEquals(LogLevel.UNKNOWN, LogLevel.fromToken("hello"))
    }

    @Test
    fun `fromToken handles trailing punctuation`() {
        assertEquals(LogLevel.INFO, LogLevel.fromToken("INFO:"))
        assertEquals(LogLevel.WARN, LogLevel.fromToken("WARN,"))
    }

    @Test
    fun `FILTERABLE includes UNKNOWN`() {
        assertEquals(
            listOf(
                LogLevel.TRACE,
                LogLevel.DEBUG,
                LogLevel.INFO,
                LogLevel.WARN,
                LogLevel.ERROR,
                LogLevel.FATAL,
                LogLevel.UNKNOWN,
            ),
            LogLevel.FILTERABLE,
        )
    }
}
