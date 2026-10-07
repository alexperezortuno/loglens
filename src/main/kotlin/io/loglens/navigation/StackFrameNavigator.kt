package io.loglens.navigation

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import io.loglens.model.StackFrame

/** Resolves Java/Kotlin stack frames to project files and navigates to their source lines. */
object StackFrameNavigator {

    fun navigate(project: Project, frame: StackFrame) {
        val fileName = frame.fileName ?: return
        val candidates = ReadAction.compute<List<VirtualFile>, RuntimeException> {
            FilenameIndex.getVirtualFilesByName(project, fileName, GlobalSearchScope.projectScope(project))
                .filter { ProjectFileIndex.getInstance(project).isInContent(it) }
        }
        val ranked = rankCandidates(frame.declaringClass, fileName, candidates)
        when (ranked.size) {
            0 -> Messages.showInfoMessage(
                project,
                "No project source file named $fileName was found.",
                "Source Not Found",
            )
            1 -> open(project, ranked.single(), frame)
            else -> {
                val paths = ranked.map(VirtualFile::getPath).toTypedArray()
                val selected = Messages.showChooseDialog(
                    project,
                    "Choose a source file for ${frame.declaringClass.orEmpty()}.${frame.methodName.orEmpty()}.",
                    "Ambiguous Stack Frame",
                    Messages.getQuestionIcon(),
                    paths,
                    paths.first(),
                )
                if (selected >= 0) open(project, ranked[selected], frame)
            }
        }
    }

    private fun open(project: Project, file: VirtualFile, frame: StackFrame) {
        val zeroBasedLine = frame.lineNumber?.let { (it - 1).coerceAtLeast(0) } ?: 0
        OpenFileDescriptor(project, file, zeroBasedLine).navigate(true)
    }

    internal fun rankCandidates(
        declaringClass: String?,
        fileName: String,
        candidates: List<VirtualFile>,
    ): List<VirtualFile> {
        val preferredPaths = rankCandidatePaths(declaringClass, fileName, candidates.map(VirtualFile::getPath).toList())
        return candidates.filter { it.path in preferredPaths }
    }

    internal fun rankCandidatePaths(
        declaringClass: String?,
        fileName: String,
        candidatePaths: List<String>,
    ): List<String> {
        val packageName = declaringClass
            ?.substringBefore('$')
            ?.substringBeforeLast('.', missingDelimiterValue = "")
            ?.replace('.', '/')
            ?.takeIf { it.isNotEmpty() }
            ?: return candidatePaths
        val suffix = "/$packageName/$fileName"
        return candidatePaths.filter { it.replace('\\', '/').endsWith(suffix) }.ifEmpty { candidatePaths }
    }
}
