package io.loglens.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import io.loglens.service.LogLensProjectService
import io.loglens.source.LogSource
import java.io.File

/** Follows logs from one service in a local Docker Compose project. */
class OpenDockerComposeLogAction : AnAction(
    "Open Docker Compose Log…",
    "Follow a Docker Compose service in LogLens",
    null,
) {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val descriptor = createSingleFileDescriptor()
            .withFileFilter { file -> file.name.endsWith(".yml", true) || file.name.endsWith(".yaml", true) }
            .withTitle("Choose Docker Compose File")
        val composeFile = FileChooser.chooseFile(descriptor, project, null) ?: return
        val service = Messages.showInputDialog(project, "Compose service name:", "Open Docker Compose Log", null)
            ?.trim()?.takeIf { it.isNotEmpty() } ?: return
        try {
            project.getService(LogLensProjectService::class.java)?.openSource(
                LogSource.DockerCompose(java.nio.file.Path.of(composeFile.path), service),
            )
            ToolWindowManager.getInstance(project).getToolWindow("LogLens")?.show()
        } catch (error: Exception) {
            Messages.showErrorDialog(project, error.message ?: error.javaClass.simpleName, "Unable to Start Docker Logs")
        }
    }

    private fun createSingleFileDescriptor(): FileChooserDescriptor {
        return try {
            // FileChooserDescriptorFactory.singleFile() added in 2025.2 (IC-252)
            FileChooserDescriptorFactory::class.java.getMethod("singleFile").invoke(null) as FileChooserDescriptor
        } catch (_: Exception) {
            // Fallback for 2024.3 (IC-243) and 2025.1 (IC-251)
            @Suppress("DEPRECATION")
            FileChooserDescriptorFactory.createSingleFileDescriptor()
        }
    }
}
