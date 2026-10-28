package io.loglens.navigation

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.ui.popup.MnemonicNavigationFilter
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.SpeedSearchFilter
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import io.loglens.model.StackFrame
import java.util.concurrent.CompletableFuture
import javax.swing.Icon

/** Resolves Java/Kotlin stack frames to project files and navigates to their source lines. */
object StackFrameNavigator {

    fun navigate(project: Project, frame: StackFrame) {
        val future = DataManager.getInstance().getDataContextFromFocusAsync() as CompletableFuture<DataContext>
        val dataContext = future.get()
        navigate(project, frame, dataContext)
    }

    fun navigate(project: Project, frame: StackFrame, dataContext: DataContext) {
        val fileName = frame.fileName ?: return
        val candidates = ApplicationManager.getApplication().runReadAction(Computable<List<VirtualFile>> { 
            FilenameIndex.getVirtualFilesByName(fileName, GlobalSearchScope.projectScope(project))
                .filter { ProjectFileIndex.getInstance(project).isInContent(it) }
                .toList()
        })
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
                val step = object : ListPopupStep<String> {
                    override fun getValues(): List<String> = paths.toList()
                    override fun isSelectable(value: String): Boolean = true
                    override fun getTextFor(value: String): String = value
                    override fun getIconFor(value: String?): Icon? = null
                    override fun getSeparatorAbove(value: String?): ListSeparator? = null
                    override fun getDefaultOptionIndex(): Int = 0
                    override fun getTitle(): String? = "Ambiguous Stack Frame"
                    override fun onChosen(selectedValue: String, finalChoice: Boolean): PopupStep<*>? {
                        val index = paths.indexOf(selectedValue)
                        if (index >= 0) open(project, ranked[index], frame)
                        return PopupStep.FINAL_CHOICE
                    }
                    override fun hasSubstep(value: String): Boolean = false
                    override fun canceled() {}
                    override fun isMnemonicsNavigationEnabled(): Boolean = false
                    override fun getMnemonicNavigationFilter(): MnemonicNavigationFilter<String>? = null
                    override fun isSpeedSearchEnabled(): Boolean = true
                    override fun getSpeedSearchFilter(): SpeedSearchFilter<String>? = null
                    override fun isAutoSelectionEnabled(): Boolean = false
                    override fun getFinalRunnable(): Runnable? = null
                }
                val popup = JBPopupFactory.getInstance().createListPopup(step)
                popup.showInBestPositionFor(dataContext)
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
