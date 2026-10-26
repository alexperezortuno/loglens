package io.loglens.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import io.loglens.service.LogLensProjectService
import io.loglens.source.ProcessLogCapture

/** Starts a local command and follows its combined output in LogLens. */
class OpenProcessLogAction : AnAction("Open Process Log…", "Follow a local process in LogLens", null) {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val command = Messages.showInputDialog(
            project,
            "Command to run (stdout and stderr will be followed):",
            "Open Process Log",
            null,
        ) ?: return
        try {
            project.getService(LogLensProjectService::class.java)?.openProcess(
                ProcessLogCapture.parseCommandLine(command),
            )
            ToolWindowManager.getInstance(project).getToolWindow("LogLens")?.show()
        } catch (error: Exception) {
            Messages.showErrorDialog(project, error.message ?: error.javaClass.simpleName, "Unable to Start Process")
        }
    }
}
