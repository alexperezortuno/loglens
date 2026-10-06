package io.loglens.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import io.loglens.service.LogLensProjectService

/**
 * Opens a file chooser limited to common log filenames, then forwards the
 * selected file to the project-scoped [LogLensProjectService].
 */
class OpenLogFileAction : AnAction() {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()
            .withFileFilter { vf ->
                val name = vf.name.lowercase()
                name.endsWith(".log") || name.endsWith(".out") || name.endsWith(".txt")
            }
            .withTitle("Open Log File")
            .withDescription("Choose a .log, .out or .txt file to view in LogLens.")

        val virtualFile = FileChooser.chooseFile(descriptor, project, null) ?: return
        val service = project.getService(LogLensProjectService::class.java) ?: return
        service.openFile(virtualFile)
        // Ensure the tool window is visible after the user opens a file.
        ToolWindowManager.getInstance(project).getToolWindow("LogLens")?.show()
    }

    init {
        templatePresentation.icon = io.loglens.icons.LogLensIcons.TOOL_WINDOW
        templatePresentation.text = "Open Log File…"
        templatePresentation.description = "Open a log file in LogLens"
    }
}