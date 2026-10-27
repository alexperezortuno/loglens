package io.loglens.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel
import java.awt.CardLayout

/**
 * Settings panel for LogLens with AI configuration (0.6+).
 */
class LogLensSettingsConfigurable : Configurable {

    private val aiEnabledCheckbox = javax.swing.JCheckBox("Enable local AI analysis")
    private val providerCombo = ComboBox<String>()
    private val ollamaUrlField = JBTextField()
    private val ollamaModelField = JBTextField()
    private val openAiUrlField = JBTextField()
    private val openAiApiKeyField = javax.swing.JPasswordField()
    private val openAiModelField = JBTextField()
    private val providerCardPanel = JPanel(CardLayout())

    private val settings = LogLensSettings.getInstance()

    override fun getDisplayName(): String = "LogLens"

    override fun createComponent(): JComponent {
        providerCombo.addItem("Ollama")
        providerCombo.addItem("OpenAI-compatible")

        val ollamaPanel = FormBuilder().apply {
            addLabeledComponent(JBLabel("Base URL:"), ollamaUrlField)
            addLabeledComponent(JBLabel("Model:"), ollamaModelField)
        }.getPanel()

        val openAiPanel = FormBuilder().apply {
            addLabeledComponent(JBLabel("Base URL:"), openAiUrlField)
            addLabeledComponent(JBLabel("API Key:"), openAiApiKeyField)
            addLabeledComponent(JBLabel("Model:"), openAiModelField)
        }.getPanel()

        providerCardPanel.add(ollamaPanel, "Ollama")
        providerCardPanel.add(openAiPanel, "OpenAI-compatible")

        providerCombo.addActionListener {
            (providerCardPanel.layout as CardLayout).show(providerCardPanel, providerCombo.selectedItem as String)
        }

        return FormBuilder().apply {
            addComponent(aiEnabledCheckbox)
            addLabeledComponent(JBLabel("Provider:"), providerCombo)
            addComponent(providerCardPanel)
            addComponentFillVertically(JPanel(), 0)
        }.getPanel()
    }

    override fun isModified(): Boolean {
        val state = settings.state
        return aiEnabledCheckbox.isSelected != state.aiEnabled
            || providerCombo.selectedItem != state.aiProviderId
            || ollamaUrlField.text != state.aiOllamaBaseUrl
            || ollamaModelField.text != state.aiOllamaModel
            || openAiUrlField.text != state.aiOpenAiBaseUrl
            || String(openAiApiKeyField.password) != state.aiOpenAiApiKey
            || openAiModelField.text != state.aiOpenAiModel
    }

    override fun apply() {
        val state = settings.state
        state.aiEnabled = aiEnabledCheckbox.isSelected
        state.aiProviderId = providerCombo.selectedItem as String
        state.aiOllamaBaseUrl = ollamaUrlField.text
        state.aiOllamaModel = ollamaModelField.text
        state.aiOpenAiBaseUrl = openAiUrlField.text
        state.aiOpenAiApiKey = String(openAiApiKeyField.password)
        state.aiOpenAiModel = openAiModelField.text
    }

    override fun reset() {
        val state = settings.state
        aiEnabledCheckbox.isSelected = state.aiEnabled
        providerCombo.setSelectedItem(state.aiProviderId)
        ollamaUrlField.text = state.aiOllamaBaseUrl
        ollamaModelField.text = state.aiOllamaModel
        openAiUrlField.text = state.aiOpenAiBaseUrl
        openAiApiKeyField.text = state.aiOpenAiApiKey
        openAiModelField.text = state.aiOpenAiModel
        (providerCardPanel.layout as CardLayout).show(providerCardPanel, state.aiProviderId)
    }
}
