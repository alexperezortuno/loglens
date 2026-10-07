package io.loglens.ui

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/** Routes ordinary .log/.out opens to the ANSI-aware LogLens viewer. */
class LogLensFileEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean =
        file.extension?.lowercase() in SUPPORTED_EXTENSIONS

    override fun createEditor(project: Project, file: VirtualFile): FileEditor =
        LogLensFileEditor(project, file)

    override fun getEditorTypeId(): String = EDITOR_TYPE_ID

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR

    private companion object {
        const val EDITOR_TYPE_ID = "io.loglens.fileEditor"
        val SUPPORTED_EXTENSIONS = setOf("log", "out")
    }
}
