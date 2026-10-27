package io.loglens.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.JBSplitter
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import io.loglens.ai.AiAction
import io.loglens.ai.AiRequest
import io.loglens.ai.AiResponse
import io.loglens.ai.AiService
import io.loglens.filter.LevelFilter
import io.loglens.filter.AdvancedFilter
import io.loglens.model.LogEntry
import io.loglens.model.LogLevel
import io.loglens.model.StackFrame
import io.loglens.model.ThrowableInfo
import io.loglens.observability.TraceGrouping
import io.loglens.observability.TraceTimeline
import io.loglens.observability.LogStatistics
import io.loglens.search.LogSearch
import io.loglens.service.LogLensProjectService.LogLensSnapshot
import io.loglens.settings.LogLensSettings
import io.loglens.util.AnsiCodes
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.JTextArea
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.UIManager
import com.intellij.openapi.ide.CopyPasteManager
import java.awt.datatransfer.StringSelection

/**
 * Compact record feed with a selected-record detail pane. The component owns:
 *  - the current [LogEntry] source list,
 *  - the active [LevelFilter] / [LogSearch],
 *  - the feed list and its detail view.
 *
 * File reading and parsing happen in bounded background pages; only the
 * retained page window is copied into this Swing model.
 */
class LogViewerPanel {

