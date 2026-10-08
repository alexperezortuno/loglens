package io.loglens.source

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProcessLogCaptureTest {
    @Test
    fun `command tokenizer preserves quoted arguments`() {
        assertEquals(
            listOf("printf", "hello world", "tail -f"),
            ProcessLogCapture.parseCommandLine("printf 'hello world' \"tail -f\""),
        )
    }

    @Test
    fun `command tokenizer rejects malformed input`() {
        assertFailsWith<IllegalArgumentException> {
            ProcessLogCapture.parseCommandLine("printf 'unfinished")
        }
    }

    @Test
    fun `capture records process output`() {
        val capture = ProcessLogCapture.start(listOf("sh", "-c", "printf 'INFO process-ready\\n'"))
        try {
            val deadline = System.nanoTime() + 2_000_000_000L
            while (System.nanoTime() < deadline &&
                !capture.path.toFile().readText().contains("INFO process-ready")) {
                Thread.sleep(10)
            }
            assertTrue(capture.path.toFile().readText().contains("INFO process-ready"))
        } finally {
            capture.stop()
        }
    }
}
