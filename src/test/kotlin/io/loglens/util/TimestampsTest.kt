package io.loglens.util

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TimestampsTest {

    @Test
    fun `parses ISO and logback timestamps`() {
        assertNotNull(Timestamps.parse("2026-01-05T14:30:00Z"))
        assertNotNull(Timestamps.parse("2026-01-05 14:30:00,123"))
        assertNotNull(Timestamps.parseBoundary("2026-01-05"))
    }

    @Test
    fun `does not parse time-only values without a date`() {
        assertNull(Timestamps.parse("14:30:00.123"))
    }
}
