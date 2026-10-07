package io.loglens.ui

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import io.loglens.filter.LevelFilter
import io.loglens.model.LogEntry
import io.loglens.model.LogLevel
import io.loglens.search.LogSearch
import io.loglens.service.LogLensProjectService.LogLensSnapshot
import io.loglens.util.AnsiCodes
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridLayout
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.JTextArea
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.UIManager

/**
 * Compact record feed with a selected-record detail pane. The component owns:
 *  - the current [LogEntry] source list,
 *  - the active [LevelFilter] / [LogSearch],
 *  - the feed list and its detail view.
 *
 * Updates are synchronous — the 0.1 MVP is sized for files that load in well
 * under a second. SPEC §9 will move this to a bounded, backgrounded pipeline.
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
    private val detailTitle = JBLabel("Select a record")
    private val detailContext = JBLabel(" ").apply { foreground = JBColor.GRAY }
    private val detailTabs = JTabbedPane().apply {
        addTab("Message", JBScrollPane(messageArea))
        addTab("Raw record", JBScrollPane(rawArea))
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
    private val splitPane = javax.swing.JSplitPane(javax.swing.JSplitPane.HORIZONTAL_SPLIT, scrollPane, details).apply {
        resizeWeight = 0.45
        setDividerLocation(0.45)
        isContinuousLayout = true
        border = BorderFactory.createEmptyBorder()
    }
    val component: JBPanel<JBPanel<*>> = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        add(splitPane, BorderLayout.CENTER)
    }

    private var source: List<LogEntry> = emptyList()
    private var filter: LevelFilter = LevelFilter()
    private var search: LogSearch = LogSearch.EMPTY

    fun update(snapshot: LogLensSnapshot) {
        source = snapshot.entries
        refresh()
    }

    fun updateFilter(filter: LevelFilter) {
        this.filter = filter
        refresh()
    }

    fun updateSearch(search: LogSearch) {
        this.search = search
        refresh()
    }

    private fun refresh() {
        val selectedIdentity = listComponent.selectedValue?.identity()
        listModel.clear()
        val visibleEntries = source
            .asSequence()
            .filter { filter.isAllowed(it) }
            .filter { search.matches(it) }
            .toList()
        visibleEntries.forEach { listModel.addElement(it) }

        val selectedIndex = visibleEntries.indexOfFirst { it.identity() == selectedIdentity }
        if (visibleEntries.isNotEmpty()) {
            listComponent.selectedIndex = selectedIndex.takeIf { it >= 0 } ?: 0
        } else {
            showDetails(null)
        }
    }

    /** Exposed for tests that want to inspect the filtered set without going through Swing. */
    internal fun visible(): List<LogEntry> =
        source.filter { filter.isAllowed(it) && search.matches(it) }

    private fun showDetails(entry: LogEntry?) {
        if (entry == null) {
            detailTitle.text = "Select a record"
            detailContext.text = " "
            messageArea.text = ""
            rawArea.text = ""
            return
        }
        detailTitle.text = listOfNotNull(entry.timestamp, entry.level.display()).joinToString("    ·    ")
        detailContext.text = metadataSummary(entry).ifBlank { entry.logger ?: " " }
        messageArea.text = entry.message
        messageArea.caretPosition = 0
        rawArea.text = prettyRaw(entry.raw)
        rawArea.caretPosition = 0
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
        const val CARD_HEIGHT = 62
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d · HH:mm:ss", Locale.getDefault())
        val DISPLAYED_METADATA_KEYS = setOf("app", "module", "function", "sourceLine")
    }
}
