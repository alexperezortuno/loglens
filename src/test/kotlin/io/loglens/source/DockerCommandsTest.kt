package io.loglens.source

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DockerCommandsTest {
    @Test
    fun `container command uses direct docker arguments`() {
        assertEquals(
            listOf("docker", "logs", "--follow", "--timestamps", "api-main"),
            DockerCommands.container("api-main"),
        )
    }

    @Test
    fun `compose command includes file and service`() {
        assertEquals(
            listOf("docker", "compose", "-f", "/tmp/compose.yml", "logs", "--follow", "--timestamps", "api"),
            DockerCommands.compose(Path.of("/tmp/compose.yml"), "api"),
        )
    }

    @Test
    fun `empty docker names are rejected`() {
        assertFailsWith<IllegalArgumentException> { DockerCommands.container(" ") }
        assertFailsWith<IllegalArgumentException> { DockerCommands.compose(Path.of("compose.yml"), "") }
    }
}
