package io.loglens.exception

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class StackTraceDetectorTest {

    @Test
    fun `peek extracts class name and message from a header line`() {
        val info = StackTraceDetector.peek("java.lang.IllegalStateException: Invalid state")
        assertNotNull(info)
        assertEquals("java.lang.IllegalStateException", info!!.className)
        assertEquals("Invalid state", info.message)
        assertEquals(emptyList(), info.frames)
    }

    @Test
    fun `peek returns null for non-throwable lines`() {
        assertNull(StackTraceDetector.peek("INFO Application started"))
        assertNull(StackTraceDetector.peek(""))
        assertNull(StackTraceDetector.peek("2026-10-01 10:30:25  INFO 1 --- [main] c.e.App : hi"))
    }

    @Test
    fun `parse assembles class, message and frames`() {
        val raw = """
            java.lang.IllegalStateException: Invalid state
                at com.example.PaymentService.process(PaymentService.java:143)
                at com.example.PaymentController.pay(PaymentController.java:72)
        """.trimIndent()
        val info = StackTraceDetector.parse(raw)
        assertNotNull(info)
        assertEquals("java.lang.IllegalStateException", info!!.className)
        assertEquals("Invalid state", info.message)
        assertEquals(2, info.frames.size)
        assertEquals("com.example.PaymentService", info.frames[0].declaringClass)
        assertEquals("process", info.frames[0].methodName)
        assertEquals("PaymentService.java", info.frames[0].fileName)
        assertEquals(143, info.frames[0].lineNumber)
    }

    @Test
    fun `parse tolerates missing line numbers in frames`() {
        val raw = """
            java.lang.RuntimeException: "x"
                at foo.Bar.qux(Unknown)
        """.trimIndent()
        val info = StackTraceDetector.parse(raw)
        assertNotNull(info)
        assertEquals(1, info!!.frames.size)
        assertNull(info!!.frames.first().lineNumber)
    }

    @Test
    fun `parse returns null when the block is not a throwable`() {
        assertNull(StackTraceDetector.parse("INFO: hello world"))
        assertNull(StackTraceDetector.parse(""))
    }

    @Test
    fun `peek tolerates leading whitespace`() {
        val info = StackTraceDetector.peek("   java.lang.RuntimeException: oops")
        assertNotNull(info)
        assertEquals("java.lang.RuntimeException", info!!.className)
    }
}