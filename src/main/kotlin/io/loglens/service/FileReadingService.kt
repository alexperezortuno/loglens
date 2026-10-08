package io.loglens.service

import com.intellij.openapi.diagnostic.logger
import java.io.BufferedReader
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.CancellationException

/** Bounded page reads for the viewer, plus small-file streaming helpers for tests. */
object FileReadingService {

    private val log = logger<FileReadingService>()

    /** Default chunk size: 64 KiB — large enough for fast scans, small enough
     *  to remain predictable on small files. */
    private const val BUFFER_SIZE: Int = 64 * 1024

    data class BoundedLine(
        val text: String,
        val lineNumber: Int,
        val startByte: Long,
        val endByte: Long,
        val truncated: Boolean,
    )

    data class LinePage(
        val records: List<BoundedLine>,
        val nextByteOffset: Long,
        val nextLineNumber: Int,
        val bytesRead: Long,
        val fileSize: Long,
        val hasMore: Boolean,
        val nextPageStartsInsideRecord: Boolean,
        val partialRecordWaiting: Boolean,
    )

    /** Read one bounded page from [path], starting at the first unread byte. */
    fun readPage(
        path: Path,
        startByteOffset: Long,
        firstLineNumber: Int,
        maxPageBytes: Long,
        maxEntries: Int,
        maxRecordBytes: Int,
        startsInsideRecord: Boolean = false,
        completeRecordsOnly: Boolean = false,
        isCancelled: () -> Boolean = { Thread.currentThread().isInterrupted },
    ): LinePage {
        require(startByteOffset >= 0) { "startByteOffset must not be negative" }
        require(firstLineNumber > 0) { "firstLineNumber must be positive" }
        require(maxPageBytes > 0) { "maxPageBytes must be positive" }
        require(maxEntries > 0) { "maxEntries must be positive" }
        require(maxRecordBytes > 0) { "maxRecordBytes must be positive" }

        FileChannel.open(path, StandardOpenOption.READ).use { channel ->
            val fileSize = channel.size()
            val start = startByteOffset.coerceAtMost(fileSize)
            channel.position(start)
            BufferedInputStream(Channels.newInputStream(channel), BUFFER_SIZE).use { input ->
                val records = mutableListOf<BoundedLine>()
                var bytesRead = 0L
                var nextLineNumber = firstLineNumber
                var insideRecord = startsInsideRecord
                var partialRecordWaiting = false

                if (insideRecord) {
                    val skipped = skipToRecordEnd(
                        input = input,
                        maxBytes = maxPageBytes,
                        remainingFileBytes = fileSize - start,
                        isCancelled = isCancelled,
                    )
                    bytesRead += skipped.bytesConsumed
                    insideRecord = !skipped.reachedRecordEnd
                }

                while (!insideRecord && records.size < maxEntries && bytesRead < maxPageBytes && start + bytesRead < fileSize) {
                    if (isCancelled()) throw CancellationException("Log loading cancelled")
                    val recordStart = start + bytesRead
                    val line = readBoundedLine(
                        input = input,
                        maxRecordBytes = maxRecordBytes,
                        remainingFileBytes = fileSize - recordStart,
                        isCancelled = isCancelled,
                    ) ?: break

                    if (completeRecordsOnly && !line.terminatedByNewline) {
                        if (!line.truncated) {
                            partialRecordWaiting = true
                            break
                        }
                        val skipped = skipToRecordEnd(
                            input = input,
                            maxBytes = maxPageBytes - line.bytesConsumed,
                            remainingFileBytes = fileSize - recordStart - line.bytesConsumed,
                            isCancelled = isCancelled,
                        )
                        if (!skipped.reachedRecordEnd) {
                            partialRecordWaiting = true
                            break
                        }
                        bytesRead += line.bytesConsumed + skipped.bytesConsumed
                    } else {
                        bytesRead += line.bytesConsumed
                    }
                    insideRecord = line.truncated && !(completeRecordsOnly && !line.terminatedByNewline)
                    records += BoundedLine(
                        text = line.text,
                        lineNumber = nextLineNumber,
                        startByte = recordStart,
                        endByte = recordStart + line.bytesConsumed,
                        truncated = line.truncated,
                    )
                    nextLineNumber++
                    if (insideRecord && bytesRead < maxPageBytes) {
                        val skipped = skipToRecordEnd(
                            input = input,
                            maxBytes = maxPageBytes - bytesRead,
                            remainingFileBytes = fileSize - (start + bytesRead),
                            isCancelled = isCancelled,
                        )
                        bytesRead += skipped.bytesConsumed
                        insideRecord = !skipped.reachedRecordEnd
                    }
                }

                val nextByteOffset = start + bytesRead
                return LinePage(
                    records = records,
                    nextByteOffset = nextByteOffset,
                    nextLineNumber = nextLineNumber,
                    bytesRead = bytesRead,
                    fileSize = fileSize,
                    hasMore = nextByteOffset < fileSize,
                    nextPageStartsInsideRecord = insideRecord,
                    partialRecordWaiting = partialRecordWaiting,
                )
            }
        }
    }

    private data class BoundedRead(
        val text: String,
        val bytesConsumed: Long,
        val truncated: Boolean,
        val terminatedByNewline: Boolean,
    )

    private data class SkipResult(val bytesConsumed: Long, val reachedRecordEnd: Boolean)

    private fun skipToRecordEnd(
        input: InputStream,
        maxBytes: Long,
        remainingFileBytes: Long,
        isCancelled: () -> Boolean,
    ): SkipResult {
        var consumed = 0L
        val limit = minOf(maxBytes, remainingFileBytes)
        while (consumed < limit) {
            if (consumed % 4096L == 0L && isCancelled()) {
                throw CancellationException("Log loading cancelled")
            }
            val next = input.read()
            if (next < 0) break
            consumed++
            if (next == '\n'.code) return SkipResult(consumed, reachedRecordEnd = true)
        }
        return SkipResult(consumed, reachedRecordEnd = consumed == remainingFileBytes)
    }

    private fun readBoundedLine(
        input: InputStream,
        maxRecordBytes: Int,
        remainingFileBytes: Long,
        isCancelled: () -> Boolean,
    ): BoundedRead? {
        val content = ByteArrayOutputStream(minOf(256, maxRecordBytes))
        var bytesConsumed = 0L
        var hasContent = false
        var truncated = false
        var terminatedByNewline = false

        while (bytesConsumed < remainingFileBytes) {
            if (bytesConsumed % 4096L == 0L && isCancelled()) {
                throw CancellationException("Log loading cancelled")
            }
            val next = input.read()
            if (next < 0) break
            bytesConsumed++
            if (next == '\n'.code) {
                hasContent = true
                terminatedByNewline = true
                break
            }
            hasContent = true
            if (content.size() < maxRecordBytes) {
                content.write(next)
            } else {
                truncated = true
                break
            }
        }

        if (!hasContent) return null
        val bytes = content.toByteArray()
        val contentLength = if (bytes.lastOrNull() == '\r'.code.toByte()) bytes.size - 1 else bytes.size
        return BoundedRead(
            text = String(bytes, 0, contentLength, StandardCharsets.UTF_8),
            bytesConsumed = bytesConsumed,
            truncated = truncated,
            terminatedByNewline = terminatedByNewline,
        )
    }

    /**
     * Unbounded helper for test fixtures and small files. The viewer should use [readPage].
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

    /** Read the entire content as a UTF-8 string for tests and very small files only. */
    fun readAll(path: Path): String {
        return Files.readString(path, StandardCharsets.UTF_8)
    }
}
