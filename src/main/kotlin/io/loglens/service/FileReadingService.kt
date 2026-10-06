package io.loglens.service

import com.intellij.openapi.diagnostic.logger
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads log files using a buffered reader so we don't pay the cost of
 * streaming massive files into UI components.
 *
 * The reader is intentionally synchronous — for 0.1 we expect files in the
 * single-digit-megabyte range; the next release introduces incremental
 * parsing on a background thread per SPEC §9.
 */
object FileReadingService {

    private val log = logger<FileReadingService>()

    /** Default chunk size: 64 KiB — large enough for fast scans, small enough
     *  to remain predictable on small files. */
    private const val BUFFER_SIZE: Int = 64 * 1024

    /**
     * Iterate over each line of [path] using a UTF-8 [BufferedReader].
     *
     * The caller receives a 1-based line number alongside each line, which
     * is then forwarded to the parser so error/exception references can be
     * navigated later.
     */
    fun forEachLine(path: Path, action: (line: String, lineNumber: Int) -> Unit) {
        Files.newInputStream(path).use { stream ->
            forEachLineStream(stream, action)
        }
    }

    /**
     * Same as [forEachLine] but for an arbitrary [InputStream].
     *
     * Exposed separately so tests can feed parser fixtures without touching
     * the filesystem.
     */
    fun forEachLineStream(
        stream: InputStream,
        action: (line: String, lineNumber: Int) -> Unit,
    ) {
        BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8), BUFFER_SIZE).use { reader ->
            var number = 1
            var line: String? = reader.readLine()
            while (line != null) {
                try {
                    action(line, number)
                } catch (e: Exception) {
                    log.warn("Error while processing line $number", e)
                }
                number++
                line = reader.readLine()
            }
        }
    }

    /**
     * Read the entire content as a UTF-8 string. Convenience for tests and
     * very small files; the production viewer should not call this.
     */
    fun readAll(path: Path): String {
        return Files.readString(path, StandardCharsets.UTF_8)
    }
}