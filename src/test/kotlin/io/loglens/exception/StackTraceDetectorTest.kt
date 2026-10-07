package io.loglens.exception

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun `parse extracts causes suppressed exceptions and omitted frames`() {
        val raw = """
            java.lang.IllegalStateException: outer
                at com.example.Service.run(Service.java:41)
            Suppressed: java.lang.IllegalArgumentException: close failed
                at com.example.Store.close(Store.kt:90)
            Caused by: java.io.IOException: disk failed
                at com.example.Store.load(Store.kt:12)
                ... 2 more
        """.trimIndent()

        val info = StackTraceDetector.parse(raw)

        assertNotNull(info)
        assertEquals("java.lang.IllegalStateException", info!!.className)
        assertEquals(41, info.frames.single().lineNumber)
        assertEquals("java.lang.IllegalArgumentException", info.suppressed.single().className)
        assertEquals("Store.kt", info.suppressed.single().frames.single().fileName)
        assertEquals("java.io.IOException", info.causes.single().className)
        assertEquals(2, info.causes.single().omittedFrameCount)
        assertEquals(raw, info.raw)
    }

    @Test
    fun `peek finds an exception after a log prefix and parses thread headers`() {
        val prefixed = StackTraceDetector.peek(
            "2026-10-01 10:30:25 ERROR com.example.App : java.lang.IllegalStateException: failed",
        )
        assertNotNull(prefixed)
        assertEquals("java.lang.IllegalStateException", prefixed!!.className)

        val thread = StackTraceDetector.peek("Exception in thread \"main\" java.lang.AssertionError: failed")
        assertNotNull(thread)
        assertEquals("java.lang.AssertionError", thread!!.className)
    }

    @Test
    fun `continuation detection recognises frames causes suppressed and omitted frames`() {
        assertTrue(StackTraceDetector.isContinuation("    at com.example.Service.run(Service.java:41)"))
        assertTrue(StackTraceDetector.isContinuation("Caused by: java.io.IOException: failed"))
        assertTrue(StackTraceDetector.isContinuation("Suppressed: java.lang.Exception: close"))
        assertTrue(StackTraceDetector.isContinuation("    ... 3 more"))
        assertFalse(StackTraceDetector.isContinuation("2026-10-01 INFO next event"))
    }
}
