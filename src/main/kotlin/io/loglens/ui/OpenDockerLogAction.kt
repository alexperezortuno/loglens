package io.loglens.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import io.loglens.service.LogLensProjectService
import io.loglens.source.LogSource

/** Follows logs from a local Docker container through LogLens. */
class OpenDockerLogAction : AnAction("Open Docker Log…", "Follow a Docker container in LogLens", null) {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val container = Messages.showInputDialog(project, "Docker container name:", "Open Docker Log", null)
            ?.trim()?.takeIf { it.isNotEmpty() } ?: return
        try {
            project.getService(LogLensProjectService::class.java)?.openSource(LogSource.DockerContainer(container))
            ToolWindowManager.getInstance(project).getToolWindow("LogLens")?.show()
        } catch (error: Exception) {
            Messages.showErrorDialog(project, error.message ?: error.javaClass.simpleName, "Unable to Start Docker Logs")
        }
    }
}
