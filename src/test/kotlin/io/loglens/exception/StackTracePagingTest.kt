package io.loglens.exception

import io.loglens.parser.ParserRegistry
import io.loglens.service.FileReadingService
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StackTracePagingTest {

    @Test
    fun `exception grouping continues across bounded file pages`() {
        val path = Files.createTempFile("loglens-stacktrace", ".log")
        try {
            val text = """
                2026-10-01 10:30:25 ERROR 1 --- [main] com.example.App : java.lang.IllegalStateException: failed
                    at com.example.Service.run(Service.java:41)
                2026-10-01 10:30:26 INFO 1 --- [main] com.example.App : ready
            """.trimIndent()
            Files.writeString(
                path,
                text + "\n",
                StandardCharsets.UTF_8,
            )

            val registry = ParserRegistry.defaults()
            val assembler = StackTraceAssembler()
            val firstPage = FileReadingService.readPage(path, 0, 1, 4096, 1, 1024)
            val firstOutput = firstPage.records.flatMap { record ->
                assembler.accept(registry.parse(record.text, record.lineNumber))
            }
            assertTrue(firstOutput.isEmpty())
            assertNotNull(assembler.preview())

            val secondPage = FileReadingService.readPage(
                path = path,
                startByteOffset = firstPage.nextByteOffset,
                firstLineNumber = firstPage.nextLineNumber,
                maxPageBytes = 4096,
                maxEntries = 2,
                maxRecordBytes = 1024,
            )
            val secondOutput = secondPage.records.flatMap { record ->
                assembler.accept(registry.parse(record.text, record.lineNumber))
            }.toMutableList()
            if (!secondPage.hasMore) secondOutput += assembler.finish()

            val exception = secondOutput.first()
            assertEquals(1, exception.lineNumber)
            assertTrue(exception.raw.contains("Service.java:41"))
            assertEquals(41, exception.throwable?.frames?.single()?.lineNumber)
            assertEquals("ready", secondOutput.last().message)
        } finally {
            Files.deleteIfExists(path)
        }
    }
}