    private val listModel = DefaultListModel<LogEntry>()
    private val listComponent: JList<LogEntry> = JBList(listModel).apply {
        cellRenderer = LogEntryCardRenderer()
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        fixedCellHeight = CARD_HEIGHT
        visibleRowCount = 14
        addListSelectionListener { event ->
            if (!event.valueIsAdjusting) showDetails(selectedValue)
        }
    }
    private val scrollPane = JBScrollPane(listComponent)
    private val messageArea = detailTextArea()
    private val rawArea = detailTextArea().apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, font.size)
    }
    private val statisticsArea = detailTextArea().apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, font.size)
    }
    private val detailTitle = JBLabel("Select a record")
    private val detailContext = JBLabel(" ").apply { foreground = JBColor.GRAY }
    private var selectedEntry: LogEntry? = null
    private var stackFrameNavigationHandler: ((StackFrame, JComponent) -> Unit)? = null
    private val exceptionSummary = JBLabel("No exception details")
    private val frameModel = DefaultListModel<FrameRow>()
    private val traceModel = DefaultListModel<TraceRow>()
    private val traceList: JList<TraceRow> = JBList(traceModel).apply {
        cellRenderer = TraceRowRenderer()
        addListSelectionListener { event ->
            if (event.valueIsAdjusting) return@addListSelectionListener
            selectedValue?.entry?.let { entry ->
                listComponent.setSelectedValue(entry, true)
                showDetails(entry)
            }
        }
    }
    private val frameList: JList<FrameRow> = JBList(frameModel).apply {
        cellRenderer = FrameRowRenderer()
        visibleRowCount = 12
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount != 1) return
                val index = locationToIndex(event.point)
                if (index < 0 || !getCellBounds(index, index).contains(event.point)) return
                frameModel.getElementAt(index).frame?.let { frame ->
                    stackFrameNavigationHandler?.invoke(frame, this@apply)
                }
            }
        })
        addMouseMotionListener(object : MouseAdapter() {
            override fun mouseMoved(event: MouseEvent) {
                val index = locationToIndex(event.point)
                val row = if (index >= 0 && getCellBounds(index, index).contains(event.point)) {
                    frameModel.getElementAt(index)
                } else {
                    null
                }
                cursor = if (row?.frame?.fileName != null) {
                    Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                } else {
                    Cursor.getDefaultCursor()
                }
            }
        })
    }
    private val copyExceptionButton = JButton("Copy exception").apply {
        isEnabled = false
        addActionListener { selectedEntry?.throwable?.raw?.let(::copyText) }
    }
    private val copyFrameButton = JButton("Copy frame").apply {
        isEnabled = false
        addActionListener { frameList.selectedValue?.frame?.let { copyText(formatFrame(it)) } }
    }
    private val aiButtons = AiAction.all.map { action ->
        JButton(action.label).apply {
            isEnabled = false
            addActionListener { onAiAction(action) }
            toolTipText = "Use local AI to ${action.label.lowercase()}"
        }
    }
    private val exceptionPanel = JPanel(BorderLayout(0, 8)).apply {
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        val controls = JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false
            add(exceptionSummary, BorderLayout.CENTER)
            val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply {
                isOpaque = false
                add(copyFrameButton)
                add(copyExceptionButton)
                aiButtons.forEach { add(it) }
            }
            add(buttons, BorderLayout.EAST)
        }
        add(controls, BorderLayout.NORTH)
        add(JBScrollPane(frameList), BorderLayout.CENTER)
    }
    private val detailTabs = JTabbedPane().apply {
        addTab("Message", JBScrollPane(messageArea))
        addTab("Raw record", JBScrollPane(rawArea))
        addTab("Exception", exceptionPanel)
        addTab("Trace timeline", JBScrollPane(traceList))
        addTab("Statistics", JBScrollPane(statisticsArea))
    }
    private val details = JPanel(BorderLayout(0, 8)).apply {
        border = BorderFactory.createEmptyBorder(12, 14, 12, 12)
        val heading = JPanel(GridLayout(2, 1, 0, 5)).apply {
            isOpaque = false
            add(detailTitle)
            add(detailContext)
        }
        add(heading, BorderLayout.NORTH)
        add(detailTabs, BorderLayout.CENTER)
    }
    private val loadStatus = JLabel("Open a log file")
    private val statisticsSummary = JLabel(" ").apply {
        foreground = JBColor.GRAY
        toolTipText = "Statistics for records currently loaded in the viewer"
    }
    private var loadMoreAction: (() -> Unit)? = null
    private var cancelLoadAction: (() -> Unit)? = null
    private var tailAction: (() -> Unit)? = null
    private var lastSnapshot: LogLensSnapshot = LogLensSnapshot.empty()
    private var newEntriesCount = 0
    private val loadMoreButton = JButton("Load more").apply {
        isEnabled = false
        addActionListener {
            if (lastSnapshot.isLoading) cancelLoadAction?.invoke() else loadMoreAction?.invoke()
        }
    }
    private val followButton = JButton("Follow").apply {
        addActionListener { tailAction?.invoke() }
    }
    private val newEntriesButton = JButton().apply {
        isVisible = false
        addActionListener {
            newEntriesCount = 0
            isVisible = false
            scrollToBottom()
        }
    }
    private var detailsVisible = loadDetailsVisibility()
    private val toggleDetailsButton = JButton("⮜ Details").apply {
        toolTipText = "Toggle details panel (Ctrl+Shift+D)"
        addActionListener { toggleDetails() }
    }

    private val mainSplitter = JBSplitter(true, loadSplitProportion()).apply {
        setFirstComponent(scrollPane)
        setSecondComponent(details)
        setDividerWidth(8)
        setHonorComponentsMinimumSize(true)
        addPropertyChangeListener("proportion") { saveSplitProportion(it.newValue as Float) }
    }

    private val rootPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        val topBar = JPanel(BorderLayout(8, 0)).apply {
            border = BorderFactory.createEmptyBorder(6, 10, 6, 10)
            val left = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply { isOpaque = false; add(loadStatus) }
            val right = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply {
                isOpaque = false
                add(statisticsSummary)
                add(newEntriesButton)
                add(followButton)
                add(loadMoreButton)
                add(toggleDetailsButton)
            }
            add(left, BorderLayout.WEST)
            add(right, BorderLayout.EAST)
        }
        add(topBar, BorderLayout.NORTH)
        add(mainSplitter, BorderLayout.CENTER)
    }

    val component: JBPanel<JBPanel<*>> = rootPanel

    init {
        frameList.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) copyFrameButton.isEnabled = frameList.selectedValue?.frame != null
        }
        applyInitialDetailsVisibility()
    }

    private fun applyInitialDetailsVisibility() {
        if (!detailsVisible) {
            mainSplitter.setSecondComponent(JPanel().apply { isOpaque = false })
            toggleDetailsButton.text = "- Details"
        }
    }

    private var source: List<LogEntry> = emptyList()
    private var filter: LevelFilter = LevelFilter()
    private var advancedFilter: AdvancedFilter = AdvancedFilter.EMPTY
    private var search: LogSearch = LogSearch.EMPTY
    private var groupByTrace = false

    fun update(snapshot: LogLensSnapshot) {
        val wasAtBottom = isAtBottom()
        if (snapshot.isTailing && snapshot.entries.size > source.size && !wasAtBottom) {
            newEntriesCount += snapshot.entries.size - source.size
        }
        lastSnapshot = snapshot
        source = snapshot.entries
        updateStatistics()
        updateLoadStatus(snapshot)
        refresh()
        if (snapshot.isTailing && wasAtBottom) scrollToBottom()
        updateNewEntriesButton()
    }

    fun setLoadMoreAction(action: (() -> Unit)?) {
        loadMoreAction = action
        updateLoadStatus(lastSnapshot)
    }

    fun setCancelLoadAction(action: (() -> Unit)?) {
        cancelLoadAction = action
        updateLoadStatus(lastSnapshot)
    }

    fun setTailAction(action: (() -> Unit)?) {
        tailAction = action
        updateLoadStatus(lastSnapshot)
    }

    fun setStackFrameNavigationHandler(handler: ((StackFrame, JComponent) -> Unit)?) {
        stackFrameNavigationHandler = handler
    }

    fun getSplitProportion(): Float = mainSplitter.proportion

    fun setSplitProportion(proportion: Float) {
        mainSplitter.proportion = proportion.coerceIn(0.1f, 0.9f)
    }

    fun isDetailsVisible(): Boolean = detailsVisible

    fun setDetailsVisible(visible: Boolean) {
        if (detailsVisible != visible) {
            toggleDetails()
        }
    }

    fun resetLayout() {
        mainSplitter.proportion = 0.55f
        if (!detailsVisible) {
            toggleDetails()
        }
    }

    fun updateFilter(filter: LevelFilter) {
        this.filter = filter
        refresh()
    }

    fun updateSearch(search: LogSearch) {
        this.search = search
        refresh()
    }

    fun updateAdvancedFilter(filter: AdvancedFilter) {
        advancedFilter = filter
        refresh()
    }

    fun updateGroupByTrace(enabled: Boolean) {
        groupByTrace = enabled
        refresh()
    }

    private fun refresh() {
        val selectedIdentity = listComponent.selectedValue?.identity()
        listModel.clear()
        val matchingEntries = source
            .asSequence()
            .filter { filter.isAllowed(it) }
            .filter { advancedFilter.matches(it) }
            .filter { search.matches(it) }
            .toList()
        val visibleEntries = if (groupByTrace) TraceGrouping.group(matchingEntries) else matchingEntries
        listModel.addAll(visibleEntries)

        val selectedIndex = visibleEntries.indexOfFirst { it.identity() == selectedIdentity }
        if (visibleEntries.isNotEmpty()) {
            listComponent.selectedIndex = selectedIndex.takeIf { it >= 0 } ?: 0
        } else {
            showDetails(null)
        }
    }

    /** Exposed for tests that want to inspect the filtered set without going through Swing. */
    internal fun visible(): List<LogEntry> =
        source.filter { filter.isAllowed(it) && advancedFilter.matches(it) && search.matches(it) }

    private fun showDetails(entry: LogEntry?) {
        selectedEntry = entry
        val aiEnabled = LogLensSettings.getInstance().state.aiEnabled
        aiButtons.forEach { it.isEnabled = false }
        if (entry == null) {
            detailTitle.text = "Select a record"
            detailContext.text = " "
            messageArea.text = ""
            rawArea.text = ""
            exceptionSummary.text = "No exception details"
            frameModel.clear()
            traceModel.clear()
            copyExceptionButton.isEnabled = false
            copyFrameButton.isEnabled = false
            return
        }
        if (TraceGrouping.isHeader(entry)) {
            selectedEntry = null
            detailTitle.text = entry.message
            detailContext.text = "Related events are grouped by trace ID"
            messageArea.text = "Select an event in this trace to inspect its details."
            rawArea.text = ""
            frameModel.clear()
            traceModel.clear()
            copyExceptionButton.isEnabled = false
            copyFrameButton.isEnabled = false
            detailTabs.selectedIndex = MESSAGE_TAB_INDEX
            return
        }
        detailTitle.text = listOfNotNull(entry.timestamp, entry.level.display()).joinToString("    ·    ")
        val context = metadataSummary(entry).ifBlank { entry.logger.orEmpty() }
        detailContext.text = listOfNotNull(
            context.takeIf { it.isNotBlank() },
            "Stack trace continues in following records".takeIf { entry.metadata["stackTracePending"] == "true" },
            "Record preview truncated at the 1 MiB limit".takeIf { entry.isTruncated },
            "Stack trace truncated at the safety limit".takeIf { entry.throwable?.isTruncated == true },
        ).joinToString("    ·    ").ifBlank { " " }
        messageArea.text = entry.message
        messageArea.caretPosition = 0
        rawArea.text = prettyRaw(entry.raw)
        rawArea.caretPosition = 0
        updateExceptionDetails(entry.throwable)
        updateTraceTimeline(entry)
        detailTabs.selectedIndex = if (entry.throwable != null) EXCEPTION_TAB_INDEX else MESSAGE_TAB_INDEX
        if (aiEnabled && entry.throwable != null) {
            aiButtons.forEach { it.isEnabled = true }
        }
    }

    private fun updateTraceTimeline(entry: LogEntry) {
        traceModel.clear()
        TraceTimeline.forEntry(entry, source).forEach { event ->
            val context = listOfNotNull(
                event.entry.metadata["service"],
                event.entry.logger,
            ).firstOrNull().orEmpty()
            val delta = event.deltaMillis?.let { "+${formatDuration(it)}" } ?: "  start"
            traceModel.addElement(TraceRow("$delta  ${event.entry.level.display()}  ${context.ifBlank { "event" }}  ${event.entry.message}", event.entry))
        }
    }

    private fun updateStatistics() {
        val stats = LogStatistics.calculate(source)
        val traces = source.mapNotNull { it.metadata["traceId"]?.takeIf(String::isNotBlank) }.toSet().size
        statisticsSummary.text = if (stats.totalEntries == 0) {
            " "
        } else {
            "errors ${stats.errorCount} · exceptions ${stats.exceptionCounts.size} · traces $traces"
        }
        statisticsArea.text = buildString {
            appendLine("Records: ${stats.totalEntries}")
            appendLine("Errors + fatal: ${stats.errorCount}")
            appendLine()
            appendLine("Log levels")
            stats.levelCounts.entries.sortedByDescending { it.value }.forEach { (level, count) ->
                appendLine("  ${level.display().padEnd(8)} $count")
            }
            appendLine()
            appendLine("Most frequent exceptions")
            appendRanked(stats.exceptionCounts)
            appendLine()
            appendLine("Most active loggers")
            appendRanked(stats.loggerCounts)
            appendLine()
            appendLine("Levels over time (UTC minute)")
            stats.levelsOverTime.forEach { (minute, counts) ->
                val summary = counts.entries.sortedByDescending { it.value }
                    .joinToString(", ") { "${it.key.display()}=${it.value}" }
                appendLine("  $minute  $summary")
            }
        }
        statisticsArea.caretPosition = 0
    }

    private fun StringBuilder.appendRanked(values: Map<String, Int>) {
        if (values.isEmpty()) {
            appendLine("  (none)")
        } else {
            values.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .take(STATISTICS_LIMIT)
                .forEach { (name, count) -> appendLine("  $count  $name") }
        }
    }

    private fun formatDuration(millis: Long): String = when {
        millis < 1_000 -> "${millis}ms"
        millis < 60_000 -> "%.3fs".format(millis / 1_000.0)
        else -> "%.1fm".format(millis / 60_000.0)
    }

    private fun updateExceptionDetails(throwable: ThrowableInfo?) {
        frameModel.clear()
        if (throwable == null) {
            exceptionSummary.text = "No exception details"
            copyExceptionButton.isEnabled = false
            copyFrameButton.isEnabled = false
            return
        }
        val summary = listOfNotNull(throwable.className, throwable.message).joinToString(": ")
            .ifBlank { "Exception" } + if (throwable.isTruncated) " (details truncated)" else ""
        exceptionSummary.text = "<html>${escapeHtml(summary)}</html>"
        addThrowableFrames(throwable, causeLabel = null, depth = 0)
        copyExceptionButton.isEnabled = true
        copyFrameButton.isEnabled = false
    }

    private fun onAiAction(action: AiAction) {
        val entry = selectedEntry ?: return
        val settings = LogLensSettings.getInstance()
        if (!settings.state.aiEnabled) return

        val provider = settings.getAiProvider()
        val aiService = AiService.create()

        val context = buildAiContext(entry)
        val request = AiRequest(
            action = action,
            entry = formatEntryForAi(entry),
            context = context,
            provider = provider,
        )

        aiButtons.forEach { it.isEnabled = false }
        val progressButton = aiButtons.find { it.text == action.label } ?: return
        progressButton.text = "…"

        object : Task.Backgroundable(null, "LogLens AI: ${action.label}", false) {
            override fun run(indicator: ProgressIndicator) {
                val response = aiService.send(request)
                ApplicationManager.getApplication().invokeLater {
                    progressButton.text = action.label
                    aiButtons.forEach { it.isEnabled = true }
                    when (response) {
                        is AiResponse.Success -> showAiResult(action.label, response.content)
                        is AiResponse.Error -> showAiError(action.label, response.message)
                    }
                }
            }
        }.queue()
    }

    private fun buildAiContext(entry: LogEntry): String {
        val traceId = entry.metadata["traceId"]
        val spanId = entry.metadata["spanId"]
        val requestId = entry.metadata["requestId"]
        val service = entry.metadata["service"]

        val related = source
            .filter { it != entry }
            .filter { it.metadata["traceId"] == traceId || it.metadata["spanId"] == spanId || it.metadata["requestId"] == requestId }
            .take(20)
            .joinToString("\n") { formatEntryForAi(it) }

        val correlation = listOfNotNull(
            traceId?.let { "traceId=$it" },
            spanId?.let { "spanId=$it" },
            requestId?.let { "requestId=$it" },
            service?.let { "service=$it" },
            entry.logger?.let { "logger=$it" },
            entry.thread?.let { "thread=$it" },
        ).joinToString(", ")

        return if (related.isNotBlank()) {
            "Correlation: $correlation\n\nRelated entries:\n$related"
        } else {
            "Correlation: $correlation\n\n(no related entries found)"
        }
    }

    private fun formatEntryForAi(entry: LogEntry): String {
        val parts = mutableListOf<String>()
        entry.timestamp?.let { parts += "time=$it" }
        entry.level?.let { parts += "level=$it" }
        entry.logger?.let { parts += "logger=$it" }
        entry.thread?.let { parts += "thread=$it" }
        parts += "message=${entry.message}"
        entry.throwable?.let { parts += "exception=${it.className}: ${it.message}" }
        entry.metadata.entries.forEach { (k, v) -> parts += "$k=$v" }
        return parts.joinToString(" | ")
    }

    private fun showAiResult(actionLabel: String, content: String) {
        val dialog = javax.swing.JDialog()
        dialog.title = "LogLens AI — $actionLabel"
        dialog.setSize(600, 400)
        dialog.setLocationRelativeTo(component)
        val textArea = javax.swing.JTextArea(content).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            font = Font(Font.MONOSPACED, Font.PLAIN, 13)
            margin = java.awt.Insets(10, 10, 10, 10)
        }
        val scrollPane = JBScrollPane(textArea)
        val copyButton = JButton("Copy").apply {
            addActionListener { copyText(content) }
        }
        val closeButton = JButton("Close").apply {
            addActionListener { dialog.dispose() }
        }
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
            add(copyButton)
            add(closeButton)
        }
        dialog.contentPane.apply {
            layout = BorderLayout()
            add(scrollPane, BorderLayout.CENTER)
            add(buttonPanel, BorderLayout.SOUTH)
        }
        dialog.isVisible = true
    }

    private fun showAiError(actionLabel: String, message: String) {
        com.intellij.openapi.ui.Messages.showErrorDialog(
            component,
            "AI request failed: $message",
            "LogLens AI — $actionLabel",
        )
    }

    private fun addThrowableFrames(throwable: ThrowableInfo, causeLabel: String?, depth: Int) {
        if (causeLabel != null) {
            val label = listOfNotNull(throwable.className, throwable.message).joinToString(": ").ifBlank { "Exception" }
            frameModel.addElement(FrameRow("${"  ".repeat(depth)}$causeLabel $label", null))
        }
        throwable.frames.forEach { frame ->
            frameModel.addElement(FrameRow("${"  ".repeat(depth)}${formatFrame(frame)}", frame))
        }
        if (throwable.omittedFrameCount > 0) {
            frameModel.addElement(FrameRow("${"  ".repeat(depth)}… ${throwable.omittedFrameCount} more", null))
        }
        throwable.causes.forEach { addThrowableFrames(it, "Caused by:", depth + 1) }
        throwable.suppressed.forEach { addThrowableFrames(it, "Suppressed:", depth + 1) }
    }

    private fun formatFrame(frame: StackFrame): String {
        val method = listOfNotNull(frame.declaringClass, frame.methodName).joinToString(".")
        val location = when {
            frame.fileName != null && frame.lineNumber != null -> "${frame.fileName}:${frame.lineNumber}"
            frame.fileName != null -> frame.fileName
            else -> "Unknown Source"
        }
        return "at $method($location)"
    }

    private fun copyText(text: String) {
        CopyPasteManager.getInstance().setContents(StringSelection(text))
    }

    private data class FrameRow(
        val text: String,
        val frame: StackFrame?,
        val depth: Int = 0,
    )

    private data class TraceRow(val text: String, val entry: LogEntry)

    private inner class TraceRowRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            val label = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            val row = value as? TraceRow ?: return label
            text = row.text
            border = BorderFactory.createEmptyBorder(5, 8, 5, 8)
            return label
        }
    }

    private inner class FrameRowRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            val label = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            val row = value as? FrameRow ?: return label
            val link = row.frame?.fileName != null
            text = "<html>${escapeHtml(row.text)}</html>"
            border = BorderFactory.createEmptyBorder(2, 8 + row.depth * 12, 2, 8)
            if (link && !isSelected) foreground = JBColor(0x245EA8, 0x73A7F5)
            return label
        }
    }

    private fun metadataSummary(entry: LogEntry): String {
        val fields = entry.metadata
        val app = fields["app"]
        val module = fields["module"]
        val function = fields["function"]
        val sourceLine = fields["sourceLine"]
        val parts = buildList {
            app?.let(::add)
            when {
                module != null && function != null -> add("$module.$function")
                module != null -> add(module)
                function != null -> add(function)
            }
            sourceLine?.let { add("line $it") }
            fields.filterKeys { it !in DISPLAYED_METADATA_KEYS }
                .entries.take(2)
                .forEach { (key, value) -> add("$key: $value") }
        }
        return parts.joinToString("  ·  ")
    }

    private fun prettyRaw(raw: String): String = runCatching {
        val json = JsonParser.parseString(raw)
        if (json.isJsonObject || json.isJsonArray) GsonBuilder().setPrettyPrinting().create().toJson(json) else raw
    }.getOrDefault(raw)

    private fun formatTimestamp(timestamp: String?): String {
        if (timestamp == null) return ""
        return runCatching { LocalDateTime.parse(timestamp).format(TIME_FORMAT) }.getOrDefault(timestamp)
    }

    private fun detailTextArea(): JTextArea = JTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        background = UIManager.getColor("Panel.background") ?: JBColor.background()
        foreground = JBColor.foreground()
    }

    private fun updateLoadStatus(snapshot: LogLensSnapshot) {
        val total = snapshot.totalBytes?.let { " of ${formatBytes(it)}" }.orEmpty()
        val progress = "first ${snapshot.entries.size} records · ${formatBytes(snapshot.bytesRead)}$total"
        val indexProgress = if (snapshot.indexedEntries > 0) {
            "indexed ${snapshot.indexedEntries} records · ${formatBytes(snapshot.indexedBytes)}"
        } else {
            null
        }
        loadStatus.text = buildList {
            add(when {
                snapshot.isLoading && snapshot.entries.isEmpty() -> "Loading log…"
                snapshot.isLoading -> "Loading more…  $progress"
                else -> "Showing the $progress"
            })
            indexProgress?.let(::add)
            snapshot.statusMessage?.let(::add)
        }.joinToString("    ·    ")
        loadMoreButton.text = if (snapshot.isLoading) "Cancel" else "Load more"
        loadMoreButton.isVisible = !snapshot.isTailing
        loadMoreButton.isEnabled = if (snapshot.isLoading && !snapshot.isTailing) {
            cancelLoadAction != null
        } else {
            loadMoreAction != null && snapshot.canLoadMore
        }
        followButton.text = when {
            !snapshot.isTailing -> "Follow"
            snapshot.isTailPaused -> "Resume"
            else -> "Pause"
        }
        followButton.isEnabled = tailAction != null
    }

    private fun updateNewEntriesButton() {
        newEntriesButton.text = if (newEntriesCount > 0) "$newEntriesCount new entries" else ""
        newEntriesButton.isVisible = newEntriesCount > 0
    }

    private fun isAtBottom(): Boolean {
        val bar = scrollPane.verticalScrollBar
        return bar.value + bar.visibleAmount >= bar.maximum - 4
    }

    private fun scrollToBottom() {
        if (listModel.isEmpty) return
        listComponent.scrollRectToVisible(listComponent.getCellBounds(listModel.size - 1, listModel.size - 1))
    }

    private fun toggleDetails() {
        detailsVisible = !detailsVisible
        if (detailsVisible) {
            mainSplitter.setSecondComponent(details)
            toggleDetailsButton.text = "⮜ Details"
        } else {
            mainSplitter.setSecondComponent(JPanel().apply { isOpaque = false })
            toggleDetailsButton.text = "⮞ Details"
        }
        mainSplitter.revalidate()
        mainSplitter.repaint()
        saveDetailsVisibility()
    }

    private fun loadSplitProportion(): Float = LogLensSettings.getInstance().state.viewerSplitProportion

    private fun saveSplitProportion(proportion: Float) {
        LogLensSettings.getInstance().state.viewerSplitProportion = proportion
    }

    private fun loadDetailsVisibility(): Boolean = LogLensSettings.getInstance().state.detailsPanelVisible

    private fun saveDetailsVisibility() {
        LogLensSettings.getInstance().state.detailsPanelVisible = detailsVisible
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024 -> "%.1f MiB".format(bytes / (1024.0 * 1024))
        bytes >= 1024 -> "%.0f KiB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    private inner class LogEntryCardRenderer : JPanel(BorderLayout(0, 4)), ListCellRenderer<LogEntry> {
        private val timestamp = JBLabel().apply { foreground = JBColor.GRAY }
        private val badge = JBLabel().apply {
            isOpaque = true
            border = BorderFactory.createEmptyBorder(2, 6, 2, 6)
        }
        private val message = JBLabel()
        private val metadata = JBLabel().apply {
            foreground = JBColor.GRAY
            font = font.deriveFont(Font.PLAIN, font.size2D - 1f)
        }
        private val leading = JPanel(FlowLayout(FlowLayout.LEFT, 7, 0)).apply {
            isOpaque = false
            add(timestamp)
            add(badge)
        }

        init {
            isOpaque = true
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor(0xE7E9ED, 0x383B40)),
                BorderFactory.createEmptyBorder(8, 10, 7, 10),
            )
            val header = JPanel(BorderLayout(10, 0)).apply {
                isOpaque = false
                add(leading, BorderLayout.WEST)
                add(message, BorderLayout.CENTER)
            }
            add(header, BorderLayout.CENTER)
            add(metadata, BorderLayout.SOUTH)
        }

        override fun getListCellRendererComponent(
            list: JList<out LogEntry>,
            value: LogEntry,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            val rowBackground = if (isSelected) list.selectionBackground else list.background
            val textForeground = if (isSelected) list.selectionForeground else JBColor.foreground()
            background = rowBackground
            timestamp.text = formatTimestamp(value.timestamp)
            timestamp.foreground = if (isSelected) textForeground else JBColor.GRAY
            badge.text = value.level.display()
            badge.background = severityColor(value.level)
            badge.foreground = Color.WHITE
            message.text = compactMessage(value)
            message.foreground = textForeground
            metadata.text = metadataSummary(value)
            metadata.foreground = if (isSelected) textForeground else JBColor.GRAY
            if (TraceGrouping.isHeader(value)) {
                badge.text = "TRACE"
                badge.background = JBColor(0x5E4B8B, 0x8E78C2)
                message.text = value.message
                message.font = message.font.deriveFont(Font.BOLD)
                metadata.text = "Related events"
            } else {
                message.font = message.font.deriveFont(Font.PLAIN)
            }
            toolTipText = value.message
            return this
        }
    }

    private fun compactMessage(entry: LogEntry): String {
        val limit = 180
        val segments = if (AnsiCodes.containsCodes(entry.raw)) {
            AnsiCodes.segments(entry.raw)
        } else {
            listOf(AnsiCodes.Segment(firstNonBlankLine(entry.message), AnsiCodes.Style()))
        }
        var remaining = limit
        val html = buildString {
            append("<html><span>")
            for (segment in segments) {
                if (remaining <= 0) break
                val text = segment.text.replace('\n', ' ').replace('\r', ' ')
                if (text.isEmpty()) continue
                val visible = text.take(remaining)
                if (visible.isEmpty()) continue
                append("<span style=\"")
                segment.style.foreground?.let { append("color:${cssColor(it)};") }
                segment.style.background?.let { append("background-color:${cssColor(it)};") }
                if (segment.style.bold) append("font-weight:bold;")
                if (segment.style.italic) append("font-style:italic;")
                if (segment.style.underline) append("text-decoration:underline;")
                append("\">")
                append(escapeHtml(visible))
                append("</span>")
                remaining -= visible.length
            }
            if (segments.sumOf { it.text.length } > limit) append("…")
            append("</span></html>")
        }
        return html
    }

    private fun firstNonBlankLine(message: String): String =
        message.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: ""

    private fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private fun cssColor(color: Color): String =
        String.format(Locale.ROOT, "#%02x%02x%02x", color.red, color.green, color.blue)

    private fun severityColor(level: LogLevel): Color = when (level) {
        LogLevel.TRACE -> JBColor(0x666666, 0x888888)
        LogLevel.DEBUG -> JBColor(0x4169A1, 0x5B8CCB)
        LogLevel.INFO -> JBColor(0x26734D, 0x31875B)
        LogLevel.WARN -> JBColor(0x9A6300, 0xB57A12)
        LogLevel.ERROR -> JBColor(0xA53030, 0xC64A4A)
        LogLevel.FATAL -> JBColor(0x6F1D36, 0x9E3150)
        LogLevel.UNKNOWN -> JBColor(0x62666D, 0x777C84)
    }

    private companion object {
        const val CARD_HEIGHT = 54
        const val MESSAGE_TAB_INDEX = 0
        const val EXCEPTION_TAB_INDEX = 2
        const val STATISTICS_LIMIT = 10
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d · HH:mm:ss", Locale.getDefault())
        val DISPLAYED_METADATA_KEYS = setOf(
            "app", "module", "function", "sourceLine", "stackTracePending", "exceptionTruncated",
        )
    }
}
