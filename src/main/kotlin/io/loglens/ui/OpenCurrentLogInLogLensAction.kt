package io.loglens.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import io.loglens.service.LogLensProjectService

/** Opens the current editor's log file in the LogLens tool window. */
class OpenCurrentLogInLogLensAction : AnAction(
    "Open in LogLens",
    "View this log in LogLens, including ANSI colors",
    null,
) {

    override fun update(event: AnActionEvent) {
        val file = CommonDataKeys.VIRTUAL_FILE.getData(event.dataContext)
        event.presentation.isEnabledAndVisible = event.project != null && isLogFile(file)
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val file = CommonDataKeys.VIRTUAL_FILE.getData(event.dataContext) ?: return
        if (!isLogFile(file)) return

        project.getService(LogLensProjectService::class.java)?.openFile(file)
        ToolWindowManager.getInstance(project).getToolWindow("LogLens")?.show()
    }

    private fun isLogFile(file: VirtualFile?): Boolean =
        file?.extension?.lowercase() in SUPPORTED_EXTENSIONS

    private companion object {
        val SUPPORTED_EXTENSIONS = setOf("log", "out", "txt")
    }
}
