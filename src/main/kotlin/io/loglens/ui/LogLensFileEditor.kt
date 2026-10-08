package io.loglens.ui

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import io.loglens.service.LogLensProjectService
import io.loglens.service.LogLensProjectService.LogLensSnapshot
import io.loglens.navigation.StackFrameNavigator
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JPanel

/** Read-only file editor that presents a log with LogLens parsing and ANSI styling. */
class LogLensFileEditor(
    private val project: Project,
    private val file: VirtualFile,
) : UserDataHolderBase(), FileEditor {

    private val service = project.getService(LogLensProjectService::class.java)
    private val filePath = Path.of(file.path)
    private val viewer = LogViewerPanel()
    private val toolbar = LogViewerToolbar(viewer)
    private val snapshotListener: (LogLensSnapshot) -> Unit = { snapshot ->
        viewer.update(if (snapshot.path == filePath) snapshot else LogLensSnapshot.empty())
    }
    private val root = JPanel(BorderLayout()).apply {
        add(toolbar.component, BorderLayout.NORTH)
        add(viewer.component, BorderLayout.CENTER)
    }

    init {
        viewer.setLoadMoreAction(service::loadMore)
        viewer.setCancelLoadAction(service::cancelLoad)
        viewer.setTailAction(service::toggleTailing)
        viewer.setStackFrameNavigationHandler { frame, _ -> StackFrameNavigator.navigate(project, frame) }
        service.addListener(snapshotListener)
        viewer.update(service.snapshot().takeIf { it.path == filePath } ?: LogLensSnapshot.empty())
        service.openFile(file)
    }

    override fun getComponent(): JComponent = root

    override fun getPreferredFocusedComponent(): JComponent = viewer.component

    override fun getName(): String = "LogLens"

    override fun getFile(): VirtualFile = file

    override fun selectNotify() {
        if (service.snapshot().path != filePath) service.openFile(file)
    }

    override fun getState(level: FileEditorStateLevel): FileEditorState = FileEditorState.INSTANCE

    override fun setState(state: FileEditorState) = Unit

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = file.isValid

    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun dispose() {
        service.removeListener(snapshotListener)
        toolbar.dispose()
    }
}
