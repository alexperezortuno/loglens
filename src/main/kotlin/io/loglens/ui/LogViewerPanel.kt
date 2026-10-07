package io.loglens.ui

import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import io.loglens.filter.LevelFilter
import io.loglens.model.LogEntry
import io.loglens.model.LogLevel
import io.loglens.search.LogSearch
import io.loglens.service.LogLensProjectService.LogLensSnapshot
import io.loglens.util.AnsiCodes
import io.loglens.util.RawText
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.DefaultListModel
import javax.swing.JList

/**
 * Scrollable list of parsed entries. The component owns:
 *  - the current [LogEntry] source list,
 *  - the active [LevelFilter] / [LogSearch],
 *  - the Swing list model that the UI shows.
 *
 * Updates are synchronous — the 0.1 MVP is sized for files that load in well
 * under a second. SPEC §9 will move this to a bounded, backgrounded pipeline.
 */
class LogViewerPanel {

    private val listModel = DefaultListModel<LogEntry>()
    private val listComponent: JList<LogEntry> = JBList(listModel).apply {
        cellRenderer = LogEntryRenderer()
        visibleRowCount = 20
    }
    private val scrollPane = JBScrollPane(listComponent)
    val component: JBPanel<JBPanel<*>> = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        add(scrollPane, BorderLayout.CENTER)
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
        listModel.clear()
        source
            .asSequence()
            .filter { filter.isAllowed(it) }
            .filter { search.matches(it) }
            .forEach { listModel.addElement(it) }
    }

    /** Exposed for tests that want to inspect the filtered set without going through Swing. */
    internal fun visible(): List<LogEntry> =
        source.filter { filter.isAllowed(it) && search.matches(it) }

    /**
     * Renders each entry on a single line. The prefix carries the detected
     * level so the user can scan for ERROR/FATAL lines at a glance. The
     * rest of the text is the raw payload, preserving the original log
     * exactly as it was on disk.
     */
    private inner class LogEntryRenderer : ColoredListCellRenderer<LogEntry>() {
        override fun customizeCellRenderer(
            list: JList<out LogEntry>,
            value: LogEntry?,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean,
        ) {
            if (value == null) return
            if (AnsiCodes.containsCodes(value.raw)) {
                AnsiCodes.segments(value.raw).forEach { segment ->
                    append(segment.text, attributesFor(segment.style))
                }
                toolTipText = AnsiCodes.strip(value.raw)
                return
            }
            val levelColor = colorFor(value.level)
            val levelName = value.level.name.padEnd(5)
            val prefix = if (value.timestamp != null) {
                "${value.timestamp} ${levelName}"
            } else {
                levelName
            }
            append(prefix, SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, levelColor))
            append("  ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
            val loggerName = value.logger
            if (!loggerName.isNullOrBlank()) {
                append(loggerName, SimpleTextAttributes(SimpleTextAttributes.STYLE_ITALIC, JBColor.GRAY))
                append(" — ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
            }
            append(RawText.summary(value), SimpleTextAttributes.REGULAR_ATTRIBUTES)
            toolTipText = value.raw
        }

        private fun attributesFor(style: AnsiCodes.Style): SimpleTextAttributes {
            var fontStyle = SimpleTextAttributes.STYLE_PLAIN
            if (style.bold) fontStyle = fontStyle or SimpleTextAttributes.STYLE_BOLD
            if (style.italic) fontStyle = fontStyle or SimpleTextAttributes.STYLE_ITALIC
            if (style.underline) fontStyle = fontStyle or SimpleTextAttributes.STYLE_UNDERLINE
            if (fontStyle == SimpleTextAttributes.STYLE_PLAIN && style.foreground == null && style.background == null) {
                return SimpleTextAttributes.REGULAR_ATTRIBUTES
            }
            return SimpleTextAttributes(fontStyle, style.foreground, style.background)
        }

        private fun colorFor(level: LogLevel): Color = when (level) {
            LogLevel.TRACE -> JBColor.GRAY
            LogLevel.DEBUG -> JBColor(Color(0x60, 0x80, 0xC0), Color(0x60, 0x80, 0xC0))
            LogLevel.INFO -> JBColor(Color(0x2C, 0x80, 0x2C), Color(0x41, 0xB1, 0x41))
            LogLevel.WARN -> JBColor(Color(0xC0, 0x80, 0x00), Color(0xE0, 0xB0, 0x40))
            LogLevel.ERROR -> JBColor(Color(0xC0, 0x40, 0x40), Color(0xFF, 0x60, 0x60))
            LogLevel.FATAL -> JBColor(Color(0x80, 0x10, 0x10), Color(0xFF, 0x40, 0x40))
            LogLevel.UNKNOWN -> JBColor.GRAY
        }
    }
}
