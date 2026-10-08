package io.loglens.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.loglens.exception.StackTraceAssembler
import io.loglens.model.LogEntry
import io.loglens.parser.ParserRegistry
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** Project-scoped bounded asynchronous log loading and current-view state. */
@Service(Service.Level.PROJECT)
class LogLensProjectService(private val project: Project) : Disposable {

    private val log = logger<LogLensProjectService>()
    private val listeners = CopyOnWriteArrayList<(LogLensSnapshot) -> Unit>()
    private val lock = Any()
    private val executor: ScheduledExecutorService = Executors.newScheduledThreadPool(1) { runnable ->
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
            if (session.isLoading || !session.canLoadMore || session.isTailing) return
            session.isLoading = true
            loadingSnapshot = session.toSnapshot()
            currentSnapshot = loadingSnapshot
        }
        dispatch(loadingSnapshot)
        schedulePage(session, firstPage = false)
    }

    /** Start following appended records, or resume a paused follow session. */
    fun toggleTailing() {
        val session: ActiveLoad
        val snapshot: LogLensSnapshot
        val shouldSchedule: Boolean
        synchronized(lock) {
            session = activeLoad ?: return
            if (session.isTailing && !session.isTailPaused) {
                session.isTailPaused = true
                session.isLoading = false
                session.task?.cancel(true)
                session.statusMessage = "Follow paused."
                shouldSchedule = false
            } else {
                session.isTailing = true
                session.isTailPaused = false
                session.canLoadMore = true
                shouldSchedule = !session.isLoading
                session.isLoading = true
                session.statusMessage = "Following file…"
            }
            snapshot = session.toSnapshot()
            currentSnapshot = snapshot
        }
        dispatch(snapshot)
        if (shouldSchedule) schedulePage(session, firstPage = false)
    }

    /** Stop following while keeping the loaded records available. */
    fun stopTailing() {
        val snapshot: LogLensSnapshot
        synchronized(lock) {
            val session = activeLoad ?: return
            session.isTailing = false
            session.isTailPaused = false
            session.isLoading = false
            session.task?.cancel(true)
            session.statusMessage = "Follow stopped."
            snapshot = session.toSnapshot()
            currentSnapshot = snapshot
        }
        dispatch(snapshot)
    }

    /** Cancel the active read without discarding the records already loaded. */
    fun cancelLoad() {
        val snapshot: LogLensSnapshot
        synchronized(lock) {
            val session = activeLoad ?: return
            if (!session.isLoading) return
            session.isLoading = false
            session.task?.cancel(true)
            if (session.isTailing) {
                session.isTailPaused = true
                session.statusMessage = "Follow paused."
            } else {
                session.canLoadMore = session.totalBytes == null || session.nextByteOffset < session.totalBytes!!
                session.statusMessage = "Loading cancelled."
            }
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
                synchronized(lock) {
                    if (session.isTailing && activeLoad === session && Files.size(session.path) < session.nextByteOffset) {
                        session.nextByteOffset = 0
                        session.nextLineNumber = 1
                        session.startsInsideRecord = false
                        session.registry.reset()
                        session.stackTraceAssembler.reset()
                        session.pendingPreview = null
                        session.entries.clear()
                        session.retainedBytes = 0
                        session.statusMessage = "File was rotated; restarted from the beginning."
                    }
                }
                val page = FileReadingService.readPage(
                    path = session.path,
                    startByteOffset = session.nextByteOffset,
                    firstLineNumber = session.nextLineNumber,
                    maxPageBytes = if (firstPage) INITIAL_PAGE_BYTES else MORE_PAGE_BYTES,
                    maxEntries = if (firstPage) INITIAL_PAGE_ENTRIES else MORE_PAGE_ENTRIES,
                    maxRecordBytes = MAX_RECORD_BYTES,
                    startsInsideRecord = session.startsInsideRecord,
                    completeRecordsOnly = session.isTailing,
                    isCancelled = { Thread.currentThread().isInterrupted || !isActive(session) },
                )

                val emitted = mutableListOf<LogEntry>()
                for (record in page.records) {
                    if (Thread.currentThread().isInterrupted || !isActive(session)) {
                        throw CancellationException("Log loading cancelled")
                    }
                    val entry = session.registry.parse(record.text, record.lineNumber)
                        .copy(isTruncated = record.truncated)
                    emitted += session.stackTraceAssembler.accept(entry)
                }
                if (!page.hasMore && !page.nextPageStartsInsideRecord && !page.partialRecordWaiting) {
                    emitted += session.stackTraceAssembler.finish()
                }
                val pendingPreview = session.stackTraceAssembler.preview()

                val result: LogLensSnapshot
                synchronized(lock) {
                    if (activeLoad !== session || !session.isLoading || Thread.currentThread().isInterrupted) return@submit
                    session.totalBytes = page.fileSize
                    session.bytesRead = page.nextByteOffset
                    session.nextByteOffset = page.nextByteOffset
                    session.nextLineNumber = page.nextLineNumber
                    session.startsInsideRecord = page.nextPageStartsInsideRecord
                    var retentionLimitReached = false
                    val acceptedEntries = session.entries.toMutableList()
                    var retainedBytes = acceptedEntries.sumOf(::estimateRetainedBytes)
                    val pendingBytes = pendingPreview?.let(::estimateRetainedBytes) ?: 0L
                    for (entry in emitted) {
                        val estimatedBytes = estimateRetainedBytes(entry)
                        if (!session.isTailing && (acceptedEntries.size + 1 + (if (pendingPreview != null) 1 else 0) > MAX_RETAINED_ENTRIES ||
                            retainedBytes + estimatedBytes + pendingBytes > MAX_RETAINED_BYTES)) {
                            retentionLimitReached = true
                            break
                        }
                        acceptedEntries += entry
                        retainedBytes += estimatedBytes
                    }
                    if (!retentionLimitReached && pendingPreview != null) {
                        if (!session.isTailing && (acceptedEntries.size + 1 > MAX_RETAINED_ENTRIES ||
                            retainedBytes + pendingBytes > MAX_RETAINED_BYTES)) {
                            retentionLimitReached = true
                        } else {
                            retainedBytes += pendingBytes
                        }
                    }
                    if (session.isTailing && !retentionLimitReached) {
                        while (acceptedEntries.size + (if (pendingPreview != null) 1 else 0) > MAX_RETAINED_ENTRIES ||
                            retainedBytes > MAX_RETAINED_BYTES) {
                            if (acceptedEntries.isEmpty()) {
                                retentionLimitReached = true
                                break
                            }
                            retainedBytes -= estimateRetainedBytes(acceptedEntries.removeAt(0))
                        }
                    }
                    if (retentionLimitReached) {
                        session.stackTraceAssembler.reset()
                        session.pendingPreview = null
                    } else {
                        session.pendingPreview = pendingPreview
                    }
                    session.entries.clear()
                    session.entries.addAll(acceptedEntries)
                    session.retainedBytes = retainedBytes
                    if (retentionLimitReached || (!session.isTailing && page.hasMore && (
                            session.entries.size >= MAX_RETAINED_ENTRIES ||
                                session.retainedBytes >= MAX_RETAINED_BYTES
                            ))
                    ) {
                        session.canLoadMore = false
                        session.statusMessage = "The viewer's memory limit was reached; loading stopped."
                        session.isLoading = false
                    } else if (page.nextPageStartsInsideRecord && !session.isTailing) {
                        session.canLoadMore = page.hasMore
                        session.statusMessage = "Skipping the remainder of an oversized record…"
                        session.isLoading = page.hasMore
                    } else if (session.isTailing) {
                        session.canLoadMore = true
                        session.isLoading = page.hasMore && !page.partialRecordWaiting
                        session.statusMessage = "Following file…"
                    } else {
                        session.canLoadMore = page.hasMore
                        if (!page.hasMore) session.statusMessage = "End of file."
                        session.isLoading = false
                    }
                    result = session.toSnapshot()
                    currentSnapshot = result
                }
                dispatch(result)
                if (result.isLoading) {
                    schedulePage(session, firstPage = false)
                } else if (result.isTailing && !result.isTailPaused) {
                    scheduleTailPoll(session)
                }
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

    private fun scheduleTailPoll(session: ActiveLoad) {
        val future = executor.schedule({
            val snapshot: LogLensSnapshot
            synchronized(lock) {
                if (activeLoad !== session || !session.isTailing || session.isTailPaused) return@schedule
                session.isLoading = true
                snapshot = session.toSnapshot()
                currentSnapshot = snapshot
            }
            dispatch(snapshot)
            schedulePage(session, firstPage = false)
        }, TAIL_POLL_MILLIS, TimeUnit.MILLISECONDS)
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
        val stackTraceAssembler = StackTraceAssembler()
        val entries = mutableListOf<LogEntry>()
        var pendingPreview: LogEntry? = null
        var isTailing: Boolean = false
        var isTailPaused: Boolean = false
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
            entries = if (pendingPreview == null) entries.toList() else entries + pendingPreview!!,
            isLoading = isLoading,
            canLoadMore = canLoadMore,
            isTailing = isTailing,
            isTailPaused = isTailPaused,
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
        val isTailing: Boolean = false,
        val isTailPaused: Boolean = false,
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
        const val TAIL_POLL_MILLIS = 1_000L
    }
}
