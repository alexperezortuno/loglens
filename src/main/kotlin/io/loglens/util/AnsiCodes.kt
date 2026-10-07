package io.loglens.util

import java.awt.Color

/** Parses and removes terminal control sequences in ANSI-formatted log text. */
object AnsiCodes {

    private val escapeSequence = Regex(
        """\u001B(?:\[[0-?]*[ -/]*[@-~]|\][^\u0007]*?(?:\u0007|\u001B\\)|[PX^_][^\u001B]*(?:\u001B\\))|\u009B[0-?]*[ -/]*[@-~]|\u009D[^\u0007]*?(?:\u0007|\u009C)""",
    )
    private val sgrSequence = Regex("""(?:\u001B\[|\u009B)([0-?]*)m""")

    /** Styled printable fragment produced by interpreting ANSI SGR codes. */
    data class Segment(val text: String, val style: Style)

    /** Foreground/background colors and text decorations active for a segment. */
    data class Style(
        val foreground: Color? = null,
        val background: Color? = null,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
    )

    /** Returns [text] without ANSI CSI, OSC, or DCS control sequences. */
    fun strip(text: String): String = escapeSequence.replace(text, "")

    /** True when [text] contains an ANSI control sequence. */
    fun containsCodes(text: String): Boolean = escapeSequence.containsMatchIn(text)

    /**
     * Splits text at ANSI sequences, preserving SGR colors/decorations and
     * dropping non-printing control sequences such as OSC hyperlinks.
     */
    fun segments(text: String): List<Segment> {
        val result = mutableListOf<Segment>()
        val style = MutableStyle()
        var offset = 0

        for (escape in escapeSequence.findAll(text)) {
            appendSegment(result, text.substring(offset, escape.range.first), style.snapshot())
            sgrSequence.matchEntire(escape.value)?.groupValues?.get(1)?.let { parameters ->
                applySgr(parameters, style)
            }
            offset = escape.range.last + 1
        }
        appendSegment(result, text.substring(offset), style.snapshot())
        return result
    }

    private fun appendSegment(target: MutableList<Segment>, text: String, style: Style) {
        if (text.isEmpty()) return
        val previous = target.lastOrNull()
        if (previous != null && previous.style == style) {
            target[target.lastIndex] = previous.copy(text = previous.text + text)
        } else {
            target += Segment(text, style)
        }
    }

    private fun applySgr(parameters: String, style: MutableStyle) {
        val codes = if (parameters.isEmpty()) listOf(0) else parameters.split(';').map { it.toIntOrNull() ?: 0 }
        var index = 0
        while (index < codes.size) {
            when (val code = codes[index]) {
                0 -> style.reset()
                1 -> style.bold = true
                3 -> style.italic = true
                4 -> style.underline = true
                22 -> style.bold = false
                23 -> style.italic = false
                24 -> style.underline = false
                in 30..37 -> style.foreground = ansiColor(code - 30)
                39 -> style.foreground = null
                in 40..47 -> style.background = ansiColor(code - 40)
                49 -> style.background = null
                in 90..97 -> style.foreground = ansiColor(code - 90 + 8)
                in 100..107 -> style.background = ansiColor(code - 100 + 8)
                38, 48 -> {
                    val setForeground = code == 38
                    when (codes.getOrNull(index + 1)) {
                        5 -> {
                            codes.getOrNull(index + 2)?.let { colorIndex ->
                                val color = indexedColor(colorIndex)
                                if (setForeground) style.foreground = color else style.background = color
                            }
                            index += 2
                        }
                        2 -> {
                            val red = codes.getOrNull(index + 2)
                            val green = codes.getOrNull(index + 3)
                            val blue = codes.getOrNull(index + 4)
                            if (red != null && green != null && blue != null) {
                                val color = Color(red.coerceIn(0, 255), green.coerceIn(0, 255), blue.coerceIn(0, 255))
                                if (setForeground) style.foreground = color else style.background = color
                            }
                            index += 4
                        }
                    }
                }
            }
            index++
        }
    }

    private fun ansiColor(index: Int): Color = ANSI_COLORS[index.coerceIn(0, 15)]

    private fun indexedColor(index: Int): Color {
        val safeIndex = index.coerceIn(0, 255)
        if (safeIndex < ANSI_COLORS.size) return ANSI_COLORS[safeIndex]
        if (safeIndex < 232) {
            val cube = safeIndex - 16
            fun channel(value: Int): Int = if (value == 0) 0 else 55 + value * 40
            return Color(channel(cube / 36), channel((cube / 6) % 6), channel(cube % 6))
        }
        val gray = 8 + (safeIndex - 232) * 10
        return Color(gray, gray, gray)
    }

    private class MutableStyle {
        var foreground: Color? = null
        var background: Color? = null
        var bold: Boolean = false
        var italic: Boolean = false
        var underline: Boolean = false

        fun reset() {
            foreground = null
            background = null
            bold = false
            italic = false
            underline = false
        }

        fun snapshot(): Style = Style(foreground, background, bold, italic, underline)
    }

    private val ANSI_COLORS = listOf(
        Color(0x00, 0x00, 0x00), Color(0xCD, 0x31, 0x31), Color(0x0D, 0xBC, 0x79), Color(0xE5, 0xE5, 0x10),
        Color(0x24, 0x72, 0xC8), Color(0xBC, 0x3F, 0xBC), Color(0x11, 0xA8, 0xCD), Color(0xE5, 0xE5, 0xE5),
        Color(0x66, 0x66, 0x66), Color(0xF1, 0x4C, 0x4C), Color(0x23, 0xD1, 0x8B), Color(0xF5, 0xF5, 0x43),
        Color(0x3B, 0x8E, 0xEA), Color(0xD6, 0x70, 0xD6), Color(0x29, 0xB8, 0xDB), Color(0xE5, 0xE5, 0xE5),
    )
}
