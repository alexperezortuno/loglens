package io.loglens.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.Messages
import io.loglens.filter.AdvancedFilter
import io.loglens.filter.LevelFilter
import io.loglens.model.LogLevel
import io.loglens.search.LogSearch
import io.loglens.settings.LogLensSettings
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

/**
 * Compact toolbar above the log list. Each level toggle and the search
 * field push changes into the [viewer] via callbacks rather than reaching
 * into its internals — that keeps the viewer's API small.
 */
class LogViewerToolbar(
    private val viewer: LogViewerPanel,
) : Disposable {

    val component: JPanel = JPanel(GridBagLayout())
    private val queryField: JTextField = JTextField()
    private val regexToggle: JCheckBox = JCheckBox("Regex")
    private val caseToggle: JCheckBox = JCheckBox("Case")
    private val levelToggles: Map<LogLevel, JCheckBox>
    private val searchButton: JButton = JButton("Search")
    private val clearButton: JButton = JButton("Clear")
    private val loggerField: JTextField = JTextField(8)
    private val threadField: JTextField = JTextField(8)
    private val excludeField: JTextField = JTextField(8)
    private val fromField: JTextField = JTextField(16)
    private val toField: JTextField = JTextField(16)
    private val applyAdvancedButton: JButton = JButton("Apply")
    private val saveFilterButton: JButton = JButton("Save")
    private val presetCombo: JComboBox<String> = JComboBox()
    private val statusLabel: JLabel = JLabel(" ")

    init {
        queryField.toolTipText = "Search within the records currently loaded in the viewer."
        queryField.text = LogLensSettings.getInstance().state.lastQuery
        levelToggles = LogLevel.FILTERABLE.associateWith { level ->
            JCheckBox(level.display()).apply {
                isSelected = LogLensSettings.getInstance().let {
                    when (level) {
                        LogLevel.TRACE -> it.state.enabledTrace
                        LogLevel.DEBUG -> it.state.enabledDebug
                        LogLevel.INFO -> it.state.enabledInfo
                        LogLevel.WARN -> it.state.enabledWarn
                        LogLevel.ERROR -> it.state.enabledError
                        LogLevel.FATAL -> it.state.enabledFatal
                        LogLevel.UNKNOWN -> it.state.enabledUnknown
                    }
                }
                addActionListener { onFilterChanged() }
            }
        }

        val gbc = GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            fill = GridBagConstraints.HORIZONTAL
            weightx = 0.0
            anchor = GridBagConstraints.WEST
            insets.set(2, 2, 2, 2)
        }

        component.add(JLabel("Search:"), gbc)
        gbc.gridx = 1
        gbc.weightx = 1.0
        gbc.gridwidth = 5
        component.add(queryField, gbc)
        queryField.addActionListener { onSearchChanged() }
        gbc.gridwidth = 1

        gbc.gridx = 6
        gbc.weightx = 0.0
        component.add(regexToggle, gbc)
        gbc.gridx = 7
        component.add(caseToggle, gbc)
        gbc.gridx = 8
        component.add(searchButton, gbc)
        gbc.gridx = 9
        component.add(clearButton, gbc)

        regexToggle.isSelected = LogLensSettings.getInstance().state.regex
        caseToggle.isSelected = LogLensSettings.getInstance().state.caseSensitive
        regexToggle.addActionListener { onSearchChanged() }
        caseToggle.addActionListener { onSearchChanged() }
        searchButton.addActionListener { onSearchChanged() }
        clearButton.addActionListener { onClear() }
        applyAdvancedButton.addActionListener { onAdvancedChanged() }
        saveFilterButton.addActionListener { saveCurrentPreset() }
        presetCombo.addActionListener { applySelectedPreset() }
        populatePresets()

        gbc.gridx = 0
        gbc.gridy = 1
        gbc.gridwidth = 10
        gbc.weightx = 1.0
        component.add(buildLevelRow(), gbc)

        gbc.gridy = 2
        gbc.gridwidth = 10
        component.add(buildAdvancedRow(), gbc)

        gbc.gridy = 3
        component.add(statusLabel, gbc)

        // Push the initial state into the viewer so the toolbar reflects the
        // current selection immediately on attach.
        viewer.updateFilter(currentFilterFromView())
        viewer.updateSearch(currentSearchFromView())
        viewer.updateAdvancedFilter(currentAdvancedFilter())
    }

    /** Re-pull state from settings, e.g. after a settings reset. */
    fun refreshFromViewer() {
        val settings = LogLensSettings.getInstance().state
        levelToggles.forEach { (level, toggle) ->
            toggle.isSelected = when (level) {
                LogLevel.TRACE -> settings.enabledTrace
                LogLevel.DEBUG -> settings.enabledDebug
                LogLevel.INFO -> settings.enabledInfo
                LogLevel.WARN -> settings.enabledWarn
                LogLevel.ERROR -> settings.enabledError
                LogLevel.FATAL -> settings.enabledFatal
                LogLevel.UNKNOWN -> settings.enabledUnknown
            }
        }
        viewer.updateFilter(currentFilterFromView())
    }

    private fun buildLevelRow(): JPanel = JPanel().apply {
        levelToggles.forEach { (_, toggle) -> add(toggle) }
    }

    private fun buildAdvancedRow(): JPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
        add(javax.swing.JLabel("Logger"))
        add(loggerField)
        add(javax.swing.JLabel("Thread"))
        add(threadField)
        add(javax.swing.JLabel("Exclude"))
        add(excludeField)
        add(javax.swing.JLabel("From"))
        add(fromField)
        add(javax.swing.JLabel("To"))
        add(toField)
        add(applyAdvancedButton)
        add(presetCombo)
        add(saveFilterButton)
    }

    private fun onFilterChanged() {
        val settings = LogLensSettings.getInstance().state
        levelToggles.forEach { (level, toggle) ->
            when (level) {
                LogLevel.TRACE -> settings.enabledTrace = toggle.isSelected
                LogLevel.DEBUG -> settings.enabledDebug = toggle.isSelected
                LogLevel.INFO -> settings.enabledInfo = toggle.isSelected
                LogLevel.WARN -> settings.enabledWarn = toggle.isSelected
                LogLevel.ERROR -> settings.enabledError = toggle.isSelected
                LogLevel.FATAL -> settings.enabledFatal = toggle.isSelected
                LogLevel.UNKNOWN -> settings.enabledUnknown = toggle.isSelected
            }
        }
        viewer.updateFilter(currentFilterFromView())
    }

    private fun onSearchChanged() {
        val settings = LogLensSettings.getInstance().state
        settings.regex = regexToggle.isSelected
        settings.caseSensitive = caseToggle.isSelected
        settings.lastQuery = queryField.text
        viewer.updateSearch(currentSearchFromView())
    }

    private fun onAdvancedChanged() {
        viewer.updateAdvancedFilter(currentAdvancedFilter())
    }

    private fun onClear() {
        queryField.text = ""
        regexToggle.isSelected = false
        caseToggle.isSelected = false
        onSearchChanged()
    }

    private fun currentFilterFromView(): LevelFilter {
        val enabled = levelToggles.entries
            .asSequence()
            .filter { it.value.isSelected }
            .map { it.key }
            .toSet()
        return LevelFilter(enabled)
    }

    private fun currentSearchFromView(): LogSearch {
        val mode = if (regexToggle.isSelected) LogSearch.Mode.REGEX else LogSearch.Mode.PLAIN
        return LogSearch(
            query = queryField.text,
            mode = mode,
            caseSensitive = caseToggle.isSelected,
        )
    }

    private fun currentAdvancedFilter(): AdvancedFilter = AdvancedFilter(
        loggerContains = loggerField.text,
        threadContains = threadField.text,
        excludeTerms = excludeField.text,
        fromTimestamp = fromField.text,
        toTimestamp = toField.text,
    )

    private fun populatePresets() {
        presetCombo.removeAllItems()
        presetCombo.addItem("Presets")
        LogLensSettings.getInstance().state.savedFilters
            .filter { it.name.isNotBlank() }
            .forEach { presetCombo.addItem(it.name) }
    }

    private fun saveCurrentPreset() {
        val name = Messages.showInputDialog("Preset name:", "Save LogLens Filter", null)?.trim()
            ?.takeIf { it.isNotEmpty() } ?: return
        val settings = LogLensSettings.getInstance().state
        settings.savedFilters.removeAll { it.name == name }
        settings.savedFilters += LogLensSettings.SavedFilter(
            name = name,
            loggerContains = loggerField.text,
            threadContains = threadField.text,
            excludeTerms = excludeField.text,
            fromTimestamp = fromField.text,
            toTimestamp = toField.text,
            query = queryField.text,
            regex = regexToggle.isSelected,
            caseSensitive = caseToggle.isSelected,
            enabledLevels = levelToggles.filterValues { it.isSelected }.keys.joinToString(",") { it.name },
        )
        populatePresets()
        presetCombo.selectedItem = name
    }

    private fun applySelectedPreset() {
        val name = presetCombo.selectedItem as? String ?: return
        if (name == "Presets") return
        val preset = LogLensSettings.getInstance().state.savedFilters.firstOrNull { it.name == name } ?: return
        loggerField.text = preset.loggerContains
        threadField.text = preset.threadContains
        excludeField.text = preset.excludeTerms
        fromField.text = preset.fromTimestamp
        toField.text = preset.toTimestamp
        queryField.text = preset.query
        regexToggle.isSelected = preset.regex
        caseToggle.isSelected = preset.caseSensitive
        val enabled = preset.enabledLevels.split(',').mapNotNull { token ->
            runCatching { LogLevel.valueOf(token.trim()) }.getOrNull()
        }.toSet()
        levelToggles.forEach { (level, toggle) -> toggle.isSelected = level in enabled }
        val settings = LogLensSettings.getInstance().state
        settings.lastQuery = queryField.text
        settings.regex = regexToggle.isSelected
        settings.caseSensitive = caseToggle.isSelected
        onFilterChanged()
        onSearchChanged()
        onAdvancedChanged()
    }

    override fun dispose() = Unit

    companion object {
        /** Exposed for tests that need the underlying Swing component. */
        internal fun build(viewer: LogViewerPanel): JPanel = LogViewerToolbar(viewer).component
    }
}
