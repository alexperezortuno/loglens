package io.loglens.service

import com.intellij.openapi.diagnostic.Logger
import java.io.BufferedReader
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.charset.Charset
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.CancellationException

/** Bounded page reads for the viewer, plus small-file streaming helpers for tests. */
object FileReadingService {

    private val log = Logger.getInstance(FileReadingService::class.java)

    /** Default chunk size: 64 KiB — large enough for fast scans, small enough
     *  to remain predictable on small files. */
    private const val BUFFER_SIZE: Int = 64 * 1024

    /** Encodings tried in order when auto-detecting file encoding. */
    private val ENCODING_PRIORITY = listOf(
        StandardCharsets.UTF_8,
        StandardCharsets.UTF_16,
        StandardCharsets.UTF_16BE,
        StandardCharsets.UTF_16LE,
        StandardCharsets.ISO_8859_1,
    )

    /** Result of encoding detection. */
    private data class DetectedEncoding(
        val charset: Charset,
        val bomBytes: Int,
    )

    /** Detect encoding from BOM, fallback to UTF-8. */
    private fun detectEncoding(path: Path): DetectedEncoding {
        val bytes = Files.readAllBytes(path).take(4).toByteArray()
        return when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
                DetectedEncoding(StandardCharsets.UTF_8, 3)
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                DetectedEncoding(StandardCharsets.UTF_16BE, 2)
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                DetectedEncoding(StandardCharsets.UTF_16LE, 2)
            else -> DetectedEncoding(StandardCharsets.UTF_8, 0)
        }
    }

    /** Create a decoder that replaces malformed sequences instead of throwing. */
    private fun createDecoder(charset: Charset): CharsetDecoder =
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)

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

            val detected = detectEncoding(path)
            val decoder = createDecoder(detected.charset)
            val bomOffset = if (start == 0L) detected.bomBytes.toLong() else 0L
            val adjustedStart = start + bomOffset

            if (adjustedStart > fileSize) {
                return LinePage(
                    records = emptyList(),
                    nextByteOffset = fileSize,
                    nextLineNumber = firstLineNumber,
                    bytesRead = 0L,
                    fileSize = fileSize,
                    hasMore = false,
                    nextPageStartsInsideRecord = false,
                    partialRecordWaiting = false,
                )
            }

            channel.position(adjustedStart)
            BufferedInputStream(Channels.newInputStream(channel), BUFFER_SIZE).use { input ->
                val records = mutableListOf<BoundedLine>()
                var bytesRead = 0L
                var nextLineNumber = firstLineNumber
                var insideRecord = startsInsideRecord
                var partialRecordWaiting = false
                val byteBuffer = ByteBuffer.allocate(BUFFER_SIZE)
                val charBuffer = java.nio.CharBuffer.allocate(BUFFER_SIZE * 2)

                if (insideRecord) {
                    val skipped = skipToRecordEnd(
                        input = input,
                        maxBytes = maxPageBytes,
                        remainingFileBytes = fileSize - adjustedStart,
                        isCancelled = isCancelled,
                    )
                    bytesRead += skipped.bytesConsumed
                    insideRecord = !skipped.reachedRecordEnd
                }

                while (!insideRecord && records.size < maxEntries && bytesRead < maxPageBytes && adjustedStart + bytesRead < fileSize) {
                    if (isCancelled()) throw CancellationException("Log loading cancelled")
                    val recordStart = adjustedStart + bytesRead
                    val line = readBoundedLine(
                        input = input,
                        decoder = decoder,
                        byteBuffer = byteBuffer,
                        charBuffer = charBuffer,
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
                        startByte = recordStart - bomOffset,
                        endByte = recordStart + line.bytesConsumed - bomOffset,
                        truncated = line.truncated,
                    )
                    nextLineNumber++
                    if (insideRecord && bytesRead < maxPageBytes) {
                        val skipped = skipToRecordEnd(
                            input = input,
                            maxBytes = maxPageBytes - bytesRead,
                            remainingFileBytes = fileSize - (adjustedStart + bytesRead),
                            isCancelled = isCancelled,
                        )
                        bytesRead += skipped.bytesConsumed
                        insideRecord = !skipped.reachedRecordEnd
                    }
                }

                val nextByteOffset = (start + bytesRead + bomOffset).coerceAtMost(fileSize)
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
        decoder: CharsetDecoder,
        byteBuffer: java.nio.ByteBuffer,
        charBuffer: java.nio.CharBuffer,
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

        byteBuffer.clear()
        charBuffer.clear()
        byteBuffer.put(bytes, 0, contentLength)
        byteBuffer.flip()
        val decodeResult = decoder.decode(byteBuffer, charBuffer, true)
        if (decodeResult.isError) {
            decodeResult.throwException()
        }
        charBuffer.flip()
        val text = charBuffer.toString()

        return BoundedRead(
            text = text,
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
        val detected = detectEncoding(path)
        val decoder = createDecoder(detected.charset)
        Files.newInputStream(path).use { stream ->
            if (detected.bomBytes > 0) stream.skip(detected.bomBytes.toLong())
            forEachLineStream(stream, decoder, action)
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
        decoder: CharsetDecoder,
        action: (line: String, lineNumber: Int) -> Unit,
    ) {
        val byteBuffer = ByteBuffer.allocate(BUFFER_SIZE)
        val charBuffer = java.nio.CharBuffer.allocate(BUFFER_SIZE * 2)
        val content = ByteArrayOutputStream(BUFFER_SIZE)
        var number = 1
        var bytesConsumed = 0L

        while (true) {
            val next = stream.read()
            if (next < 0) break
            bytesConsumed++
            if (next == '\n'.code) {
                byteBuffer.clear()
                charBuffer.clear()
                byteBuffer.put(content.toByteArray())
                byteBuffer.flip()
                val decodeResult = decoder.decode(byteBuffer, charBuffer, true)
                if (decodeResult.isError) decodeResult.throwException()
                charBuffer.flip()
                try {
                    action(charBuffer.toString(), number)
                } catch (e: Exception) {
                    log.warn("Error while processing line $number", e)
                }
                number++
                content.reset()
                continue
            }
            content.write(next)
        }
        if (content.size() > 0) {
            byteBuffer.clear()
            charBuffer.clear()
            byteBuffer.put(content.toByteArray())
            byteBuffer.flip()
            val decodeResult = decoder.decode(byteBuffer, charBuffer, true)
            if (decodeResult.isError) decodeResult.throwException()
            charBuffer.flip()
            try {
                action(charBuffer.toString(), number)
            } catch (e: Exception) {
                log.warn("Error while processing line $number", e)
            }
        }
    }

    fun forEachLineStream(
        stream: InputStream,
        action: (line: String, lineNumber: Int) -> Unit,
    ) {
        forEachLineStream(stream, StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE), action)
    }

    /** Read the entire content as a UTF-8 string for tests and very small files only. */
    fun readAll(path: Path): String {
        return Files.readString(path, StandardCharsets.UTF_8)
    }
}
