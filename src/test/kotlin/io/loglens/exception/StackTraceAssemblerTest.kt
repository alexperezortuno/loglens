package io.loglens.exception

import io.loglens.model.LogEntry
import io.loglens.model.LogLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StackTraceAssemblerTest {

    @Test
    fun `groups frames and causes across page boundaries`() {
        val assembler = StackTraceAssembler()
        val header = entry("java.lang.IllegalStateException: outer", 10)

        assertEquals(emptyList(), assembler.accept(header))
        assertNotNull(assembler.preview())
        assertEquals(emptyList(), assembler.accept(entry("    at com.example.Service.run(Service.java:41)", 11)))
        assertEquals(emptyList(), assembler.accept(entry("Caused by: java.io.IOException: disk", 12)))
        assertEquals(emptyList(), assembler.accept(entry("    at com.example.Store.load(Store.kt:12)", 13)))

        val next = entry("INFO next event", 14)
        val completed = assembler.accept(next)

        assertEquals(2, completed.size)
        val exception = completed.first()
        assertEquals(10, exception.lineNumber)
        assertEquals(4, exception.raw.lineSequence().count())
        assertEquals("java.lang.IllegalStateException", exception.throwable?.className)
        assertEquals(41, exception.throwable?.frames?.single()?.lineNumber)
        assertEquals("java.io.IOException", exception.throwable?.causes?.single()?.className)
        assertEquals(12, exception.throwable?.causes?.single()?.frames?.single()?.lineNumber)
        assertEquals(next, completed.last())
        assertEquals(emptyList(), assembler.finish())
    }

    @Test
    fun `finish flushes a header at end of file`() {
        val assembler = StackTraceAssembler()
        assembler.accept(entry("java.lang.RuntimeException: incomplete", 2))

        val completed = assembler.finish().single()

        assertEquals(2, completed.lineNumber)
        assertEquals("java.lang.RuntimeException", completed.throwable?.className)
        assertFalse(completed.throwable!!.isTruncated)
    }

    @Test
    fun `oversized exception groups are emitted with a truncation marker`() {
        val assembler = StackTraceAssembler(maxBlockCharacters = 16, maxBlockLines = 4)
        val output = assembler.accept(entry("java.lang.RuntimeException: this header is too long", 1))

        assertEquals(1, output.size)
        assertEquals("true", output.single().metadata["exceptionTruncated"])
        assertTrue(output.single().throwable!!.isTruncated)
        assertEquals(emptyList(), assembler.finish())
    }

    private fun entry(message: String, lineNumber: Int): LogEntry = LogEntry(
        level = if (message.startsWith("INFO")) LogLevel.INFO else LogLevel.ERROR,
        message = message,
        raw = message,
        lineNumber = lineNumber,
        throwable = StackTraceDetector.peek(message),
    )
}
