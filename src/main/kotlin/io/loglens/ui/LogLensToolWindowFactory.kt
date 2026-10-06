package io.loglens.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Creates the LogLens tool window content. A single [LogLensToolWindowContent]
 * instance is constructed per project and reused for the lifetime of the
 * tool window.
 */
class LogLensToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val content = LogLensToolWindowContent(project)
        val tab = ContentFactory.getInstance().createContent(
            content.component,
            "LogLens",
            /* isLockable = */ false,
        )
        toolWindow.contentManager.addContent(tab)
        content.onAttach()
    }
}