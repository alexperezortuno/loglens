package io.loglens.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import io.loglens.ai.AiProvider

/**
 * Plugin-wide preferences. Kept intentionally small for 0.1 — just the bits
 * the viewer needs to remember between sessions.
 *
 * Registered in `plugin.xml` as:
 * <applicationService serviceImplementation="io.loglens.settings.LogLensSettings"/>
 */
@State(
    name = "io.loglens.settings",
    storages = [Storage("loglens.xml")],
)
class LogLensSettings : PersistentStateComponent<LogLensSettings.State> {

    private var stateValue: State = State()

    override fun getState(): State = stateValue

    override fun loadState(state: State) {
        stateValue = state
    }

    data class State(
        var caseSensitive: Boolean = false,
        var regex: Boolean = false,
        var enabledTrace: Boolean = false,
        var enabledDebug: Boolean = false,
        var enabledInfo: Boolean = true,
        var enabledWarn: Boolean = true,
        var enabledError: Boolean = true,
        var enabledFatal: Boolean = true,
        var lastQuery: String = "",
        var enabledUnknown: Boolean = true,
        var savedFilters: MutableList<SavedFilter> = mutableListOf(),
        // AI settings (0.6)
        var aiEnabled: Boolean = false,
        var aiProviderId: String = "ollama",
        var aiOllamaBaseUrl: String = "http://localhost:11434",
        var aiOllamaModel: String = "llama3.1",
        var aiOpenAiBaseUrl: String = "",
        var aiOpenAiApiKey: String = "",
        var aiOpenAiModel: String = "",
        // Layout persistence (UX-001)
        var viewerSplitProportion: Float = 0.55f,
        var detailsPanelVisible: Boolean = true,
        var toolbarVisible: Boolean = true,
    )

    data class SavedFilter(
        var name: String = "",
        var loggerContains: String = "",
        var threadContains: String = "",
        var correlationContains: String = "",
        var excludeTerms: String = "",
        var fromTimestamp: String = "",
        var toTimestamp: String = "",
        var query: String = "",
        var regex: Boolean = false,
        var caseSensitive: Boolean = false,
        var enabledLevels: String = "INFO,WARN,ERROR,FATAL,UNKNOWN",
    )

    fun getAiProvider(): AiProvider {
        return when (stateValue.aiProviderId) {
            "ollama" -> AiProvider.Ollama(
                baseUrl = stateValue.aiOllamaBaseUrl,
                model = stateValue.aiOllamaModel,
            )
            "openai-compatible" -> AiProvider.OpenAiCompatible(
                baseUrl = stateValue.aiOpenAiBaseUrl,
                apiKey = stateValue.aiOpenAiApiKey,
                model = stateValue.aiOpenAiModel,
            )
            else -> AiProvider.Ollama()
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(): LogLensSettings = ApplicationManager.getApplication()
            .getService(LogLensSettings::class.java)
    }
}
