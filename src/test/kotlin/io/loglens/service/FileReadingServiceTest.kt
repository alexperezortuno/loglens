package io.loglens.service

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

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
}