package io.loglens.util

/** Removes terminal control sequences so ANSI-formatted logs can be parsed and displayed as text. */
object AnsiCodes {

    private val escapeSequence = Regex(
        """\u001B(?:\[[0-?]*[ -/]*[@-~]|\][^\u0007]*?(?:\u0007|\u001B\\)|[PX^_][^\u001B]*(?:\u001B\\))|\u009B[0-?]*[ -/]*[@-~]|\u009D[^\u0007]*?(?:\u0007|\u009C)""",
    )

    /** Returns [text] without ANSI CSI, OSC, or DCS control sequences. */
    fun strip(text: String): String = escapeSequence.replace(text, "")
}
