package io.loglens.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanel
import io.loglens.service.LogLensProjectService
import java.awt.BorderLayout

/**
 * Root panel of the LogLens tool window. Hosts the search/filter toolbar
 * on top and the [LogViewerPanel] in the center.
 *
 * The content stays a thin shell so the bulk of the UI lives in the focused
 * [LogViewerPanel] — keeping the surface area small makes it easier to test
 * in isolation and to evolve the layout incrementally.
 */
class LogLensToolWindowContent(private val project: Project) {

    val component: JBPanel<JBPanel<*>> = JBPanel<JBPanel<*>>(BorderLayout())

    private val viewer: LogViewerPanel = LogViewerPanel()
    private val toolbar: LogViewerToolbar

    init {
        toolbar = LogViewerToolbar(viewer)
        component.add(toolbar.component, BorderLayout.NORTH)
        component.add(viewer.component, BorderLayout.CENTER)
    }

    fun onAttach() {
        val service = project.getService(LogLensProjectService::class.java) ?: return
        // Apply current snapshot immediately in case the project already had one.
        service.snapshot().let { viewer.update(it) }
        service.addListener { snapshot ->
            viewer.update(snapshot)
            toolbar.refreshFromViewer()
        }
    }
}