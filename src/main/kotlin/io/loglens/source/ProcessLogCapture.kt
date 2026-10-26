package io.loglens.source

import java.io.BufferedWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/** Captures a local process' combined output so the normal file pipeline can follow it. */
class ProcessLogCapture private constructor(
    val command: List<String>,
    val path: Path,
    private val process: Process,
) {
    private val writer: Thread = Thread({ copyOutput() }, "LogLens-process-reader").apply {
        isDaemon = true
        start()
    }

    private fun copyOutput() {
        try {
            Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            ).use { output: BufferedWriter ->
                process.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                    lines.forEach { line ->
                        output.append(line).append('\n')
                        output.flush()
                    }
                }
            }
        } catch (_: Exception) {
            // The owning project service may stop and delete the capture while this thread exits.
        }
    }

    fun stop() {
        process.destroy()
        if (process.isAlive) process.destroyForcibly()
        writer.interrupt()
        Files.deleteIfExists(path)
    }

    companion object {
        fun start(command: List<String>, directory: Path? = null): ProcessLogCapture {
            require(command.isNotEmpty()) { "A process source needs a command." }
            val path = Files.createTempFile("loglens-process-", ".log")
            try {
                val builder = ProcessBuilder(command).redirectErrorStream(true)
                if (directory != null) builder.directory(directory.toFile())
                return ProcessLogCapture(command, path, builder.start())
            } catch (error: Exception) {
                Files.deleteIfExists(path)
                throw error
            }
        }

        /** Minimal shell-independent tokenization for commands entered in the action dialog. */
        fun parseCommandLine(value: String): List<String> {
            val result = mutableListOf<String>()
            val token = StringBuilder()
            var quote: Char? = null
            var escaping = false
            for (character in value.trim()) {
                when {
                    escaping -> { token.append(character); escaping = false }
                    character == '\\' && quote != '\'' -> escaping = true
                    quote != null && character == quote -> quote = null
                    quote == null && (character == '\'' || character == '"') -> quote = character
                    quote == null && character.isWhitespace() -> {
                        if (token.isNotEmpty()) { result += token.toString(); token.clear() }
                    }
                    else -> token.append(character)
                }
            }
            require(quote == null) { "Unclosed quote in process command." }
            require(!escaping || token.isNotEmpty()) { "Incomplete escape in process command." }
            if (escaping) token.append('\\')
            if (token.isNotEmpty()) result += token.toString()
            require(result.isNotEmpty()) { "Enter a process command." }
            return result
        }
    }
}
