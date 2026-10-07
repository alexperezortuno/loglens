package io.loglens.ui

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import io.loglens.service.LogLensProjectService
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel

/** Read-only file editor that presents a log with LogLens parsing and ANSI styling. */
class LogLensFileEditor(
    project: Project,
    private val file: VirtualFile,
) : UserDataHolderBase(), FileEditor {

    private val viewer = LogViewerPanel()
    private val toolbar = LogViewerToolbar(viewer)
    private val root = JPanel(BorderLayout()).apply {
        add(toolbar.component, BorderLayout.NORTH)
        add(viewer.component, BorderLayout.CENTER)
    }

    init {
        val service = project.getService(LogLensProjectService::class.java)
        service.openFile(file)
        viewer.update(service.snapshot())
    }

    override fun getComponent(): JComponent = root

    override fun getPreferredFocusedComponent(): JComponent = viewer.component

    override fun getName(): String = "LogLens"

    override fun getFile(): VirtualFile = file

    override fun getState(level: FileEditorStateLevel): FileEditorState = FileEditorState.INSTANCE

    override fun setState(state: FileEditorState) = Unit

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = file.isValid

    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun dispose() {
        toolbar.dispose()
    }
}
