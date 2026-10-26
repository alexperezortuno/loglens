package io.loglens.source

import java.nio.file.Path

/** A local input that can be presented by the common LogLens viewer. */
sealed interface LogSource {
    val displayName: String

    data class File(val path: Path) : LogSource {
        override val displayName: String = path.fileName.toString()
    }

    data class Process(val command: List<String>) : LogSource {
        init {
            require(command.isNotEmpty()) { "A process source needs a command." }
        }

        override val displayName: String = command.joinToString(" ")
    }

    data class DockerContainer(val container: String) : LogSource {
        init { require(container.isNotBlank()) { "A Docker container name is required." } }
        override val displayName: String = "Docker: $container"
    }

    data class DockerCompose(val composeFile: Path, val service: String) : LogSource {
        init { require(service.isNotBlank()) { "A Compose service name is required." } }
        override val displayName: String = "Compose: $service"
    }
}
