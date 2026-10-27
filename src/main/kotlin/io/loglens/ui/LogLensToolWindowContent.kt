package io.loglens.ui

import com.intellij.ide.DataManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBPanel
import io.loglens.navigation.StackFrameNavigator
import io.loglens.service.LogLensProjectService
import io.loglens.settings.LogLensSettings
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel

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
    private var toolbarVisible = true
    private val toggleToolbarButton = JButton("⮝ Toolbar").apply {
        toolTipText = "Toggle toolbar (Ctrl+Shift+T)"
        addActionListener { toggleToolbar() }
    }
    private val resetLayoutButton = JButton("Reset Layout").apply {
        toolTipText = "Reset layout to defaults"
        addActionListener { resetLayout() }
    }

    init {
        toolbar = LogViewerToolbar(viewer)
        setupLayout()
        applyInitialToolbarVisibility()
    }

    private fun setupLayout() {
        val toolbarWrapper = JPanel(BorderLayout()).apply {
            add(toolbar.component, BorderLayout.CENTER)
val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
            isOpaque = false
            add(toggleToolbarButton)
            add(resetLayoutButton)
        }
            add(buttonPanel, BorderLayout.EAST)
        }

        val mainSplitter = JBSplitter(false, loadToolbarSplitProportion()).apply {
            setFirstComponent(toolbarWrapper)
            setSecondComponent(viewer.component)
            setDividerWidth(8)
            setHonorComponentsMinimumSize(true)
            setResizeEnabled(false)
            addPropertyChangeListener("proportion") { saveToolbarSplitProportion(it.newValue as Float) }
        }

        component.add(mainSplitter, BorderLayout.CENTER)
    }

    private fun loadToolbarSplitProportion(): Float = LogLensSettings.getInstance().state.toolbarVisible?.let { if (it) 0.15f else 0.0f } ?: 0.15f

    private fun saveToolbarSplitProportion(proportion: Float) {
        // Only save if toolbar is visible
        if (toolbarVisible) {
            // We don't persist the proportion for the toolbar splitter since it's fixed
        }
    }

    private fun applyInitialToolbarVisibility() {
        toolbarVisible = LogLensSettings.getInstance().state.toolbarVisible ?: true
        if (!toolbarVisible) {
            toggleToolbar()
        }
    }

    private fun toggleToolbar() {
        toolbarVisible = !toolbarVisible
        LogLensSettings.getInstance().state.toolbarVisible = toolbarVisible
        toggleToolbarButton.text = if (toolbarVisible) "⮝ Toolbar" else "⮞ Toolbar"
        component.revalidate()
        component.repaint()
    }

    fun onAttach() {
        val service = project.getService(LogLensProjectService::class.java) ?: return
        viewer.setLoadMoreAction(service::loadMore)
        viewer.setCancelLoadAction(service::cancelLoad)
        viewer.setTailAction(service::toggleTailing)
        viewer.setStackFrameNavigationHandler { frame, _ ->
            val dataContext = DataManager.getInstance().getDataContext(component)
            StackFrameNavigator.navigate(project, frame, dataContext)
        }
        // Apply current snapshot immediately in case the project already had one.
        service.snapshot().let { viewer.update(it) }
        service.addListener { snapshot ->
            viewer.update(snapshot)
            toolbar.refreshFromViewer()
        }
    }

    fun resetLayout() {
        viewer.resetLayout()
        toolbarVisible = true
        LogLensSettings.getInstance().state.toolbarVisible = true
        toggleToolbarButton.text = "⮝ Toolbar"
        component.revalidate()
        component.repaint()
    }
}
