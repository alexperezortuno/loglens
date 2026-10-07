package io.loglens.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.loglens.model.LogEntry
import io.loglens.parser.ParserRegistry
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Project-scoped bounded asynchronous log loading and current-view state. */
@Service(Service.Level.PROJECT)
class LogLensProjectService(private val project: Project) : Disposable {

    private val log = logger<LogLensProjectService>()
    private val listeners = CopyOnWriteArrayList<(LogLensSnapshot) -> Unit>()
    private val lock = Any()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "LogLens-file-reader").apply { isDaemon = true }
    }

    private var activeLoad: ActiveLoad? = null

    @Volatile
    private var currentSnapshot: LogLensSnapshot = LogLensSnapshot.empty()

    fun snapshot(): LogLensSnapshot = currentSnapshot

    /** Begin loading [file] asynchronously from its first record. */
    fun openFile(file: VirtualFile) {
        openPath(Path.of(file.path))
    }

    /** Begin loading [path] asynchronously from its first record. */
    fun openPath(path: Path) {
        val session: ActiveLoad
        val initial: LogLensSnapshot
        synchronized(lock) {
            activeLoad?.task?.cancel(true)
            session = ActiveLoad(path)
            activeLoad = session
            initial = session.toSnapshot()
            currentSnapshot = initial
        }
        dispatch(initial)
        schedulePage(session, firstPage = true)
    }

    /** Load the next bounded page for the currently open log. */
    fun loadMore() {
        val session: ActiveLoad
        val loadingSnapshot: LogLensSnapshot
        synchronized(lock) {
            session = activeLoad ?: return
            if (session.isLoading || !session.canLoadMore) return
            session.isLoading = true
            loadingSnapshot = session.toSnapshot()
            currentSnapshot = loadingSnapshot
        }
        dispatch(loadingSnapshot)
        schedulePage(session, firstPage = false)
    }

    /** Cancel the active read without discarding the records already loaded. */
    fun cancelLoad() {
        val snapshot: LogLensSnapshot
        synchronized(lock) {
            val session = activeLoad ?: return
            if (!session.isLoading) return
            session.isLoading = false
            session.task?.cancel(true)
            session.canLoadMore = session.totalBytes == null || session.nextByteOffset < session.totalBytes!!
            session.statusMessage = "Loading cancelled."
            snapshot = session.toSnapshot()
            currentSnapshot = snapshot
        }
        dispatch(snapshot)
    }

    /** Notify [listener] whenever a new page or load status is published. */
    fun addListener(listener: (LogLensSnapshot) -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: (LogLensSnapshot) -> Unit) {
        listeners -= listener
    }

    private fun schedulePage(session: ActiveLoad, firstPage: Boolean) {
        val future = executor.submit {
            try {
                val page = FileReadingService.readPage(
                    path = session.path,
                    startByteOffset = session.nextByteOffset,
                    firstLineNumber = session.nextLineNumber,
                    maxPageBytes = if (firstPage) INITIAL_PAGE_BYTES else MORE_PAGE_BYTES,
                    maxEntries = if (firstPage) INITIAL_PAGE_ENTRIES else MORE_PAGE_ENTRIES,
                    maxRecordBytes = MAX_RECORD_BYTES,
                    startsInsideRecord = session.startsInsideRecord,
                    isCancelled = { Thread.currentThread().isInterrupted || !isActive(session) },
                )

                val parsed = ArrayList<Pair<LogEntry, Long>>(page.records.size)
                for (record in page.records) {
                    if (Thread.currentThread().isInterrupted || !isActive(session)) {
                        throw CancellationException("Log loading cancelled")
                    }
                    val entry = session.registry.parse(record.text, record.lineNumber)
                        .copy(isTruncated = record.truncated)
                    parsed += entry to record.endByte - record.startByte
                }

                val result: LogLensSnapshot
                synchronized(lock) {
                    if (activeLoad !== session || !session.isLoading || Thread.currentThread().isInterrupted) return@submit
                    session.totalBytes = page.fileSize
                    session.bytesRead = page.nextByteOffset
                    session.nextByteOffset = page.nextByteOffset
                    session.nextLineNumber = page.nextLineNumber
                    session.startsInsideRecord = page.nextPageStartsInsideRecord
                    var retentionLimitReached = false
                    for ((entry, recordBytes) in parsed) {
                        val estimatedBytes = estimateRetainedBytes(entry)
                        if (session.entries.size >= MAX_RETAINED_ENTRIES ||
                            session.retainedBytes + estimatedBytes > MAX_RETAINED_BYTES
                        ) {
                            retentionLimitReached = true
                            break
                        }
                        session.entries += entry
                        session.retainedBytes += maxOf(recordBytes, estimatedBytes)
                    }
                    if (retentionLimitReached || (page.hasMore && (
                            session.entries.size >= MAX_RETAINED_ENTRIES ||
                                session.retainedBytes >= MAX_RETAINED_BYTES
                            ))
                    ) {
                        session.canLoadMore = false
                        session.statusMessage = "The viewer's memory limit was reached; loading stopped."
                        session.isLoading = false
                    } else if (page.nextPageStartsInsideRecord) {
                        session.canLoadMore = page.hasMore
                        session.statusMessage = "Skipping the remainder of an oversized record…"
                        session.isLoading = page.hasMore
                    } else {
                        session.canLoadMore = page.hasMore
                        if (!page.hasMore) session.statusMessage = "End of file."
                        session.isLoading = false
                    }
                    result = session.toSnapshot()
                    currentSnapshot = result
                }
                dispatch(result)
                if (result.isLoading) schedulePage(session, firstPage = false)
            } catch (_: CancellationException) {
                // A newer file request superseded this page.
            } catch (error: Exception) {
                log.warn("Unable to load log file ${session.path}", error)
                val result: LogLensSnapshot
                synchronized(lock) {
                    if (activeLoad !== session || !session.isLoading) return@submit
                    session.isLoading = false
                    session.canLoadMore = false
                    session.statusMessage = "Unable to read log: ${error.message ?: error.javaClass.simpleName}"
                    result = session.toSnapshot()
                    currentSnapshot = result
                }
                dispatch(result)
            }
        }
        synchronized(lock) {
            if (activeLoad === session) session.task = future else future.cancel(true)
        }
    }

    private fun isActive(session: ActiveLoad): Boolean = synchronized(lock) {
        activeLoad === session && session.isLoading
    }

    private fun dispatch(snapshot: LogLensSnapshot) {
        val application = ApplicationManager.getApplication()
        val notify = Runnable {
            if (project.isDisposed || currentSnapshot !== snapshot) return@Runnable
            listeners.forEach { listener ->
                try {
                    listener(snapshot)
                } catch (error: Exception) {
                    log.warn("Log viewer listener raised an exception", error)
                }
            }
        }
        if (application.isDispatchThread) notify.run() else application.invokeLater(notify)
    }

    private fun estimateRetainedBytes(entry: LogEntry): Long {
        val textCharacters = entry.raw.length.toLong() + entry.message.length +
            entry.metadata.entries.sumOf { (key, value) -> key.length.toLong() + value.length }
        return textCharacters * 2 + ENTRY_OBJECT_OVERHEAD_BYTES
    }

    override fun dispose() {
        synchronized(lock) {
            activeLoad?.task?.cancel(true)
            activeLoad = null
        }
        executor.shutdownNow()
        listeners.clear()
    }

    private class ActiveLoad(
        val path: Path,
    ) {
        val registry: ParserRegistry = ParserRegistry.defaults()
        val entries = mutableListOf<LogEntry>()
        var nextByteOffset: Long = 0
        var nextLineNumber: Int = 1
        var startsInsideRecord: Boolean = false
        var bytesRead: Long = 0
        var totalBytes: Long? = null
        var retainedBytes: Long = 0
        var isLoading: Boolean = true
        var canLoadMore: Boolean = true
        var statusMessage: String? = null
        var task: Future<*>? = null

        fun toSnapshot(): LogLensSnapshot = LogLensSnapshot(
            path = path,
            entries = entries.toList(),
            isLoading = isLoading,
            canLoadMore = canLoadMore,
            bytesRead = bytesRead,
            totalBytes = totalBytes,
            statusMessage = statusMessage,
        )
    }

    /** Immutable view of the loaded window and its paging state. */
    data class LogLensSnapshot(
        val path: Path?,
        val entries: List<LogEntry>,
        val isLoading: Boolean = false,
        val canLoadMore: Boolean = false,
        val bytesRead: Long = 0,
        val totalBytes: Long? = null,
        val statusMessage: String? = null,
    ) {
        companion object {
            fun empty(): LogLensSnapshot = LogLensSnapshot(path = null, entries = emptyList())
        }
    }

    private companion object {
        const val INITIAL_PAGE_BYTES = 10L * 1024 * 1024
        const val INITIAL_PAGE_ENTRIES = 10_000
        const val MORE_PAGE_BYTES = 4L * 1024 * 1024
        const val MORE_PAGE_ENTRIES = 5_000
        const val MAX_RETAINED_ENTRIES = 20_000
        const val MAX_RETAINED_BYTES = 32L * 1024 * 1024
        const val MAX_RECORD_BYTES = 1024 * 1024
        const val ENTRY_OBJECT_OVERHEAD_BYTES = 256L
    }
}
