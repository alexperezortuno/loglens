package io.loglens.service

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.CancellationException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FileReadingServiceTest {

    @Test
    fun `forEachLineStream yields every line with 1-based numbering`() {
        val text = """
            INFO line one
            WARN line two
            ERROR line three
        """.trimIndent()
        val collected = mutableListOf<Pair<String, Int>>()
        FileReadingService.forEachLineStream(ByteArrayInputStream(text.toByteArray())) { line, n ->
            collected += line to n
        }
        assertEquals(3, collected.size)
        assertEquals("INFO line one" to 1, collected[0])
        assertEquals("WARN line two" to 2, collected[1])
        assertEquals("ERROR line three" to 3, collected[2])
    }

    @Test
    fun `forEachLineStream handles an empty input`() {
        val collected = mutableListOf<Pair<String, Int>>()
        FileReadingService.forEachLineStream(ByteArrayInputStream(ByteArray(0))) { line, n ->
            collected += line to n
        }
        assertEquals(emptyList(), collected)
    }

    @Test
    fun `forEachLineStream survives a faulty action`() {
        val text = "INFO ok\nWARN broken\nINFO still-ok\n"
        val collected = mutableListOf<String>()
        FileReadingService.forEachLineStream(ByteArrayInputStream(text.toByteArray())) { line, _ ->
            if (line.startsWith("WARN")) error("simulated")
            collected += line
        }
        assertContentEquals(listOf("INFO ok", "INFO still-ok"), collected)
    }

    @Test
    fun `forEachLineStream handles a single line without trailing newline`() {
        val collected = mutableListOf<Pair<String, Int>>()
        FileReadingService.forEachLineStream(ByteArrayInputStream("INFO only".toByteArray())) { line, n ->
            collected += line to n
        }
        assertEquals(listOf("INFO only" to 1), collected)
    }

    @Test
    fun `readPage starts at the beginning and resumes at the next UTF-8 record`() {
        val path = Files.createTempFile("loglens-page", ".log")
        try {
            Files.writeString(path, "🎙️ start\r\nsecond\nlast", StandardCharsets.UTF_8)
            val first = FileReadingService.readPage(
                path = path,
                startByteOffset = 0,
                firstLineNumber = 1,
                maxPageBytes = 1024,
                maxEntries = 2,
                maxRecordBytes = 1024,
            )

            assertEquals(listOf("🎙️ start", "second"), first.records.map { it.text })
            assertEquals(listOf(1, 2), first.records.map { it.lineNumber })
            assertTrue(first.hasMore)

            val second = FileReadingService.readPage(
                path = path,
                startByteOffset = first.nextByteOffset,
                firstLineNumber = first.nextLineNumber,
                maxPageBytes = 1024,
                maxEntries = 2,
                maxRecordBytes = 1024,
            )
            assertEquals(listOf("last"), second.records.map { it.text })
            assertEquals(3, second.records.single().lineNumber)
            assertEquals(false, second.hasMore)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun `readPage obeys entry and byte budgets`() {
        val path = Files.createTempFile("loglens-budget", ".log")
        try {
            Files.writeString(path, "one\ntwo\nthree\nfour\n", StandardCharsets.UTF_8)
            val page = FileReadingService.readPage(
                path = path,
                startByteOffset = 0,
                firstLineNumber = 1,
                maxPageBytes = 8,
                maxEntries = 2,
                maxRecordBytes = 1024,
            )
            assertEquals(listOf("one", "two"), page.records.map { it.text })
            assertEquals(8, page.bytesRead)
            assertTrue(page.hasMore)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun `readPage bounds an oversized record and resumes after skipping its remainder`() {
        val path = Files.createTempFile("loglens-large-line", ".log")
        try {
            Files.writeString(path, "123456789\nnext\n", StandardCharsets.UTF_8)
            val first = FileReadingService.readPage(
                path = path,
                startByteOffset = 0,
                firstLineNumber = 1,
                maxPageBytes = 8,
                maxEntries = 5,
                maxRecordBytes = 4,
            )

            assertEquals("1234", first.records.single().text)
            assertTrue(first.records.single().truncated)
            assertTrue(first.nextPageStartsInsideRecord)
            assertTrue(first.hasMore)

            val second = FileReadingService.readPage(
                path = path,
                startByteOffset = first.nextByteOffset,
                firstLineNumber = first.nextLineNumber,
                maxPageBytes = 1024,
                maxEntries = 5,
                maxRecordBytes = 4,
                startsInsideRecord = first.nextPageStartsInsideRecord,
            )
            assertEquals(listOf("next"), second.records.map { it.text })
            assertEquals(2, second.records.single().lineNumber)
            assertEquals(false, second.hasMore)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun `readPage responds to cancellation`() {
        val path = Files.createTempFile("loglens-cancel", ".log")
        try {
            Files.writeString(path, "one\ntwo\n", StandardCharsets.UTF_8)
            assertFailsWith<CancellationException> {
                FileReadingService.readPage(
                    path = path,
                    startByteOffset = 0,
                    firstLineNumber = 1,
                    maxPageBytes = 1024,
                    maxEntries = 2,
                    maxRecordBytes = 1024,
                    isCancelled = { true },
                )
            }
        } finally {
            Files.deleteIfExists(path)
        }
    }
}
