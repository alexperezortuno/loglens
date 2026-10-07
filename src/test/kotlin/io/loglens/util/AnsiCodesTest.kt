package io.loglens.util

import kotlin.test.Test
import kotlin.test.assertEquals

class AnsiCodesTest {

    @Test
    fun `strip removes CSI color codes`() {
        assertEquals("warning", AnsiCodes.strip("\u001B[33mwarning\u001B[0m"))
    }

    @Test
    fun `strip removes OSC hyperlinks but keeps link text`() {
        val link = "\u001B]8;;https://example.com\u001B\\docs\u001B]8;;\u001B\\"
        assertEquals("docs", AnsiCodes.strip(link))
    }
}
