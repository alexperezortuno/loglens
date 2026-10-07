package io.loglens.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class StackFrameNavigatorTest {

    @Test
    fun `prefers source paths matching the declaring class package across modules`() {
        val candidates = listOf(
            "/project/module-a/src/main/java/com/example/Service.java",
            "/project/module-b/src/test/java/com/example/Service.java",
            "/project/other/src/main/java/org/example/Service.java",
        )

        assertEquals(
            listOf(candidates[0], candidates[1]),
            StackFrameNavigator.rankCandidatePaths("com.example.Service", "Service.java", candidates),
        )
    }

    @Test
    fun `returns all candidate paths when class package cannot narrow matches`() {
        val candidates = listOf("/project/a/Foo.java", "/project/b/Foo.java")

        assertEquals(candidates, StackFrameNavigator.rankCandidatePaths(null, "Foo.java", candidates))
    }
}
