package io.loglens.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.loglens.model.LogEntry
import io.loglens.parser.ParserRegistry
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Project-scoped state holder. Currently keeps the most recently opened log
 * and its parsed entries so the tool window can react to "Open with LogLens".
 *
 * The class is intentionally synchronous for 0.1; SPEC §9 (large files) and
 * SPEC §10 (tail mode) will land in 0.3 and trigger the move to background
 * coroutines.
 */
@Service(Service.Level.PROJECT)
class LogLensProjectService(private val project: Project) : Disposable {

    private val log = logger<LogLensProjectService>()
    private val registry: ParserRegistry = ParserRegistry.defaults()
    private val listeners = CopyOnWriteArrayList<(LogLensSnapshot) -> Unit>()

    @Volatile
    private var currentSnapshot: LogLensSnapshot = LogLensSnapshot.empty()

    fun snapshot(): LogLensSnapshot = currentSnapshot

    /** Open [file] by reading and parsing it line-by-line. */
    fun openFile(file: VirtualFile) {
        openPath(Path.of(file.path))
    }

    /** Open a file on disk and parse its lines. */
    fun openPath(path: Path) {
        log.info("Opening log file: $path")
        val entries = mutableListOf<LogEntry>()
        registry.reset()
        FileReadingService.forEachLine(path) { line, number ->
            entries += registry.parse(line, number)
        }
        publish(LogLensSnapshot(path = path, entries = entries))
    }

    /** Notify [listener] whenever a new file is opened. */
    fun addListener(listener: (LogLensSnapshot) -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: (LogLensSnapshot) -> Unit) {
        listeners -= listener
    }

    private fun publish(snapshot: LogLensSnapshot) {
        currentSnapshot = snapshot
        listeners.forEach {
            try {
                it(snapshot)
            } catch (e: Exception) {
                log.warn("Listener raised an exception", e)
            }
        }
    }

    override fun dispose() {
        listeners.clear()
    }

    /** Immutable view of the currently loaded log. */
    data class LogLensSnapshot(
        val path: Path?,
        val entries: List<LogEntry>,
    ) {
        companion object {
            fun empty(): LogLensSnapshot = LogLensSnapshot(path = null, entries = emptyList())
        }
    }
}