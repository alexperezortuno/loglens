package io.loglens.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun `segments retain standard colors and resets`() {
        val segments = AnsiCodes.segments("\u001B[34mblue\u001B[0m plain")

        assertEquals(2, segments.size)
        assertEquals("blue", segments[0].text)
        assertEquals(java.awt.Color(0x24, 0x72, 0xC8), segments[0].style.foreground)
        assertEquals(" plain", segments[1].text)
        assertEquals(null, segments[1].style.foreground)
    }

    @Test
    fun `segments support truecolor background and text decorations`() {
        val segments = AnsiCodes.segments("\u001B[1;3;4;38;2;12;34;56;48;5;196mstyled")
        val style = segments.single().style

        assertTrue(style.bold)
        assertTrue(style.italic)
        assertTrue(style.underline)
        assertEquals(java.awt.Color(12, 34, 56), style.foreground)
        assertEquals(java.awt.Color(0xFF, 0x00, 0x00), style.background)
        assertFalse(AnsiCodes.containsCodes(AnsiCodes.strip("\u001B[31mred\u001B[0m")))
    }
}
