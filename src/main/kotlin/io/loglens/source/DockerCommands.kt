package io.loglens.source

import java.nio.file.Path

/** Builds shell-independent Docker CLI invocations for local log sources. */
object DockerCommands {
    fun container(container: String): List<String> {
        require(container.isNotBlank()) { "A Docker container name is required." }
        return listOf("docker", "logs", "--follow", "--timestamps", container)
    }

    fun compose(composeFile: Path, service: String): List<String> {
        require(service.isNotBlank()) { "A Compose service name is required." }
        return listOf(
            "docker", "compose", "-f", composeFile.toAbsolutePath().toString(),
            "logs", "--follow", "--timestamps", service,
        )
    }
}
