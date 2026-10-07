package io.loglens.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager
import io.loglens.icons.LogLensIcons

/**
 * Toggles the LogLens tool window. Registered against the Window menu.
 */
class ToggleLogLensToolWindowAction : AnAction(
    "LogLens",
    "Show the LogLens tool window",
    LogLensIcons.TOOL_WINDOW,
) {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val manager = ToolWindowManager.getInstance(project)
        val window = manager.getToolWindow("LogLens") ?: return
        if (window.isVisible) window.hide() else window.show()
    }
}